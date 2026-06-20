# Terms of Service — OSRS LLM Helper

> **STATUS: DRAFT — NEEDS LAWYER REVIEW BEFORE PUBLICATION.**
> Author: agent `r-legal` for RAI-34, 2026-06-21.
> Structure modelled on Linear's ToS (https://linear.app/terms) and
> Vercel's ToS (https://vercel.com/legal/terms). Substance is original.

**Effective date:** TBD.
**Last updated:** 2026-06-21 (draft).

---

## 0. The "please actually read this" headline

OSRS LLM Helper is **not a bot, not an autoclicker, not a botting
helper**. It does not click for you, type for you, or play OSRS for you.
It is a chat companion that sees your game state (because you told it to)
and gives you advice. **You** still play the game.

Everything you send it goes to an AI model on **OpenRouter**. That
includes your inventory, your bank, your quest log, and the question
you typed. If that's not something you're comfortable with, do not use
this service — see "Decline" in the in-plugin consent flow.

By installing the plugin, creating an account, or using any part of the
service, you agree to these Terms.

---

## 1. Definitions

- **"Service"** — the OSRS LLM Helper RuneLite plugin, the dashboard, the
  marketing site, and the backend service that connects them.
- **"You" / "User"** — the natural person who installs the plugin and/or
  holds a billing account with us.
- **"OSRS"** — Old School RuneScape, owned and operated by Jagex Ltd.
- **"Jagex"** — Jagex Limited, the publisher of OSRS.
- **"RuneLite"** — the third-party open-source OSRS client we run inside.
- **"Game State"** — the subset of your in-game information our plugin
  reads via the RuneLite API: inventory, bank, gear, quest progress, etc.
- **"Model Provider"** — the upstream LLM provider routed to by
  OpenRouter (e.g. Anthropic, OpenAI, Google).

## 2. Eligibility

You must be at least:
- **13 years old** in the United States; or
- **16 years old** in the European Economic Area (or the lower age
  permitted under your member state's law); or
- the local age of digital consent where you live, whichever is higher.

You must have a valid OSRS / RuneScape account in good standing with
Jagex. We do not require you to share your Jagex credentials with us —
we never ask for them.

## 3. The relationship with Jagex + RuneLite

OSRS LLM Helper is an **independent product**. We are not affiliated
with, endorsed by, or sponsored by Jagex Limited or RuneLite Pty Ltd.
"Old School RuneScape", "RuneScape", and "Jagex" are trademarks of Jagex
Limited. "RuneLite" is a trademark of RuneLite Pty Ltd.

You acknowledge that your use of OSRS is governed by the **Jagex Terms
& Conditions** and the **Jagex Rules of Conduct**. **You are responsible
for ensuring your use of OSRS LLM Helper complies with those rules.**

To the best of our understanding, OSRS LLM Helper operates inside the
RuneLite plugin sandbox and:

- does not modify the OSRS game client;
- does not synthesise mouse or keyboard input;
- does not automate gameplay actions;
- does not provide an unfair advantage in PvP, minigames, or competitive
  events beyond the kind of advice a knowledgeable friend or wiki could
  provide;
- only displays informational overlays, marks, and highlights using the
  RuneLite plugin overlay API.

If Jagex publishes guidance or makes a determination that our service
violates their rules, we will adjust the product or, if necessary,
discontinue it.

You are solely responsible for the consequences to your own OSRS
account. **No refund, credit, or compensation will be paid in respect of
any Jagex moderation action**, including but not limited to mutes, bans,
or account locks. (See §11.)

## 4. What we provide

Subject to these Terms and your active subscription, we grant you a
**limited, non-exclusive, non-transferable, revocable licence** to:

- install and run the OSRS LLM Helper RuneLite plugin on your own devices;
- use the dashboard at `[domain TBD]/app`;
- send chat messages to our backend that are forwarded to an AI model
  via OpenRouter;
- receive overlays, highlights, and chat responses from the AI model.

## 5. Game state + AI model disclosure

**This is the most important clause.** Read it carefully.

When you use the chat:

1. Your **typed message** is sent to our backend.
2. A **snapshot of your game state** — as configured by the active tool
   set — is sent to our backend.
3. Our backend forwards both to **OpenRouter**.
4. OpenRouter routes them to a **Model Provider** (Anthropic, OpenAI, or
   another LLM operator).
5. The Model Provider's reply comes back the same path.

We instruct OpenRouter to disable prompt logging where the provider
allows it. **However:**

- The Model Provider may temporarily retain prompts for abuse detection
  regardless of our setting.
- We cannot guarantee that all upstream providers honour every no-train
  / no-log flag in every region.
- You should not type anything into the chat you would not be willing to
  share with the Model Provider's abuse-monitoring team.

See `PRIVACY.md` §6 for the sub-processor list.

## 6. Acceptable use

You **must not**:

- use the Service to automate gameplay, click on your behalf, or
  otherwise violate the Jagex Rules of Conduct;
- attempt to extract, reverse-engineer, or train a competing model on
  our system prompts or tool definitions;
- resell, sublicense, or share your account credentials;
- use the Service to abuse, harass, or threaten another player or our staff;
- attempt to bypass our token quota, billing system, or rate limits;
- use the Service to discover or exploit vulnerabilities in OSRS, the
  RuneLite client, or any third-party software;
- use the Service for any unlawful purpose;
- use the Service to facilitate real-world trading (RWT) of OSRS gold
  or items for non-OSRS currency.

We may suspend or terminate your account for any of the above, with or
without notice.

## 7. Subscriptions + billing

### 7.1 Plans
We offer monthly subscription plans plus pay-as-you-go top-ups. Current
plans, prices, and token allowances are published at `[domain TBD]/pricing`.
Prices may change with 30 days' notice; existing subscribers keep their
current price until renewal.

### 7.2 Billing cycle
Subscriptions auto-renew on the same day each month (or annually) until
cancelled. You can cancel any time from the dashboard or via the Stripe
Customer Portal. Cancellation takes effect at the end of the current
billing period.

### 7.3 Payment processor
All payments are processed by **Stripe**. We never see your card details.

### 7.4 Taxes
Prices are shown exclusive of tax unless stated otherwise. Stripe Tax
calculates and applies VAT / sales tax based on your billing location.

### 7.5 Failed payments
If a renewal fails, we will retry over 14 days. Your access may be
paused after the first retry fails.

### 7.6 Tokens + quotas
Each subscription includes a monthly token allowance. Unused tokens
**do not roll over**. Top-up tokens **do** roll over for 12 months from
purchase. We cap turn-level token usage to prevent runaway costs.

## 8. Refunds

### 8.1 UK / EU consumers (cooling-off)

If you are a consumer in the United Kingdom or the EU, you have a right
to cancel within **14 days** of starting your subscription under the UK
**Consumer Contracts (Information, Cancellation and Additional Charges)
Regulations 2013** and equivalent EU consumer law.

**Important:** because we deliver digital content immediately, **we ask
you to expressly consent to immediate delivery and to acknowledge that
you lose your right to cancel once delivery has begun** (Reg. 37(1)(a)).
We capture this consent in the checkout flow.

**Our policy is more generous than the law requires.** We offer a
**no-questions-asked refund within 14 days of first payment** for new
subscribers, regardless of usage, provided you have not consumed more
than **20% of the period's token allowance**. Email refunds@[domain TBD]
to request one.

### 8.2 Other regions

Outside the UK/EU we do not offer refunds for partial months. If you
believe you have been billed in error, contact us — we will fix it.

### 8.3 Annual plans

Annual plans are refundable on a pro-rata basis within the first 30
days if you have not exceeded 20% of the annual token allowance.

## 9. Free tier

The free tier exists for evaluation. It is rate-limited, may use a
cheaper model, and may be modified or discontinued at any time.

## 10. AI accuracy + reliance

The Service uses large language models. Large language models can be
**confidently wrong**. We make no warranty that any chat output is
accurate, current, or suitable for any in-game purpose.

**Do not stake high-value items, untradeables, or hardcore/iron-mode
lives on advice from this service without verifying.**

You retain all rights to anything you type into the chat. We claim no
ownership over your inputs or outputs.

## 11. No liability for Jagex action

You acknowledge that:

- Jagex may at its sole discretion mute, ban, or otherwise sanction OSRS accounts;
- the rules around third-party plugins, AI assistance, and overlays are evolving;
- nothing in this service is endorsed by Jagex.

**We are not liable for any sanction Jagex applies to your account, even
if you were following advice from our service when the sanction was
applied.** No refunds, credits, item replacements, or compensation will
be paid in respect of Jagex moderation outcomes.

## 12. Disclaimers

THE SERVICE IS PROVIDED "AS IS" AND "AS AVAILABLE", WITHOUT WARRANTY OF
ANY KIND, EXPRESS OR IMPLIED, INCLUDING WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT. WE DO NOT
WARRANT THAT THE SERVICE WILL BE UNINTERRUPTED, ERROR-FREE, OR SECURE.

Nothing in this section limits any non-excludable consumer right under
applicable law.

## 13. Limitation of liability

To the maximum extent permitted by law, our aggregate liability arising
out of or in connection with the Service is limited to the **greater
of (a) the fees you paid us in the 12 months prior to the event giving
rise to the claim, or (b) GBP £100**.

We are not liable for: lost OSRS items, lost OSRS XP, lost ranks, lost
ranks in clan / GIM groups, indirect, incidental, consequential,
special, exemplary, or punitive damages.

Nothing in this section limits liability for: death or personal injury
caused by our negligence, fraud or fraudulent misrepresentation, or any
liability that cannot be excluded by law.

## 14. Indemnity

You will indemnify and hold us harmless from any claim brought by a
third party (including Jagex) arising out of (a) your breach of these
Terms, (b) your violation of the Jagex Rules of Conduct, (c) your
misuse of the Service, or (d) any content you submit through the Service.

## 15. Termination

Either party may terminate this agreement at any time:

- **You** by cancelling your subscription and uninstalling the plugin.
- **We** by giving you 30 days' written notice (immediate notice if you
  have materially breached these Terms or applicable law).

## 16. Changes to these Terms

Material changes will be announced 30 days in advance by email and
in-product. Continued use after the effective date constitutes
acceptance.

## 17. Governing law + disputes

These Terms are governed by the laws of **England and Wales**. The
courts of England and Wales have exclusive jurisdiction, except that:

- consumers in the EU may bring proceedings in their own member state;
- consumers in California may use small-claims court in California.

We will try to resolve disputes informally first; please email
legal@[domain TBD] before filing.

## 18. Miscellaneous

- **Severability:** if any clause is unenforceable, the rest stands.
- **No waiver:** failure to enforce a right is not a waiver.
- **Assignment:** you may not assign these Terms; we may, on notice.
- **Entire agreement:** these Terms + the Privacy Policy + the in-plugin
  Consent are the entire agreement.

## 19. Contact

legal@[domain TBD] — for legal notices.
support@[domain TBD] — for everything else.

---

## Sources + structure references

- Linear ToS: https://linear.app/terms
- Vercel ToS: https://vercel.com/legal/terms
- UK Consumer Contracts Regulations 2013 (SI 2013/3134)
- OpenRouter ToS: https://openrouter.ai/terms
- Jagex T&Cs: https://www.jagex.com/en-GB/terms

**NEEDS LAWYER REVIEW.** Especially: the Jagex-disclaimer language
(critical to risk), the cooling-off carve-out language, the liability
cap, and the choice-of-law clause.
