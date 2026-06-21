# Marketing IA proposal: voice-first angle

**Author:** loop-mplus9 (design + copy track)
**Date:** 2026-06-21
**Status:** proposal for quorum synthesis. Pure doc, no code changes.
**Companion reads:** `BRAND_VOICE.md`, `NAME_CANDIDATES.md`,
`docs/research/community/pain-points.md`,
`docs/research/community/_naming-signals.md`,
`docs/research/community/creators.md`,
`docs/architecture/HUB_RELEASE_STRATEGY.md`.

> The thesis: the brand voice is the product. If we let a feature checklist
> drive the page structure, we ship another "AI for X" landing and the
> voice gets bolted on as colour text. If we let the voice drive structure,
> the page reads like a real person typed it, which is the entire wedge
> we have against every other "ChatGPT-but-for-Y" page on the internet.

---

## 1. Design read

The visitor is a 22-to-38 year old OSRS player, very likely already a
RuneLite user, who has just clicked a link in a Discord thread, a tweet,
or a creator description. They arrive **suspicious**. Two specific fears
sit in the front of their mind: *"is this a botting tool that will get
my main banned"*, and *"is this another AI grift with a wrapper around a
chatbot"*. They have lived through ChatGPT-for-OSRS attempts already.
They have also installed Quest Helper (555K of them) and WikiSync (302K
of them) without thinking twice, because those tools feel native to the
community. The page has one job before they leave: prove we are made
**by people who play, for people who play**, and that we sound like
someone they would actually let into their party chat. Pricing, features,
trust, and the install button are downstream of that single proof.

The voice-first commitment locks in *who* is speaking, which locks in
*what* the page shows. A page narrated by "the clever friend who already
read the wiki" cannot open with corporate hero copy. It cannot have a
3-equal-feature-card grid. It cannot have stock illustrations. It must
look like a quietly competent in-game NPC opened their mouth and a
landing page came out.

---

## 2. Three dials, justified

Per the taste-skill discipline, these are not defaults.

- **`DESIGN_VARIANCE: 7`.** Not 9 (the agency-experimental tier) because
  the voice is *calm*. Not 5 (Linear-clean) because the audience expects
  the texture of OSRS: a quietly chaotic world, not a software dashboard.
  7 means asymmetric grids, varied section rhythms, two real
  compositional surprises on the page, but never visual chaos.
- **`MOTION_INTENSITY: 4`.** The whole pitch of the voice is *settled,
  documentary calm*. J1mmy energy is 8. Settled energy is 4. Tibbly
  voice is Settled with one J1mmy beat allowed. Motion: a single
  ambient parallax in the hero, scroll-reveal stagger on the in-client
  chat replay, a soft live ticker on the presence count. No marquees.
  No GSAP pinned horizontal panels. The page does not show off; it
  exhales.
- **`VISUAL_DENSITY: 5`.** Higher than a luxury brand site (3) because
  OSRS players read dense interfaces every day and a too-airy page reads
  as *foreign*. Lower than a cockpit (7) because the voice trades on
  knowing when to stop. 5 = a real chat replay you can read, a real
  pricing tier you can scan, but never six bullets where two will do.

---

## 3. Type system, palette, icon source

- **Display + UI sans:** `PP Neue Montreal`. Tight, modern, a touch
  Scandinavian. Reads competent and adult. Not Inter (banned, the LLM
  default), not Geist (every AI tool ships with it now). Neue Montreal
  is what design-literate brands reach for when they want "this looks
  professionally art-directed" without screaming agency.
- **Body serif (sparingly):** `GT Sectra Display` for one place only: a
  pulled quote from a creator partner section. Serif is a spice, not the
  meal. Banned defaults Fraunces and Instrument Serif are explicitly
  avoided.
- **Mono (UI ribbon, tool-call chips, in-chat code):** `JetBrains Mono`.
  Reads like the RuneLite settings panel without imitating it.
- **In-game flavour font (constrained to one element):** RuneStar's
  `RuneScape-Bold-12` (CC0) used ONLY for the live-presence counter
  number. One in-game callback per page. Sparingly, exactly like the
  voice spec demands one piece of texture per reply.
- **Palette: "lit-torch dusk".** Deep ink background (`#0F1115`, a touch
  blue, not pure black), wood-brown surface (`#1E1A14` for cards), a
  single warm-amber accent (`#E8A24A`) for CTAs and link hover, a cool
  parchment off-white (`#F0E6D1`) for body text on dark, and a single
  cold mint (`#86D9B8`) reserved for status / success only. This is
  *not* the banned beige+brass+oxblood premium-consumer palette. The
  background is cold ink, not warm cream. The accent is one amber, not
  brass. Tom's existing `osrs-gold` lives in this family but is a
  different temperature; the proposal upgrades it from "RuneLite gold
  text" to a calibrated, dusk-lit warmth.
- **Icon source:** **Phosphor Icons** for all UI chrome (nav, FAQ
  chevrons, footer links). Two reasons: it's first in the taste-skill
  allowed list, and its hairline weight reads quieter than Lucide,
  which matches the voice. **In-content imagery** stays the OSRS Wiki
  sprite catalogue (BSD-2-Clause via RuneLite, already wired in
  `@osrs-llm-helper/osrs-assets`). Zero hand-rolled SVGs. Zero
  hand-built div screenshots.

---

## 4. Information architecture (voice-first)

A voice-first lens asks each section the same question: *"would a calm,
competent friend say this here, or are we filling space?"* The current
site has 7 sections. The proposal collapses to 6, in a different order,
because the friend would speak in a different sequence.

1. **`Hero`, first sentence, not first headline.** No eyebrow. No
   "Stop alt-tabbing." A single conversational opening line set as
   display type, a one-line proof underneath, two CTAs (install,
   read more). The hero is what the friend would actually say if you
   walked up and asked "what is this".
2. **`The proof line`, a sub-hero strip, NOT a logo wall.** Three
   facts in monospace, separated by hairlines: *"reads game state via
   the same RuneLite APIs as Quest Helper"*, *"never moves your
   character"*, *"every reply is a real model on a real
   conversation"*. This addresses the *category fear* before the
   feature pitch lands. Voice in monospace, deadpan.
3. **`The shape of a chat`, one real exchange, not a feature grid.**
   The current `Demo` section, expanded to be *the* centrepiece. A
   slow, scroll-revealed conversation: a player message, the tool
   calls visible as small chips, the reply. Then a *second* exchange
   below it on a different topic (a clue, or a slayer task). Reading
   two real exchanges teaches the voice better than any "what Tibbly
   does" grid can.
4. **`What you can ask`, the pain-point ladder, but as quoted
   user messages, not feature cards.** Six rows. Each row is a real
   player message in italic, then a one-line answer in Tibbly's voice,
   then a small hairline. This is where we acknowledge pain points
   from the research corpus *in their own words*. See section 5 for
   exact copy.
5. **`Pricing`, three tiers, voice-led tier descriptions, hard cap
   stated as plain language not "metering".** Drop the four-tier
   layout to three (Free + Hobbyist + Pro, with Iron repositioned as
   an "ask for Iron" footer note). Four equal-width tier cards on
   marketing pages read as commodity SaaS. Three feels like a choice;
   four feels like an upsell ladder. Iron is not lost; it becomes the
   "if you want this, write us" closer.
6. **`Things players actually ask`, FAQ, kept, but the question
   wording rewritten to match how a player types in Discord.** Lowercase,
   no punctuation, real-grammar. Then answers in voice. This is the
   single place the page is allowed to be long; it's where suspicion
   gets unwound.
7. **`Footer`, kept, slimmed.** Two columns, not four. Attribution,
   legal, hello@. The current four-column footer is corporate. A
   friend's footer is two columns.

Sections **killed** from the current site: `FeatureGrid` (six in-client
superpowers as cards; voice does not pitch features, it demonstrates
them; absorbed into `The shape of a chat` + `What you can ask`),
`LiveCounter` as its own section (kept, but moved to a thin top-of-page
strip the size of an OSRS status bar, not a hero-sized presence flex).

A voice-first lens makes the deletions easy: the friend would never
hand you a six-tile feature grid. The friend would say "here, look at
this conversation".

---

## 5. Sample copy

All copy below is freshly written for this proposal. Zero em-dashes.
Specific pain-points from `pain-points.md` are cited in brackets;
those brackets do not ship in the visible copy.

**Hero (single voice-led sentence + proof):**

> Most of OSRS is a search problem.
>
> You already know what you want to do. The wiki knows the answer.
> Tibbly is the third thing in the room. It reads what's in your bank,
> reads what's on the wiki, and tells you the next move in one
> sentence.
>
> `[ install the RuneLite plugin ]`   `read how it works`

**Sub-hero proof strip (mono):**

> reads game state via the same client APIs every RuneLite plugin uses ·
> never moves your character, never types for you · every reply is a
> real model on a real conversation

**Section 3 lead-in to the demo:**

> Easier to show than tell. Here's a real turn.

**Section 4, "What you can ask", six rows, lifted-from-Discord style.
Each acknowledges a pain point in the research corpus.**

> *"stuck on dragon slayer 2 after the vorkath cutscene where do i go"*
> Back to Ava in Draynor Manor, she's got the next step. Anti-dragon
> shield + an antifire in your inventory before you leave, the next
> leg gets nasty. Your shield's in bank tab 7, I marked it.
> *(P1: quest next-step, 555K Quest Helper installs.)*

> *"got a 173 dust devil task where's best"*
> Smoke dungeon for loot, Kourend catacombs for speed. You're in
> Verac's so you don't actually need the face mask in smoke; your
> prayer's tanking the damage. Bring a stam, you're on 22% run energy.
> *(P3: gear/inventory advice, 210K Inventory Setups installs.)*

> *"what does show this to ulrich in edgeville mean"*
> Emote clue, not a talk-to. Stand by Ulrich north of the bank, dance
> emote. Don't need anything in inventory but bring the casket. Bald
> guy with a broom.
> *(P5: clue scrolls, six tiers + five containers = 30 wrapper variants.)*

> *"is fang worth it over rapier at 80 attack"*
> Yes, almost everywhere. Rapier still wins at CoX tents and a few
> niche slayer mobs, but for what you actually do (a lot of ToA last
> week) fang is the upgrade. You're 1.4M short at current GE.
> *(P3 again + P8: "what should I do right now".)*

> *"build me a vorkath tab from what i own"*
> Done. Twelve slots filled from your bank, one substitute flagged
> (you don't have a Salve (ei), I subbed Salve (e) and noted the DPS
> drop). I left two prayer slots empty so you can drop pots in.
> *(P7: bank chaos, 225K Bank Tag Layouts installs.)*

> *"how do i get to brimhaven fastest from edgeville with 87 magic and
> a royal seed pod"*
> Royal seed pod to Prifddinas, charter ship to Brimhaven. Saves you
> the lap around Karamja. If you've burned the seed pod today, glory
> to Karamja and walk, it's a wash.
> *(P6: pathing, 189K Shortest Path installs.)*

**Pricing, three tiers, voice-led:**

> **Free.** Bring your own key, or kick the tyres on us.
> Thirty messages a day on the routing-tier model. Watermark on
> replies. All the live tools work. If you want to plug your own
> OpenAI or OpenRouter key in for unlimited use, that's a config
> dropdown. No card. No login.

> **Hobbyist. £7 a month.**
> Your main account, sorted. Smart routing picks the right model for
> the question. Full tool surface, no watermark, no rate-limit
> anxiety. Hard cap means you cannot accidentally spend £400 on
> tokens because you left a chat open.

> **Pro. £19 a month.**
> Iron mains, GIM groups, creators. Deeper reasoning where it counts,
> group-shared game state for raid prep, stream-overlay mode for the
> camera. Same hard cap, larger bucket.

> *Hardcore quest-cape pilots: there's an Iron tier (£49) we hand-tune
> per player. Email if you want it.*

**FAQ rewrites (lowercase, in voice):**

> *will i get banned*
> No. The plugin reads game state the way every other RuneLite plugin
> reads game state. It does not move your character, it does not click
> for you, it does not type into the game window. Jagex has tolerated
> the RuneLite category for ten years. We sit firmly inside that
> category and have a written "helps you play, never plays for you"
> commitment in the terms.

> *is this just chatgpt with a wrapper*
> No, and yes. Yes in the sense that there's a language model
> downstream. No in the sense that the value is in what we hand the
> model: your live inventory, your bank tab, your quest progress, your
> exact tile, the wiki page for the thing you asked about. A wrapper
> has none of that.

> *do i have to pay*
> No. Install the plugin, use the local panels (bank viewer, farming
> grid, slayer detail, quest board) forever. Paste your own API key
> for chat. Or pay us to make it just work, and to skip the key
> management.

> *what about leagues*
> Live game state is live game state. Leagues 6 Demonic Pacts works
> the day it ships. Same for whatever the August update brings.

**Footer (slimmed):**

> *Tibbly · the OSRS LLM Helper.*
> Helps you play. Never plays for you.
> Not affiliated with Jagex.
>
> *hello@tibbly.app · GitHub · privacy · terms*

---

## 6. What gets killed

- **The hero's two "marketing" lines** ("Stop alt-tabbing. Start playing.")
  Voice does not lead with stadium chants. It opens a conversation.
- **The six-card `FeatureGrid`.** Three-equal-cards is a banned default
  in the taste-skill, and a feature grid is the most un-friend thing a
  friend can hand you. The features survive as quoted user questions in
  Section 4.
- **The four-tier pricing layout.** Four equal columns reads as upsell
  ladder. Three feels like a choice.
- **The big presence counter as its own hero-sized section.** It still
  ships, but as a thin status-bar strip at the very top of the page,
  the size of an OSRS minimap corner. It earns its keep as ambient
  presence, not as a flex.
- **The eyebrow on every section** (`The wiki tab-out tax`, `What
  Tibbly does`, `Pricing`, `Live network`, etc.) The taste-skill caps
  eyebrows at one per three sections; the current site uses six. The
  proposal uses one (above the hero proof strip) and that's it.
- **The "Sonnet 4.6 · routed for this question" line** in the demo.
  Tom locked: no hardcoded model names in marketing. The replacement
  reads "smart-routed for this question."

---

## 7. One opinionated take

**The visible price of the Hobbyist tier should drop to £5, not £7.**

Every other proposal will hold the £7 / £19 / £49 line because
`docs/research/llm-providers/cost-model.md` shows 97.4% margin on
Hobbyist and pricing pages do not get re-litigated lightly. I am saying
the £2 we leave on the table at £5 is the cheapest piece of voice work
on the page. Here is the argument:

- The community signal from `_naming-signals.md` is explicit: "the OSRS
  community tolerates paid tools but doesn't love them." Pricing is the
  single highest-friction moment on the page.
- £7 reads as *deliberate SaaS pricing*. £5 reads as *price of a pint*.
  One of those two prices fits the voice; the other does not.
- The voice's whole strategic claim is that we are not corporate. £7 is
  a corporate number (chosen for margin), £5 is a friend number (chosen
  for "sure, I'll try it").
- The cost-model gives us the room to absorb a £2 drop on the entry
  tier without endangering the model. Hobbyist is not the margin tier;
  Pro is. Letting Hobbyist run leaner widens the funnel into Pro, where
  the real money lives.
- The dashboard A/B test cost is negligible. We can run £5 vs £7 for
  two weeks post-launch and let the data answer. But the *default* in
  the marketing page draft should be £5, because the voice argues for
  it.

If another proposal disagrees on price, I'd happily lose on the data.
But the default should be the one consistent with the voice, and that
is £5.

---

*End of proposal.*
