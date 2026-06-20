# Cookie Policy

> **STATUS: DRAFT — NEEDS LAWYER REVIEW BEFORE PUBLICATION.**
> Author: agent `r-legal` for RAI-34, 2026-06-21.

**Effective date:** TBD.
**Last updated:** 2026-06-21.

---

## TL;DR

Our marketing site uses **essential cookies only**. We do not use
tracking pixels, ad cookies, or third-party advertising tags.
**Therefore there is no cookie banner** — under the UK PECR + EU
ePrivacy Directive, strictly-necessary cookies do not require consent
(see ICO guidance on the "strictly necessary" exemption).

The dashboard (`/app`) uses an authentication cookie because you are
logged in. Closing your account or logging out clears it.

The RuneLite plugin does not run in a browser and therefore does not
use cookies.

---

## What is a cookie

A cookie is a small text file a website asks your browser to store, so
the site can recognise you between page loads. Cookies are technically
the only mechanism this policy is about, but the same rules apply to
similar technologies (localStorage, sessionStorage, IndexedDB) and we
treat them the same way.

## What we use, in full

### Marketing site (`[domain TBD]`)

| Name                | Type       | Purpose                                      | Duration | First/third party |
|---------------------|------------|----------------------------------------------|----------|-------------------|
| `__Host-csrf`       | Essential  | CSRF token for any form submission           | Session  | First             |
| `cf_clearance`      | Essential  | Cloudflare bot challenge result              | 30 days  | Third (Cloudflare)|

No analytics cookies. We use **Plausible** (or PostHog EU TBD) which
runs **without cookies** — it identifies sessions by a daily-rotating
hash of (date salt + IP + user agent) on the server side, never
written to your device. (See Plausible Data Policy.)

### Dashboard (`[domain TBD]/app`)

| Name                | Type       | Purpose                                      | Duration | First/third party |
|---------------------|------------|----------------------------------------------|----------|-------------------|
| `__Host-session`    | Essential  | Keeps you logged in                          | 30 days  | First             |
| `__Host-csrf`       | Essential  | CSRF token                                   | Session  | First             |
| `cf_clearance`      | Essential  | Cloudflare bot challenge result              | 30 days  | Third (Cloudflare)|

### Plugin (RuneLite client)

The plugin is a Kotlin program inside the RuneLite client. It uses
**no cookies**. It stores local config (consent record, device key)
on disk inside the RuneLite profile directory.

### What we do NOT use

- Google Analytics, GA4, Tag Manager.
- Facebook Pixel, TikTok Pixel, LinkedIn Insight, Twitter Pixel.
- Advertising network cookies.
- Cross-context behavioural advertising tags.
- Session replay (Hotjar, Mouseflow, FullStory).
- A/B testing tools that drop cookies.

If we ever do, we will add a proper cookie banner first.

## Why no cookie banner

Under the UK PECR Regulation 6 and the EU ePrivacy Directive Art. 5(3),
consent is required for cookies that are **not** "strictly necessary
for the provision of an information society service explicitly
requested by the user".

A login cookie, a CSRF token, and a CDN bot-challenge cookie all meet
the strict-necessity test. (ICO: "Use of cookies and similar
technologies", November 2024 update.)

Because nothing we drop is non-essential, no banner is required. If
that ever changes we will add a banner that:

- defaults all non-essential cookies to OFF;
- lets you say no with one click (no dark patterns);
- has equal-prominence Accept and Reject buttons.

## How to control cookies

You can clear cookies via your browser settings at any time. Clearing
the dashboard's session cookie will log you out. Clearing the CSRF
cookie will require a page refresh.

## Do Not Track + Global Privacy Control

We honour browser-level GPC headers on the dashboard. Because we don't
sell or share personal information for advertising, this is largely a
no-op — but if a tool ever changed that, GPC would suppress it.

## Changes

We will update this policy whenever we add or remove a cookie. The
"Last updated" date will change.

## Contact

privacy@[domain TBD]

---

## Sources

- UK PECR (Privacy and Electronic Communications Regulations 2003),
  Regulation 6.
- ePrivacy Directive 2002/58/EC, Art. 5(3).
- ICO guidance "Use of cookies and similar technologies" (Nov 2024).
- Plausible Data Policy (cookie-less analytics).

**NEEDS LAWYER REVIEW.** Especially: the determination that `cf_clearance`
qualifies as "strictly necessary" — some DPAs argue Cloudflare's anti-bot
cookie is a borderline case.
