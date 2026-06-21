# Data disclosure — every byte that leaves the client

> This is the source-of-truth list. The plugin's `warning=` line, the in-plugin
> consent dialog, the marketing privacy page, and the `docs/architecture/
> DATA_FLOW.md` diagram all derive from this table. If a row is added here,
> all four downstream surfaces must be updated in the same PR.
>
> **Compliance bar:** every datum we transmit must be listed below, with
> *when* we send it, *why*, and the *user control* that prevents it.

## A. Connection-level metadata (sent every connection)

| # | Field | When | Why | User control |
|---|---|---|---|---|
| A1 | IP address | Every WSS connection, while cloud chat is on | TCP/TLS handshake — unavoidable; backend uses it for rate-limiting + abuse prevention | Disable cloud chat → no connection → no IP. Optionally use Tailscale / VPN as the user chooses. |
| A2 | Plugin version + RuneLite version + OS | Connection handshake | Backend gates new RPC shapes by version | Disable cloud chat. |
| A3 | Device key (random UUID, generated at first run, stored in plugin config) | Connection handshake | Anonymous identifier so backend can bill the right customer | User can rotate from the dashboard; uninstalling the plugin deletes it from local config. |

## B. Player identity (sent on chat session start)

| # | Field | When | Why | User control |
|---|---|---|---|---|
| B1 | In-game player display name (`Client.localPlayer.name`) | When the user starts a chat session AND is logged into the game | LLM grounding ("you have...", "your..."); also used to bind multiple OSRS accounts to one paying customer | Cloud chat off → never sent. Logged-out → never sent (we read `null`). User can choose "anonymous mode" config flag → we send `"anonymous"` and the LLM addresses generically. |
| B2 | Account type (Iron / HC / GIM / Main / League / DMM) | First chat per session | LLM advice is account-type specific (HC iron shouldn't go to Vorkath turn 1) | Cloud chat off → never sent. |
| B3 | Combat level | First chat per session | High-leverage for advice; tiny payload | Cloud chat off → never sent. |
| B4 | World number | First chat per session | LLM may suggest a different world (e.g. soulwars world). Public info — visible to anyone in-game. | Cloud chat off → never sent. |

## C. Chat input (sent every message)

| # | Field | When | Why | User control |
|---|---|---|---|---|
| C1 | The text the user typed into our chat panel | Every user message | LLM input | The user typed it intending it to be sent. |
| C2 | Optional attached screenshot of the game view | Only if user clicks the "attach screenshot" button | LLM can read game state we don't expose via tools | Per-message opt-in (button is not pressed by default). Image is cropped to the game viewport, no desktop capture. |

## D. On-demand player state (sent only as tool-call responses)

The LLM running on our backend selects a tool, asks the plugin to invoke it,
and the plugin returns the result. We **do not** push these proactively. They
flow only as RPC responses to a specific tool call. The LLM is rate-limited
in how often it can call any tool family per chat turn.

| # | Tool family | Fields | When | Why | User control |
|---|---|---|---|---|---|
| D1 | `stats()` | All 23 skill levels + XP + combat level | LLM calls when goal/skilling questions | Goal planning | Per-tool-family allow-list in plugin config. Cloud chat off → never sent. |
| D2 | `inventory()` | Item IDs + names + quantities | LLM calls when "what should I bank" / "do I have a..." questions | Gear advice | Per-tool allow-list. Plugin-side redact list (user can mark item IDs to omit). |
| D3 | `bank(query, limit)` | Item IDs + names + quantities; bounded by `limit` (default 50) | LLM calls when bank-relevant questions | Same | Per-tool allow-list. Same redact list. **Never sent in full** — always filtered by query string. |
| D4 | `equipment()` | Item ID per slot + slot stats if asked | LLM calls when combat/gear questions | Gear advice | Per-tool allow-list. |
| D5 | `player()` | World coords (tile X/Y/plane), area name, run energy, prayer, HP, special attack | LLM calls when location/state questions | Routing advice ("teleport to falador") | Per-tool allow-list. World coords are *already* broadcast in-game to nearby players; not a secret. |
| D6 | `quests(filter)` | Quest names + state (started/finished/incomplete) | LLM calls when "next quest" / quest-cape questions | Quest planning | Per-tool allow-list. |
| D7 | `buffs()` | Active prayers, active overhead prayer, vengeance status, antifire timer, stamina, divine boosts | LLM calls when combat-prep questions | PvM advice | Per-tool allow-list. |
| D8 | `combat()` | Last attack style, last weapon, autoretaliate state | LLM calls when combat questions | Combat advice | Per-tool allow-list. |
| D9 | `diary()` | Diary progress per region per tier | LLM calls when "what diary should I do" questions | Diary planning | Per-tool allow-list. |
| D10 | `spellbook()` | Current spellbook | LLM calls when teleport/magic questions | Routing advice | Per-tool allow-list. |
| D11 | `poh()` | POH room layout, jewellery box / teleport throne tier, nexus presets | LLM calls when "use POH to get to..." questions | Routing advice | Per-tool allow-list. |
| D12 | `combatAchievements()` | Completed CA IDs + tier progress | LLM calls when CA-related questions | CA planning | Per-tool allow-list. |
| D13 | `slayer()` | Current task monster + count + master | LLM calls when slayer questions | Task strategy | Per-tool allow-list. |
| D14 | `geOffers()` | Current GE offers (item ID, qty, price, completed qty) | LLM calls when "how's my flip" questions | Flip advice | Per-tool allow-list. *No prices sent to the GE on user behalf — never. We only read.* |
| D15 | `nearbyNpcs()` | NPC IDs + names + tile coords + HP within render distance | LLM calls when "what's that monster" / aggro questions | Identification | Per-tool allow-list. Public in-world data. |
| D16 | `nearbyObjects()` | Object IDs + names + tile coords within render distance | LLM calls when "where's the bank" questions | Routing | Per-tool allow-list. Public in-world data. |
| D17 | `groundItems()` | Ground item IDs + names + tile coords within render distance | LLM calls when "what dropped" questions | Loot identification | Per-tool allow-list. Public in-world data. |
| D18 | `accountIdentity()` (RAI-5) | RuneLite-exposed account type (NORMAL/IRONMAN/UIM/HCIM/GIM/HCGIM), isIronman / isGroupIronman flags, world id, world type flags (MEMBERS / PVP / DEADMAN / SEASONAL / …), world host, launcher display name (Jagex account) | LLM calls when account-type-sensitive advice is required | "don't suggest trading on an Ironman", "don't risk HCIM at Vorkath" | Per-tool allow-list. Cloud chat off → never sent. Launcher name is already shown in-game for Jagex accounts. |
| D19 | `raidLayout()` (RAI-5) | Which raid is active (CoX / ToB / ToA / NONE), CoX room state + total points, ToB phase + party orb states (alive / dead / dc), ToA invocation level + party HP, current map regions | LLM calls when raid-strategy / phase / party questions are asked | "what phase of Verzik am I on", "what's our invocation", "is anyone dead" | Per-tool allow-list. Cloud chat off → never sent. All values are already visible to every player in the raid. |
| D20 | `targetProjectiles()` (RAI-5) | Projectile graphic id, remaining game ticks, target name, source name — only for projectiles aimed at the local player or their current target. Capped at 8 entries. | LLM calls when combat advice needs flick timing | "what prayer should I flick" | Per-tool allow-list. Cloud chat off → never sent. Projectile graphic IDs are public game data. |
| D21 | `activePrayers()` (RAI-5) | Active standard prayers + active Ruinous Powers prayers (as human-readable names), current/max prayer points, whether quick-prayer is toggled on | LLM calls when prayer-state questions are asked | "am I praying right", "switch to Augury" | Per-tool allow-list. Cloud chat off → never sent. Prayer state is broadcast in-game via the overhead icon. |
| D22 | `farmingSummary()` (RAI-5) | Coarse patch histogram: counts of READY / GROWING / DISEASED / DEAD / EMPTY / UNKNOWN patches across every region in the v1 table (Catherby, Falador, Morytania, Ardougne, Hosidius, Farming Guild, Gnome Stronghold, Lletya, Prifddinas, etc.). Reads live `FARMING_TRANSMIT_*` varbits only — no historical / per-profile state | LLM calls when "do I have anything to harvest" questions are asked | "are my herbs ready", "anything diseased in my patches" | Per-tool allow-list. Cloud chat off → never sent. All values are public farming patch state already visible to anyone walking into the patch. |
| D23 | `farmingPatches(region)` (RAI-5) | Per-region farming detail: list of `{patchName, type (HERB/ALLOTMENT/FRUIT_TREE/TREE/BUSH/FLOWER), state, rawVarbit}` for the patches in the requested region. Mirrors RuneLite's package-private `FarmingWorld` mapping for the high-leverage regions; v2 adds HARDWOOD_TREE / CACTUS / SPIRIT_TREE / CALQUAT / ANIMA / CELASTRUS / REDWOOD / CRYSTAL_TREE / HESPORI / MUSHROOM / BELLADONNA / CORAL / GRAPES | LLM calls when "what's growing in region X" questions are asked | "are my Catherby herbs ready", "anything in the Farming Guild fruit tree patch" | Per-tool allow-list. Cloud chat off → never sent. All values are public farming patch state already visible to anyone walking into the region. |

## D-bis. Account panel fetches (RuneLite in-plugin Tibbly panel — D-8)

The in-plugin "Tibbly account" sidebar (see
`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/AccountPanel.kt`) issues
short read-only HTTPS calls through [EgressGate.egressHttp] when the player
opens the panel and at most every 30 seconds while it is mounted. All calls
are gated on `cloudChatEnabled && consentAccepted` — if either is off, the
panel renders a static explainer and makes no network calls.

| # | Endpoint | Sent | Received | Why | User control |
|---|---|---|---|---|---|
| D-bis-1 | `GET /v1/account/summary` | SHA-256 device-key hash (header), optional in-game player name (header — same value already disclosed as B1) | Tier badge string, subscription status string, renewal date, list of paired OSRS character names already known to the backend, list of paired device display names | Render the panel header + paired-account list | Disable cloud chat → no fetch. |
| D-bis-2 | `GET /v1/account/usage-proxy` | SHA-256 device-key hash (header) | Tier-aware proxy *only*: either `messages used today / messages per day` (free / Hobbyist), or `subscription active + renewal date` (Pro), or `unlimited` (Iron). NEVER raw token counts. | Render the "Today" card | Disable cloud chat → no fetch. |
| D-bis-3 | `POST /v1/billing/portal` | SHA-256 device-key hash (header), empty JSON body | A Stripe-hosted portal URL the plugin opens in the system browser | Player manages payment without leaving RuneLite | Don't click "Manage subscription". |
| D-bis-4 | `GET /v1/me/export` | SHA-256 device-key hash (header) | A JSON dump of every row the backend has attributed to this user (Art. 15 / Art. 20). Saved via a JFileChooser dialog. | GDPR data export | Don't click "Download my data". |
| D-bis-5 | `DELETE /v1/me` | SHA-256 device-key hash (header) | 204 No Content (cascade-deletes the user record) | GDPR Art. 17 right-to-erasure | Don't click "Delete my account". |
| D-bis-6 | `DELETE /v1/accounts/:id` | SHA-256 device-key hash (header), an account id the panel already received in D-bis-1 | 200 OK / 404 | Unpair a single OSRS character | Don't click "Forget". |

## D-tris. BYO mode — direct third-party LLM calls (Tier 2)

When `chatMode` is set to one of `byo-anthropic` / `byo-openai` /
`byo-openrouter` (see
`apps/plugin/src/main/kotlin/co/rowm/osrsllm/OsrsLlmHelperConfig.kt`),
the plugin's chat path talks DIRECTLY to the chosen provider's HTTPS
endpoint using the player's own API key. The request **never** transits
the Tibbly backend.

The actual HTTP client (`DirectChatRunner.kt`) lands in a follow-up PR;
this table fixes the contract so the disclosure is correct the moment
that runner is wired in. Until the runner ships, the plugin only logs
"BYO chat mode active" and makes no outbound BYO request.

| # | Destination | Sent | Why | User control |
|---|---|---|---|---|
| D-tris-1 | `https://api.anthropic.com/v1/messages` (BYO Anthropic only) | The text the player typed in the chat panel; player-selected model id; `Authorization: Bearer <byoApiKey>`; chat history within the session. NO Tibbly device key, NO Tibbly backend involvement. | Player gets a chat response from Anthropic directly | Set `chatMode` to anything other than `byo-anthropic`. Clear `byoApiKey`. |
| D-tris-2 | `https://api.openai.com/v1/chat/completions` (BYO OpenAI only) | Same shape as D-tris-1, signed with the OpenAI key | Player gets a chat response from OpenAI directly | Set `chatMode` to anything other than `byo-openai`. Clear `byoApiKey`. |
| D-tris-3 | `https://openrouter.ai/api/v1/chat/completions` (BYO OpenRouter only) | Same shape as D-tris-1, signed with the OpenRouter key. Player-selected model id may name any model OpenRouter routes. | Player gets a chat response from OpenRouter directly | Set `chatMode` to anything other than `byo-openrouter`. Clear `byoApiKey`. |
| D-tris-4 | `https://api.tibbly.io/v1/feedback/rate` (BYO telemetry — opt-in only) | Provider name, mode label, round-trip latency in ms, plugin version. NO player name, NO API key, NO message content, NO game state. | Publish an uptime dashboard for BYO users so the hub reviewer can see the feature works | Off by default. Toggle `byoTelemetryOptIn` off in the config UI. |

**Key handling.** The API key is stored in the RuneLite config with
`secret = true` (rendered password-masked in the config UI). The plugin
build is gated by the `:checkNoKeyLeak` Gradle task — any literal API
key prefix in production Kotlin sources fails the build. The key value
is **never** logged, **never** echoed in chat replies, and **never**
included in error messages or telemetry pings.

**Allow-list.** When `DirectChatRunner.kt` lands (next PR) it will only
call the three host names listed above (exact match — no wildcards).
The `EgressGate` will reject any other host.

## E. NEVER sent

| Field | Why never |
|---|---|
| OSRS password / authenticator code | The plugin can't read them; RuneLite doesn't expose them. |
| Email address from Jagex account | Not exposed by RuneLite client. |
| Any keystroke other than what the user typed into our chat panel | We don't hook keyboard events. |
| Mouse position / clicks | We don't read those. |
| Other RuneLite plugin state | Plugin sandbox boundary. |
| Files from the user's filesystem | We never call `File*` APIs except for the config one we own. |
| Chat messages typed in-game by the user | We don't read `ChatMessage` events for transmission; only for our own UI to show context. We could lift this restriction *only* if we add a per-feature opt-in. |
| Friends-list and clan-chat member names | Not in scope (Wise Old Man does this; we don't need to). |

## E2. Embodied companion (RAI-67)

The embodied-companion feature adds three plugin → backend frames and one
backend → plugin frame on the existing authenticated WS link. No new
network destination, no new identifiers leave the client. All frames are
gated by the same master cloud-chat toggle in section G.

| #   | Field                              | When                                           | Why                                                                          | User control                                                                            |
|-----|------------------------------------|------------------------------------------------|------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| E2a | `companion_trigger.triggerType`    | Plugin fires when a trigger condition matches  | Backend needs to know which trigger (login / death / hover_examine / ...)    | Cloud chat off → never sent. Per-trigger toggle in plugin config (RAI-68 follow-up).    |
| E2b | `companion_trigger.contextSnapshot`| Same                                           | Free-form snapshot the plugin pre-renders into a 1-2 sentence "right now"    | Cloud chat off → never sent. The snapshot only carries data already covered by B+D.      |
| E2c | `companion_interaction_event`      | Player clicks the sprite / names it / etc.     | Backend updates `companion_profile` (nickname, style notes, forget command)  | The player explicitly performed the UI action.                                          |
| E2d | `companion_memory_hint`            | Plugin flags an in-game beat as memorable      | Backend queues it for end-of-session memory extraction                       | Cloud chat off → never sent. Plugin-side allow-list for which probes ship as evidence.  |
| E2e | `companion_line` (server → plugin) | Backend reply containing the proactive line    | The plugin renders the text in the speech bubble                              | N/A; this is data flowing TO the plugin.                                                |

Backend-side state introduced:

- `companion_profile` (one row per `(user_id, osrs_account_id)`) — stores
  the visual starter pick, personality archetype, optional nickname, and
  the cumulative voice-style notes (capped at 20).
- `companion_memories` — up to ~50 single-sentence beats per profile,
  each with a decaying weight; rows below the weight floor are
  soft-forgotten.

Privacy posture:

- Memories are scoped per `(user_id, osrs_account_id)`. A user with
  multiple OSRS characters keeps a separate companion per character; the
  backend never blends state across characters even when they share a
  billing principal.
- The `/v1/me` GDPR export (Art. 15) returns both companion tables.
- The `/v1/me` GDPR delete (Art. 17) cascades through both tables.
- The in-game `forget` command is the friendly per-session version of
  Art. 17 — it soft-marks recent memories without touching the rest of
  the account.

The LLM call that synthesises each `companion_line` runs against the
cheap-tier model via `chooseCheapModel(tier)`; the end-of-session
extraction reads `model_catalog` for the cheapest non-retired Anthropic
model. No model id is hardcoded (D-9). Companion paths never use Opus,
regardless of billing tier.

## F. Retention and use, backend side

| Field | Retained | Used for | TTL |
|---|---|---|---|
| Chat messages (in + out) | Yes | Conversation history within a session; thread continuity | 30 days, then deleted (configurable per tier) |
| Tool-call request/response payloads | Yes | Replay / debugging / audit; can be reviewed by user from dashboard | 30 days |
| Token-usage counters per device key | Yes | Stripe billing | Forever (anonymised after account delete) |
| IP address | Logged for rate-limit, not persisted past 24h | Abuse | 24h |
| OSRS player name binding → Stripe customer | Yes | Multi-account billing | Until user unbinds |
| `companion_profile` rows | Yes | Personality + nickname + style notes for the embodied companion | Until user unbinds (Art. 17 cascade) |
| `companion_memories` rows | Yes | One-sentence beats the companion can recall on future sessions | Weighted decay, soft-forgotten when weight < 0.1; Art. 17 cascade |

## G. User controls (in plugin config)

- **Cloud chat** — master toggle. Off by default.
- **Per-tool-family allow-list** — checkboxes for each row in section D. All on by default *only after* the user toggles cloud chat on for the first time (consent dialog).
- **Redact items** — comma-separated item IDs the plugin should drop from any payload (D2/D3/D4).
- **Anonymous mode** — replace player name with `"anonymous"` in B1.
- **Disable nearby-world snapshots** — disables D15/D16/D17 globally.
- **Wipe local data** — clears device key, consent, chat history.
- **Open privacy policy** — button to `osrsllm.app/privacy`.
- **Export my data** — button to download a JSON of everything the backend has stored for this device key.

## H. Compliance map — where each row gets surfaced

| Surface | Rows it must list |
|---|---|
| Manifest `warning=` | One sentence summarising A1, B1, C1, and the D-family generally. |
| First-run consent dialog | All rows in A, B, C, D (collapsed); "Manage per-tool sharing" button to expand. |
| Plugin About tab | Summary of A, B, C, D, E; link to docs site. |
| Marketing site `osrsllm.app/privacy` | Full table, plus retention (F). |
| `docs/architecture/DATA_FLOW.md` | Full sequence diagram (this PR). |

If a new tool is added to the plugin and it sends any field not already in this
table, the SUBMISSION_CHECKLIST.md item 4.1 (cross-reference) FAILS until this
table is updated and the four surfaces above are kept in sync.
