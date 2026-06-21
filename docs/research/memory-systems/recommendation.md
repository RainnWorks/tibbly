# Memory architecture recommendation

> **TL;DR.** Keep doing what worked tonight. **Linear (issues + comments +
> status) + `docs/agents/` (NORTH_STAR, STATUS, DECISION_LOG, OPEN_QUESTIONS
> as flat Markdown) is our durable memory.** Plug **Mem0** in later for
> *user-facing* memory (per-paying-customer facts across chats); do not
> adopt it for the agent loop. **Do not adopt Letta, CrewAI, AutoGen, or
> LangGraph as the orchestrator** — they replace the parts we already
> proved out.

## Why this is the right call (and not bias toward the familiar)

The overnight run on 2026-06-21 closed 17 PRs and 13 Linear issues across
three milestones (M1 productized client, M2 marketing, M3 backend) over
~6 hours, with the operating-loop's only memory being:

1. **Linear** — every task is a Linear issue with status, comments, and PR
   linkages. Status transitions are the *episodic* memory ("we did this and
   it landed"). Comments and issue descriptions are the *semantic* memory
   ("this is why").
2. **`docs/agents/`** — the seven flat-Markdown files
   (`NORTH_STAR.md`, `SCOPE_GUARD.md`, `STATUS.md`, `LOOP_LOG.md`,
   `DECISION_LOG.md`, `OPEN_QUESTIONS.md`, `HANDOFF.md`,
   `MEMORY_ARCHITECTURE.md`) plus `GAPS.md` (this PR). Every agent reads
   them at loop start and writes back at loop end. Append-only logs prevent
   silent state-loss.
3. **`git log` + PR descriptions** — the *procedural* memory. The
   "how we did it" is the diff; the "why" is the PR body.
4. **`~/.claude/projects/.../memory/`** — Claude Code's auto-memory of
   user durable preferences ("never pause, never ask"). Survives across
   conversations; it's how loop 8 inherits the directives from loop 1.

This worked. Concrete evidence:

- **Zero context drift.** STATUS.md and DECISION_LOG.md held; loop N+1 never
  re-did loop N's work. Anti-spiral controls in
  `docs/agents/MEMORY_ARCHITECTURE.md` (loop budget, agent re-entry guard,
  drift signal, no-new-features rule) were enforced via the Markdown
  contract.
- **HANDOFF.md is honest.** It correctly captures 17 merged PRs, the live
  Tibbly branding decision, the legal stack risk, and the Q-18 Jagex posture
  open question. No hallucination.
- **Linear-driven status was the source of truth.** Every issue we used to
  count progress (17 Done) corresponds to a merged PR.

A bolt-on memory framework would have added infrastructure (Postgres or
SQLite or vector store), a server process to babysit, a new prompting
shape (Letta agents, CrewAI Crews, AutoGen agents, LangGraph nodes), and
zero marginal coherence beyond what Linear + Markdown already gave us.

## Where each evaluated framework lands in the stack

| Layer | Tool | Why |
|---|---|---|
| Within-loop scratch | Claude conversation context + sub-agent context | Volatile, fine for 5–10 min |
| Cross-loop (overnight) | **Linear + `docs/agents/*.md` + git** | Already proven; zero infra |
| Cross-conversation (Tom-to-Tom) | **`~/.claude/projects/.../memory/`** (Claude Code auto-memory) + `CLAUDE.md` + `HANDOFF.md` | Already proven; survives conversation boundaries |
| Per-user product memory (future) | **Mem0** | Bolt-on, paid users send messages over time; this is the right job for Mem0's extract→update loop |
| Per-chat session state | **Drizzle/Postgres `chats` + `messages` tables** (already in `apps/backend/src/db/schema.ts`) | Plain SQL is fine; not an "agent memory" problem |
| Letta | not adopted | Parallel orchestrator; we'd be running two |
| CrewAI memory | not adopted | Only ships with Crews; we don't use Crews |
| AutoGen / MS Agent Framework | not adopted | Same |
| LangGraph checkpoints + Store | not adopted | Requires modelling the loop as a LangGraph state graph |

## When to revisit (signals)

- **If the overnight loop loses coherence across loops** (e.g. loop 7 forgets
  loop 3's decision), instrument `LOOP_LOG.md` further first. If the
  Markdown contract still leaks state, then look at LangGraph checkpoints
  for the loop itself.
- **If we want a separate cloud agent driving Linear/GitHub from outside
  Claude Code** (e.g. a Fly-hosted "PM bot" that triages issues at 9am),
  Letta is the right starting point — its agent-managed three-tier memory
  is purpose-built for that profile.
- **When paying users start using the product** and we want "Tibbly
  remembered I'm an Iron, that I'm doing CG, and that I hate group
  content" — wire Mem0 in front of the chat loop. That's a v1.1 problem,
  not a launch problem.

## Concrete next-step (small)

When Mem0 lands as a dependency for *user* memory (post-launch), keep this
architecture: Mem0 handles per-paying-user facts; Linear + Markdown keep
handling the agent-of-agents loop. Two different memories for two different
audiences.

No code change required to ship this recommendation tonight. The proof is
the run itself.

## Sources for the "Mem0 for users later" call

- [Mem0 Guide 2026 — RockB](https://baeseokjae.github.io/posts/mem0-agent-memory-guide-2026/)
- [Memory Layer for Open Source Agent Frameworks — Mem0](https://mem0.ai/blog/memory-layer-for-open-source-agent-frameworks)
- [State of AI Agent Memory 2026 — Mem0](https://mem0.ai/blog/state-of-ai-agent-memory-2026)
- [Mem0 in 2026 — CallSphere](https://callsphere.ai/blog/vw3g-mem0-agent-memory-open-source-library-2026)
