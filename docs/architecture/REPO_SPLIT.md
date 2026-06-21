# Repo split: migration plan

Companion to `docs/architecture/LICENSING.md`. That doc decided the licenses.
This doc decides where each piece lives and how we get there from the current
single-repo monorepo.

## Why split at all

The alternative is keeping a single monorepo with `LICENSE-PLUGIN.md` and
`LICENSE-BACKEND.md` files at the relevant subdirectories. That is legally
defensible and avoids the migration cost. It is also a worse signal.

A public repo whose root README says "MIT-licensed RuneLite plugin" is
unambiguous. A mixed-license monorepo confuses every forker:

- The RuneLite hub reviewer clicks the GitHub link in our PR. They land on a
  repo with a private-but-mentioned `apps/backend/` and a "see LICENSE-X.md
  per directory" note. They have to read three files to find out whether the
  plugin they are reviewing is actually MIT.
- A casual contributor opens an issue with a patch against `apps/plugin/` and
  has to be told the CLA-ish rules differ from the rest of the tree.
- A privacy-skeptical player tries to read what runs in their RuneLite
  client and has to navigate a tree where most of the visible code is the
  marketing site.

Splitting costs us about three days of careful work. It buys clarity that
compounds for the lifetime of the project. Worth doing before v1 launch.
Painful to do after, because anyone who has cloned, starred, or linked the
repo will need a redirect.

## Destination layout

**`RainnWorks/tibbly-plugin`** (public, MIT).

Contains the entire current `apps/plugin/` subtree, the plugin-facing docs
under `docs/runelite-hub/`, and the security audit. Has its own
`README.md`, `LICENSE` (MIT), `CONTRIBUTING.md`, `SECURITY.md`. Independent
versioning and release cadence. CI is GitHub Actions with the existing
Gradle build matrix.

**`RainnWorks/tibbly-platform`** (private, proprietary).

Contains `apps/backend/`, `apps/ops/`, `apps/marketing/`, `apps/dashboard/`
(if retained), the `docs/agents/`, the architecture docs that are not
plugin-facing, `docs/research/`, `docs/legal/`. Single Bun workspace.
This is the operations-and-product monorepo. Stays a Bun workspace because
the backend, ops, and marketing share types and tooling.

**`RainnWorks/tibbly-protocol`** (public, CC-BY-4.0).

Doc-only repo. Carries the WebSocket protocol spec as a single Markdown
document, with companion JSON schemas. Versioned independently of either
side. Lets a future Tibbly mobile client, a hypothetical community desktop
client, or our own internal tooling target a stable contract without
needing access to backend internals.

**`@tibbly/shared-types`** (npm package, MIT).

The Zod schemas and TypeScript types both sides import. Lives in its own
public GitHub repo (`RainnWorks/tibbly-shared-types`) so npm provenance and
release notes have a home. Strictly types and schemas, no runtime logic.

The current `RainnWorks/osrs-llm-helper` repo gets archived with a redirect
notice. See Q-26 below.

## Migration sequence

**Phase 0: prep.** Finalize the licensing docs (this PR). Run a final round
of em-dash and LLM-slop audits in the plugin source so the first public
commit reads cleanly. Confirm `.gitignore` excludes every secret the
plugin should not carry into history. Land any pending hub-compliance
changes that affect plugin file structure, so the post-split history is
the audit-ready history.

**Phase 1: extract plugin to its own repo.** Use `git subtree split` from
the monorepo at the `apps/plugin/` prefix. Preserve full history; RuneLite
hub reviewers do look at commit history, and the early plugin commits are
the most useful audit artifact. Create `RainnWorks/tibbly-plugin`, push
the split branch as `main`, add the MIT `LICENSE`, the README, the
CONTRIBUTING, and the SECURITY policy. Tag `v0.1.0-pre-hub`.

**Phase 2: extract shared types to npm.** `git subtree split` at
`packages/shared-types/`. Create `RainnWorks/tibbly-shared-types`. Publish
`@tibbly/shared-types@0.1.0` to npm under the `@tibbly` scope. Add a
`README.md` that explains the package is the wire-format contract and
points at `tibbly-protocol` for the human-readable spec.

**Phase 3: update plugin imports.** The plugin currently imports from
`packages/shared-types` via the Bun workspace symlink. Replace with an npm
dependency on `@tibbly/shared-types`. Run the plugin test suite; fix any
type-resolution issues. The plugin repo now stands alone.

**Phase 4: rename the current monorepo.** Rename
`RainnWorks/osrs-llm-helper` to `RainnWorks/tibbly-platform`. Make it
private. Remove `apps/plugin/` and `packages/shared-types/` from the tree
(history stays, but the working tree drops the now-extracted directories).
Add the proprietary LICENSE. Update the root README to make clear this is
the closed-source half.

**Phase 5: publish protocol spec.** Lift `apps/backend/src/ws/*` and any
relevant sections of `docs/architecture/PROTOCOL.md` into the new
`RainnWorks/tibbly-protocol` repo. Strip the implementation; keep the
contract. Add CC-BY-4.0 `LICENSE`. Tag `v1.0.0` once the schema is the one
the plugin actually uses against the backend.

## Bun workspaces complication

Today the plugin imports from `packages/shared-types` via workspace symlink,
which means a change to the schema is reflected instantly in the plugin
build. Post-split this becomes an npm release cycle. Two follow-ups:

- During development, both repos can be linked locally with `bun link`. We
  will document this in both READMEs so a contributor can iterate without
  publishing a release every time.
- Add a CI check on the plugin repo that asserts no import path reaches
  outside the plugin tree. Specifically, ban imports like
  `from "../../backend/..."` or any path that would only resolve in the
  pre-split monorepo. This catches accidental coupling early.

A second CI check on the platform repo asserts the same in reverse: no
runtime import from the backend reaches into `tibbly-plugin` source.

## Estimated effort

About three days of careful migration work. Phase 0 is half a day. Phase 1
is the riskiest hour (the `git subtree split` is permanent). Phase 2 and 3
together are about a day. Phase 4 is half a day. Phase 5 is half a day plus
review.

Worth doing before v1 launch. After launch, every fork, star, and external
link adds friction to the rename.

## Open questions

**Q-25: when do we run the migration?** Two windows: before we open the
RuneLite hub PR, or after. Recommended: before. The first hub PR review
fetches the GitHub link, and that link should land on a repo whose
root-level structure matches what reviewers expect from a plugin. If we
migrate after, we are doing a public rename mid-review.

**Q-26: do we archive `RainnWorks/osrs-llm-helper` or delete it?**
Recommended: archive with a notice pointing at the new repos. GitHub's
archive UX is good enough; deleted repos break old links permanently,
including any link that has already been captured by search engines or
shared in Discord. Lowest-regret option is archive with a top-level
`README.md` rewrite that says "this project is now Tibbly. The plugin
lives at X, the protocol spec lives at Y."

**Q-27: where does the private platform repo actually live?** GitHub
private is the default; the team already lives there. Alternatives are
paid GitLab (cheaper for private repos at scale, more ops complexity) or
self-hosted Gitea (cheapest at scale, real ops burden). At the current
team size of one, GitHub private is right. Revisit if the team grows past
three or if private-repo Actions minutes become a binding cost.

## Cross-references

- `docs/architecture/LICENSING.md`. The licensing decisions this plan
  implements.
- `docs/agents/DECISION_LOG.md` D-10. The parent decision.
- `docs/agents/OPEN_QUESTIONS.md` Q-25 / Q-26 / Q-27. The open questions
  enumerated above.
- `docs/runelite-hub/SUBMISSION_CHECKLIST.md`. The hub PR checklist that
  Q-25's timing question feeds into.
