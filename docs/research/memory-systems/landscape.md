# Memory frameworks landscape (2026-06-21)

Closes RAI-9 (research half). Lightweight comparison of the five frameworks
that show up in every "agent memory" conversation in 2026. One focused
WebSearch per framework; this is the survey, not an integration spike.

The autonomous overnight loop we ran on 2026-06-21 (17 PRs merged, 13 Linear
issues moved to Done, 60+ doc files maintained) is the workload we evaluate
fit against. Specifically:

- **Episodic coherence over 6–8 hours of unsupervised loops.** Did loop 7
  remember what loop 3 decided?
- **Cross-conversation continuity.** If Tom comes back tomorrow, do we wake
  up with state?
- **Plays well with sub-agents we already use** (Claude Code worktree
  agents, Linear MCP, gh CLI).
- **Zero infra to babysit.** We have no Postgres, no Redis, no vector store
  running outside of dev. Anything that needs one is paying a tax we don't
  need to pay tonight.

## At-a-glance

| Framework | Memory model | Persists where | Integration cost | Fit for our loop |
|---|---|---|---|---|
| **Letta / MemGPT** | Three-tier OS-style (core / archival / recall), agent-managed via tool calls; episodic + semantic + procedural | Postgres / SQLite (server) + Letta-hosted option | High — run a Letta server, port our prompts to its agent format | Strong episodic coherence is the right shape for an overnight loop, but operationally heavy for a 1-engineer team running this on a laptop |
| **CrewAI memory** | Unified `Memory` API; short-term (ChromaDB+RAG), long-term (SQLite), entity (knowledge graph), external | Embedded ChromaDB + SQLite (dev); pluggable Mem0 backend (prod) | Medium — only useful if we adopt CrewAI as the orchestration layer | Doesn't fit: we orchestrate with Claude Code agents + Linear, not Crews |
| **AutoGen / Microsoft Agent Framework** | In-memory message history by default; pluggable: buffer history, vector RAG, persistent key/value, conversational history | Whatever you plug in; framework is BYO storage | Medium — useful if we adopt AGF as the orchestrator | Doesn't fit our orchestration; same reason as CrewAI |
| **Mem0** | Two-stage `extraction` + `update` over conversation; ADD/UPDATE/DELETE/NOOP decisions; three stores: vector (semantic), graph (relationships), key-value (facts) | Pluggable: 20 vector backends (Qdrant, pgvector, etc.) + graph + KV | Low (managed) / Medium (self-host) — drop-in Python/Node SDK, bolts onto any agent stack | Best raw-fit for cross-conversation memory in a future stage; not needed for the loop itself |
| **LangGraph checkpoints + store** | Short-term: checkpointers persist graph state at every node; long-term: `Store` for cross-thread memory (user prefs, facts) | MemorySaver (dev), SqliteSaver (local), PostgresSaver (prod), Redis (community), AgentCore (AWS) | Medium-high — requires modelling the loop as a LangGraph state graph | Doesn't fit: we'd have to rewrite the orchestration as a graph |

## Per-framework notes

### Letta / MemGPT

- **Memory shape:** three tiers borrowed from OS memory management — *core
  memory* always in-context (like RAM), *archival memory* in an external
  vector store (like disk), *recall memory* of past conversations. Maps
  cleanly onto the standard episodic / semantic / procedural triple.
- **Who owns the memory:** the agent itself, via tool calls. It decides when
  to push to archival, when to pull back. This is the "agentic memory"
  thesis.
- **Persistence:** a Letta server (open-source, Apache-2.0) running over
  Postgres or SQLite; Letta Cloud is the hosted variant.
- **Integration cost for us:** real. We'd run a Letta server, redo our
  agent prompts in Letta's agent format, and adopt its tool-call shape. The
  payoff (episodic coherence across hours) is exactly what we need, but it's
  a parallel orchestrator to Claude Code, not a memory bolt-on.
- **Verdict:** revisit when we want a memory layer that can drive **other**
  agent systems (e.g. backend agents running on Fly). Not for the overnight
  Claude Code loop itself.

### CrewAI memory

- **Memory shape:** unified `Memory` class — one API covers short-term
  (ChromaDB + RAG, single execution), long-term (SQLite, across executions),
  and entity (knowledge graph over people / companies / concepts) memory.
- **Recall:** composite scoring blends semantic similarity, recency, and
  importance; configurable "half-life" tunes recency vs importance trade-off.
  CrewAI's own writeup recommends Mem0 for any production setup.
- **Integration cost for us:** only matters if we adopt **Crews** as the
  orchestration unit. We orchestrate via Claude Code agents + Linear issues
  + Markdown files; we don't run Crews.
- **Verdict:** not for us. The interesting bits (recency vs importance
  half-life) can be cribbed conceptually into our Linear-as-memory model
  later.

### AutoGen / Microsoft Agent Framework

- **Memory shape:** keeps a per-conversation message history in memory by
  default. Pluggable extensions: sliding-window buffer, vector RAG, key/value
  state, conversational history.
- **2026 status:** the AutoGen project split in March 2026; **Microsoft
  Agent Framework v1.0** (devblogs.microsoft.com) is the consolidated path
  forward with a pluggable memory architecture.
- **Integration cost for us:** same as CrewAI — only useful if AGF is the
  orchestrator. It isn't.
- **Verdict:** not for us. AGF's memory pattern (pluggable, BYO storage) is
  a reasonable design — it influences our recommendation below.

### Mem0

- **Memory shape:** the two-stage `extract` → `update` loop is the
  interesting bit. New conversation arrives → LLM extracts key facts →
  decides ADD / UPDATE / DELETE / NOOP against existing memory → writes to
  one of three stores (vector / graph / key-value).
- **Why it shows up everywhere:** ~48K GitHub stars, $24M Series A
  (Oct 2025), Netflix / Lemonade / Rocket Money in production. The default
  drop-in memory layer for any agent stack.
- **Integration cost for us:** low for hosted Mem0 (Node/Python SDK,
  bolt-on); medium for self-host (run Qdrant or pgvector + a KV).
- **Fit:** **excellent** for cross-conversation user memory once we have
  *paying users sending messages over time*. That's a stage-2 problem
  (post-launch), but Mem0 is the right tool to reach for then.

### LangGraph checkpoints + memory Store

- **Memory shape:** two-axis split. Checkpointers save the **graph state**
  after every node execution (short-term, thread-scoped — enables HIL, time
  travel, fault tolerance, conversation continuity). The `Store` persists
  cross-thread state (user prefs, facts, shared knowledge).
- **Backends:** MemorySaver (dev), SqliteSaver (local), PostgresSaver
  (prod), Redis (`redis-developer/langgraph-redis`), Aerospike, AWS Bedrock
  AgentCore. Strong production story.
- **Integration cost for us:** the model is right — checkpoint-after-every-
  step is exactly how we'd want the overnight loop to behave — but it
  requires modelling the loop as a LangGraph state graph, which we don't
  have.
- **Verdict:** not for us tonight. The *idea* of "checkpoint state after
  every node" is what Linear + commits already give us — each Linear status
  change and each `git commit` is our checkpoint.

## What this maps onto for us

- Episodic coherence over the night → **Linear issue lifecycle**
  (Backlog → Started → In Review → Done) + **`git log`** + the append-only
  Markdown logs (`LOOP_LOG.md`, `DECISION_LOG.md`).
- Cross-conversation continuity → **`~/.claude/projects/.../memory/`
  auto-memory** + **CLAUDE.md** + **`docs/agents/HANDOFF.md`**.
- Long-horizon user memory (paying customers) → **Mem0** (future).

## Sources

- [Letta (MemGPT) Walkthrough — SurePrompts (2026)](https://sureprompts.com/blog/letta-memgpt-walkthrough)
- [Best AI Agent Memory Frameworks in 2026 — Atlan](https://atlan.com/know/best-ai-agent-memory-frameworks-2026/)
- [Mem0 vs Letta vs MemGPT 2026 — TokenMix](https://tokenmix.ai/blog/ai-agent-memory-mem0-vs-letta-vs-memgpt-2026)
- [Memory — CrewAI docs](https://docs.crewai.com/en/concepts/memory)
- [CrewAI Memory in production with Mem0](https://mem0.ai/blog/crewai-memory-production-setup-with-mem0)
- [Memory and RAG — AutoGen docs](https://microsoft.github.io/autogen/stable//user-guide/agentchat-user-guide/memory.html)
- [Microsoft Agent Framework v1.0 (devblogs)](https://devblogs.microsoft.com/agent-framework/microsoft-agent-framework-version-1-0/)
- [Mem0 — InfoWorld writeup](https://www.infoworld.com/article/4026560/mem0-an-open-source-memory-layer-for-llm-applications-and-ai-agents.html)
- [State of AI Agent Memory 2026 — Mem0 blog](https://mem0.ai/blog/state-of-ai-agent-memory-2026)
- [LangGraph Persistence — LangChain docs](https://docs.langchain.com/oss/python/langgraph/persistence)
- [LangGraph checkpoint package — PyPI](https://pypi.org/project/langgraph-checkpoint/)
- [LangGraph Redis checkpointer + store](https://github.com/redis-developer/langgraph-redis)
