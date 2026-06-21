package co.rowm.osrsllm.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RAI-23 — local device key generation + persistence.
 *
 * The key requirements:
 *
 *   1. A freshly-installed plugin generates a 40-char nanoid-alphabet key.
 *   2. Subsequent `getOrCreate()` calls return the SAME value (idempotent).
 *   3. The SHA-256 hash is deterministic for a given input and is what gets
 *      sent over the wire — the raw key never leaves this device.
 *   4. A corrupted persisted value is replaced rather than trusted.
 *   5. `reset()` wipes persistence and the next call regenerates.
 */
class DeviceKeyTest {

    /** In-memory test double for [DeviceKey.Store] — no RuneLite ConfigManager. */
    private class FakeStore : DeviceKey.Store {
        var stored: String? = null
        var writes: Int = 0
        var clears: Int = 0
        override fun read(): String? = stored
        override fun write(value: String) {
            stored = value
            writes++
        }
        override fun clear() {
            stored = null
            clears++
        }
    }

    @Test
    fun `generate produces 40-char key from the nanoid alphabet`() {
        val key = DeviceKey.generate()
        assertEquals(40, key.length)
        assertTrue(
            "generated key contains non-alphabet character: $key",
            DeviceKey.isValid(key),
        )
    }

    @Test
    fun `generate produces high-entropy unique values`() {
        val k1 = DeviceKey.generate()
        val k2 = DeviceKey.generate()
        assertNotEquals("two consecutive generate() calls must not collide", k1, k2)
    }

    @Test
    fun `getOrCreate writes a fresh key on first call`() {
        val store = FakeStore()
        val key = DeviceKey(store).getOrCreate()
        assertEquals(40, key.length)
        assertEquals(key, store.stored)
        assertEquals(1, store.writes)
    }

    @Test
    fun `getOrCreate is idempotent on subsequent calls`() {
        val store = FakeStore()
        val device = DeviceKey(store)
        val first = device.getOrCreate()
        val second = device.getOrCreate()
        val third = device.getOrCreate()
        assertEquals(first, second)
        assertEquals(second, third)
        assertEquals("write must happen exactly once", 1, store.writes)
    }

    @Test
    fun `getOrCreate replaces an invalid persisted value`() {
        val store = FakeStore().apply { stored = "this is not a valid 40-char nanoid" }
        val device = DeviceKey(store)
        val key = device.getOrCreate()
        assertTrue(DeviceKey.isValid(key))
        assertNotEquals("invalid value should be replaced", "this is not a valid 40-char nanoid", key)
    }

    @Test
    fun `getOrCreate replaces a blank persisted value`() {
        val store = FakeStore().apply { stored = "" }
        val key = DeviceKey(store).getOrCreate()
        assertTrue(DeviceKey.isValid(key))
        assertEquals(1, store.writes)
    }

    @Test
    fun `reset clears persistence so next call regenerates`() {
        val store = FakeStore()
        val device = DeviceKey(store)
        val first = device.getOrCreate()
        device.reset()
        assertNull(store.stored)
        assertEquals(1, store.clears)
        val second = device.getOrCreate()
        assertNotEquals("reset must yield a different key on next call", first, second)
    }

    @Test
    fun `hashForTransport is deterministic and 64-char hex`() {
        val key = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmn"
        val h1 = DeviceKey.hashForTransport(key)
        val h2 = DeviceKey.hashForTransport(key)
        assertEquals(h1, h2)
        assertEquals(64, h1.length)
        assertTrue("hex chars only: $h1", h1.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `hashForTransport differs for different inputs`() {
        assertNotEquals(
            DeviceKey.hashForTransport("alpha"),
            DeviceKey.hashForTransport("beta"),
        )
    }

    @Test
    fun `hashForTransport never returns the raw key`() {
        val raw = DeviceKey.generate()
        val hashed = DeviceKey.hashForTransport(raw)
        assertNotEquals(raw, hashed)
        assertFalse("hash must not contain the raw key prefix",
            hashed.startsWith(raw.take(10)))
    }

    @Test
    fun `isValid rejects wrong length`() {
        assertFalse(DeviceKey.isValid(""))
        assertFalse(DeviceKey.isValid("short"))
        assertFalse(DeviceKey.isValid("A".repeat(39)))
        assertFalse(DeviceKey.isValid("A".repeat(41)))
        assertTrue(DeviceKey.isValid("A".repeat(40)))
    }

    @Test
    fun `isValid rejects keys with characters outside the alphabet`() {
        val bad = "A".repeat(39) + "!"
        assertFalse(DeviceKey.isValid(bad))
        val withSpace = "A".repeat(39) + " "
        assertFalse(DeviceKey.isValid(withSpace))
    }

    @Test
    fun `device key constants stay aligned with the persistence schema`() {
        assertEquals("osrsllm", DeviceKey.CONFIG_GROUP)
        assertEquals("deviceKey", DeviceKey.CONFIG_KEY)
        assertEquals(40, DeviceKey.LENGTH)
    }

    @Test
    fun `instance and store wiring are exposed as expected`() {
        val store = FakeStore()
        val device = DeviceKey(store)
        assertNotNull(device.getOrCreate())
    }
}
