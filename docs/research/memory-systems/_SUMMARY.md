# Memory systems — research summary

Closes RAI-9. Researcher: catch-up agent. Date: 2026-06-21.

## 3-line key finding

1. **Linear + flat-Markdown `docs/agents/` + git is sufficient memory** for
   the autonomous overnight loop — that's what we ran tonight, 17 PRs and
   13 Done issues with zero context drift.
2. **Mem0 is the right plug-in for *user* memory** (per-paying-customer
   facts across chats) once we have paying customers; not needed for the
   agent loop itself.
3. **Do not adopt Letta, CrewAI, AutoGen, or LangGraph as the orchestrator**
   — they replace parts (orchestration, state, prompts) that we've already
   proved out with Claude Code agents + Linear + Markdown contracts.

## Files

- [`landscape.md`](./landscape.md) — five-framework comparison
  (Letta/MemGPT, CrewAI, AutoGen/MAF, Mem0, LangGraph) with cited URLs.
- [`recommendation.md`](./recommendation.md) — why Linear+docs+Mem0-later
  is the call, mapped onto every layer of our agent stack, with revisit
  signals.

## What this changes elsewhere in the repo

- `docs/agents/MEMORY_ARCHITECTURE.md` — already describes the
  Linear+Markdown contract; no edit needed. The recommendation here is the
  external validation.
- Future: when paying users land, add `apps/backend/src/llm/user-memory.ts`
  wrapping `@mem0/node` between Drizzle's `messages` table and the
  OpenRouter prompt. No code tonight.

## Re-validate when

- We add a second autonomous loop (not driven by Claude Code) — Letta then
  becomes interesting for *that* loop, not this one.
- We have a paying customer asking "why does it forget what I said last
  week?" — that's when Mem0 lands.
