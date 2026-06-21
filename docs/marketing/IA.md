# Tibbly marketing IA: canonical synthesis

> Author: agent loop-mplus9, synthesis lens.
> Date: 2026-06-21.
> Status: canonical spec. Buildable. Supersedes the four sibling proposals
> (`PROPOSAL_VISUAL.md` #46, `PROPOSAL_VOICE.md` #47,
> `PROPOSAL_CONVERSION.md` #48, `PROPOSAL_OBJECTIONS.md` #49). Where this
> doc disagrees with a sibling, this doc wins. Where it stays silent, the
> sibling's reasoning still applies as background.

This is the page Tibbly ships to a cold OSRS player who clicked a Discord
link from a Settled-style YouTuber. It is not a brand book. It is the
section list, the type stack, the palette tokens, the motion budget, and
the verdict on every disagreement between the four proposals. A senior
engineer should be able to read this and ship the page in five focused
days. A copywriter should be able to write the rest of the page from the
opening copy in section 6. A designer should be able to build the
component library without a second meeting.

---

## 1. Synthesis principles

Three principles resolve every disagreement below. They are ordered: the
earlier ones outrank the later ones when two principles conflict.

**Principle 1: trust before features.** The OSRS buyer is shopping for
permission to install Tibbly without losing their account. Every feature
pitch we run before we have dispatched the ban risk is wasted ink. PR #49
(objections) is correct that the no-automation guarantee belongs above
the fold. PR #48 (conversion) is correct that this same guarantee is the
single biggest scroll-killer for cold Reddit traffic. Both agree on the
substance and disagree only on placement; we side with #49 on placement
because trust dispatched once at the top earns the right to talk about
features at all.

**Principle 2: honesty over polish.** We do not have testimonials, we do
not have a "Most picked" install count, and we do not have a globe of
live users that reads as anything other than embarrassing at launch. Any
proof we surface has to be real and verifiable from the file system or
the database. This kills the "Most picked" badge (#48 wanted to keep it),
demotes the live counter to a thin status strip (#47), and rejects logo
walls and a comparison table against named competitors (#49 explicitly
flagged this risk).

**Principle 3: OSRS-native without IP risk.** The page should read as of
the game without copying Jagex assets. RuneStar CC0 fonts and RuneLite
BSD-2 sprites only. No wiki sprites in marketing surfaces, no Jagex
wordmark, no NPC likenesses, no Lumbridge skyline. PR #46 was right to
reach for the literal in-game pixel display font (it is CC0 and we own
the file); PR #49 was wrong to suggest we lean on wiki CC-BY-SA sprites
for the "what the plugin reads" diagram (Jagex IP risk plus an
attribution chain we do not want on the homepage).

---

## 2. The page IA

Nine sections. Order matters. Each section's job is named. Each
disagreement absorbed or rejected is cited by PR number.

**1. Sticky nav.** 72px dark strip, wordmark left, three anchors
(`demo`, `pricing`, `faq`) and one CTA `install plugin` right-aligned.
We absorb #48's sticky `Pricing from £7/mo` micro-anchor as a
right-aligned text link beside the CTA. We reject the full sticky
pricing chip as visual clutter; the anchored text link does the same
job at one-third the weight.

**2. Hero.** Two-up split, chatbox-frame left at 60% width, commissioned
illustration slot right at 40%. Headline holds `Stop alt-tabbing. Start
playing.` (#46 / #48 alignment) because it is the only line in the four
proposals that names the specific pain (tab-out tax) and the specific
relief (playing again) in two short clauses. Sub-hero second sentence
is the no-automation guarantee, not the voice opener (#49 wins this over
#47): `Tibbly reads your bank, your quest log, and your inventory. It
never moves your character, never clicks a tile, never types in chat.`
Two CTAs (`install plugin`, `see the demo`).

**3. Trust strip.** One row, three claims in monospace, separated by
hairlines. This is #49's anti-bot strip and #48's anti-bot strip
collapsed (they proposed the same section, almost the same words). Job:
close the Jagex-ban objection within 200ms of the hero finishing. Beats:
read-only RuneLite API, no input synthesis, plugin source on GitHub.
Each claim links to a doc or a file. Each claim is a real fact we can
defend in a Reddit thread.

**4. The shape of a chat (demo).** Pinned single-screen demo, one real
exchange typed in over scroll, tool-call chips visible between message
and reply. Absorbs #47's "let the voice prove itself" thesis and #46's
pinned-ScrollTrigger budget. Reject #47's call for two stacked exchanges
on the page; one demo earns more attention than two. Reject the model
name caption (Tom's lock, all four proposals agree). Replace with
`routed for this question`.

**5. Free tier strip.** Standalone, full-width, single giant CTA. This
is #48's opinionated take and we adopt it in full. The Free tier exists
to convert cold Reddit traffic that will not commit to a paid tier in
the first scroll. Its job is funnel entry. Placing it in the pricing
table buries it; placing it under the demo (after the voice has proved
itself) maximises conversion. Copy beats: thirty messages a day, no
card, no login, pair the plugin and go.

**6. Inventory grid (features, reframed).** Four by two square cells,
hairline gold-on-warm-brown borders, one skill icon top-left of each
cell, one line of plain copy describing what the tool actually does.
This is #46's inventory motif applied to #48's surviving FeatureGrid.
Reject #47's call to kill the FeatureGrid entirely; the OSRS player
needs a feature recap before the pricing decision, and inventory-grid
framing makes the recap read as OSRS-native rather than as the banned
three-equal-card SaaS default. Eight cells, eight features. No empty
cells.

**7. Pricing.** Three visible tiers (Free, Hobbyist, Pro) plus Iron as a
footer note (#47 wins this over #48). Four equal tier cards on a
marketing page read as upsell ladder; three reads as a real choice.
Iron does not vanish; it sits below the table as `Iron tier (£49)
exists for quest cape pilots, raid prep, and 12-month plans. Email if
you want it.` Hobbyist holds at £7 (we reject #47's £5 cut, see Section
5). The fourteen-day full refund line sits next to every paid CTA, not
just in the footer (#48's CRO note). Hard cap appears once per card,
not three times (#49 wanted three; that reads as defensive).

**8. FAQ.** Vertical accordion list inside the chatbox frame. Question
wording rewritten lowercase to match how a player types in Discord
(#47's voice cue). Bot question first (#48's order). Each answer leads
with the answer (#49's instruction). Eight questions, not twelve;
beyond eight the player has already scrolled past or decided.

**9. Footer.** Two columns (#47 wins over the current four). Left
column: wordmark, the line `Helps you play. Never plays for you.`, the
`Not affiliated with Jagex Ltd.` disclaimer on its own row. Right
column: `hello@tibbly.app`, GitHub link, privacy, terms. Attribution
microcopy at the very bottom: RuneStar CC0 fonts, RuneLite BSD-2
sprites.

Sections explicitly **not** in the IA:
- A testimonials carousel (#49 right to flag: we have none).
- A logo wall (we have no logos to flex).
- A comparison table against named competitors (lifts them by
  association, attracts legal noise).
- A standalone `Who built this` section. Tom's name and face belong on
  an `/about` deep page, not the cold-traffic homepage.
- A `Live network` globe with region pills (#48 / #49 both wanted to
  cut or demote; we agree, cut entirely from this homepage).

Total section count: nine. The current site has roughly seven sections,
so this is a small net add. The build is mostly edits to existing
section components, plus two new components (TrustStrip, FreeStrip)
and one component to delete (the standalone LiveCounter section
becomes a thin nav-adjacent indicator instead).

---

## 3. Type, palette, motion, icons

**Primary sans.** `@fontsource-variable/inter-tight`. Body type, UI
copy, sub-hero, FAQ answers, every legal line. Inter Tight is the right
compromise between #46's Inter Tight pick (modern, holds at small sizes
next to a heavy display) and #47's PP Neue Montreal (no commercial
license clarity for self-hosting in our cost band). We reject #48's
Geist Sans because the ops console already owns Geist and we want a
deliberate brand divergence between operator surfaces and player
surfaces.

**Display face for H1 only.** `runescape_bold.ttf` (RuneStar CC0,
vendored at `packages/osrs-assets/fonts/`). PR #46's opinionated take is
correct: the literal in-game pixel display font is the single fastest
"you are home" cue we own and the licensing is the cleanest CC0 in our
stack. We constrain it to the H1 and nothing else; H2 and below use
Inter Tight at a heavy weight. This is the lighter version of #46's
proposal (which used the pixel face for every section headline) and
addresses #47's concern that pixel display at scale reads as gimmick.
One headline, one moment, one brand cue.

**Monospace.** `@fontsource-variable/jetbrains-mono`. Tool-call chips,
demo speaker labels, pricing numerics with tabular alignment, the
trust-strip claims. All four proposals converged on a monospace at this
register; JetBrains Mono Variable matches ops which is the one
intentional brand bridge between operator and player surfaces.

**Palette.** Resolved toward warm-gold-on-near-black (#46) over cold-
ink-on-wood (#47). Reasoning: cold ink reads as generic AI startup; warm
gold reads as OSRS. The buyer's existential fear is being mistaken for
an AI startup that will get them banned. We bias every micro-decision
away from that read.

| Token | Hex | Purpose | WCAG vs. `--bg` |
|---|---|---|---|
| `--bg` | `#0b0703` | Page background, warm-tinted near-black | n/a |
| `--surface` | `#1a1006` | Cards, nav strip, footer | 1.4:1 (UI only) |
| `--parchment` | `#3a2a16` | Chatbox interior, pricing strip | n/a |
| `--border` | `#5a3e1a` | Hairlines, chatbox stroke | n/a |
| `--gold` | `#f3c75a` | H1, primary CTA fill, focus ring, accents | 9.4:1 AAA |
| `--gold-ink` | `#0b0703` | Text on gold CTA | 9.4:1 AAA |
| `--text` | `#f4e9c1` | Body copy on `--bg` | 13.2:1 AAA |
| `--muted` | `#a08a5a` | Helper copy, tag labels | 4.9:1 AA |
| `--success` | `#5a8a3a` | Online dot, plugin-connected pill | reserved |
| `--danger` | `#b22222` | Hard-cap callout, errors | reserved |

WCAG AA contrast pair guaranteed: `--gold` text on `--bg` background
hits 9.4:1, comfortably past AA's 4.5:1 floor. Body text `--text` on
`--bg` hits 13.2:1.

**Motion.** Single value: `MOTION_INTENSITY: 4`. Where motion lives:
hero ambient skill-icon rain at 0.12 opacity (already exists), chatbox
typewriter on the H1 reply over 600ms, demo pinned ScrollTrigger
playing one exchange over one screen height, presence ticker on the nav
indicator. Where motion is banned: footer, FAQ, pricing once entrance
reveal completes, any infinite loop that does not communicate real
state. Reduced-motion fallback wraps every animation above intensity 3.

**Icon source.** `lucide-react` only. One family across both apps. We
reject #47's Phosphor pick because ops already standardised on lucide
and a mixed icon set in the same monorepo will leak across PRs within a
quarter. Global stroke 1.75px. OSRS-native imagery comes from the 26
RuneLite BSD-2 skill PNGs in `packages/osrs-assets/skill_icons/`, never
inline SVG, never hot-linked from the wiki.

**Imagery.** RuneStar CC0 fonts and RuneLite BSD-2 sprites only. The
hero illustration is a declared placeholder slot: `TODO commissioned
hero art, 1600x1200 WebP, painterly fantasy in the spirit of the
in-game title screens, hooded adventurer at a campfire reading a
glowing scroll, no Jagex-recognisable NPCs.` Sourcing plan: commission
for v1 launch (~£400 to £1200), fall back to a still in-RuneLite
plugin screenshot until the commission lands.

---

## 4. Final dial settings

```
DESIGN_VARIANCE: 7   MOTION_INTENSITY: 4   VISUAL_DENSITY: 5
```

**`DESIGN_VARIANCE: 7`.** Below #46's 8 (the page is a payment funnel
and an 8 trades novelty for legibility on a sceptical cohort). Above
#48's 6 (the OSRS audience expects texture; a Linear-clean 6 reads as
"another AI for X" page). #47's 7 is the right number. We use the
asymmetric chatbox-and-illustration hero, the inventory-grid feature
block, and the pricing plaque strip as three real layout surprises.
Demo, FAQ, footer stay symmetric. At least five distinct layout
families across nine sections.

**`MOTION_INTENSITY: 4`.** Lower than #46's 7 (a 7 fits a game site
demo reel, not a paid-SaaS conversion fold). Higher than #48's 2 (no
motion at all reads as static and undermines the "live game state"
claim). #47's 4 matches the calm, documentary register the voice
demands. One pinned ScrollTrigger on the demo, ambient sprite drift in
the hero, presence ticker, CTA hover lift. Nothing else.

**`VISUAL_DENSITY: 5`.** All four proposals converged on 5. OSRS players
read dense interfaces every day; airy pages read as foreign. 5 is the
RuneLite-native upper bound without becoming a cockpit.

---

## 5. The specific disagreements and verdicts

Every meaningful conflict between the four proposals, the verdict, the
reasoning. PRs cited inline.

| # | Disagreement | Verdict | Why |
|---|---|---|---|
| 1 | Hero second sentence: voice opener (#47) vs no-automation guarantee (#49) | **#49 wins.** | Principle 1: trust before features. The buyer cannot accept any voice or value pitch until the existential ban risk is dispatched. |
| 2 | Hobbyist tier price: £7 (#46, #48, #49) vs £5 (#47) | **£7 holds.** | We are the only paid SaaS in the OSRS space that knows your bank. The price is for the context engineering, not the chat. £5 reads as panic discount; £7 reads as deliberate. We do not yet have data to override the cost model. We A/B post-launch (Phase 5). |
| 3 | FeatureGrid: live (#48) vs dead (#47) vs reframed as inventory (#46) | **Reframe as 4x2 inventory grid (#46).** | The OSRS player needs a feature recap before pricing. Killing it (#47) leaves a gap; keeping the three-equal-card SaaS default (#48 implicit) reads as templated. Inventory framing reads as OSRS-native. |
| 4 | Free tier placement: under hero strip (#48) vs in price table (#47) vs after technical-proof (#49) | **#48 wins.** Standalone strip between demo and inventory grid. | Free is the conversion accelerator for cold traffic. Placing it in the pricing table dilutes both. Placing it under the hero (as #48 proposed) buries the trust strip; we shift one section down to land between demo and features. |
| 5 | "Most picked" badge on Hobbyist (#48) | **Killed (#49).** | Principle 2: honesty over polish. We have no install data yet. The badge is a fake until it is real. Restore once we have credible numbers. |
| 6 | Pricing placement: above fold 5 (#48) vs after technical proof (#49) | **Section 7, after the inventory grid.** | The feature recap earns the right to ask for money. Above-fold pricing on cold traffic asks for money before the buyer knows what they are buying. Sticky nav anchor (`from £7/mo`) keeps the price visible without forcing the table early. |
| 7 | Display face: literal in-game pixel font (#46) vs PP Neue Montreal (#47) vs Cabinet Grotesk (#48) vs current RuneScape UF (#49) | **RuneStar `runescape_bold.ttf` for H1 only (#46).** | Constrained to the H1 it is the single fastest brand cue. Constrained beyond the H1 it becomes texture spam. PP Neue Montreal has licensing ambiguity; Cabinet Grotesk reads as another agency landing. |
| 8 | Palette: warm-gold-on-near-black (#46, #48) vs cold-ink (#47) | **Warm gold (#46).** | Cold ink reads as generic AI startup. The buyer's existential fear is being mistaken for an AI startup. Bias every micro-decision away from that read. |
| 9 | Icon source: lucide (#46) vs Phosphor (#47, #49) | **lucide-react (#46).** | Ops console already standardised on lucide. Mixing icon sets across the monorepo leaks across PRs within a quarter. One family wins on bundle weight and brand consistency. |
| 10 | Live presence: hero-sized section (#46 keep) vs thin strip (#47) vs demoted to fold 9 (#48) vs status pill in nav (#49) | **Status pill in nav (#49) plus a single mono line above the footer.** | At launch the live count will be small and a small count in a hero-sized slot actively hurts. The status pill in the nav reads as ambient liveness. The mono line above the footer gives the count a place to live without flexing. |
| 11 | Sticky pricing nav (#48) | **Adopted as a text link, not a chip.** | The text link does the job at one-third the visual weight. A persistent pricing chip in a 72px nav crowds the install CTA. |
| 12 | Two stacked demo exchanges (#47) | **Rejected; one demo only.** | Pinned ScrollTrigger budget allows one exchange. Two stacked exchanges blow the attention budget on a fold the conversion-first read (#48) is right to keep lean. |
| 13 | "Who built this" section with Tom's face (#49) | **Rejected from the homepage.** | Belongs on an `/about` deep page. The homepage is for cold traffic; an unknown founder face on cold traffic does not earn trust, it raises questions. |
| 14 | Wiki sprites in the "how it sees the game" diagram (#49) | **Rejected.** | Principle 3: OSRS-native without IP risk. Wiki sprites are CC-BY-SA with a Jagex attribution chain. Use lucide and the RuneLite skill PNGs. |
| 15 | Comparison table against named competitors (#49 considered, then dropped) | **Stay dropped.** | Naming competitors lifts them by association. The trust strip and the demo together do the differentiation work. |

---

## 6. Copy for the three hardest sections

Zero em-dashes. Brand voice: the clever friend who already read the
wiki. Calm, dry, lore-literate. Never bubbly.

**Hero.**

> Eyebrow (mono, faint): `tibbly · the OSRS co-pilot`
>
> H1 (pixel display): `Stop alt-tabbing. Start playing.`
>
> Sub-hero (Inter Tight, body): `Tibbly reads your bank, your quest log,
> and your inventory. It never moves your character, never clicks a
> tile, never types in chat. The same plugin shape as Quest Helper,
> with a wiki-fluent helper sitting next to you.`
>
> CTAs: `[ install plugin ]` (gold-filled) and `see the demo` (ghost).

**Trust strip (the no-automation guarantee).**

> Section eyebrow: none.
>
> Header (Inter Tight, heavy): `You play. Tibbly watches.`
>
> Three claims in monospace, hairline-separated:
>
> > `read-only. reads game state via the same RuneLite plugin APIs
> > every other RuneLite plugin uses.`
> >
> > `no input synthesis. never moves your mouse, never types in chat,
> > never clicks a tile.`
> >
> > `outbound only. one TLS connection out to our backend. no localhost
> > server, no listening port.`
>
> Below the strip, one line in Inter Tight muted: `the plugin source is
> on GitHub. every egress is logged inside the plugin. read the
> no-automation guarantee.`

**Free tier strip (standalone, between demo and inventory grid).**

> Header (pixel display, smaller than H1): `Start free.`
>
> Sub (Inter Tight, body): `Thirty messages a day on the routing-tier
> model. All the live tools work, every quest, every clue, every
> slayer task. No card, no login. Pair the plugin to your account when
> you want more.`
>
> Single giant CTA: `[ install the plugin ]`
>
> Below the CTA, one mono line: `or bring your own provider key for
> unlimited use. that is a config dropdown, not a tier.`

---

## 7. Implementation order

Five phases. Each one is a discrete PR. Total budget: five focused days.

**Phase 1 (Day 1): copy and structure.** Edits only to
`apps/marketing/src/sections/*.tsx`. Hero second sentence to the
no-automation guarantee. Trust strip added as a new section component
(small, copy-only). Demo caption swap. FAQ question rewording to
lowercase Discord style. Footer slim to two columns. Iron tier moved
to a footnote. "Most picked" badge removed. Highest ROI, lowest risk.

**Phase 2 (Day 2): type system and palette.** New `@fontsource` package
additions for Inter Tight and JetBrains Mono Variable. Vendor wiring
for `runescape_bold.ttf` from `packages/osrs-assets/fonts/`. Token
swap in `styles.css` from the current variables to the table in
Section 3. Constrain pixel display to the H1.

**Phase 3 (Days 3-4): net-new sections and the inventory reframe.**
TrustStrip component, FreeStrip component, InventoryGrid component
(replaces FeatureGrid). Pricing column count drop from four to three.
Sticky nav with the pricing text anchor. Status pill in the nav.

**Phase 4 (parallel to Phase 3): hero illustration treatment.** The
implementation lands before the final artwork. Until the commission
arrives, the right column holds a worn-parchment placeholder with a
real chatbox snippet from `BRAND_VOICE.md` sample 1. The slot ships
empty-friendly; replacing the asset is a one-file swap.

**Phase 5 (Day 5): instrumentation.** One A/B experiment per
opinionated take we want to validate post-launch: (a) £5 vs £7
Hobbyist, (b) hero second sentence as guarantee vs voice opener, (c)
Free strip placement at fold 5 vs fold 3. PostHog events keyed on
`data-cta` and `data-section` attributes already present in the
PricingTiers wiring.

Out of scope for these five days: the commissioned illustration
itself, a translation pass, any deep `/about` page, a status page, a
changelog feed in the nav.

Trade-off honest call: this IA is optimised for cold first-time
visitor conversion. The returning visitor sees the trust strip every
time, which after their tenth visit is fold-eating noise. We accept
that cost. The returning visitor's primary route is `/pricing` or
direct to `/install`, not the homepage.

---

## 8. What I did not pick

Three of the four proposals' opinionated takes were partially absorbed.
Some were rejected outright. Honest accounting:

- **#46 (visual): pixel font everywhere.** Adopted only for the H1.
  Rejected for H2 and below. The proposal's full-stack pixel
  treatment makes section headers read as gimmick rather than as a
  single brand moment. The H1-only constraint keeps the cue without
  the fatigue.

- **#47 (voice): £5 Hobbyist, kill the FeatureGrid, cold ink palette.**
  Held the £7 price (we A/B post-launch). Kept the FeatureGrid in
  inventory framing. Picked warm gold over cold ink. The voice
  proposal's core thesis (the voice is the product, the page should
  demonstrate it) is absorbed throughout the demo and FAQ sections;
  the specific opinionated cuts are not.

- **#48 (conversion): pricing above fold 5, sticky pricing chip, "Most
  picked" badge.** Adopted the Free strip standalone (their best
  call). Demoted pricing to section 7 (after features earn the right
  to ask). Sticky nav as a text link, not a chip. "Most picked" badge
  killed on honesty grounds.

- **#49 (objections): hero second sentence becomes the no-automation
  guarantee.** Adopted in full. This is the single most important
  call in the synthesis. The other #49 picks (Tom's face on the
  homepage, wiki sprites in a diagram, twelve-section IA) were
  rejected for reasons in Sections 2 and 5.

If a reader of this doc wants to argue with the synthesis, the most
defensible counter-arguments are: (a) the £7 vs £5 call could flip on
post-launch data, and the IA is wired to absorb that; (b) the lucide
vs Phosphor call is genuinely 60/40 and reasonable people could pick
the other way. Both are flagged as A/B candidates in Phase 5.

---

*End of canonical IA. Next doc to update: `docs/INDEX.md` adds an entry
for this file under `docs/marketing/`.*
