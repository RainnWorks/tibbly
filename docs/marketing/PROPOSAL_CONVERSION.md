# Marketing IA proposal: conversion-first angle

Author: agent loop-mplus9 (CRO lens). 2026-06-21.

Treats the page as a sales asset. Its only job: lift the rate at which
a cold OSRS player completes Stripe Checkout for £7, or grabs Free as
a funnel entry.

## 1. Funnel hypothesis

**Channel mix.** (1) YouTube preroll on a trusted creator (J1mmy,
Limpwurt, Soup, B0aty per `creators.md`), intent curious-scanning.
(2) `r/2007scape` thread linking the demo, intent adversarial: "is
this a bot, is this slop, why does it cost money". (3) Plugin Hub
listing when we land it, intent product-led.

**First 5 seconds.** They need (a) OSRS-specific signal, not generic
AI SaaS; (b) the line `Helps you play. Never plays for you.`; (c) a
chat shape they recognise (stuck quest, clue, slayer); (d) a price
without scrolling sideways.

**Scroll kill points.** Above the fold: generic SaaS gradient bounces
the Reddit cohort. Pricing: if the tier picker is below 1.5 viewports
mobile never reaches it. FAQ: if "is this a bot" is not the first
question visible, the Reddit cohort closes the tab.

## 2. Three dials (taste-skill)

- `DESIGN_VARIANCE: 6`. Above 6 trades novelty for legibility. A
  paywall-suspicious player reads novelty as "marketing-y". 6 keeps the
  RuneLite texture (skill grid, gold accent, 1px chrome) without going
  Awwwards.
- `MOTION_INTENSITY: 2`. Current hero rains skill icons. Motion with no
  conversion message reads as budget spent in the wrong place. Drop to:
  chat-message stagger on the demo (which IS the conversion message),
  CTA hover lift, presence ticker. `motion-must-be-motivated` applies.
- `VISUAL_DENSITY: 5`. OSRS UI is dense. Art-gallery-airy reads as
  "SaaS pitch dressed up". 5 is RuneLite-native without losing mobile
  legibility.

## 3. Type, palette, icon source

**Type.** Display **Cabinet Grotesk** (modern-but-warm, no pixel-font
tax). Body **Geist Sans** (conversion-tested, reads respectable).
Mono **Geist Mono** on tool names and live numbers only (reads as
"system"). Inter and Fraunces banned per constraint.

**Palette.** Single-accent on warm near-black. Surface one step
lighter. Border warm grey for RuneLite chrome. Sole accent **OSRS
gold** (#ffcc00) on H1, primary CTA, presence number, reply ribbon.
Banned: beige/cream, brass, purple gradient. Gold IS the warm
accent; doubling muddles. CRO: one saturated accent on dark ground
maxes CTA contrast (WCAG AA); dark mode lifts 3-5% on gaming
audiences who play in low light.

**Icons.** OSRS skill PNGs from `@osrs-llm-helper/osrs-assets` for
in-game concepts (double as trust signals); `@phosphor-icons/react`
regular for utility chrome (one family locked); real SVG marks for
RuneLite logo and Stripe badge.

## 4. Information architecture (kill order, lowest = cut first in A/B)

| # | Section | Kill | Job |
|---|---|---|---|
| 1 | Sticky nav (logo, anchor, CTA `from £7/mo`) | never | reach pricing in one click |
| 2 | Hero (H1 + sub + 2 CTAs + mono anti-bot line) | never | answer "what is this" in 5s |
| 3 | **Free-tier strip** (one giant `Start free`) | low | funnel entry for sceptics |
| 4 | Demo (one chat, real tool calls) | never | prove the product exists |
| 5 | Pricing (4 tiers, £7 highlighted) | never | the conversion fold |
| 6 | Anti-bot strip (3 bullets) | low | resolve the #1 Reddit objection inline |
| 7 | Feature grid (6 capabilities, tightened) | medium | reasons-to-believe |
| 8 | Problem cards (quest, clue, slayer + new "what do I do today") | medium | secondary objections |
| 9 | Live counter (presence) | high | social proof; cut if launch numbers low |
| 10 | FAQ (bot Q first) | low | long-tail objection sink |
| 11 | Footer (Stripe badge, refund, contact) | never | trust closure |

Shifts: pricing UP, live counter DOWN, anti-bot strip NEW.

## 5. Sample copy

**Hero.** H1: `Stop alt-tabbing. Start playing.` (keeps current line,
concrete pain P2 framing). Sub (20 words): `An OSRS co-pilot that
already sees your bank, your quest log, and your inventory. No API
keys. No bot.` Three objections in one sentence. CTAs: `Start free ·
install plugin` + `See 12s demo`. Mono line under: `Helps you play.
Never plays for you. Read-only RuneLite plugin.`

**Free strip (new).** H3: `30 free messages a day. Forever.` Sub:
`Install the plugin. Pair RuneLite. Upgrade only when you hit the
wall.` One giant button.

**Demo H2.** `A real Dragon Slayer II turn. Live tool calls.` Append
to the reply: `Used 1 of today's free messages.` Narrates upgrade
pressure without selling.

**Pricing H2.** `Pay for what you actually do.` Sub: `Free for kicking
the tyres. £7 for the player you actually are. Hard cap per tier.` Tiers:

- Free £0: `30 messages a day, watermarked. BYO model key coming soon
  for power users.`
- Hobbyist £7 (`Most picked`): `Your main account, sorted. Quest, clue,
  bank prep, slayer.`
- Pro £19: `For iron mains, GIM groups, creators. Deeper reasoning on
  the hard turns.`
- Iron £49: `Quest cape pilots, raid prep, 12-month plans. Top-tier
  model on the hardest steps.`

**Anti-bot strip.** H3: `Why this won't get you banned.` Three columns,
hairlines, no cards:

1. `Read-only. We see what RuneLite already sees. Nothing clicks for you.`
2. `Same plugin shape as Quest Helper. 555,505 RuneLite players run that.`
3. `Outbound TLS only. No localhost server, no keystroke synthesis.`

The 555,505 figure is the Quest Helper install count (pain P1). Real
number, concrete, anchored to a tool every OSRS player recognises.

**New problem card (pain P8).** `What should I do right now?` Body:
`Your gear, your bank, your quest log, today's GE prices. Tibbly is
the only tool that knows all four at once.`

**FAQ order.** Bot Q first, then state-access, then "do I need an
Anthropic key" (no), then cost, then refunds, then multi-account,
then Hub status, then privacy, then models (generic, no IDs), then
"new quest next Wednesday".

## 6. CTA design

Two intents only: `Start free` (install) and `Choose [tier]` (Stripe).
Everything else is a text link.

Placement: hero (1 primary + 1 secondary); free strip (1 giant); demo
end (text link only, the demo's job is to convince not convert);
pricing (1 per card, £7 highlighted); sticky nav (`from £7/mo` always
top right); footer (text link); mobile sticky bottom bar under 768px
shows `Start free · £0` for thumb-reach.

Shape: sharp corners, 2px gold border, 8% gold fill, gold text. Hover
saturates to 25% with a soft glow. `:active` shifts down 1px. Gold on
near-black passes 4.5:1. Every label is 3 words or fewer.

Wiring: existing `POST /v1/billing/checkout/{tier}` already returns a
Stripe URL and `PricingTiers` already redirects. Add
`data-cta="primary-{tier}"` so analytics can measure click-through
without a refactor.

## 7. Trust + objection handling

1. **Will I get banned?** Hero mono line; anti-bot strip; FAQ #1.
   Cite Quest Helper (pain P1, 555,505 installs) as precedent.
2. **AI slop in a RuneScape skin?** The demo with real tool calls is
   the only thing that proves the product exists. Presence counter is
   secondary support.
3. **Why pay when other plugins are free?** Free tier visible; pricing
   sub-line `Free for kicking the tyres`; FAQ contrasts the BYO-key
   onboarding tax to Tibbly's Stripe-and-go path (the wedge per
   `differentiation.md`).
4. **Wiki stale?** Feature card says `live wiki retrieval, never
   stale`; FAQ `What if a quest drops next Wednesday`. Implicit truth
   in pain P11 (Sailing rebalanced multiple times since launch).
5. **What if I cancel?** `14-day full refund` hoisted next to every
   paid CTA so the safety net is visible at click moment.

## 8. What gets killed

- Hero **skill-icon rain background**. Motion with no conversion
  message. One static sprite composition instead.
- Hero **featured-skills row**. Six icons that say nothing. Replaced
  by the mono anti-bot line.
- **Live counter at fold 3.** Demoted to fold 9; at launch the small
  count actively hurts if it lands early.
- Pricing **CCR footnote only at the bottom**. Hoist `14-day full
  refund` next to every paid CTA.
- Demo footer **model-name caption** (vendor ID leak). Replace with
  `Routed to deep mode for this question.` Per Tom's directive.

## 9. One opinionated take

**Move the Free tier out of the pricing comparison and put it as a
standalone strip directly under the hero.** Voice-led and visual-led
proposals will want the four-tier table to BE the hero. I disagree.

Free is the conversion accelerator for cold traffic. A sceptical
Reddit-referred player will not click `Choose Hobbyist` on the first
scroll. They will click `Start free, 30 messages a day, no card`.
That is the funnel entry. The four-tier card matters on scroll 2,
after Free has hooked them.

Practically: `Start free` strip between hero and demo. Four-tier card
after the demo as the upgrade decision point. Splits the page into
two funnels (free install vs paid checkout) measured independently in
A/B without contamination.

Trade-off: this slightly buries the £7 anchor on first scroll.
Mitigation: sticky nav button reads `Pricing from £7/mo` so the
anchor stays in view at all depths without forcing the four-tier
comparison early. This is the trade I would defend at a CRO board.
