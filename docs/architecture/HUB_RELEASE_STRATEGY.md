# Hub release strategy + three-tier value model

*Captured 2026-06-21 from a Tom ↔ Claude conversation about whether the
RuneLite Plugin Hub will accept a plugin whose backend is a paid SaaS.*

## The question

> "How likely are we to get this product actually in the RuneLite store
> given that we're not supposed to make money off it?"

## The actual rule

The hub's posture (per `docs/runelite-hub/POLICY_SUMMARY.md` lines 85-90)
is NOT "no commercial plugins". It's:

- No in-game automation, no Jagex ToS violations
- No localhost HTTP exposing player info (the literal reason PR #11453
  was rejected — already addressed by RAI-38's `EgressGate` pivot)
- No runtime library/code download
- Backend uptime: the moment the cloud backend goes silent, the hub
  auto-disables the plugin manifest

**Direct precedent**: **ScapeGPT** (PR #4271, merged 2023) shipped as a
paid OpenAI frontend plugin. Later auto-disabled when the backend went
down, not for policy reasons. This is the pattern we follow.

## Probability estimate (honest)

| Outcome | Odds |
|---|---|
| First submission accepted | ~40% |
| Accepted after maintainer feedback iteration | ~70% |
| Permanent rejection | ~15% |
| Eventually auto-disabled because of backend downtime | high, eventually |

The bigger existential risk isn't the hub gate — it's **Jagex's posture**
on AI co-pilots as a category. RuneLite has operated under tacit Jagex
tolerance for ~10 years; nothing in that tolerance is a license. If
Jagex's bot-detection or ToS team makes a determination against the
category, paying customers could get banned overnight regardless of
hub status.

## What we need to build to be honest in the hub-version

The hub-version must work *without* the player needing a Tibbly
subscription. "Tools-only mode without an LLM" isn't actually a product
— without an interpreter the tools are useless to a player. So the
honest answer is a three-tier value floor inside the same plugin binary.

### Tier 1 — Zero infra, zero key: local structured panels

Pure local. No LLM, no Tibbly backend, no third-party API.

The plugin already has 72+ game-state probes (`StateProbes.kt`,
`FarmingTables.kt`, etc.). Surface them as RuneLite-style sidebar
panels:

- **Bank tab viewer** — filterable, sortable, GE-price-annotated.
- **Farming patch grid** — ready / growing / diseased / dead state per
  patch, region-grouped.
- **Slayer task detail** — current monster + wiki snippet + best gear
  recommendation from the static knowledge base.
- **Quest progress board** — current step highlighted, prerequisites
  flagged.

This is the **value floor** — what every plugin like Quest Helper or
Slayer Plus already does. It doesn't sell the paid product but it's
something the hub reviewer can install, run offline, and immediately
get useful behaviour from. No network, no LLM, no backend required.

**Engineering**: each panel is ~1 day's work wrapping the existing
probes in Swing UI.

### Tier 2 — Free tier: bring your own LLM key

The player pastes their own OpenAI / Anthropic / OpenRouter API key
into a config field. The chat panel works exactly like Tibbly cloud
mode, but the request goes **plugin → provider**, never through our
backend. We don't see the message, the data, or take a cut.

This is exactly how **ScapeGPT** got merged in 2023 — the pattern the
hub maintainers have already approved.

**Engineering** (~2-3 days):

- New `apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/DirectChatRunner.kt`
  that uses OkHttp to talk to `api.anthropic.com` / `openrouter.ai`
  / `api.openai.com` directly. Sibling to `CloudChatRunner.kt` (which
  stays as the Tibbly-cloud path).
- Settings UI extension: a "Mode" dropdown — **Tools only** /
  **Direct (BYO key)** / **Tibbly cloud**. Defaults to Tools only on
  hub install.
- Key storage: RuneLite config (`configManager.setConfiguration(...)`)
  treated as sensitive. NEVER logged. NEVER echoed in chat. NEVER
  embedded in error messages.
- `EgressGate` extended to accept `OutboundPayload.DirectChatRequest`
  with a destination URL allow-list (`api.anthropic.com`,
  `openrouter.ai`, `api.openai.com` — exact host match, no wildcards).
- New `:checkNoKeyLeak` Gradle guard. Mirrors the
  `:checkAccountPanelNoRawTokens` pattern from PR #40: greps the
  production Kotlin sources for any literal "OPENAI_API_KEY" /
  "ANTHROPIC_API_KEY" / "sk-" prefix outside the
  `DirectChatRunner.kt` request body and fails the build.
- Tests:
  - Round-trip against a mock OpenAI/Anthropic endpoint
  - Allow-list rejection for a non-allow-listed host
  - Key never appears in `AuditLog`
  - Settings UI hides the key field's contents (`.password` glyph)

### Tier 3 — Paid tier: Tibbly cloud

The current `CloudChatRunner.kt` path. What the £7 / £19 / £49 tiers
buy:

- **Managed routing** — Haiku → Sonnet → Opus escalation per tier,
  no key management.
- **Tier-aware quotas** with hard caps. Player-friendly "23/30
  messages used today" proxy (PR #40), not raw token counts.
- **Account binding** to OSRS characters. Multiple OSRS accounts on
  one paying customer.
- **Multi-device sync** — paired plugins on different machines see
  the same chat history and balance.
- **Billing in £** — Stripe, hard cap at zero, no surprise overage.
- **Conversation memory** across sessions (server-side).

## The honest marketing pitch

> "Tibbly works for free with your own OpenAI key. Upgrade to a Tibbly
> subscription when you want it to *just work* — managed routing, no
> API keys, OSRS account sync, and a fairer per-message cap than your
> provider's per-token billing."

The same plugin binary, three modes selectable in config. The hub
reviewer can install and test tier 1 (tools only — fully working).
A power user can use tier 2 (BYO key) free forever. Paying customers
get tier 3.

## Hub-submission timeline

**Phase A: sideload via `external-plugin-hub` (week 0-12).**

Skip the hub PR initially. Ship the plugin via the `external-plugin-hub`
config field that RuneLite supports. Players install by pasting a URL.
Zero hub friction; we get users, telemetry, and a clean uptime record
to point at.

**Phase B: hub PR submission (week 12+).**

After 1-3 months of:
- Clean backend uptime record (publishable dashboard URL).
- A community of sideload users who can vouch for the plugin in PR
  comments (this is what Group Ironman Tracker did).
- Marketing materials that don't read as in-plugin advertising.
- The "Direct mode" tier 2 demonstrably working in the hub-version
  binary without any Tibbly infra at all.

**Phase C: maintain in hub (ongoing).**

Treat backend uptime as P0. The ScapeGPT auto-disable is the most
documented failure mode. Ops console gets a "hub manifest health"
widget that monitors RuneLite's plugin manifest endpoint for our
plugin's `disabled` field; alert on transition to `true`.

## What would tank odds

- Aggressive in-plugin paywall nags or upsell modals.
- Locking essential tools behind the paywall (the tools listed in
  Tier 1 must work for free, no exceptions).
- "Open browser to buy" links inside the plugin chat.
- Framing the plugin description as ad-revenue or affiliate-driven.
- Any HTTP server in the plugin (the `:checkMcpServerGated` Gradle
  guard from PR #27 already enforces this at build time).
- Going non-responsive in PR review (RuneGPT PR #7459 was closed for
  non-response, not policy).

## What would move odds further up

1. Open-source the WS protocol spec + plugin RPC schemas (Q-17 default
   was "spec yes, backend no" — stand by that, but actually ship the
   spec repo).
2. Get a sideload user community of 200+ before submitting.
3. Tier 1 panels shipped and tested (so the hub reviewer sees the
   plugin is useful with zero infra).
4. Tier 2 BYO-key mode shipped and tested.
5. Publishable backend uptime page.
6. Pre-PR outreach to a sympathetic maintainer (not for approval —
   just to surface category questions before the public PR).

## Open questions for Tom

- **Q-22**: ship Tier 1 panels + Tier 2 BYO-key for the hub version,
  or sideload-only initially with cloud-only in the plugin? Default:
  build all three tiers; tier 1 + 2 unlock the hub submission path
  but ALSO are useful product surface for paying customers (a Pro
  player still wants the bank-tab viewer panel).
- **Q-23**: when to start the 12-week hub clock? Default: after
  Tier 1 + Tier 2 land + we have 50+ sideload users.
- **Q-24**: rebrand from "Tibbly" to a less-AI-coded name for the
  hub submission? Some hub maintainers may pattern-match harder on
  "ChatGPT for OSRS" than on the actual technical merits. Default: no,
  the brand voice ("the clever friend who already read the wiki") is
  intentional. Hub maintainers are technical; the name doesn't matter.

## Reversibility

Everything in this strategy is reversible. The 3-tier value model is
additive — tier 3 (paid cloud) is what already exists; tier 1 and 2
are NEW code that extends the plugin without removing anything. If we
ship them and then realise the hub will accept a cloud-only version,
the tier 1 + 2 code stays as a free-forever product floor.
