# Handoff to Tom — 2026-06-21

*Rebuilt every loop. First file Tom should read on wake-up. 90 seconds of context, then jump.*

## TL;DR (one paragraph)

Overnight the productize phase went from "monorepo skeleton + raw docs" to **17 PRs merged on `main`** closing 17 Linear issues across all three must-ships — the productized client (plugin pivots from a localhost HTTP MCP to a single outbound WSS; tool gating drops turn-1 surface from ~8K → ≤1.5K tokens), the marketing page (Vite + React + Tailwind v4 skeleton with eight section stubs and a live `/v1/presence` counter), and the backend (Hono + Drizzle + PGLite, the 11-table schema, GDPR Art. 15/17 endpoints, event-driven analytics, and an admin ops dashboard). Three architectural wins make the rest of the night cheap: **(1)** OpenRouter routing locked in with 97/88/79% margins at the three tiers, **(2)** RuneLite Plugin Hub compliance package shipped (we now know exactly why the previous "OSRS MCP plugin" PR was rejected and have the egress-gate fix landed), and **(3)** the full legal stack (Privacy, Terms, Consent flow, Sub-processors, Retention, Cookies) drafted ahead of the lawyer pass. The brand is **Tibbly** with the "clever friend who already read the wiki" voice. The currently in-flight pairing-code work (RAI-18) and the marketing-design pass (RAI-29/30, this loop) are the remaining gates on a runnable end-to-end demo.

## Top 3 wins

1. **Plugin Hub blocker neutralised.** RAI-35 + RAI-38 together: we discovered PR #11453 ("Add OSRS MCP plugin") was rejected for exposing player info over HTTP *even on loopback with auth tokens*. The localhost MCP HTTP server is now gone; outbound traffic flows through a single auditable `EgressGate` to the backend WSS. We are now structurally hub-eligible. See `docs/runelite-hub/PRECEDENT.md` §B.1.
2. **Token-economy math is locked.** RAI-8 produced a 3-tier routing contract (Haiku-only → Haiku-router + Sonnet → +Opus escalation) and a per-chat cost model. With tool gating (RAI-25) holding turn-1 surface ≤1.5K tokens, the margins are Hobbyist **97.4%** / Pro **88.0%** / Iron **79.1%**. The 1.5K cap is the single biggest margin lever and is now telemetered.
3. **Full SaaS surface scaffolded in one night.** Backend (RAI-14/15/17/37), dashboard (RAI-26), marketing (RAI-28), shared protocol types (RAI-17), pairing-code flow (RAI-18, in review), legal stack (RAI-34), and asset package (RAI-7) all landed. `bun install` from the repo root brings up all three apps; tests are green on every merged branch.

## Demo this first (in order)

1. **Repo health:** `bun install && bun run typecheck && bun run test` from `/Users/tom/Projects/osrs-llm-helper`. Should be green across all workspaces.
2. **Backend up:** `cd apps/backend && bun run dev` — hit `GET http://localhost:3001/health`, `GET /v1/presence`, then `POST /v1/me` to inspect the GDPR Art. 15 stub. Hit `/admin/usage` for the ops dashboard data.
3. **Marketing site:** `cd apps/marketing && bun run dev` — confirm hero, the eight section stubs render, and the `<LiveCounter>` pings the backend. Then **review the RAI-29/30 PR that lands later this loop** for the section content fills.
4. **Dashboard:** `cd apps/dashboard && bun run dev` — `/`, `/pair`, `/usage`, `/accounts`, `/billing` routes mount; auth state is in localStorage. Pairing-code wiring (RAI-18) is *In Review*.
5. **Plugin:** `cd apps/plugin && ./gradlew shadowJar` — builds clean. `McpServerService` is now under `local/` (legacy) and *not* registered; egress is via `cloud/BackendWsClient` + `cloud/EgressGate`.
6. **Legal stack:** open `docs/legal/PRIVACY.md`, `TERMS.md`, `CONSENT_FLOW.md`. **Do not publish or take payment until a UK/EU consumer + data-protection solicitor has reviewed.** See §LEGAL below.

## Top 3 risks

1. **Jagex's third-party-AI-assistant posture is undefined (Q-18).** Our product reads OSRS game state and sends it to a third-party LLM. If Jagex makes a moderation determination against the tool, every paying user could be banned. `TERMS.md` §11 disclaims that liability but enforceability against UK/EU consumers is *not guaranteed*. The default is "ship with mitigations, do NOT proactively contact Jagex Legal" — Tom should confirm.
2. **Unsigned commits this run (Q-7).** 1Password SSH signing agent hard-failed across every parallel agent. Every overnight branch + the merges to `main` are unsigned. Either retro-sign with a rebase `--exec` pass or accept unsigned for these branches and only require signing on future commits.
3. **OSRS Wiki sprite licensing for the marketing page (Q-4).** CC-BY-NC-SA 3.0 forbids commercial reuse. We mitigated by building `packages/osrs-assets` from BSD-2 RuneLite-extracted + CC0 RuneStar sources only — *no Wiki sprites in the paid product*. Marketing copy must follow the same rule. RAI-29/30 (this loop) enforces this in the section fills.

## Open questions ranked (read OPEN_QUESTIONS.md for detail)

1. **Q-18** — Reach out to Jagex Legal? Default: no. *Highest-impact, hardest-to-reverse decision.*
2. **Q-2** — Tier pricing ($7 / $19 / $49). Locked in code and copy; margins prove the numbers but willingness-to-pay is unvalidated.
3. **Q-7** — Unsigned commits — accept or retro-sign?
4. **Q-13** — Product name **Tibbly** — USPTO TESS check was unavailable; do it before any logo/brand spend. `tibbly.com` is taken by an unrelated art seller; fallbacks `tibbly.app` / `tibbly.gg` / `gettibbly.com`.
5. **Q-12** — Brand voice register ("calm + dry, rare ribbing") and whether the helper ever gets a proper first name.

## In flight at wake-up

- **RAI-18** — Pairing-code flow (device key + in-game code). *Status:* In Review.
- **RAI-29 + RAI-30** — Marketing section design fills (Hero / ProblemSolution / Demo / Pricing / FeatureGrid / FAQ / Footer / LiveCounter style). *This loop's work.* Worktree: `../osrs-llm-helper-rai29-30`, branch `agent/rai-29-30/marketing-design`.

## Files to skim (in order)

- `docs/agents/NORTH_STAR.md` — the three must-ships and the anchors.
- `docs/agents/STATUS.md` — live agent board.
- `docs/agents/DECISION_LOG.md` — every reversible/irreversible decision made overnight (D-1 through D-7).
- `docs/agents/OPEN_QUESTIONS.md` — full Q-1 through Q-18 with defaults picked.
- `docs/research/llm-providers/_SUMMARY.md` — the margin math (97/88/79%).
- `docs/runelite-hub/PRECEDENT.md` — why we changed the egress shape.
- `docs/marketing/BRAND_VOICE.md` + `docs/marketing/NAME_CANDIDATES.md` — Tibbly + tone.

## !! LEGAL — NEEDS LAWYER REVIEW BEFORE LAUNCH !!

> **RAI-34 deliverable. Do not publish, ship to plugin hub, take a payment, or
> expose chat to a real user until a qualified solicitor (UK / EU consumer +
> data protection) has reviewed the documents in `docs/legal/`.**

Drafts that exist as of 2026-06-21:

- `docs/legal/PRIVACY.md`
- `docs/legal/TERMS.md`
- `docs/legal/CONSENT_FLOW.md`
- `docs/legal/SUB_PROCESSORS.md`
- `docs/legal/DATA_RETENTION.md`
- `docs/legal/COOKIE_POLICY.md`

Single biggest legal risk: see Top Risk #1 above (Jagex posture). Other items flagged in-doc for the lawyer: cooling-off carve-out wording (Reg. 37 UK CCR), CCPA "sale/share" determination, controller-vs-processor designation, Cloudflare `cf_clearance` "strictly necessary" classification, children's age-gate UX.

## Next session's natural starting point

- Approve the top-5 open-questions defaults.
- Tom logs into the dashboard with a pairing code from the plugin (once RAI-18 merges).
- Sends "what's my next clue step?" — sees a Sonnet 4.6 response with tool calls bounded to ≤1.5K tool tokens turn-1, cost attributed in the admin dashboard.
