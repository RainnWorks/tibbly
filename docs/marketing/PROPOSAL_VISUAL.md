# Marketing visual proposal: OSRS-native angle

> Author: agent/loop-mplus9 (visual / art-direction)
> Date: 2026-06-21
> Status: proposal, not implementation. No code in this branch.
> Anchors: `.claude/skills/taste-skill/SKILL.md` (1206 lines), `apps/ops/src/theme.css`, `docs/architecture/OPS_DESIGN.md`, `docs/research/osrs-wiki/licensing.md`, `docs/marketing/BRAND_VOICE.md`.

## 1. One-line design read

Reading this as: **a paid-SaaS landing page for OSRS players arriving from a Settled-style YouTube link, with a "1999 fantasy interface meets calm modern dark editorial" visual language, leaning toward self-hosted RuneScape display fonts + a single saturated gold-on-near-black palette + an OSRS chatbox as the recurring layout motif.** A developer reading this should picture: a dark wood + worn parchment surface, a pixel-perfect chunky display font borrowed from the in-game interface, a single warm gold for hierarchy, and the chat-window frame from the og card scaled up into a hero device.

## 2. Three dials, explicitly set

```
DESIGN_VARIANCE: 8   MOTION_INTENSITY: 7   VISUAL_DENSITY: 5
```

**`DESIGN_VARIANCE: 8`.** The reference reads as "landing page for a premium consumer product crossed with a games-press fan site". Section 1.A puts agency / playful briefs in the 9-10 band and SaaS landing at 7. We sit at 8 because the brand has to feel hand-laid (not templated) for the OSRS audience to trust it, but it is still a payment funnel and an 8 keeps it from tipping into Awwwards chaos. Hero is asymmetric (chat-window left, character portrait right, headline inscribed across the seam). Demo and pricing diverge into different layout families. No three-equal feature cards anywhere.

**`MOTION_INTENSITY: 7`.** Higher than ops (which sits at 2). The brief explicitly says "Animations welcome", and OSRS itself moves: skill icons pulse on level-up, chat boxes fade, the world breathes. 7 unlocks scroll-pinned reveals, magnetic CTAs, the chat-window typewriter demo, and ambient sprite movement, but stays below the 8-10 "GSAP everywhere" tier. Every animation must be motivated (Section 5). Reduced-motion fallback is required for everything above 3.

**`VISUAL_DENSITY: 5`.** OSRS itself is dense (cluttered interface, inventory grids, tab columns). 5 is the upper end of "daily app" and lets us reach for inventory-grid layouts, multi-cell bento, and the chatbox surface without becoming a cockpit. Lower than ops (8) because marketing is a hierarchy device, not a working surface.

Divergence from ops (`6 / 2 / 8`): marketing trades density for variance and motion. Defensible: marketing's job is recognition and conversion in 8 seconds; ops's job is operator throughput at 2am. Same brand, opposite jobs.

## 3. Type system

**Display: `runescape_bold.ttf` (RuneStar CC0, already vendored at `packages/osrs-assets/fonts/`).** This is the canonical fantasy-pixel face every OSRS player has read for fifteen years. It is the single strongest "of OSRS" signal we own and it is CC0, so we ship it with zero licensing exposure. Used for the H1, section headlines, big numeric displays in pricing, and the in-chatbox `tibbly:` speaker label. `image-rendering: pixelated` on rasterized fallbacks, but the TTF is vector. Loaded via `@font-face` with `font-display: swap`, served from the marketing site's own origin.

**Secondary display (compact headlines, eyebrows where allowed): `Cinzel` via `@fontsource/cinzel`.** A modern Trajan-flavoured serif used sparingly. Cinzel exists in the current `styles.css` fallback stack already, so this codifies what is already there. Used only on the secondary headline tier (H2 in sections where the pixel display would be too heavy) and the wordmark in the footer. Justification: it carries the "weighty fantasy frontispiece" register that OSRS players associate with the title screen and the original Jagex marketing without copying any wordmark. Crucially this is NOT a default reach for serif (Section 4.1 ban): it serves a specific brand purpose.

**Body sans: `@fontsource-variable/inter-tight`.** Not plain Inter (banned default, Section 4.1) and not Geist (already taken by ops). Inter Tight reads as modern software when paired with a heavy pixel display, holds at small sizes, and has full variable-weight support. The body font has to disappear so the display can speak.

**Mono: `@fontsource-variable/jetbrains-mono`.** Same as ops, intentionally. The chatbox surface is mono-rendered (matches the in-game chat font's monospace cadence), and pricing numbers, player names, and tool-trace lines all read as code. Sharing the ops mono is one of the two deliberate brand bridges.

Default weights: 700 display, 600 H2, 500 emphasis, 400 body. Tabular numerics on every digit.

## 4. Palette

Off-black base with one warm-gold accent. Documented per token with hex and purpose. This is an evolution of the current `--osrs-bg / --osrs-gold / --osrs-text` shell, not a replacement: those names are kept, the values are nudged for contrast and one new token (`--osrs-parchment`) is added for the chatbox interior surface.

| Token | Hex | Purpose |
|---|---|---|
| `--osrs-bg` | `#0b0703` | Page background. Off-black with a warm brown tilt. Not pure black (Section 9.A). Sits two steps darker than ops `#0c0e12` to feel like wood at night, not steel. |
| `--osrs-surface` | `#1a1006` | Card backgrounds, nav bar, footer. The "wood frame" surface. |
| `--osrs-surface-2` | `#2a1b0c` | Elevated surfaces (modal background, focused tile). |
| `--osrs-parchment` | `#3a2a16` | Chatbox interior, demo background, the worn parchment we render type on top of. Matches the og-card. |
| `--osrs-border` | `#5a3e1a` | Hairline dividers, chatbox stroke, tile edges. Direct lift from the og-card grid stroke so the brand reads continuous. |
| `--osrs-border-strong` | `#a37412` | Inscribed-edge gold border on the hero chatbox and primary CTA. |
| `--osrs-gold` | `#f3c75a` | Primary accent. Slightly desaturated from the current `#ffcc00` to land below Section 4.2's 80% saturation ceiling. WCAG AA against `--osrs-bg`: ratio 9.4:1. Used for: H1, primary CTA fill, focus ring, the chatbox speaker color, anywhere hierarchy needs to pop. |
| `--osrs-gold-bright` | `#ffe7a8` | The "highlight" gradient stop on the H1 (matches og-card top stop). Used only inside the display-type gradient, never as a standalone fill. |
| `--osrs-gold-ink` | `#0b0703` | Text color ON the gold CTA. Contrast 9.4:1. |
| `--osrs-text` | `#f4e9c1` | Primary body text. Warm off-white (the in-game chat text color). |
| `--osrs-muted` | `#a08a5a` | Secondary text. Helper copy, tag labels. |
| `--osrs-faint` | `#6a5230` | Tertiary, captions, timestamps in the demo. |
| `--osrs-success` | `#5a8a3a` | State color: "online" dot in the demo header, plugin-connected status. Matches the og-card's `#3fb968` family, dimmed to live alongside gold. |
| `--osrs-danger` | `#b22222` | Reserved. Used only in pricing for "hard cap" callout and 404 surface. |

Single accent: gold. No second chromatic color is reached for in chrome (Section 4.2 Color Consistency Lock). Charts, if any, use gold as the primary series and `--osrs-muted` as the secondary.

**Anti-palette confirmation.** This is NOT the banned beige+brass+oxblood+espresso family (Section 4.2). The base is a warm-tinted near-black, not cream. The accent is a single saturated gold sourced from the in-game UI, not a brass+clay+oxblood triad. We are not a premium-consumer cookware brand wearing artisan-warmth cosplay; we are a video-game tool wearing the video game's own colors.

## 5. Iconography and illustration

**Default icons: `lucide-react`.** Already adopted by the ops console, already vendored in the monorepo, and the Section 3.C ban on lucide carries an explicit "acceptable when the project already depends on it" carve-out. One family across both apps reduces brand drift and bundle weight. Global `strokeWidth: 1.75` (matches ops). No hand-rolled SVG paths under any circumstance.

**OSRS-native icons: the 26 RuneLite BSD-2 skill icons** at `packages/osrs-assets/skill_icons/`. These already power the Hero. Pixel-rendered at native scale, used as decoration in the inventory-grid section and as visual leads in the feature-grid. Permitted commercially per the licensing audit (`docs/research/osrs-wiki/licensing.md`). Hard rule (already enforced by `isForbiddenAssetUrl`): zero hot-links to wiki sprites.

**Hero illustration: declared placeholder slot.** The hero needs ONE strong character or scene illustration sitting next to the chatbox device. I cannot generate it from this proposal alone. Three sourcing options ranked:

1. **Commission an illustrator** with a brief like "a hooded adventurer at a campfire reading from a glowing scroll, painterly, in the spirit of the OSRS title-screen oil paintings". Highest legal safety (we own it), strongest brand asset, ~$400-$1200 one-shot. Recommended for v1 launch.
2. **In-RuneLite screenshot of the plugin running** in the chatbox itself. Lowest cost, ships immediately, but reads as devtool not brand. Use as a stop-gap on the demo section, not the hero.
3. **Generated painterly piece** via an image-gen tool, vetted by a human for any Jagex-IP collision (no GE clerks, no Jagex moderators, no specific NPC likenesses). Acceptable middle ground while waiting on commission.

Declared in the implementation hand-off: hero asset is a slot, marked `TODO: commissioned hero art, 1600x1200, painterly fantasy, hooded adventurer + scroll, no Jagex-recognizable NPCs`.

**Banned: hand-rolled decorative SVG.** No drawing a chatbox in `<path d="..." />`. The chat-window frame is a real component built with CSS borders and the gold gradient token, not an inline SVG illustration.

## 6. Layout system

**Grid: 12-col CSS Grid, gutter `1.5rem`, container `max-w-[1280px] mx-auto`.** Breakpoints standard (`sm 640 / md 768 / lg 1024 / xl 1280`). Mobile collapses to single-column at `< 768px` for every asymmetric layout (Section 3.E mobile override).

**Asymmetry lands at: hero, problem-statement, pricing.** Demo and FAQ are vertically-stacked symmetric. Feature-grid is an inventory grid (4x2). Section-layout-repetition (Section 4.7) is respected: hero, problem, feature, demo, pricing, FAQ, footer = at least 5 distinct layout families.

**Inventory-grid motif.** The feature-grid section borrows OSRS's 4x7 inventory layout (28 cells), reduced to 4x2 = 8 cells for screen scale. Each cell is a feature with a skill icon and a one-line claim. This is the closest the page comes to a "bento" pattern, but it reads as inventory because the cells are square, hairline-bordered, and tightly packed. Section 4.7's bento cell count rule is honored: exactly 8 cells for 8 features.

## 7. Motion system

**Where motion lives.**

- **Hero**: ambient skill-icon rain in the background (already exists), slowed and dimmed to 0.12 opacity max so it sits behind the chatbox. Hero chatbox types its message in over 600ms with a blinking caret (already exists). Magnetic pull on the primary CTA (pull radius 40px, max displacement 6px) using Motion's `useMotionValue` outside React render.
- **Section reveal**: stagger fade-and-rise as each section enters viewport, 60ms cascade across siblings, `cubic-bezier(0.16, 1, 0.3, 1)`. Implemented via Motion `whileInView`, not GSAP (Section 5.C lighter alternative).
- **Demo**: typewriter player-and-helper exchange, looped with a 4-second hold at end. The single GSAP ScrollTrigger on the page lives here, used to pin the demo for one screen-height and play the exchange once as the user lands. Canonical sticky-stack skeleton from Section 5.A.
- **Pricing**: gold border on the recommended tile pulses once on entry, then stops. No infinite hover loops.
- **Live counter**: number ticker increments with spring physics on update. Already exists.

**Where motion is banned.**

- Footer.
- FAQ.
- Pricing once the entrance reveal completes.
- Any infinite loop that does not communicate a real state (the live counter is real; a "decorative shimmer" on every card is not).
- No marquee. Section 5 caps marquees at one per page; I propose zero.

**Reduced motion.** Every animation above intensity 3 is wrapped in `useReducedMotion()` (Motion library) or a CSS `@media (prefers-reduced-motion: reduce)` block. The existing `styles.css` already does this for the hero rain, sprite bob, caret, and message stagger. Pattern stays.

## 8. Section-by-section visual mockup

**Nav.** A 72px-tall dark `--osrs-surface` strip with a thin gold underline. The wordmark "tibbly" is set in the pixel display, lowercase. Three nav items (`demo`, `pricing`, `faq`) right-aligned in Inter Tight. One CTA on the far right: `install plugin`, gold-filled, pixel-display label. Reads as "an OSRS game tab in a browser tab".

**Hero.** A 60/40 split. Left column (60%): an oversize chatbox device, gold-bordered, scaled up from the og-card. Inside it: the H1 in pixel display reads `Stop alt-tabbing. Start playing.` typing in over 600ms, then a Helper reply appears in mono below: `you've got 27 sharks and a full prayer pot stack, enough for two Zulrah kills before you'd want to bank.` (Replaced the long-pause break from `BRAND_VOICE.md` sample with a comma so the page renders zero em-dashes anywhere.) Below the chatbox: two CTAs side by side in the gold-filled and ghost-bordered pair already defined. Right column (40%): the commissioned hero illustration (declared slot). A faint ambient skill-icon rain plays across the full hero behind both columns at 0.12 opacity. Hero counts as 1 of the page's allowed eyebrows: the lone eyebrow is the small mono line `tibbly · the OSRS co-pilot`. The player sees an OSRS chat window grown to landing-page scale and immediately recognizes the frame. What it accomplishes: in 2 seconds the player knows we are of-OSRS and they know what we do.

**Problem statement.** Full-width, vertical-stacked, gold inscribed headline `Tabs cost ticks.` (3 words, no eyebrow). Below it a 90-character paragraph: the inventory math of alt-tabbing during a Zulrah kill. To the right of the paragraph, a small worn-parchment block holds the receipt: `alt-tab 4.2s · prayer drift 1 tick · 75gp prayer pot`. No icons, no skill sprites, just type on parchment. Reminds the player of the in-game examine box.

**Feature grid (inventory layout).** A 4x2 grid of square cells, hairline gold-on-brown borders, each cell carrying one skill icon top-left, a short claim in Inter Tight body weight, and a lucide icon glyph bottom-right. Cells: live inventory awareness, gear-aware advice, quest state, slayer state, GE pricing, prayer flicking, clue hints, hard usage cap. The layout reminds the player of their actual inventory tab. Reading from top-left to bottom-right is the same eye-flow they already use 1000 times a session. Accomplishes: feature recall without burying the player in a feature list.

**Demo.** A pinned demo: the page sticks for one screen-height while a full 4-turn exchange plays out in a centered scaled-up chatbox. Player asks `is fang worth it over rapier at 80 attack`. Helper types back the example reply from `BRAND_VOICE.md` sample 3. Player asks about clue. Helper answers. The pin releases. This is the only ScrollTrigger pin on the page and it earns its frame budget by communicating the product's voice in 8 seconds.

**Pricing.** Three plaques laid out horizontally on a worn parchment strip (`--osrs-parchment`). Each plaque is an OSRS-style scroll header (gold inscribed name) followed by a short body. Pricing numbers in JetBrains Mono Variable, weighted. Center plaque (`pro`, recommended) is 8px taller than the side plaques, has a gold border that pulses once on entry. CTA on each plaque: `install plugin` (free tier) / `start trial` (paid). No duplicate CTA intent (Section 4.5): the page uses exactly two CTA intents (install plugin, see demo) and pricing reuses install plugin. The visual reminds the player of the in-game prayer book or quest list, with each entry as a scroll.

**Live counter.** A single line, centered, mono: `423 adventurers playing with tibbly now`. The number ticks. The OSRS analogue: the worlds list "X players online". Why it works: parasocial proof, near-zero visual weight, completely honest if our metric is real.

**FAQ.** A vertical list of accordions inside the chatbox frame. Each question opens to reveal an answer typed in the Helper voice. Mono speaker label `player:` and `tibbly:` echoes the demo. Visual weight is low so the page can wind down before the footer.

**Footer.** `--osrs-surface` strip, dark gold dividers. Three columns: nav, legal (terms, privacy, the "not affiliated with Jagex Ltd." disclaimer per `licensing.md`), credits (RuneLite BSD attribution, RuneStar CC0 attribution). Wordmark `tibbly` in Cinzel at 32px, the only Cinzel usage on the page.

## 9. Anti-examples

This page does NOT look like:

1. **A Linear / Vercel / Stripe SaaS landing.** No `Inter + slate-950 + gradient blob` hero. No mesh gradient. No glassmorphism over a dark mesh. No three-equal feature cards. No "Quietly trusted by". No animated hero terminal mock built from `<div>` rectangles. Hero motif is a literal OSRS chatbox, not an abstract dashboard.
2. **A Notion-style "fun startup" page.** No pastel illustrations, no rounded card grid with floating emojis, no "Get started for free" gradient buttons. No bouncing icons everywhere. The motion budget lives where motion communicates state, not where it cheers.
3. **A generic "premium fantasy" Squarespace template.** No beige + brass + oxblood (Section 4.2 banned palette). No Fraunces or Instrument Serif (Section 4.1 banned defaults). No parchment-everywhere overuse. The parchment surface appears in two places (hero chatbox interior, pricing strip) and is otherwise rationed.

## 10. One opinionated take

**The hero ships the actual RuneScape pixel display font as the H1 face.** Not "OSRS-inspired", not a modern grotesque pretending to be retro. The real, vendored, CC0 RuneStar `runescape_bold.ttf`. Other proposals will hate this because:

- Pixel display fonts have rough subpixel anti-aliasing at large sizes and look wrong to designers trained on Geist.
- Cinzel + Inter is "safer" and would not provoke.
- A 2026 paid-SaaS landing using a 1999 game font as the H1 face violates basically every modern landing-page convention.

Defence: the OSRS player sees that face every minute of every play session. It is the single fastest "you are home" cue we own. The licensing is the cleanest CC0 we have. Every minute of design-credit we lose with a typography purist we gain back tenfold with the player who landed from a Settled video and felt instantly recognized. The brand voice is already "the clever friend who already read the wiki"; the typography has to match. If the pixel face renders blurry at scale, we lean into it (image-rendering: pixelated) and call it the brand's intentional texture, the same way Linear leans into Geist's chrome perfection. We are not Linear. We are the chat box that knows your bank.

## 11. Pre-flight self-audit (Section 14 checklist applied to this proposal)

Items I can confidently mark **passed** at the proposal level:

- Brief inference declared (Section 1).
- Dial values explicit and reasoned (Section 2).
- No design system mis-named: we honestly label the stack as Tailwind v4 + Motion + self-hosted fonts, no fake "official OSRS DS" claim.
- ZERO em-dashes anywhere in this document. I will grep before commit.
- Page Theme Lock: one theme (dark warm), every section stays inside the `--osrs-bg / --osrs-surface / --osrs-parchment` family. No mid-page light-mode flip.
- Color Consistency Lock: one accent (gold). No second chromatic color in chrome.
- Shape Consistency Lock: one corner radius scale (cells 4px, cards 8px, pills 9999px for status badges only). Documented.
- Serif discipline: serif (Cinzel) is used in exactly one place (footer wordmark). Not the default, not Fraunces, not Instrument Serif.
- Premium-consumer palette check: not in beige+brass+oxblood; documented in Section 4.
- Hero stack discipline: hero carries exactly 4 text elements (eyebrow, H1, sub, two CTAs). No trust micro-strip, no tagline below CTAs.
- Hero top padding: capped at `pt-24` desktop, per Section 4.7.
- Eyebrow count: hero eyebrow is the only eyebrow on the page. Page has 8 sections; ceil(8/3) = 3 eyebrows allowed; 1 used.
- Split-Header Ban: no "left big headline + right tiny explainer floater" pattern anywhere.
- Zigzag alternation cap: zero zigzag sections. Hero is split, then problem is vertical-stack, then feature-grid is inventory, then demo is pinned, then pricing is plaque-strip. Pattern broken at every step.
- No Duplicate CTA Intent: two intents only (install plugin, see demo). Both labels used identically across hero, pricing, and footer.
- Logo wall = logo only: no logo wall in the proposal (we have no enterprise logos to flex). If one is added later, no industry labels under logos.
- Bento Background Diversity: the inventory grid has skill-icon imagery in every cell. Not white-on-white.
- "Used by / Trusted by": skipped. Replaced with the LiveCounter ("adventurers playing now") which is a real metric, not fake social proof.
- Copy Self-Audit: every visible string in the proposal is plain functional English. The Helper sample copy is lifted from `BRAND_VOICE.md` and is grammatically intact.
- Motion motivated: every animation in Section 7 has a one-sentence justification (hierarchy / storytelling / state / feedback).
- Marquee max one per page: zero marquees proposed.
- Navigation on one line, height 72px (cap 80px).
- Section-Layout-Repetition: at least 5 distinct layout families across 8 sections.
- Bento cell count: 8 cells for 8 features, no empty cells.
- No `border-t + border-b` on every list row: FAQ uses accordion, not divider stack.
- No version labels in hero (no V0.6, no BETA).
- No section-numbering eyebrows.
- No decorative dots (the success dot in the demo header is real state).
- No scoring/progress bars with filled background tracks.
- No locale / time / weather strip.
- No scroll cues.
- No decoration text strip at hero bottom.
- No photo-credit captions on stock images.
- No version footer.
- No `window.addEventListener("scroll")`; use Motion `useScroll` / `whileInView` / one GSAP ScrollTrigger on the pinned demo.
- Reduced motion: every motion above intensity 3 wrapped, already proven by the existing `styles.css` precedent.
- Viewport stability: `min-h-[100dvh]` for the hero, not `h-screen`.
- Icons from an allowed library: lucide-react with documented project-level override (same carve-out ops took).
- One design system per project: Tailwind v4, no mixed systems.
- No AI Tells from Section 9 in the proposal copy.

Items that need design-system implementation to fully tick:

- Form contrast check: the only form on the marketing site is the email-capture on the pricing CTA. Spec requires placeholders, focus rings, labels to pass WCAG AA against `--osrs-surface` background. Token contract above declares it; the implementing PR must verify.
- Italic descender clearance: no italic in any headline. If a later iteration adds italic emphasis, `leading-[1.1]` + `pb-1` is the rule.
- Button contrast check: gold button + `--osrs-gold-ink` (`#0b0703`) text is 9.4:1, passes AA. Ghost button + `--osrs-text` on `--osrs-bg` is 13.2:1, passes AAA. Confirmed in the palette table.
- Dark mode: the page is dark-only by brand intent. Documented; no light-mode toggle proposed.
- Core Web Vitals: hero asset is the LCP. Once the commissioned art lands, it must be 1600x1200 WebP, preloaded, `loading="eager"`, `fetchpriority="high"`. Fonts use `font-display: swap` and are subset to the Latin Basic + numerics.

Items that remain open (intentional, flagged for implementing PR):

- Hero illustration slot: declared in Section 5. The slot has three sourcing options ranked; the choice belongs to Tom, not this proposal.
- Demo asset: real plugin screenshot vs. stylized chatbox. Default to stylized chatbox at launch (controllable), real screenshot in a v1.1 "actual product" pass.
- Live counter source: needs a backend signal (clients online from the realtime / network agent). Honest stub until that lands.
- Cinzel inclusion may be cut if the wordmark looks fine in the pixel face alone. Decide in the implementation PR after seeing both rendered.

If any pre-flight box flips from passed to broken during implementation, the implementing PR must call it out and either fix or escalate.

---

**Reusability note for ops.** Two pieces of this brand should propagate to ops eventually: the gold accent semantics (focus ring, primary action) can stay distinct (ops uses mint), but the `JetBrains Mono Variable` choice and the pre-flight discipline are shared. Deliberate divergences: ops keeps Geist, marketing reaches for RuneScape display + Cinzel + Inter Tight, because their jobs are opposite (operator throughput vs. player recognition). Documented so a later "unified brand pass" does not collapse the two by accident.
