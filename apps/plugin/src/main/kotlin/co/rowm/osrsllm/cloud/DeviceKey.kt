package co.rowm.osrsllm.cloud

import net.runelite.client.config.ConfigManager
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RAI-23 — local device key, generated once on first install and persisted via
 * RuneLite's `ConfigManager` under (group="osrsllm", key="deviceKey").
 *
 * SECURITY MODEL:
 *  - The raw key is a 40-character nanoid drawn from a URL-safe alphabet.
 *    40 chars × log2(64) = 240 bits of entropy.
 *  - The plugin only ever sends the SHA-256 hash of the raw key over the wire
 *    (see [hashForTransport]). The raw key never leaves this device.
 *  - The backend stores an Argon2 hash of whatever the plugin sends, so even
 *    a database compromise can't be replayed against the device.
 *  - Generation is single-shot: callers go through [getOrCreate] which is
 *    idempotent — re-installing the plugin without wiping the RuneLite config
 *    keeps the same device key.
 *
 * The raw key is NEVER logged. Only the first 4 chars of the hash appear in
 * debug logs, just enough to disambiguate "did we generate" vs "did we load".
 */
@Singleton
class DeviceKey @Inject constructor(
    private val store: Store,
) {
    private val log = LoggerFactory.getLogger(DeviceKey::class.java)

    /**
     * Thin wrapper around the bits of ConfigManager we touch. Lets us mock
     * persistence in unit tests without instantiating a real ConfigManager
     * (whose constructor is private and pulls in half of RuneLite).
     */
    interface Store {
        fun read(): String?
        fun write(value: String)
        fun clear()
    }

    /**
     * Return the persisted raw device key, generating + persisting one on the
     * very first call for this RuneLite install.
     *
     * Thread-safe: synchronized so two simultaneous startups can't race and
     * write two different keys.
     */
    @Synchronized
    fun getOrCreate(): String {
        val existing = store.read()
        if (!existing.isNullOrBlank() && isValid(existing)) {
            log.debug("DeviceKey loaded (hashPrefix={})", hashForTransport(existing).take(4))
            return existing
        }
        val fresh = generate()
        store.write(fresh)
        log.info("DeviceKey generated (hashPrefix={})", hashForTransport(fresh).take(4))
        return fresh
    }

    /**
     * Test / reset helper — wipes the persisted key. The very next
     * [getOrCreate] call will generate a fresh one.
     */
    fun reset() {
        store.clear()
    }

    companion object {
        const val CONFIG_GROUP: String = "osrsllm"
        const val CONFIG_KEY: String = "deviceKey"
        const val LENGTH: Int = 40

        /**
         * URL-safe nanoid alphabet — same 64-char set as the JS reference impl.
         * Excludes nothing because the key never appears in a URL path; we
         * just need ample entropy in a small fixed length.
         */
        private const val ALPHABET: String =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"

        private val rng: SecureRandom = SecureRandom()

        /** Generate a fresh 40-char nanoid-style device key. */
        fun generate(): String {
            val bytes = ByteArray(LENGTH)
            rng.nextBytes(bytes)
            val sb = StringBuilder(LENGTH)
            for (b in bytes) {
                sb.append(ALPHABET[(b.toInt() and 0x3F)])
            }
            return sb.toString()
        }

        /**
         * SHA-256 hex of the raw key — what gets transmitted to the backend.
         * The pairing endpoint accepts the hex string as `deviceKey`; the
         * backend Argon2-hashes whatever we send before storage.
         */
        fun hashForTransport(rawKey: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(rawKey.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                sb.append(HEX[v ushr 4])
                sb.append(HEX[v and 0x0F])
            }
            return sb.toString()
        }

        /** Cheap sanity check: right length and every char drawn from the alphabet. */
        fun isValid(key: String): Boolean {
            if (key.length != LENGTH) return false
            for (c in key) {
                if (ALPHABET.indexOf(c) < 0) return false
            }
            return true
        }

        private val HEX: CharArray = "0123456789abcdef".toCharArray()
    }
}

/**
 * Production [DeviceKey.Store] implementation backed by RuneLite's
 * [ConfigManager]. Lives next to [DeviceKey] so the production wiring is
 * easy to grep for.
 */
@Singleton
class ConfigManagerDeviceKeyStore @Inject constructor(
    private val configManager: ConfigManager,
) : DeviceKey.Store {
    override fun read(): String? =
        configManager.getConfiguration(DeviceKey.CONFIG_GROUP, DeviceKey.CONFIG_KEY)

    override fun write(value: String) {
        configManager.setConfiguration(DeviceKey.CONFIG_GROUP, DeviceKey.CONFIG_KEY, value)
    }

    override fun clear() {
        configManager.unsetConfiguration(DeviceKey.CONFIG_GROUP, DeviceKey.CONFIG_KEY)
    }
}
