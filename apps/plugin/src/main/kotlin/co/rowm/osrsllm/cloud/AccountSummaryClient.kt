package co.rowm.osrsllm.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D-8 plugin-pivot: thin HTTP client for `/v1/account/summary` +
 * `/v1/account/usage-proxy`.
 *
 * Every call goes through [EgressGate.egressHttp] — the same secure-only HTTP
 * door the pairing flow uses. There is no other transport path; the gate
 * enforces `https://` (derived from the configured `wss://` backend URL) and
 * audits every request.
 *
 * # What the panel renders, server-side computed
 *
 * The backend computes the tier-aware proxy and the masked DTOs; this client
 * does NOT re-derive token counts, balances, or any raw billing fields.
 * If the backend changes the shape, the [AccountSummary] / [UsageProxy]
 * sealed deserialisation will throw — the panel surfaces "couldn't load"
 * rather than rendering a stale UI.
 *
 * # Auth (RAI-39)
 *
 * We send the SHA-256 of the device key as an `Authorization: Bearer` token.
 * The backend argon2-verifies it against `devices.device_key_hash`
 * (the SAME value it stored at pairing time, since the pairing payload
 * is the SHA-256 too). The `x-device-key` header is a non-authoritative
 * hint used by `/v1/account/summary` to mark `(this RuneLite)` on the
 * paired-device list.
 *
 * The raw device key NEVER leaves the plugin; only the SHA-256 transport
 * hash does. Audit C1/C3 closed: backend no longer treats the bearer as
 * a raw userId — it verifies it as a real credential.
 *
 * Sensitive fields (raw email, Stripe customer id, balance tokens) are
 * stripped server-side before they reach the wire, so this DTO surface is
 * deliberately small.
 */
@Singleton
class AccountSummaryClient @Inject constructor(
    private val egress: EgressGate,
    private val deviceKey: DeviceKey,
    private val backendUrlSupplier: PairingFlow.BackendUrlSupplier,
) {
    private val log = LoggerFactory.getLogger(AccountSummaryClient::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "form"
    }

    /** Result of [fetchSummary] — either a parsed DTO or a human-readable error. */
    sealed class SummaryResult {
        /** Backend returned 200 with a parseable body. */
        data class Ok(val summary: AccountSummary) : SummaryResult()

        /** Backend returned a non-2xx, or the body wouldn't parse. */
        data class Error(val message: String) : SummaryResult()
    }

    /** Result of [fetchUsageProxy] — either a tier-aware proxy or a human error. */
    sealed class UsageResult {
        data class Ok(val proxy: UsageProxy) : UsageResult()
        data class Error(val message: String) : UsageResult()
    }

    /**
     * GET `/v1/account/summary`. Blocking; callers must hop off the Swing EDT
     * (the panel uses a background `SwingWorker`).
     *
     * @param currentPlayerName the in-game `client.localPlayer.name` if logged in,
     *   so the backend can mark the matching `osrsAccounts` row as `isCurrent`.
     */
    fun fetchSummary(currentPlayerName: String?): SummaryResult {
        val raw = deviceKey.getOrCreate()
        val hashed = DeviceKey.hashForTransport(raw)
        val backendUrl = backendUrlSupplier.get()
        val extraHeaders = mutableListOf<Pair<String, String>>(
            "Authorization" to "Bearer $hashed",
            "x-device-key" to hashed,
        )
        if (!currentPlayerName.isNullOrBlank()) {
            extraHeaders += "x-current-player" to currentPlayerName
        }
        val resp = try {
            egress.egressHttp(
                method = "GET",
                backendUrl = backendUrl,
                path = "/v1/account/summary",
                bodyJson = null,
                headers = extraHeaders,
            )
        } catch (e: Exception) {
            log.debug("account/summary failed: {}", e.message)
            return SummaryResult.Error(e.message ?: e::class.simpleName ?: "unknown")
        }
        if (resp.status !in 200..299) {
            return SummaryResult.Error("HTTP ${resp.status}")
        }
        return try {
            SummaryResult.Ok(json.decodeFromString(AccountSummary.serializer(), resp.body))
        } catch (e: Exception) {
            log.debug("account/summary parse failed: {}", e.message)
            SummaryResult.Error("parse: ${e.message}")
        }
    }

    /** GET `/v1/account/usage-proxy`. */
    fun fetchUsageProxy(): UsageResult {
        val raw = deviceKey.getOrCreate()
        val hashed = DeviceKey.hashForTransport(raw)
        val backendUrl = backendUrlSupplier.get()
        val resp = try {
            egress.egressHttp(
                method = "GET",
                backendUrl = backendUrl,
                path = "/v1/account/usage-proxy",
                bodyJson = null,
                headers = listOf(
                    "Authorization" to "Bearer $hashed",
                    "x-device-key" to hashed,
                ),
            )
        } catch (e: Exception) {
            log.debug("account/usage-proxy failed: {}", e.message)
            return UsageResult.Error(e.message ?: e::class.simpleName ?: "unknown")
        }
        if (resp.status !in 200..299) {
            return UsageResult.Error("HTTP ${resp.status}")
        }
        return try {
            UsageResult.Ok(json.decodeFromString(UsageProxy.serializer(), resp.body))
        } catch (e: Exception) {
            log.debug("account/usage-proxy parse failed: {}", e.message)
            UsageResult.Error("parse: ${e.message}")
        }
    }

    /**
     * Open the Stripe customer portal in the system browser.
     *
     * The plugin first POSTs to `/v1/billing/portal`, which returns a `{ url }`.
     * We then hand that off to `java.awt.Desktop.browse` — RuneLite ships a
     * desktop AWT shim so this works on every supported platform.
     */
    fun openCustomerPortal(): String? {
        val raw = deviceKey.getOrCreate()
        val hashed = DeviceKey.hashForTransport(raw)
        val backendUrl = backendUrlSupplier.get()
        val resp = try {
            egress.egressHttp(
                method = "POST",
                backendUrl = backendUrl,
                path = "/v1/billing/portal",
                bodyJson = "{}",
                headers = listOf(
                    "Authorization" to "Bearer $hashed",
                    "x-device-key" to hashed,
                ),
            )
        } catch (e: Exception) {
            log.debug("billing/portal failed: {}", e.message)
            return null
        }
        if (resp.status !in 200..299) return null
        return try {
            val obj = json.parseToJsonElement(resp.body) as? kotlinx.serialization.json.JsonObject
                ?: return null
            (obj["url"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * GDPR Art. 15 / Art. 20 — download the player's data as JSON bytes.
     * Returns the raw response body so the caller (Swing JFileChooser path)
     * can write it to disk. NEVER cached on disk by this client.
     */
    fun downloadExport(): String? {
        val raw = deviceKey.getOrCreate()
        val hashed = DeviceKey.hashForTransport(raw)
        val backendUrl = backendUrlSupplier.get()
        val resp = try {
            egress.egressHttp(
                method = "GET",
                backendUrl = backendUrl,
                path = "/v1/me/export",
                bodyJson = null,
                headers = listOf(
                    "Authorization" to "Bearer $hashed",
                    "x-device-key" to hashed,
                ),
            )
        } catch (e: Exception) {
            log.debug("me/export failed: {}", e.message)
            return null
        }
        return if (resp.status in 200..299) resp.body else null
    }

    /**
     * GDPR Art. 17 — hard delete. Returns true on 2xx, false otherwise.
     * The panel re-confirms with a typed "DELETE" dialog before calling.
     */
    fun deleteAccount(): Boolean {
        val raw = deviceKey.getOrCreate()
        val hashed = DeviceKey.hashForTransport(raw)
        val backendUrl = backendUrlSupplier.get()
        val resp = try {
            egress.egressHttp(
                method = "DELETE",
                backendUrl = backendUrl,
                path = "/v1/me",
                bodyJson = null,
                headers = listOf(
                    "Authorization" to "Bearer $hashed",
                    "x-device-key" to hashed,
                ),
            )
        } catch (e: Exception) {
            log.warn("me delete failed: {}", e.message)
            return false
        }
        return resp.status in 200..299
    }

    /**
     * DELETE `/v1/accounts/:id` — unpair an OSRS character from the user.
     * Returns true on 2xx, false otherwise.
     */
    fun forgetOsrsAccount(id: String): Boolean {
        val raw = deviceKey.getOrCreate()
        val hashed = DeviceKey.hashForTransport(raw)
        val backendUrl = backendUrlSupplier.get()
        val resp = try {
            egress.egressHttp(
                method = "DELETE",
                backendUrl = backendUrl,
                path = "/v1/accounts/$id",
                bodyJson = null,
                headers = listOf(
                    "Authorization" to "Bearer $hashed",
                    "x-device-key" to hashed,
                ),
            )
        } catch (e: Exception) {
            log.warn("accounts delete failed: {}", e.message)
            return false
        }
        return resp.status in 200..299
    }
}

/* ---------------------------------------------------------------- DTOs ---- */

/**
 * Mirrors `AccountSummaryDTO` in `apps/backend/src/api/account.ts`. Renaming
 * a field here without renaming it there is a wire-break — both sides decode
 * with strict-ish settings (the client ignores unknown fields so the backend
 * can add new ones non-breakingly, but the named fields below must match).
 */
@Serializable
data class AccountSummary(
    val tier: String? = null,
    val subscriptionStatus: String? = null,
    val renewsAt: String? = null,
    val pairedOsrsAccounts: List<PairedOsrsAccount> = emptyList(),
    val pairedDevices: List<PairedDevice> = emptyList(),
    val consent: ConsentInfo = ConsentInfo(),
)

@Serializable
data class PairedOsrsAccount(
    val id: String,
    val displayName: String,
    val accountType: String,
    val isCurrent: Boolean = false,
)

@Serializable
data class PairedDevice(
    val id: String,
    val displayName: String? = null,
    val lastSeenAt: String? = null,
    val isCurrent: Boolean = false,
)

@Serializable
data class ConsentInfo(
    val canDeleteAccount: Boolean = true,
)

/**
 * Mirrors `UsageProxyDTO` in `apps/backend/src/api/account.ts`.
 *
 * Discriminator: `form`. Every variant is the *tier-aware proxy* the panel
 * renders verbatim — there is no token-arithmetic field here, by design.
 * The `:checkAccountPanelNoRawTokens` Gradle gate greps for the forbidden
 * identifiers in panel sources to enforce it.
 */
@Serializable
sealed class UsageProxy {
    @Serializable
    @SerialName("messages-left")
    data class MessagesLeft(
        val messagesUsedToday: Int,
        val messagesPerDay: Int,
    ) : UsageProxy()

    @Serializable
    @SerialName("subscription-active")
    data class SubscriptionActive(
        val renewsAt: String,
    ) : UsageProxy()

    @Serializable
    @SerialName("unlimited")
    object Unlimited : UsageProxy()
}

/** Local helper — `JsonPrimitive.contentOrNull` is missing in some serialization versions. */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    runCatching { this.content }.getOrNull()
