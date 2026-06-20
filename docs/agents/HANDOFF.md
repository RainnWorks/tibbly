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
