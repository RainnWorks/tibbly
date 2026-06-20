# Privacy Policy — OSRS LLM Helper

> **STATUS: DRAFT — NEEDS LAWYER REVIEW BEFORE PUBLICATION.**
> Author: agent `r-legal` for RAI-34, 2026-06-21.
> Structure modelled on Plausible (https://plausible.io/privacy) and
> PostHog (https://posthog.com/privacy). Substance is original.

**Effective date:** TBD (do not publish until reviewed).
**Last updated:** 2026-06-21 (draft).

---

## TL;DR

1. We need to know **who you are** so we can bill you, stop you from going over
   quota, and respond to support requests. That means we collect a device key,
   the OSRS player name(s) you bind to your account, your email, billing
   details (held by Stripe), and an approximate location derived from your IP.
2. We need to know **what you said and what the game looked like** at the
   moment you said it, so we can send that context to an LLM and give you a
   useful answer. That means inventory, bank, gear, quest state, slayer task,
   location, the chat messages you type, and the model's replies.
3. We keep chats for **30 days by default** (90 if you opt-in to the longer
   history), aggregated usage metrics for **13 months**, and Stripe-side
   billing records for as long as accounting law requires (usually 6-7 years).
4. The only places your data leaves us are: **OpenRouter** (to call the LLM),
   **Stripe** (to bill you), and a privacy-respecting analytics provider
   (Plausible or PostHog EU-hosted — TBD).
5. You can **export everything we have on you** and **delete your account** at
   any time from the dashboard. Deletion is irreversible. Some Stripe-side
   billing records are preserved per accounting law (see §10).

---

## 1. Who we are

**Controller:** [LEGAL ENTITY NAME TBD] ("we", "us", "OSRS LLM Helper").
**Contact:** privacy@[domain TBD]
**Postal:** [registered office TBD]
**Data Protection contact:** dpo@[domain TBD]

This policy covers the OSRS LLM Helper RuneLite plugin (`apps/plugin/`),
the dashboard at `[domain TBD]/app`, the marketing site at `[domain TBD]`,
and the backend service that connects them.

This policy does **not** cover Jagex (the OSRS publisher), the RuneLite
project, or any third party we link out to.

## 2. The two kinds of data we handle

| Bucket                | Includes                                              | Why we have it             |
|-----------------------|-------------------------------------------------------|----------------------------|
| **Account data**      | email, device key, player names, billing info, plan   | identify + bill you        |
| **In-session data**   | chat input, game state snapshots, model output, tokens| answer your in-game question |

## 3. Account data

When you install the plugin and pair it with the dashboard, we collect:

- **Device key** — a random opaque identifier generated locally inside the
  plugin on first launch. Lets us bind your installs to your account without
  needing a password every time.
- **OSRS player name(s)** — read from `Client.localPlayer.name` when you
  authorise the binding. Used to show you "which character is currently
  chatting" and to support per-character context. You can unlink at any time.
- **Email address** — for login, receipts, and operational announcements.
- **Billing information** — card details, billing address, tax country.
  **We never see your card number.** Stripe holds it. We hold the Stripe
  customer ID, the last 4 digits, the expiry, and the brand.
- **Approximate location (city-level)** — derived from your IP at session
  start. Used for fraud screening, tax compliance, and aggregate analytics.
  The raw IP is not retained beyond 30 days.
- **Plan + entitlements** — which tier you're on, how many tokens you have
  left, model preferences.

**Legal basis (GDPR Art. 6):**
- Contract performance (Art. 6(1)(b)) for everything needed to deliver the
  service you paid for.
- Legitimate interest (Art. 6(1)(f); cf. Recital 47 on fraud prevention) for
  the IP-derived geo and abuse signals.
- Legal obligation (Art. 6(1)(c)) for tax + accounting records.

## 4. In-session data

While you are actively chatting:

- **Chat messages** — what you type into the in-game / dashboard chat input.
- **Game state snapshots** — the subset of state our tools collect: inventory,
  bank tabs, gear, quest progress, slayer task, current region/tile, recent
  XP drops, clue scroll state, group iron man state if applicable.
- **Model output** — what the LLM responds with, including any tool calls.
- **Token counts** — input + output tokens per turn for billing.

This data is sent over TLS to our backend, forwarded to **OpenRouter** to
reach the chosen model, and the response is sent back to your plugin.

**We do not collect:**
- Your Jagex account password (we never see it).
- Your bank PIN (we never see it).
- Mouse/keyboard input or screen recordings.
- Other players' game state.
- Private message contents from in-game friends chat (unless you explicitly
  paste them into the assistant input).

**Legal basis:** Contract performance (Art. 6(1)(b)).

## 5. Cookies + similar technologies

The marketing site uses **essential cookies only**. No tracking pixels, no
third-party advertising cookies, no Facebook/Google Ads tags.

For analytics we use **server-side, cookie-less event aggregation** via
Plausible or PostHog (EU-hosted instance, TBD). This is why **there is no
cookie banner** — under GDPR + ePrivacy, essential cookies do not require
consent.

See `COOKIE_POLICY.md` for the full breakdown.

## 6. Sub-processors

See `SUB_PROCESSORS.md` for the full table.

Headline list:

| Provider        | Purpose                       | Data shared                          | Region    |
|-----------------|-------------------------------|--------------------------------------|-----------|
| OpenRouter      | LLM routing                   | chat messages, game state snapshot   | US        |
| Stripe          | Billing + subscriptions       | email, billing info, plan            | US + EU   |
| Plausible/PostHog (TBD) | Privacy-respecting analytics | aggregated, anonymised events    | EU        |
| Sentry (TBD)    | Error monitoring              | error stack traces, anonymised user id | EU      |
| Fly.io / Render (TBD) | Backend hosting         | all in-session data in transit + at rest | US/EU |
| Neon (TBD)      | Production Postgres           | account + in-session data            | EU        |

> **Important about OpenRouter.** OpenRouter is a router to many model
> providers (Anthropic, OpenAI, Google, etc.). When we route a turn to a
> given model, the prompt goes to that model's provider. OpenRouter's
> policy explicitly states they "do not control, and are not responsible
> for, LLMs' handling of your Inputs or Outputs, including for use in
> their model training" (openrouter.ai/privacy). We enable
> prompt-logging-off on every route we can.

## 7. International transfers

If you're in the EU/UK and we transfer your data to a non-EU/UK provider
(Stripe US, OpenRouter US, etc.), we rely on:

- **EU Standard Contractual Clauses (2021/914)**; and
- **UK International Data Transfer Addendum** for UK personal data.

## 8. Retention

See `DATA_RETENTION.md` for the precise schedule. Summary:

- Chat messages + game state snapshots: **30 days** (default), or **90 days**
  if you opt in.
- Aggregated, anonymised usage metrics: **13 months**.
- IP addresses + session logs: **30 days**.
- Account record: until you delete the account, then 30-day grace, then hard delete.
- Billing records: **6 years** UK / **7 years** US per accounting law.

## 9. Your rights

If you are in the EU/UK, under GDPR/UK GDPR you have the right to:
**Access** (Art. 15), **Rectify** (Art. 16), **Erase** (Art. 17),
**Restrict** (Art. 18), **Portability** (Art. 20), **Object** (Art. 21),
**Withdraw consent** (Art. 7), and **Lodge a complaint** with your
supervisory authority (UK: the ICO).

If you are in California, under CCPA/CPRA you have analogous rights.
**We do not sell your data and we do not share it for cross-context
behavioural advertising.**

**How to exercise:** dashboard → Account → Privacy. Both Export and Delete
are self-serve. For anything more complex, email privacy@[domain TBD].
We respond within 30 days.

## 10. Account deletion + the accounting carve-out

When you delete your account:

1. We immediately stop processing your in-session data.
2. Within 30 days we hard-delete: device keys, player bindings, chat
   history, game state snapshots, session logs.
3. **We preserve Stripe-side billing records** (invoices, customer object,
   payment intents) for **6 years** (UK) or **7 years** (US) — whichever
   applies — to comply with tax + accounting law (HMRC SI 2010/2792 reg. 9
   in the UK; IRC §6001 in the US).
4. After the accounting period expires, we delete the Stripe customer too.

If you want a full export before deletion, use the Export button first.
Deletion is irreversible.

## 11. Security

- TLS 1.3 in transit.
- AES-256 at rest (managed by hosting provider).
- Device keys are hashed at rest with Argon2id.
- Stripe holds card data — we never do.

We will publish a `SECURITY.md` with disclosure contact before launch.

## 12. Children

Our service is not directed at children under 13 (US COPPA) / 16 (most of
the EU). OSRS itself has age guidance from Jagex; we defer to that. If we
become aware that we have collected data from a child under the applicable
age, we will delete it.

## 13. Changes to this policy

Material changes will be announced in-product 14 days before they take
effect and by email if you have a paid plan.

## 14. Contact

Email: privacy@[domain TBD]

---

## Sources + structure references (not part of the published policy)

- Plausible Analytics privacy policy: https://plausible.io/privacy — structural template
- PostHog privacy policy: https://posthog.com/privacy — CCPA "no sale" stance
- Stripe DPA: https://stripe.com/legal/dpa (eff. 2025-11-18)
- OpenRouter privacy policy: https://openrouter.ai/privacy — quoted re: model-training disclaimer
- OpenRouter terms: https://openrouter.ai/terms — basis for "log-off by default" position
- GDPR Recital 47 — basis for IP retention
- UK ICO guidance on "strictly necessary" cookies
- IRS Publication 583 + HMRC VAT record-keeping — basis for billing retention

**NEEDS LAWYER REVIEW.** Especially: controller/processor designation,
SCCs/IDTA language, children section thresholds, CCPA "sale/share"
determination.
