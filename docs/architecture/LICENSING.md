# Licensing: hybrid (MIT plugin, proprietary backend)

Canonical source. If another doc disagrees, this one wins. Decision recorded as
D-10 in `docs/agents/DECISION_LOG.md` on 2026-06-21.

## The decision

| Component | License | Repo |
|---|---|---|
| `apps/plugin/` | **MIT** | public: `RainnWorks/tibbly-plugin` |
| `apps/backend/` | **Proprietary** (Tibbly Limited, all rights reserved) | private: `RainnWorks/tibbly-platform` |
| `apps/ops/` | Proprietary | private: same as backend |
| `apps/marketing/` | Proprietary | private: same as backend |
| `apps/mobile/` (future) | **MIT** | public: `RainnWorks/tibbly-mobile` |
| `packages/shared-types/` | **MIT** | published as `@tibbly/shared-types` on npm |
| WebSocket protocol spec | **CC-BY-4.0** | public: `RainnWorks/tibbly-protocol` (doc-only) |

Tom's directive, captured from the 2026-06-21 session:

> I fundamentally don't believe open source is the correct model because of how
> LLMs and agents can use it as full context no matter really the license. But
> that will dramatically impact how RuneLite plugin developers view this
> project. So I'm wondering if it's possible to actually split it: allow the
> RuneLite developers to view the code for the backend... actually no, the
> **plugin** open-source so RuneLite maintainers can audit, the **backend**
> closed-source so we keep the USP. We open source as much as possible but we
> keep the really sort of USP closed source.

## Why this split

Two arguments to separate.

**License-as-defense is theatre against LLMs.** Any code we publish ends up in
training corpora, retrieval indexes, and agent context windows. AGPL does not
stop a hosted clone if the cloner is willing to publish their changes (Group
Ironmen Tracker proves this works in this community). BUSL with conversion only
slows down the determined competitor by a quarter. The honest read is that for
a product of our size, no license materially raises the cost of being copied.

**License-as-signal is what matters.** The audience for the license is humans,
not lawyers. RuneLite plugin reviewers read GitHub. Privacy-skeptical OSRS
players read GitHub. Future hires read GitHub. They each ask a different
question:

- Reviewer: "Can I read every line that touches the game client?" Yes, MIT
  plugin repo. PR moves forward.
- Player: "Is this just a wrapper around something I can't see?" The plugin is
  open. The backend is a hosted service that the plugin talks to. The boundary
  is clear from the README.
- Hire: "Do they ship clean code under their own name?" The plugin repo is the
  portfolio piece. The backend stays internal.

The ScapeGPT precedent matters too. ScapeGPT shipped as a fully-closed Chrome
extension and a hosted API. They were trusted enough to attract paying users,
but they never cleared the RuneLite hub bar because the hub requires source
review. Our split lets us clear the hub bar on the plugin side while keeping
the same commercial posture on the service side.

## Per-component reasoning

**Plugin under MIT.** Required to pass RuneLite hub review on first
submission. MIT matches the precedent set by player-stats-sync, LeaguesSync,
and RuneGPT (Q-15 default). Forks are welcome; the cost of someone running a
competing backend behind a fork is a working backend, which is the hard part.

**Backend proprietary.** Holds the model-routing logic, the per-tier prompt
shaping, the OpenRouter cost-control heuristics, and the billing. Tom called
out these as the USP. Closed-source is the simpler signal for "this is a
hosted service, not a kit." We are not selling code; we are selling the
running thing.

**Ops and marketing proprietary, co-located with the backend.** No external
consumer for either. Living in the same private repo as the backend means
one CI matrix, one deployment pipeline, one PR queue. Splitting them later is
straightforward if the team grows.

**Mobile companion MIT, in its own public repo.** Mobile is a thin client
against the documented protocol. Open-sourcing it signals that any future
client (community-built desktop, web overlay, voice assistant) can interop.
The mobile companion is researched but not built (see
`docs/research/mobile-companion/`).

**Shared types MIT, as an npm package.** Both the plugin and any future open
client need the Zod schemas. Publishing under `@tibbly/shared-types` is the
clean version of "your protocol is your API." Versioned separately so a
breaking change is loud.

**Protocol spec under CC-BY-4.0 in a doc-only repo.** CC-BY-4.0 is the right
license for prose plus JSON schemas: attribution required, derivatives
allowed. The repo carries the WebSocket message catalog and version notes.

## What this means for contributors

Anyone can fork `tibbly-plugin`, swap the backend URL to their own service,
and run a competing product. The MIT grant is unconditional. We are not
relying on the license to stop them; we are relying on the backend being the
hard part to build well, and on the Tibbly brand being the social proof a
paid user expects.

We will accept PRs to the plugin under standard MIT terms. We will not accept
PRs to the backend. There is no CLA on the plugin because there is nothing to
relicense; we may still ask first-time contributors to confirm they have the
right to submit the code.

## What this means for paying customers

Paying customers are paying for the hosted service, the brand promise of
uptime, the experiments we run on prompt and model routing, and the support
channel. They are not paying for code that is hidden from them. The plugin
they install is fully readable. The service it talks to is ours to operate.

The simplest way to put it: the plugin is the receipt; the service is the
meal.

## What this does not mean

This is not an open-core play. We are not soliciting backend PRs from the
community and then keeping the "premium" features in a separate private repo.
The backend is closed because it is a commercial service, not because we are
hoarding patches. There is no public roadmap for the backend repo; there is
no community-tier of the service.

It is also not a step toward "eventually we will open the backend." We may,
but the reversibility section below is the honest read.

## Compared to alternatives

| Option | What you get | What it costs |
|---|---|---|
| Full open (MIT everything) | Maximum reviewer goodwill; easiest hub PR | A competitor can stand up a hosted clone in a week |
| Full closed (proprietary everything) | Maximum control | RuneLite hub PR is harder; player trust is lower; no audit story |
| Open-core (MIT base + private "pro" backend modules) | Community contribution to backend; cleaner story for OSS marketing | Constant maintenance tax keeping the boundary clean; few of our improvements fit a "core vs pro" split |
| BUSL with conversion to Apache after 4 years | Discourages immediate hosted clones | Still readable, still trainable, and the conversion clock is meaningless to LLMs |
| AGPL | Forces hosted clones to publish their changes | Scares enterprise users; does not stop a Group-Ironmen-Tracker-style competitor who is happy to publish |

We considered each. The MIT-plugin + proprietary-backend split is the closest
match to what Tom actually wants: maximum signal of openness on the part
players see, maximum control on the part we operate.

## Reversibility

**Plugin license is the hardest to reverse.** Once the plugin is published
under MIT, every commit before a relicense is permanently MIT. Forks made
during the MIT window stay MIT. We cannot retroactively close the plugin. We
can change the license on future commits, but the historical commits are
still available under the original terms. Treat MIT on the plugin as a
one-way door.

**Backend license is fully reversible.** It is closed today; we can open it
under any license later if we change our mind. There is no historical
commitment that locks us out. The cost of opening later is mostly social
(we would have to explain the shift) and competitive (the model routing
logic becomes copyable).

**Shared types under MIT is also a one-way door**, but the surface area is
tiny: Zod schemas and type aliases. Even a strict reading of "this is
trade-secret" would not survive once the WebSocket frames are visible on the
wire to anyone running the plugin.

**Protocol spec under CC-BY-4.0 is reversible** in the sense that we can
stop publishing future versions under that license, but anything we have
published is permanently usable by anyone who attributes us. That is the
point.

The practical implication: think hard before the first public commit on
`tibbly-plugin`. After that, the door closes behind us.

## Cross-references

- `docs/architecture/REPO_SPLIT.md`. The migration plan for moving the
  monorepo into the two-public-plus-one-private layout described above.
- `docs/agents/DECISION_LOG.md` D-10. The decision record.
- `docs/agents/OPEN_QUESTIONS.md` Q-25 / Q-26 / Q-27. The open questions
  on timing and infrastructure.
- `docs/agents/OPEN_QUESTIONS.md` Q-17. Superseded by D-10; see resolution
  note there.
- `docs/runelite-hub/PRECEDENT.md`. The precedent plugins whose MIT choice
  we are matching.
