# Open questions for Tom on wakeup — append only

## Q-1 — Domain name? — 2026-06-21
**Picked default:** `osrsllm.app` everywhere in copy/configs.
**Other options:** osrshelper.ai, scapeagent.com, copilot-osrs.com.
**Why not asked:** user offline; placeholder swappable via root `.env`.

## Q-2 — Pricing tiers — 2026-06-21
**Picked default:** $7 Hobbyist (100k tokens/month), $19 Pro (500k tokens,
priority model), $49 Iron (2M tokens, Opus access, group sharing).
**Other options:** higher/lower based on community willingness-to-pay
research (R2 will sharpen).

## Q-3 — RuneLite plugin distribution — 2026-06-21
**Picked default:** ship via RuneLite's plugin hub eventually; for now,
sideload via `external-plugin-hub` config.
**Other options:** insist on RL hub before public launch.

## Q-4 — Use Wiki sprites commercially? — 2026-06-21
**Picked default:** OSRS Wiki content is CC-BY-NC-SA 3.0 — NON-COMMERCIAL.
For paid SaaS marketing, this is borderline. Plan A: build a separate
sprite catalog from the actual game files (CC-BY-SA per Jagex policy for
fair use of game assets in derivative tools). Plan B: keep wiki images out
of the marketing page and only use sprites we extract from the running
game client + community-licensed-OK sources.
**Other options:** confirm with Jagex/Wiki team; rely on RuneLite's
existing sprite usage as fair-use precedent.
**Why not asked:** real legal Q. Flagged loudly.

## Q-5 — Network presence — opt-in or opt-out? — 2026-06-21
**Picked default:** opt-in to appear on the public "X agents online" widget.
Default off. Privacy first.
**Other options:** opt-out (boost marketing).

## Q-6 — Account binding — confirm OSRS account names somehow? — 2026-06-21
**Picked default:** trust the plugin-reported player name; the user can
nuke a binding from the dashboard. We can later add a verification step
(plugin posts a one-time string to chat-message that the backend reads).

## Q-7 — Commits unsigned this run — 2026-06-21 (RAI-13 / A1 — Architect)
**Issue:** The 1Password SSH signing agent hard-failed across every
parallel agent during the overnight run with
`error: 1Password: failed to fill whole buffer` (and later
`agent returned an error`). `ssh-add -L` reported "agent has no identities".
**Action taken:** Committed once with `git -c commit.gpgsign=false commit`
on branch `agent/eng-infra/monorepo`. Without this we would have lost the
entire monorepo restructure.
**Picked default:** keep the unsigned commit; everything else this session
is also likely to be unsigned for the same reason.
**On wakeup:** unlock 1Password, then to retro-sign:
`git rebase --exec 'git commit --amend --no-edit -S' --root` on each branch,
force-push. Or accept unsigned for these branches and only require signing
on merge to `main`.

## Q-8 — Multiple agents share one git checkout — 2026-06-21 (RAI-13 / A1)
**Issue:** Sibling agents in this run `git checkout` different branches in
the same primary worktree, blowing away any unstaged work. RAI-13's first
attempt was wiped twice this way.
**Action taken:** RAI-13 finished from a dedicated `git worktree add` at
`../osrs-llm-helper-rai13` on branch `agent/eng-infra/monorepo`.
**Recommendation:** every parallel agent should spawn in its own
`git worktree` (or in a fully isolated `worktree` agent), not just a
branch on the shared checkout. Worth fixing in the spawner before the
next overnight.
