# Marketing IA proposal: OSRS player objection-driven angle

> Author: empathy-driven product designer for Tibbly, loop mplus9, 2026-06-21.
> Sibling proposals exist; this one is the objection-reversal angle. It does
> not modify existing marketing code. It is meant to be argued with, not
> rubber-stamped.

The current site sells features. The features are good. The buyer never gets
there. A skeptical OSRS player scrolls a Tibbly page with ten objections
queued in their head before the second fold loads. If those objections are
not resolved in the order they arrive, the buyer churns to a different tab
and never comes back. This proposal reverse-engineers the page from those
objections.

---

## 1. The 10 objections

| # | Objection (player voice) | What they are actually worried about | Product fact that resolves it | Where on the page it gets resolved |
|---|---|---|---|---|
| 1 | "Is this a bot? Will Jagex ban me?" | Account loss. Years of XP gone. | Zero input synthesis. No mouse, no keyboard, no automation. Observation + advice only. Same shape as Quest Helper. | Hero sub-headline + "How it talks to Jagex" strip below hero |
| 2 | "Will it play for me? I want to play, not watch." | Loss of the thing they enjoy doing. | The product literally cannot move the character. Tools are read-only into the client; overlays only highlight. | Hero strip ("Helps you play. Never plays for you.") repeated as a section header |
| 3 | "How does it know my game state? Is it reading my screen?" | Spyware, screen-recording, keylogger fear. | RuneLite plugin API reads structured game state. No screen scrape, no input capture. Egress is one outbound WSS. Audit log visible inside the plugin. | "How it sees the game" section with a diagram |
| 4 | "Why is this £7? ChatGPT is free." | Feeling ripped off. | ChatGPT does not see your bank. The cost is context engineering, not the chat. Free tier with your own API key exists, forever. | Pricing section + a dedicated "Why pay" callout above pricing |
| 5 | "What does this do that Quest Helper / wiki / RuneLite plugins don't?" | Buying a re-skin of free things. | Quest Helper does not know your bank; the wiki does not know your slayer task; no plugin combines them. One LLM with all your live context does. | Feature grid framed against named free alternatives |
| 6 | "What if your backend goes down? Vapourware?" | Throwing money into a black hole. | Three tiers, same plugin: local panels work offline forever; BYO key works without our backend; only the cloud tier needs us. Public status page. | "Three modes" section + status link in footer + "if the cloud goes dark" callout |
| 7 | "What model? Is it good?" | Paying for slop. | Concrete routing posture (cheap for routing, mid for most, premium for hard) without naming. Live trace in the in-product chat. | Demo section already shows model route. Add a "we route per question" line. |
| 8 | "Can I cancel? Is this a scam?" | Subscription trap. | One-click cancel inside Stripe portal. UK CCR 14-day no-questions refund. No card on the free tier. | Pricing section bottom + a "frictionless exit" mini-section |
| 9 | "Will it respect my Iron / HCIM / GIM rules?" | Account-mode integrity. Trading restrictions, drop tables, group sharing. | Tibbly reads account type and refuses to suggest cross-account trades. GIM mode shares only within the group. HCIM gets risk-warned by default. | Iron tier card + dedicated "Account modes" strip |
| 10 | "Who built this?" | Bot farmer side project. | Honest answer: one developer (Tom), open plugin source, open WS protocol spec, open data-disclosure doc, public security contact. We do not have a team to point at. | "Who" section (new) + GitHub link in nav + a name + face |

The order matters. The first two objections are the only ones that, if left
unresolved, end the session immediately. Jagex bans are existential to the
buyer. Everything else is negotiation.

---

## 2. Three dials set (taste-skill: calm, trustworthy, RuneScape-veteran competent)

- **Information density:** medium-high. RuneScape players read the wiki for
  fun. They tolerate dense text if it earns its space. We do not need a Stripe
  hero with three gradient blobs and one sentence; we need a hero that puts
  the no-automation guarantee in the first 200ms. Justified by audience tolerance.
- **Visual temperature:** warm-dim. The OSRS palette is a single hex away
  from cosy: deep blacks, parchment text, gold accents. We avoid the cold
  blue / white "AI startup" tells (Inter font, Tailwind gradient mesh,
  generated 3D render). We are not Inflection. Justified by trustworthiness;
  every cold-blue AI hero on the page is a trigger for the bot association.
- **Motion intensity:** restrained. Sprites can drift, copy stays still.
  The current hero rain is fine because it is opacity-low and slow. Nothing
  parallaxes on scroll, nothing pulses behind copy, nothing autoplays sound.
  Justified by veteran-competent. OSRS players have a low tolerance for
  marketing motion (the game itself runs at 30fps with a 0.6 second tick;
  anything snappier than that reads as foreign).

---

## 3. Type system, palette, icon source

- **Type system.** Display: RuneScape UF (current `font-heading`). Body: an
  IBM-Plex-Sans-equivalent at 16px, slightly looser leading than default
  (1.6) so dense legal-adjacent copy reads. Monospace for the data callouts
  (model name, tool count, "Updated every 5 seconds"). Stay with two fonts
  plus the mono. The current site already does this; do not add a third
  display face.
- **Palette.** Hold the OSRS dark theme: bg `#0d0c08` surface `#1b1810`
  border `#2d2618` text `#e8d9a8` gold `#ffcc00` muted `#6c6346`. Add one
  trust accent: a desaturated forest green (~`#4a7d3a`) for "safe" badges
  ("offline-capable", "no automation", "Stripe-secured"). Avoid red except
  for genuine errors. Avoid neon any colour.
- **Icon source.** Three tiers, in order: (1) the existing
  `@osrs-llm-helper/osrs-assets` skill icons; (2) the official OSRS Wiki
  CC-BY-SA equipment + interface sprites for the "what the plugin reads"
  diagram; (3) a single set of monoline UI glyphs (Phosphor-style, 1.5px
  stroke, gold-dim) for things that have no in-game analogue (Stripe lock,
  shield, status dot). Three sources is the ceiling; more breaks the calm
  dial.

---

## 4. Information architecture (section by section)

Top-to-bottom, with the objection each section closes in parentheses.

1. **Hero** (objections 1, 2). Headline holds the existing "Stop alt-tabbing"
   line; subhead pivots to the no-automation guarantee. A single strip below
   the CTAs: "Reads your game state. Never your keyboard." This is the
   first 200ms.
2. **Trust strip** (objections 1, 3, 10). One row of badges: "No automation",
   "Outbound only, no localhost server", "Open-source plugin", "Audit log
   inside the plugin", "RuneLite plugin API only". Each badge links to the
   doc it claims.
3. **How it sees the game** (objection 3). A diagram: Plugin reads OSRS
   client via RuneLite API; one outbound WSS to backend; backend to
   OpenRouter; LLM reply back. Below the diagram, the data disclosure
   summary in a table. Mirrors `DATA_DISCLOSURE.md`.
4. **Three modes** (objection 6). The three-tier value floor (local panels
   / BYO key / Tibbly cloud) drawn as three columns. Closes "what if your
   backend dies": the first two columns do not need us at all.
5. **Feature grid against named alternatives** (objection 5). Re-frame the
   current six-card grid: each card titled "Quest Helper does X, Tibbly
   also Y", "WikiSync does X, Tibbly also Y". Specific, not abstract.
6. **Demo** (objection 7). Keep the current CSS chat preview; add the
   per-turn routing line and a "live model trace" tooltip.
7. **Pricing** (objections 4, 8). Keep the four-tier layout. Add an
   above-pricing "Why pay when ChatGPT is free" strip with the honest
   answer (context engineering, not chat). Add a "Cancel in one click"
   note + "First 14 days refunded, no questions" below.
8. **Account modes** (objection 9). A new strip: "Iron-safe. HCIM-warned.
   GIM-aware." Three small cards.
9. **Live counter** (objections 6, 10, social proof of liveness). Keep
   the existing component. Add a status pill: "API status: ok · 12ms".
10. **FAQ** (cleanup tier for stragglers). Keep the existing FAQ; reshape
    each Q-A so its first sentence is the answer.
11. **Who built this** (objection 10). New section. One paragraph, a
    photo or wiki-portrait stylisation, links to the GitHub org and the
    Linear-style changelog.
12. **Footer** (legal anchor). Keep the existing structure; bump the
    "Not affiliated with Jagex" paragraph to one full line of text, not
    a footnote, because the player reads it.

What is NOT in the IA: a testimonials carousel (we have none yet, fakes
are worse than absence), a logo cloud (no logos to show), a comparison
table against ScapeGPT or other AI tools (lifts them by association),
a "for streamers" tab (the Pro tier covers it; a tab would dilute).

---

## 5. The credibility stack

Three layers, each visible without reading.

- **Social proof (currently weakest).** The live presence counter is the
  only piece we have. Augment: install count badge ("X RuneLite players
  with Tibbly active"), region pills from the existing component, a
  changelog dot ("shipped: bank tab viewer · 2 days ago") in the nav.
- **Technical proof.** Open plugin source on GitHub with a count
  ("Plugin: 47 Kotlin files, BSD-2-Clause"), the WS protocol spec link
  ("Read the wire format"), the data disclosure table, the
  `EgressGate.kt` audit line ("Player data leaves through one function").
  Linking to specific files signals competence more than any badge.
- **Legal-language proof.** Direct quotes from the Privacy Policy and
  Terms above the pricing CTA: "We never see your card. We never see
  your Jagex password. Chat history deleted at 30 days." A "Not
  affiliated with Jagex" line under each pricing card, not buried in
  footer fine-print.

---

## 6. Specific copy for the 3 hardest objections

### Objection 1: "Will Jagex ban me?"

> **You play. Tibbly watches.**
>
> Tibbly never moves your mouse, never types in chat, never clicks a tile.
> It reads the same game state every RuneLite plugin reads (inventory,
> quest log, equipment, location) and answers in the side panel. It is
> the same category as Quest Helper, WikiSync, and Inventory Setups,
> which together run on over a million OSRS accounts. We are not Jagex,
> and we do not speak for them; we built Tibbly to fit the third-party
> client posture Jagex has tolerated for ten years. We will not refund a
> ban (no one credibly can), but we will tell you exactly what we send
> and exactly what we never touch.
>
> *Read the [no-automation guarantee](#) · [what we read, by tool](#) ·
> [Jagex policy notes](#)*

### Objection 4: "Why is this £7? ChatGPT is free."

> **ChatGPT does not know what is in your bank.**
>
> The cost of Tibbly is not the chat. The chat is the cheap part. The
> cost is the live wire to your game state: the plugin that reads your
> inventory and bank and slayer task and clue scroll the instant you ask,
> the routing that picks a cheap model for "what time is the daily
> reset" and a smarter one for "plan my Inferno", and the bill we eat
> when a quest walkthrough takes thirty turns.
>
> If you would rather pay zero, the same plugin runs in **free mode
> with your own provider key**, forever, with no Tibbly subscription.
> The cloud tier is for players who want it to just work.

### Objection 6: "What if your backend goes down?"

> **You bought a plugin, not a hostage.**
>
> Tibbly is one plugin with three modes. **Local panels** (bank viewer,
> farming grid, slayer detail) work without our backend and without an
> LLM key at all. **Bring your own key** mode talks directly to the
> provider; we are not in the loop. **Tibbly cloud** is the paid mode
> and the only one that needs us.
>
> If our backend goes silent, the cloud tier pauses and the other two
> modes keep running. We publish [uptime](#) and a [downtime credit
> policy](#) (full pro-rata on any day with more than thirty minutes of
> outage). Cancel in one click from the Stripe portal; first fourteen
> days are refunded no questions asked.

---

## 7. Trust signals (visible without close reading)

- **Plugin-source-on-GitHub** badge next to the hero CTA, with star
  count if non-trivial. Lives in the trust strip.
- **"Open WS protocol spec"** chip, gold-dim, linking to the spec repo.
  Reading the spec is hard; the existence of one is the signal.
- **Hub-status indicator**, a small pill in the nav: "Sideload (Hub
  submission pending)" or "Hub-listed", flipped by config when we ship
  the PR.
- **Stripe trust mark** in the pricing section ("Payments by Stripe.
  Card details never touch our servers.") The lock glyph in the gold-dim
  monoline set.
- **EU-hosted analytics** mention in the footer, single line.
- **Live API status pill** in the live-counter section ("ok · 12ms").
  The presence of this pill is itself the message.
- **Audit log preview** screenshot in the "How it sees the game" section
  showing the per-egress entries from `cloud/AuditLog.kt`.
- **Visible monthly cap** in pricing copy. The phrase "hard cap" should
  appear three times on the page.

---

## 8. What gets killed

- **The skill-icon rain in the hero.** It is pretty. It does not resolve
  any objection. A skeptical player reads it as "this is a marketing
  page", not "this is a tool". Replace with a still grid of six relevant
  skills.
- **The "live network" globe / region pills** in its current form. The
  count is fine; the regions read as theatre to a Reddit-pilled buyer.
  Keep the count, kill the pills (or move them to a `/live` deep page).
- **"Six in-client superpowers."** "Superpowers" is the bot-association
  word. Reframe to "Six things Tibbly knows that the wiki doesn't".
- **"Most picked" badge on the Hobbyist tier.** We have no install data
  yet. Drop until the data is real.
- **Footer's "Tibbly · the OSRS LLM Helper" lockup repeated three times.**
  Once is enough.

---

## 9. One opinionated take

The site as it stands is honest, but it sells the wrong unit. It sells
**features** (six tiles, tile marks, NPC highlights, bank prep) when the
buyer is shopping for **permission to install it without losing the
account they have spent years building**.

The single highest-leverage change is to move objection #1 (Jagex bans)
out of the FAQ and into the hero. Right now, the answer to the only
question that ends the session in the first 200ms is fourteen scroll
units away. Move it up. Make it the second sentence of the page. The
buyer will not pay seven pounds before they have decided we are not
going to get them banned, and we are answering that question last
instead of first.

If we change only one thing this week, change that.
