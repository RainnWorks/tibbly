package co.rowm.osrsllm.cloud

/**
 * Typed wrapper around the backend WebSocket URL.
 *
 * REVIEWER NOTE — RAI-38:
 * This is the ONLY type the plugin uses to represent the backend endpoint.
 * Construction throws on anything other than the [WSS_PREFIX] scheme, so an
 * accidental plaintext or non-WebSocket configuration value cannot survive
 * past startup. That means there is no code path through the production
 * source set that can transmit player data over plaintext.
 *
 * The Gradle task `:checkNoPlaintextUrls` enforces that no string literal
 * matching a plaintext URL scheme survives in the `cloud/` or `plugin/`
 * source sets — see `apps/plugin/build.gradle.kts`.
 */
@JvmInline
value class BackendUrl(val value: String) {
    init {
        require(value.startsWith(WSS_PREFIX)) {
            "BackendUrl must start with '$WSS_PREFIX' (refusing plaintext / non-WebSocket transport). got=${redact(value)}"
        }
        require(value.length > WSS_PREFIX.length) {
            "BackendUrl host is empty"
        }
    }

    override fun toString(): String = value

    companion object {
        // Kept as a constant so the only literal protocol prefix in the production
        // source set is here, in a value class that refuses anything else.
        const val WSS_PREFIX: String = "wss://"

        /** Strip a possibly-secret query string before logging. */
        private fun redact(raw: String): String {
            val q = raw.indexOf('?')
            return if (q < 0) raw else "${raw.substring(0, q)}?<redacted>"
        }
    }
}
