# Competitor research — one-page summary

Researcher: agent R6 (RAI-10). Date: 2026-06-21.

Read this before writing marketing copy, pricing copy, or the FAQ.

---

## The category, in three sentences

The AI-for-OSRS category is **unowned**. The most-starred entry in the
space is a botting code generator (24 stars). Every player-facing AI
assistant we found tops out at **1 star** and is BYO-API-key, side-loaded,
and unbilled.

---

## The headline finding

**No AI/LLM plugin has been merged into the RuneLite Plugin Hub.** The
single submission (PR #11453, nickbeddows-ctrl, opened 2026-04-10) was
rejected on 2026-05-15 under the rule *"Plugins which expose player
information over HTTP."* Whoever ships the first Hub-acceptable design
captures the discovery channel. Our outbound-WebSocket transport is
purpose-built to clear that bar.

---

## The seven competitors at a glance

| # | Project | What it is | Distribution | Pricing | Star signal | Sentiment |
| - | ------- | ---------- | ------------ | ------- | ----------- | --------- |
| 1 | OSRS MCP — nickbeddows-ctrl | RuneLite plugin, local MCP HTTP server, 40+ tools | Side-loaded (Plugin Hub rejected) | BYO via Claude Desktop / Cursor | 1 ⭐ | Rejected by maintainers; design is the precedent we cannot repeat |
| 2 | osrs-ai-companion — Scrambles56 | RuneLite plugin, sidebar chat, milestone reactions, personality presets | Side-loaded | BYO Anthropic key | 1 ⭐ | Personal project; no measurable community |
| 3 | osrs-ai-assistant — SteveDoesCoding | RuneLite plugin, Wise Old Man + GE + wiki tools | Side-loaded | BYO Anthropic key | 0 ⭐ | No traction |
| 4 | OSRS_AI_System — brandoninkel | Stand-alone web GUI, RAG over 35.9K wiki embeddings, LangGraph + Ollama | GitHub source, three terminals | Local LLaMA 3.1 | 1 ⭐ | Heavy docs, light adoption |
| 5 | osrs-llm — kineticquant | Fine-tuned Phi-3.5-mini-instruct, distributed model | HuggingFace / GitHub | Free, BYO hardware | 1 ⭐ | Stale the moment OSRS patches |
| 6 | RunescapeGPT — harmindersinghnijjar | GPT-3.5 wrapper that generates DreamBot scripts | Docker | BYO OpenAI key | 24 ⭐ | Highest demand in category — but TOS-violating |
| 7 | Eight near-empty GitHub repos | template forks, abandoned, joke repos | — | — | 0 ⭐ each | Tracked in landscape.md so we don't re-research |

Full evidence with quotes and URLs in `landscape.md`.

---

## The wedge, in one paragraph

We are the only OSRS AI tool that combines **live in-client game state**
(via outbound WebSocket, not local HTTP — Hub-friendly by design),
**a real billing relationship** (Stripe-managed credits, not a BYO
Anthropic key the player has to register for), and **fresh retrieval**
(live wiki + live GE feed via tool calls, not stale-by-design fine-tuned
weights). The upper-right quadrant of "game-state aware + hosted billing"
has no occupant.

Full argument with copy hooks in `differentiation.md`.

---

## The hero candidate

> **The only OSRS co-pilot that sees your game live and bills by the
> token — no API keys, no copy-paste, no botting.**

Sub-header: *"You don't have to describe your inventory. We already see it."*

Backup heros and copy hooks for the pricing page, comparison table, and
defensive FAQ all live in `differentiation.md`.

---

## What competitors do better than us (today)

- **Personality presets** (Scrambles56) — copy this for the companion-tone
  system. Cheap UX win.
- **Wise Old Man integration** (SteveDoesCoding) — wire `wiseoldman.net`
  into our M1 tool surface as `get_player_gains(name, period)`.
- **"Knows OSRS deeply" positioning** (kineticquant) — own this message,
  earn it with live retrieval instead of a stale fine-tune.

## What competitors fail at — own these in copy

- **Setup friction.** BYO-key onboarding is a 20-minute developer ritual.
- **Pricing opacity.** Anthropic invoices arrive a month late.
- **Stale knowledge.** Fine-tunes lock and OSRS patches every Wednesday.
- **TOS risk.** RunescapeGPT carries a self-incriminating disclaimer.

---

## What this changes elsewhere in the repo

- `docs/architecture/SYSTEM.md` must explicitly document the
  outbound-WSS-only transport as the design that clears the PR #11453 bar.
- `docs/marketing/BRAND_VOICE.md` should adopt the "Helps you play.
  Never plays for you." line as the always-present defensive tag.
- M1 tool surface needs `get_player_gains` (Wise Old Man API).
- Dashboard must show tokens / pence per turn — the BYO-key plugins
  cannot do this, and it's a one-screen demo.

---

## Files in this folder

- `landscape.md` — seven primary competitors + adjacent incumbents,
  with sourced quotes and full URLs.
- `differentiation.md` — the wedge, the four-quadrant view, copy hooks.
- `_SUMMARY.md` — this file.

---

## Sources (canonical list)

- https://github.com/runelite/plugin-hub/pull/11453
- https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features
- https://github.com/nickbeddows-ctrl/osrs-mcp-plugin
- https://github.com/Scrambles56/osrs-ai-companion
- https://github.com/SteveDoesCoding/osrs-ai-assistant
- https://github.com/brandoninkel/OSRS_AI_System
- https://github.com/kineticquant/osrs-llm
- https://osrs-llm.pages.dev
- https://github.com/harmindersinghnijjar/RunescapeGPT
- https://github.com/wise-old-man/wise-old-man
- https://oldschool.runescape.wiki/
