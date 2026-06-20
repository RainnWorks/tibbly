# Differentiation — why we win the AI-for-OSRS category

Researcher: agent R6 (RAI-10). Date: 2026-06-21.
Pair this with `landscape.md` before writing any marketing copy.

---

## The one-line wedge (hero candidate)

> **The only OSRS co-pilot that sees your game live and bills by the token —
> no API keys, no copy-paste, no botting.**

Backup hero variants, each leaning on a different competitor weakness:

- **"You don't have to describe your inventory. We already see it."**
  (Hits the wiki-RAG school: brandoninkel, kineticquant.)
- **"AI for OSRS, without the Anthropic billing setup."**
  (Hits the BYO-key plugins: Scrambles56, SteveDoesCoding, nickbeddows.)
- **"Trained models go stale every Wednesday. We don't."**
  (Hits the fine-tune school: kineticquant.)
- **"Helps you play. Never plays for you."**
  (Hits the bot-codegen school: harmindersinghnijjar — and Jagex TOS panic.)

Primary recommendation: lead with the one-liner above on the hero, then use
"You don't have to describe your inventory" as the H2 immediately below.

---

## The wedge in one sentence

Live in-client tool calls + a billing relationship + Plugin-Hub-shaped
distribution. **Nobody else has all three.** Most don't have any.

## Why the wedge is defensible

### 1 · Live in-client tool calls

The five competitors that touch live game state (Scrambles56,
SteveDoesCoding, nickbeddows-ctrl, plus the two near-empty repos) all do it
by hosting a local HTTP MCP server inside the RuneLite plugin. That design
is the **exact pattern the Plugin Hub rejected** in PR #11453 — quoting
maintainer riktenx, *"Plugins which expose player information over HTTP."*

Our design moves the listener out of the player's machine entirely. The
plugin opens an **outbound** WebSocket to our backend; the backend is the
LLM driver; tool calls travel back over the same socket. Net effect:

- No inbound port on the player's machine.
- No `127.0.0.1:NNNN` HTTP listener.
- No `ProcessBuilder`, no SSH key generation, no shell-out of any kind.
- TLS-only transport.

That is precisely the surface that *should* clear the Hub bar that killed
nickbeddows. If it does, we own the discovery channel. If it doesn't, we
ship side-loaded like everyone else does today — but we still ship from a
single signed jar with a one-line install, not a "run three terminals"
setup.

### 2 · Billing relationship

**Every other competitor is BYO-API-key.** The user opens
console.anthropic.com (or platform.openai.com), generates a key, pastes it
into a plugin config file, and from that moment forward is their own
billing department. This is fatal to consumer onboarding. We do this work
for them: the dashboard handshakes a Stripe Customer to the device key the
plugin already mints, credits land server-side, every chat turn debits a
counter that we already meter for OpenRouter cost-control reasons (see
`docs/architecture/TOOL_ECONOMY.md`).

### 3 · Plugin-Hub-shaped distribution

The Plugin Hub is the only mass-discovery channel that matters for
RuneLite users. Quest Helper has 555K installs *because* it's in the Hub.
WikiSync has 302K installs *because* it's in the Hub. Side-loaded plugins
get hundreds at best — the install path requires `--developer-mode`, which
trips a warning dialog and selects for power users only. By designing the
transport from day one around the rules the Hub rejected nickbeddows under,
we keep the door open. If we earn entry, we win discovery overnight.

---

## The four-quadrant view

Two axes: knowledge-grounded vs game-state-aware, free vs hosted.

```
                 Game-state aware
                        |
   nickbeddows-ctrl     |     ← us (game-state + hosted, sole occupant)
   Scrambles56          |
   SteveDoesCoding      |
   (all BYO key)        |
                        |
 ───────────────────────┼───────────────────────  Hosted / billed
                        |
   brandoninkel         |
   kineticquant         |     (no occupant)
   (RAG over wiki,      |
    no game state)      |
                        |
                   Free / self-host
```

The upper-right quadrant — *game-state-aware + hosted billing* — is empty.
That is the lane we sprint into.

## What competitors do better than us (today) — copy these

- **Scrambles56's personality presets.** "Wise Old Man / Drunken Dwarf /
  Proud Dad" is a UX win that costs nothing to ship. Steal this pattern
  for our companion-tone system. See `docs/marketing/BRAND_VOICE.md` for
  current direction.
- **SteveDoesCoding's Wise Old Man integration.** Their plugin reads
  XP-gain history from wiseoldman.net. Add this as an M1 tool — it
  unlocks "your XP/hour over the last week" answers without us tracking
  anything ourselves. Hits R2 community pain point P5 (progress framing).
- **kineticquant's "trained on all OSRS content" pitch.** They are wrong
  about the approach (stale weights), but the *positioning* — "knows
  OSRS deeply" — is the right consumer message. We earn the same message
  by grounding live retrieval against the wiki.

## What competitors fail at — own these

- **Setup friction.** Every BYO-key plugin gates first chat behind an
  Anthropic console signup, billing setup, key generation, and a plugin
  config file edit. Our path: install plugin, click "pair to account",
  log in to dashboard, swipe Stripe, chat. Five clicks vs. a 20-minute
  developer ritual.
- **Pricing legibility.** The BYO-key model means the player has no idea
  what the chat is costing until the Anthropic invoice arrives. Our
  dashboard shows tokens and pence per turn in real time.
- **Stale knowledge.** kineticquant's fine-tune locked in January 2026.
  The Sailing rebalances of May 2026 (per R2 community research) and the
  Leagues 6 — Demonic Pacts content are *not in their weights.* Our tool
  call hits the live wiki and the live GE feed, so we are always current.
- **TOS risk.** RunescapeGPT carries an explicit "this violates Jagex TOS"
  disclaimer. We carry the opposite: a one-liner above the fold, *"Helps
  you play. Never plays for you,"* and we tie this to the
  scope-guard non-goals in `docs/agents/SCOPE_GUARD.md`.

## Marketing copy hooks that drop straight in

- **Above the fold (primary recommendation):**
  *"The only OSRS co-pilot that sees your game live and bills by the
  token — no API keys, no copy-paste, no botting."*
- **Sub-header:**
  *"You don't have to describe your inventory. We already see it."*
- **Pricing-page reframe vs BYO-key plugins:**
  *"Other AI plugins ask you to set up an Anthropic billing account
  before your first chat. We just take Stripe."*
- **Comparison-table column header (vs Quest Helper):**
  *"Knows every quest. And every item in your bank. And the price of
  every item in your bank."*
- **Footer / FAQ defensive line:**
  *"We're a normal RuneLite plugin. Read-only of game state, outbound
  WebSocket to our servers, no automation, no input synthesis. Jagex-
  friendly by design."*

---

## What this means for the build tonight

- Keep the transport story tight: **plugin → outbound WSS → backend**.
  Document this in `docs/architecture/SYSTEM.md` precisely because PR
  #11453 lives in our rear-view mirror.
- M1 tool surface must include `get_player_gains(name, period)` against
  the Wise Old Man API — the only competitor-feature parity we owe.
- Marketing landing page must answer two questions above the fold:
  (a) "is this a bot?" — no, never, here's why; (b) "do I have to
  set up Anthropic billing?" — no, we handle it.
- Scope-guard reaffirms no automation, no RMT helpers, no client
  modifications outside the plugin sandbox. These are the lines that
  make us defensibly different from RunescapeGPT and safe-bot-osrs.

---

## Sources

- All sources cited in `landscape.md`.
- Direct quotes from PR #11453 comments (raiyni, riktenx)
  pulled via `gh api repos/runelite/plugin-hub/issues/11453/comments`.
- R2 community research summary at
  `docs/research/community/_SUMMARY.md`.
- Brand voice direction at `docs/marketing/BRAND_VOICE.md`.
- Scope guard at `docs/agents/SCOPE_GUARD.md`.
