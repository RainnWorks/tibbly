# Memory architecture — how this autonomous run stays sane

## The risk

Running unattended for 6–8 hours over many loop cycles, the failure modes are:

1. **Spiral / scope drift** — agent polishes adjacent things that weren't asked for.
2. **Hallucination** — agent invents requirements the user never stated.
3. **Context loss** — conversation gets summarized; earlier decisions vanish.
4. **Sycophancy** — agent assumes user wants Y and bias-confirms.
5. **Agent-of-agent drift** — sub-agents inherit my drift and amplify it.
6. **Re-doing work** — agent rebuilds what an earlier loop already finished.

Each one is mitigated by writing to disk what would otherwise live in volatile context.

## The memory stack (top = most volatile, bottom = most durable)

| Layer | Where it lives | When read | When written |
|---|---|---|---|
| Conversation turn | Current LLM context | Live | Live |
| Sub-agent context | Per-agent context | During agent run | During agent run |
| Auto-memory (cross-conversation) | `~/.claude/projects/-Users-tom-Projects-osrs-llm-helper/memory/` | Loaded each new conversation | Updated when user states something durable |
| Repo CLAUDE.md | `./CLAUDE.md` | Loaded each new conversation | At project setup, on major directive changes |
| Agent docs | `./docs/agents/*.md` | Each loop start | Each loop end + each agent completion |
| Product/architecture docs | `./docs/{product,architecture,marketing}/*.md` | Each loop start | At spec time + when implementation reveals truth |
| Git history | `./.git/` | Anytime | Each commit |

## Recentering protocol (run at the START of every loop)

```
1. Read docs/agents/NORTH_STAR.md       — what we said we'd build
2. Read docs/agents/SCOPE_GUARD.md      — what we explicitly will NOT do
3. Read docs/agents/STATUS.md           — what's done, in-flight, blocked
4. Read docs/agents/LOOP_LOG.md (tail)  — what the last loop decided
5. Read docs/agents/DECISION_LOG.md (tail) — recent decisions
6. Ask: is what I'm about to do consistent with 1+2?
   - If yes: proceed
   - If no: stop, document the temptation in SCOPE_GUARD.md, redirect
```

## The seven files that hold the system together

```
docs/agents/
├── NORTH_STAR.md       # 1 page. The literal user goals. Re-read every loop.
├── SCOPE_GUARD.md      # Explicit non-goals. Things tempting but off-mission.
├── STATUS.md           # Live board. Updated every loop.
├── LOOP_LOG.md         # Append-only. One entry per loop.
├── DECISION_LOG.md     # Append-only. Every decision + rationale + alt considered.
├── OPEN_QUESTIONS.md   # Things flagged for user review when they return.
└── HANDOFF.md          # User-facing TL;DR for when they wake up.
```

### NORTH_STAR.md

The single most important doc. ≤200 lines. Bullet form. Reads in 30 seconds.
Contains:

- The product in one paragraph (paid SaaS, OSRS LLM assistant, RuneLite plugin + backend)
- The 5–7 must-ships
- The 3 hard constraints (Bun, OpenRouter, OSRS-Wiki-feel)
- The 3 explicit non-goals (no in-game automation, no real-money trading helpers, no client modifications outside the plugin sandbox)
- The acceptance test for "did we succeed tonight"

If I'm ever about to do something not traceable to a bullet in this file,
I stop and either justify it in DECISION_LOG.md or drop it.

### SCOPE_GUARD.md

Append-only list of things I rejected. Each entry: "considered X because Y; rejected because Z". Prevents re-considering rejected ideas in later loops.

### STATUS.md

Live progress board. Rebuilt each loop end. Per-agent:
- name, current task, blocked? on what?, last update timestamp, % done
- list of completed tasks with PR/commit refs

### LOOP_LOG.md

Append-only timeline. Each loop adds:
```
## Loop N — <timestamp>
### Read
- (what I re-read)
### Worked on
- (what I did this loop)
### Spawned
- (agents launched)
### Deferred
- (things I noticed but pushed to a later loop)
### Next loop should
- (specific action items)
```

The "Next loop should" section is the most important — it's how I hand off
to my future self.

### DECISION_LOG.md

Append-only. Each decision:
```
## D-<num> — <short title> — <timestamp>
**Context:** (why was a decision needed)
**Options considered:** A, B, C
**Chosen:** B
**Rationale:** ...
**Reversible?:** yes/no
**Revisit if:** (signal that should trigger reconsidering)
```

### OPEN_QUESTIONS.md

Things I picked a default for but want user input on later:
```
## Q-<num> — <question> — <timestamp>
**Picked default:** X
**Other reasonable options:** Y, Z
**Why I didn't ask:** user offline, picked the lowest-regret option
```

### HANDOFF.md

The doc the user opens when they get back. Structured as:
```
# What happened overnight (TL;DR)
# Demo this first
# Open decisions to confirm
# Risks I'm aware of
# Next session's natural starting point
```

Updated continuously. Always reflects current state.

## Agent brief shape (every sub-agent gets these sections)

```
## Mission           — one sentence
## In scope          — bullet list, ≤7 items
## Out of scope      — bullet list of tempting-but-no
## Inputs            — what docs to read, in what order
## Outputs           — what files to produce, with paths
## Acceptance        — checklist; ALL must be true to mark done
## Test plan         — how to prove the work
## Hand-off          — what they update in STATUS.md / DECISION_LOG.md
## Do-not-stop rule  — repeat the "do not pause" directive
```

## Auto-memory writes (cross-conversation)

The following get written to `~/.claude/projects/.../memory/`:
- user profile: extended for this project's productization phase
- project: SaaS transformation underway, paying customers, OpenRouter-driven
- feedback: "Do not pause, never stop, autonomous mode" — durable preference
- reference: where docs live in this repo

These survive across conversations so the NEXT time I'm invoked I pick up
context without the user re-stating it.

## Anti-spiral controls

1. **Loop budget.** Each loop has a soft cap of ~5000 tokens of new writes.
   If I exceed it, I stop and schedule the next loop.
2. **Agent re-entry guard.** Before spawning an agent for X, grep
   `docs/agents/STATUS.md` for "X" — if it's already done or in-flight, skip.
3. **Diff-only edits.** Prefer `Edit` over `Write` when the target file already
   exists. Don't rebuild docs that already exist.
4. **Drift signal.** If I find myself working on something that wasn't in
   NORTH_STAR.md, that's a signal to stop and re-read.
5. **No-new-features rule mid-loop.** I implement what the docs already specify.
   New features get appended to a "later" file, not built ad-hoc.

## How sub-agents avoid the same failure modes

Each agent prompt MUST include:
- "Read NORTH_STAR.md before doing anything."
- "Don't expand scope. Stick to your In-Scope list."
- "Append your decisions to DECISION_LOG.md."
- "Update STATUS.md with your progress before exiting."
- "If you discover something off-mission but valuable, write it to OPEN_QUESTIONS.md and move on."
