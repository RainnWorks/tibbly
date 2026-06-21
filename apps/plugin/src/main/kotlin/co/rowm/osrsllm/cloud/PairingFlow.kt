package co.rowm.osrsllm.cloud

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RAI-23 — orchestrates the device pairing dance.
 *
 *   1. Plugin generates / loads a device key (see [DeviceKey]).
 *   2. Plugin POSTs `/v1/pairing/request` with the SHA-256 hash of the raw key.
 *      Backend returns a 6-char code + 10-minute expiry.
 *   3. UI shows the code; the user enters it on the dashboard, which calls
 *      `/v1/pairing/claim` server-side and binds the device to the customer.
 *   4. Plugin polls `/v1/pairing/status?code=...` until the row is claimed
 *      OR the user closes the modal OR the code expires.
 *
 * All outbound traffic goes through [EgressGate.egressHttp]. The flow itself
 * has no transport code — it builds JSON bodies, hands them to the gate,
 * and reasons about the response.
 *
 * Cancellation: the consumer can stop polling by calling [cancel] (the modal
 * calls this on close).
 */
@Singleton
class PairingFlow @Inject constructor(
    private val egress: EgressGate,
    private val deviceKey: DeviceKey,
    private val backendUrlSupplier: BackendUrlSupplier,
) {
    private val log = LoggerFactory.getLogger(PairingFlow::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val activePoll = AtomicReference<Thread?>(null)

    @Serializable
    data class RequestBody(
        val deviceKey: String,
        val playerName: String? = null,
    )

    /**
     * Result of [requestCode] — the 6-char code the player types into the
     * dashboard, plus its ISO-8601 expiry from the backend.
     */
    data class CodeIssued(
        val code: String,
        val expiresAt: String,
    )

    sealed class PollOutcome {
        data class Claimed(val playerName: String?) : PollOutcome()
        object Pending : PollOutcome()
        object Expired : PollOutcome()
        data class Error(val message: String) : PollOutcome()
    }

    /**
     * Ask the backend to issue a fresh pairing code for this device.
     *
     * @param playerName optional — `client.getLocalPlayer().getName()` if the
     *   user is currently logged in; backend stores it for nicer UX.
     */
    fun requestCode(playerName: String?): CodeIssued {
        val raw = deviceKey.getOrCreate()
        val hashed = DeviceKey.hashForTransport(raw)
        val body = json.encodeToString(
            RequestBody.serializer(),
            RequestBody(deviceKey = hashed, playerName = playerName),
        )
        val backendUrl = backendUrlSupplier.get()
        val resp = egress.egressHttp(
            method = "POST",
            backendUrl = backendUrl,
            path = "/v1/pairing/request",
            bodyJson = body,
        )
        if (resp.status !in 200..299) {
            throw PairingException("pairing/request failed: ${resp.status} ${resp.body}")
        }
        val obj = json.parseToJsonElement(resp.body) as? JsonObject
            ?: throw PairingException("pairing/request returned non-object body: ${resp.body}")
        val code = obj["code"]?.jsonPrimitive?.content
            ?: throw PairingException("pairing/request missing 'code': ${resp.body}")
        val expiresAt = obj["expiresAt"]?.jsonPrimitive?.content
            ?: throw PairingException("pairing/request missing 'expiresAt': ${resp.body}")
        log.info("Pairing code issued (length={}, expiresAt={})", code.length, expiresAt)
        return CodeIssued(code = code, expiresAt = expiresAt)
    }

    /**
     * One-shot poll: ask the backend whether [code] has been claimed yet.
     *
     * The backend may not have a status endpoint at the moment we ship — if
     * it returns 404, we treat that as [PollOutcome.Pending] so the UI can
     * keep waiting. A 410 means the code is gone (expired or already
     * claimed elsewhere) and we surface [PollOutcome.Expired].
     */
    fun pollOnce(code: String): PollOutcome {
        val backendUrl = backendUrlSupplier.get()
        val resp = try {
            egress.egressHttp(
                method = "GET",
                backendUrl = backendUrl,
                path = "/v1/pairing/status?code=$code",
                bodyJson = null,
            )
        } catch (e: Exception) {
            return PollOutcome.Error(e.message ?: e::class.simpleName ?: "unknown")
        }
        return when (resp.status) {
            in 200..299 -> {
                val obj = (json.parseToJsonElement(resp.body) as? JsonObject)
                    ?: return PollOutcome.Error("status returned non-object: ${resp.body}")
                val claimedAt = obj["claimedAt"]?.stringOrNull()
                val playerName = obj["playerName"]?.stringOrNull()
                if (claimedAt.isNullOrBlank()) PollOutcome.Pending
                else PollOutcome.Claimed(playerName = playerName)
            }
            404 -> PollOutcome.Pending
            410 -> PollOutcome.Expired
            else -> PollOutcome.Error("status returned ${resp.status}: ${resp.body}")
        }
    }

    /**
     * Spawn a background poll loop that calls [pollOnce] every
     * [intervalMillis] until one of: claimed, expired, [cancel] is called,
     * or the code's overall TTL elapses. Results land on [onResult] (called
     * on the polling thread).
     *
     * Returns immediately; the consumer is expected to dismiss any UI from
     * the result callback.
     */
    fun startPolling(
        code: String,
        ttlMillis: Long = DEFAULT_POLL_TTL_MILLIS,
        intervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
        onResult: (PollOutcome) -> Unit,
    ) {
        cancel()
        val deadline = System.currentTimeMillis() + ttlMillis
        val t = Thread({
            while (!Thread.currentThread().isInterrupted) {
                if (System.currentTimeMillis() >= deadline) {
                    onResult(PollOutcome.Expired)
                    return@Thread
                }
                val outcome = pollOnce(code)
                if (outcome !is PollOutcome.Pending) {
                    onResult(outcome)
                    return@Thread
                }
                try {
                    Thread.sleep(intervalMillis)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@Thread
                }
            }
        }, "osrsllm-pairing-poll").apply { isDaemon = true }
        activePoll.set(t)
        t.start()
    }

    /** Stop any in-flight poll. Safe to call when no poll is running. */
    fun cancel() {
        val t = activePoll.getAndSet(null)
        runCatching { t?.interrupt() }
    }

    class PairingException(message: String) : RuntimeException(message)

    /**
     * Hook so the plugin can supply the current backend URL (read once per
     * call from config) without [PairingFlow] depending on RuneLite config
     * classes. Tests pass a fixed-value supplier.
     */
    fun interface BackendUrlSupplier {
        fun get(): BackendUrl
    }

    companion object {
        /** 10 minutes — matches the backend's PAIRING_TTL_MS. */
        const val DEFAULT_POLL_TTL_MILLIS: Long = 10 * 60 * 1000L
        /** Poll cadence. Cheap GET; 3s feels responsive without being noisy. */
        const val DEFAULT_POLL_INTERVAL_MILLIS: Long = 3_000L
    }
}

/**
 * Returns the string content of a [kotlinx.serialization.json.JsonElement], or
 * `null` if the element is `JsonNull` or not a primitive. `jsonPrimitive` on
 * `JsonNull` yields the string `"null"`, which is rarely what callers want.
 */
private fun kotlinx.serialization.json.JsonElement.stringOrNull(): String? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> if (isString) content else content.takeIf { it != "null" }
    else -> null
}

