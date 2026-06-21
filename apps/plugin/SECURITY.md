# Security policy

We take the security of the osrs-llm-helper plugin and its backend seriously.
This document explains how to report a vulnerability, what to expect from us
when you do, and what is in and out of scope.

## How to report a vulnerability

Email **security@osrs-llm-helper.example** with the subject line
`[osrs-llm-helper] <short description>`.

> **Note:** `security@osrs-llm-helper.example` is a placeholder while this
> project is pre-launch. The production address will be published here and
> in the plugin's RuneLite Hub manifest before the first paid release. The
> current operational contact during the pre-launch period is
> `security@rowm.co` (see `apps/plugin/SECURITY_DESIGN.md` section 6).

When emailing please include, as much as you have:

- A description of the vulnerability.
- The version of the plugin and, if relevant, the backend
  (`api.osrsllm.app`) you tested against.
- Steps to reproduce, or a proof-of-concept patch.
- Any logs, screenshots, or HTTP/WSS captures that help us reproduce.
- Whether you would like to be credited publicly (and under which name).

If a public channel is the only option available to you, you may file a
GitHub Security Advisory on the repo. **Please do not open a regular public
issue or pull request** that discloses the vulnerability; private contact
first is strongly preferred.

## Response SLA

We will:

- **Acknowledge** receipt of your report within **72 hours** (business days
  are not an excuse — this is calendar hours).
- Provide a **triaged severity assessment within 5 business days**.
- For confirmed High / Critical issues, ship a fix or a documented mitigation
  within **30 days**.
- For Medium / Low issues, ship within the next scheduled release or 90 days,
  whichever is sooner.
- Keep you updated at least once a week while the report is open.

If we cannot meet a deadline above we will tell you that, in advance, with
the reason and a revised date.

## Coordinated disclosure

We prefer **coordinated disclosure**. Please give us a reasonable window —
ordinarily 90 days from acknowledgement, shorter if a fix ships sooner — before
publishing details. We will:

- Credit you in the release notes (unless you prefer anonymity).
- Publish a brief post-mortem in `docs/agents/DECISIONS.md` once a fix has
  shipped and players have had time to update.
- Coordinate timing with you for joint blog posts, CVE assignment, or
  responsible-disclosure databases where relevant.

## In scope

The following are in scope for this policy:

- The shipped plugin under `apps/plugin/src/main/kotlin/`, including the
  WSS transport, the egress gate, the audit log, the consent state, the
  inbound message codec, and the tool-routing layer.
- The backend at `apps/backend/` and the deployment at
  `api.osrsllm.app` (the production endpoint).
- The dashboard at `apps/dashboard/` and the deployment at the
  production URL (TBD pre-launch).
- The marketing site at `apps/marketing/` only insofar as a bug there exposes
  player data, allows account takeover, or breaks the auth handshake.
- Any committed `.env`, key, token, or other credential in a public repo
  controlled by this project.

Examples of issues we want to hear about:

- A way to bypass the `EgressGate` consent check.
- A way to make the plugin send a payload that is not a member of the
  `OutboundPayload` sealed family.
- A way to coerce the plugin to talk to a host other than the one
  configured in `BackendUrl`.
- A reflection / classloader / deserialization gadget that elevates plugin
  code execution beyond what RuneLite's plugin sandbox intends.
- An auth bypass between dashboard and backend (e.g. session fixation,
  CSRF on a state-changing endpoint, JWT validation flaw).
- A way to read another player's data, chat history, or Stripe state.
- Any committed credential — even a stale one — in this repo's history.
- Any path that allows arbitrary code execution on the player's machine
  through a tool call response.
- Any path that violates Jagex's third-party client guidelines (which would
  also break the RuneLite Hub policy).

## Out of scope

The following are explicitly out of scope. Reports on these items are
welcome but will be closed as informational.

- **A user running a modified build from source.** Our legibility claim
  covers what we ship via the RuneLite Plugin Hub, not what someone
  rebuilds locally with the consent gate ripped out. See
  `docs/runelite-hub/THREAT_MODEL.md` threat T11.
- **The developer-only `local/McpServerService` path** when the user has
  explicitly enabled `developerMode=true` AND `localMcpEnabled=true`. This
  path is off by default, excluded from the Plugin Hub build gates, and
  documented in `apps/plugin/SECURITY_DESIGN.md` section 4.
- **Denial of service against your own account** by spamming the backend
  past your tier's rate limit. The rate limit is the feature.
- **Social engineering** of project maintainers, contributors, or RuneLite
  Hub reviewers.
- **TLS traffic-analysis** of the player <-> backend connection. We do not
  attempt to defeat traffic analysis. See `THREAT_MODEL.md` threat T14.
- **Self-XSS** in the dashboard requiring the victim to paste attacker code
  into devtools.
- **Reports generated solely by an automated scanner** with no triage,
  proof of concept, or reasoning about impact.
- **OSRS game-rule violations** by other plugins or by Jagex itself.
- **Vulnerabilities in OpenRouter, Stripe, RuneLite client, or any other
  third-party software** the plugin depends on. Please report those to
  the vendor.

## Safe-harbour

If you make a good-faith effort to comply with this policy when researching
and reporting a vulnerability, we will:

- Not pursue or support legal action against you.
- Work with you to understand and fix the issue.
- Recognise your contribution publicly if you wish.

Good faith means: stop at the point of demonstrating impact, do not access
data beyond your own, do not degrade service for other players, do not
publicly disclose before we have shipped a fix.

## What we will not do

- We will not pay a bounty. (We may add a bounty programme post-launch; this
  document will be updated when we do.)
- We will not sign a non-disclosure agreement that prevents you from
  publishing your findings after coordinated disclosure.
- We will not retaliate against good-faith researchers.

## Other security documentation

- `apps/plugin/SECURITY_DESIGN.md` — the reviewer-facing one-page legibility
  audit guide.
- `apps/plugin/DATA_DISCLOSURE.md` — every payload we send and why.
- `docs/runelite-hub/SECURITY_AUDIT.md` — the verbatim grep audit, PASS/FAIL.
- `docs/runelite-hub/THREAT_MODEL.md` — STRIDE table, mitigations, accepted
  risks.

Last updated: 2026-06-21 (RAI-36).
