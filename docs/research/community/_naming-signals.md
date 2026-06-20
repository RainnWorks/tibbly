# Community naming signals — what OSRS players call similar tools

Researcher: agent r7 (RAI-11). Date: 2026-06-21.

Hand-off note for the marketing + naming agents: this is the lexical
landscape we have to fit into when we name our product.

---

## How OSRS tools are named in the wild

Surveying the existing tool ecosystem (RuneLite plugin hub corpus from
R2's `pain-points.md`, OSRS-adjacent fan sites and Discord bots) gives
a clear naming grammar. Names cluster into five archetypes:

### Archetype A — *"X Helper"* (utility-forward)

Most common shape in the RuneLite hub.

- **Quest Helper** (555K installs, #1 plugin)
- **Hunllef Helper**
- **Boss Timers**
- **Loot Tracker**
- **Loot Lookup** (140K installs)

**Signal:** Players read "X Helper" as *a tool that helps with X*.
Clear, boring, doesn't bid for emotional connection. Our placeholder
`osrs-llm-helper` is exactly this archetype — fine as a working name,
weak as a brand.

### Archetype B — *Sync / Tracker* (data-forward)

- **WikiSync** (302K installs)
- **Loot Tracker**
- **XP Tracker**
- **Bank Tags Together**

**Signal:** Players accept "Sync" or "Tracker" as functional brand
suffixes when the value is "we record / mirror your state".

### Archetype C — Lore borrow (Jagex IP risk)

- **Tempoross Helper** (boss name → Jagex IP)
- **Gauntlet Extended** (minigame name → Jagex IP)
- **Zulrah Plugin** (boss name → Jagex IP)

**Signal:** Free RuneLite plugins get away with borrowing in-game proper
nouns. **A paid SaaS does not.** We must not. (See `CLAUDE.md` security
note.)

### Archetype D — Invented word / mascot

- **117 HD** (the renderer — abstract numeric)
- **Wise Old Man** (the Discord bot — references an in-game NPC, but the
  bot itself is fan-built and broadly tolerated)
- **Konar's Tasks**

**Signal:** Invented or mascot-shaped names are the OSRS-community
"signature look" — Wise Old Man being the model exemplar. A friendly
proper noun reads native; a Silicon-Valley-style abstract brand reads
foreign and is mocked.

### Archetype E — Streamer / clan / discord shorthand

Players in chat refer to tools by **whatever's shortest to type**.
"GE tracker", "wise old man", "wiki", "quest helper" → "QH". A two-syllable
proper noun gets a free pass; a four-word brand becomes an unwanted
acronym.

**Signal:** Optimize for the in-chat shorthand. If our brand is two
syllables and easy to type, players will use it verbatim. If it's longer,
they'll invent shorthand we don't control.

---

## What players call the wiki

Universally: **"the wiki"**. Lowercase. No one says "the OSRS wiki" in
chat. This is good news for us — it means a non-wiki-named tool can claim
the "wiki on tap" job without being called "the wiki" itself.

## What players call AI/LLM tools today

There's almost no shared vocabulary yet, because nothing has crossed the
adoption threshold. Floating terms observed:

- **"the bot"** — common but conflicts with the strongly negative
  bot/macro connotation in OSRS. AVOID this framing in our copy.
- **"AI helper"** — generic, low affection.
- **"copilot"** — corporate-feeling, doesn't fit OSRS register.
- **"chat thing"** — what a player would say in a Discord thread.

**Signal:** the field is open. Whoever lands a name first owns the
shorthand. Strong argument for a memorable proper-noun brand (Archetype D)
over a descriptive name (Archetype A).

## What players call follower NPCs

The community has deep affection for in-game followers and pets — Bob
the Cat, the Wise Old Man, Yelps (deprecated but iconic), Hans (the
account-age NPC), and the slayer master roster (Turael, Mazchna, Vannaka,
Chaeldar, Konar, Nieve/Steve, Duradel, Krystilia). The naming grammar
here is:

- Short (1–2 syllables).
- Slightly silly (Hans, Bob, Yelps) or slightly portentous (Duradel,
  Chaeldar).
- Often consonant-heavy (Konar, Mazchna).
- Rarely a real human name; usually invented or quasi-medieval.

**Signal:** A product name that mimics this NPC-name grammar will feel
*native* to OSRS players. This is the direction we should bias toward,
without copying any specific NPC.

---

## What players call paid/premium tools

Most paid OSRS-adjacent tools live on Patreon or Ko-fi, not as SaaS.
The vocabulary players use about them:

- **"the patreon plugin"** (generic, slightly grudging)
- **"X's tool"** (named after the creator, e.g. "Mod Ash's lookup")
- **"the premium version"** (when a plugin has tiers)

**Signal:** The OSRS community tolerates paid tools but doesn't love them.
A brand that *feels* like a free RuneLite plugin (warm proper noun,
friendly tone) lowers the resistance to paying.

---

## Implications for our naming decision

1. **Avoid Archetype A** for the brand itself (we can keep it as the
   tagline, e.g. "your in-game helper").
2. **Avoid Archetype C** absolutely — no Jagex IP borrows.
3. **Lean Archetype D** — invented, mascot-shaped, NPC-grammar-flavored.
4. **2 syllables max**, easy to type in chat, verbable ("ask X", "X says").
5. **Land before competition does** — the AI-OSRS field has no incumbent
   vocabulary; first mover owns the shorthand.

This is exactly the rationale behind picking **Tibbly** over Wikit/
Scribbins in `docs/marketing/NAME_CANDIDATES.md`:

- Tibbly is Archetype D (invented, NPC-shaped, 2 syllables).
- Wikit is Archetype B-ish (functional suffix; sells short).
- Scribbins is Archetype D but 2-syllable + spelling-tax.

---

## Open follow-ups for the next research loop

- **Reddit / Discord ethnography:** confirm players really do shorthand
  to 2-syllable proper nouns (this loop was inferential; R2 noted Reddit
  scraping was blocked — try again via `claude-in-chrome`).
- **YouTube creator vocabulary:** when J1mmy, Soup, Settled, Limpwurt,
  or B0aty refer to RuneLite plugins on stream, what's the average word
  count of the reference? Hypothesis: ≤2 words.
- **OSRS Wiki NPC roster scrape:** confirm no in-game NPC or item named
  Tibbly, Wikit, or Scribbins. This loop did a soft check from prior
  knowledge — the rigorous version needs a wiki scrape.
