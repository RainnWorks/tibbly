# Sub-processors

> **STATUS: DRAFT — NEEDS LAWYER REVIEW BEFORE PUBLICATION.**
> Author: agent `r-legal` for RAI-34, 2026-06-21.
> Publishable under GDPR Art. 28(2) + 28(4). Users get 30 days' notice
> before a new sub-processor is added.

**Last updated:** 2026-06-21.

---

## Headline list

| #  | Provider              | Purpose                                   | Personal data shared                                                            | Region (processing)            | DPA / Terms                                                |
|----|-----------------------|-------------------------------------------|---------------------------------------------------------------------------------|--------------------------------|------------------------------------------------------------|
| 1  | **OpenRouter, Inc.**  | LLM routing                               | Chat input text, game state snapshot, ephemeral session ID                       | US (with onward routing to model provider region) | https://openrouter.ai/terms + https://openrouter.ai/privacy |
| 2  | **Stripe, Inc.** + Stripe Payments Europe Ltd. | Subscriptions, payments, tax, invoices | Email, name, billing address, payment method (token only, not card PAN), purchase history | US + EU (Ireland) | https://stripe.com/legal/dpa (eff. 2025-11-18) |
| 3  | **Plausible Insights OÜ** *(or PostHog Inc. — TBD)* | Privacy-respecting product analytics | Anonymised event stream, hashed IP, user-agent, country | EU (Plausible: Germany; PostHog: EU instance) | https://plausible.io/dpa or https://posthog.com/dpa |
| 4  | **Functional Software, Inc. ("Sentry")** | Error monitoring, crash reports | Stack traces, anonymised user id, browser/OS, scrubbed request payloads | US (EU region available, will use) | https://sentry.io/legal/dpa/ |
| 5  | **Fly.io, Inc.** *(backend host — TBD vs Render)* | Backend service hosting | All in-session data while in flight + at rest in DB volume | US + EU regions (we'll pin EU primary) | https://fly.io/legal/dpa/ |
| 6  | **Neon Inc.** *(prod Postgres — TBD)* | Managed Postgres for prod | All account + in-session data | EU (Frankfurt) | https://neon.tech/dpa |
| 7  | **Cloudflare, Inc.** | CDN, DDoS protection, WAF for marketing + dashboard | IP, request metadata, country | Global edge | https://www.cloudflare.com/cloudflare-customer-dpa/ |
| 8  | **Resend, Inc.** *(transactional email — TBD)* | Account emails, receipts, password reset | Email address, name, message body | US | https://resend.com/legal/dpa |

## Sub-processors of sub-processors (Model Providers via OpenRouter)

OpenRouter is itself a router. When we send a turn to OpenRouter, it
routes to one of the following model providers depending on the model
selected:

| Model provider | Models routed to                              | Default training policy           | Region    |
|----------------|-----------------------------------------------|-----------------------------------|-----------|
| Anthropic, PBC | Claude Opus 4.7, Sonnet 4.6, Haiku 4.5        | Does not train on API inputs      | US        |
| OpenAI, LLC    | GPT-4.x, GPT-5.x                              | Does not train on API inputs      | US        |
| Google LLC     | Gemini family                                 | Does not train on API inputs      | US        |
| (others)       | per OpenRouter's catalogue, on demand         | varies — we will only route to no-train-default models | varies |

We default to **Anthropic Claude** for chat turns. Other providers are
only routed to if the user explicitly selects them.

> **Important caveat re: training.** OpenRouter's privacy policy explicitly
> disclaims responsibility for what upstream providers do with prompts.
> See PRIVACY.md §6. If any provider changes that policy, we will:
>  - stop routing to them until we have explicit user opt-in, and
>  - announce the change with 30 days' notice.

## 30-day notice mechanism

When we add a new sub-processor, we will:

1. Add the row to this table.
2. Email all account holders with the change + the effective date (>=30 days out).
3. Post a notice on the dashboard.
4. Honour any objection: an account holder may terminate before the
   effective date and receive a pro-rated refund of any prepaid term.

This satisfies our Art. 28(2) "general authorisation" obligation.

## Audit + assurance

For each sub-processor we keep on file (in `infra/legal/dpas/`):

- The signed DPA (or the URL of their accepted ToS-bound DPA).
- The provider's most recent SOC 2 / ISO 27001 report when available.
- Their published list of *their* sub-processors, reviewed quarterly.

## Changes log

- 2026-06-21 — Initial draft. None of the rows are signed yet. None of
  the providers are yet under DPA with us. **NEEDS LAWYER REVIEW + DPA
  EXECUTION BEFORE WE GO LIVE.**

---

## Sources

- Stripe DPA: https://stripe.com/legal/dpa
- OpenRouter privacy: https://openrouter.ai/privacy
- OpenRouter terms: https://openrouter.ai/terms
- GDPR Art. 28 — sub-processor obligations.
- EDPB Guidelines 07/2020 on the concepts of controller and processor.
