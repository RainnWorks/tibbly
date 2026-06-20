package co.rowm.osrsllm.cloud

import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable snapshot of the consent the player has granted us at plugin startup.
 *
 * REVIEWER NOTE — RAI-38:
 * Consent is captured once during `OsrsLlmHelperPlugin.startUp()` from the
 * player's saved RuneLite config, and is *not* mutated thereafter for the
 * lifetime of the plugin. This means a config flip can never silently start
 * sending data mid-session — the player must stop the plugin, change the
 * setting, and start it again, which mirrors how RuneLite gates every other
 * privacy-sensitive plugin.
 *
 * The single bool tracked here is the affirmative "yes, send my data to the
 * Rowm-hosted backend" toggle that the consent dialog wires up.
 */
class ConsentState private constructor(
    val accepted: Boolean,
    val acceptedAtMillis: Long,
) {
    companion object {
        private val held = AtomicReference<ConsentState?>(null)

        /**
         * Capture the consent for this plugin lifetime. Called exactly once from
         * `OsrsLlmHelperPlugin.startUp()`. Subsequent calls during the same lifetime
         * throw — the state is genuinely write-once.
         */
        @Synchronized
        fun freeze(accepted: Boolean, nowMillis: Long = System.currentTimeMillis()): ConsentState {
            check(held.get() == null) {
                "ConsentState already frozen for this plugin lifetime — call reset() on shutdown"
            }
            val s = ConsentState(accepted = accepted, acceptedAtMillis = nowMillis)
            held.set(s)
            return s
        }

        /** Plugin shutdown hook. */
        @Synchronized
        fun reset() {
            held.set(null)
        }

        /** Test-only accessor. */
        internal fun snapshot(): ConsentState? = held.get()
    }
}
