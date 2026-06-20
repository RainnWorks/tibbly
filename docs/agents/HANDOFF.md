# Handoff to Tom on wakeup

*This file is rebuilt every loop. When Tom comes back, this is the first thing he reads.*

## TL;DR — what happened overnight

*Will be filled by Loop N — currently still in Loop 0 (initial setup).*

## Demo this first

1. *(filled by Loop N+)*

## Open decisions to confirm

See `docs/agents/OPEN_QUESTIONS.md`. Top 3:
- **Q-4 OSRS Wiki sprite licensing for commercial use** — needs your call.
- **Q-2 Pricing tier numbers** — placeholder set, please validate against R2 research.
- **Q-1 Domain name** — placeholder `osrsllm.app`.

## Risks I'm aware of

- The OSRS Wiki CC-BY-NC-SA license forbids commercial reuse. Marketing
  strategy may need Plan B (sprite extraction from game client per RuneLite
  precedent). Q-4.
- Token-economy plan must be tested with real chats before we commit pricing.
- The plugin-to-backend WS protocol is a one-shot redesign — getting it wrong
  costs a session of rework. R6 (gaps) should scrutinize it before B3 builds.

## !!  LEGAL — NEEDS LAWYER REVIEW BEFORE LAUNCH  !!

> **RAI-34 deliverable. Do not publish, ship to plugin hub, take a
> payment, or expose chat to a real user until a qualified solicitor
> (UK / EU consumer + data protection) has reviewed the documents in
> `docs/legal/`.**

Drafts that exist as of 2026-06-21:

- `docs/legal/PRIVACY.md`
- `docs/legal/TERMS.md`
- `docs/legal/CONSENT_FLOW.md`
- `docs/legal/SUB_PROCESSORS.md`
- `docs/legal/DATA_RETENTION.md`
- `docs/legal/COOKIE_POLICY.md`

**Single biggest risk:** Jagex's third-party-AI-assistant policy is
undefined. Our product reads OSRS game state and sends it to a third-
party LLM. If Jagex makes a moderation determination against the tool,
every paying user could be banned. `TERMS.md` §11 disclaims that
liability — but the enforceability of that disclaimer against UK/EU
consumers is **not guaranteed**. Get a written legal opinion before
taking the first payment.

Other things flagged in-doc for the lawyer:

- Cooling-off carve-out wording (Reg. 37 UK CCR) lives in checkout,
  cross-referenced from `TERMS.md` §8.1 + `CONSENT_FLOW.md` §11.
- CCPA "sale/share" determination — currently claiming "no sale"; verify.
- Controller vs. processor designation — currently controller for
  account data, processor for in-session pass-through to OpenRouter.
- Cloudflare `cf_clearance` "strictly necessary" classification.
- Children's age-gate UX (passive statement vs. explicit checkbox).

Backend GDPR Art. 15 + 17 stubs landed at
`apps/backend/src/api/me.ts` + `apps/backend/test/me.test.ts` —
**parked behind RAI-13 (monorepo) + RAI-14 (backend skeleton)** because
the Drizzle schema + Hono app they import don't exist yet.

## Next session's natural starting point

- Approve the three open-questions defaults.
- Tom logs into the dashboard with a pairing code from the plugin.
- Sends "what's my next clue step?" — sees a Sonnet 4.6 response with tool
  calls bounded to ≤1.5K tool tokens turn-1.

## Files Tom will want to skim

- `docs/agents/NORTH_STAR.md`
- `docs/agents/STATUS.md`
- `docs/agents/DECISION_LOG.md`
- `docs/agents/OPEN_QUESTIONS.md`
- `docs/agents/GAPS.md`
