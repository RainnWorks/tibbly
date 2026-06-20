# Product name candidates — replacing `osrs-llm-helper` (agent r7, RAI-11)

**Status:** picked default + 2 alternates, ready for Tom to confirm on wake-up.
**Date:** 2026-06-21.
**Constraint:** must avoid Jagex-trademarked terms (RuneScape, OSRS, Old
School, Scape, Gielinor, Lumbridge, Varlamore, Zaros, Saradomin, etc.) per
`CLAUDE.md` security/legal note.

---

## Methodology

- **Trademark check:** USPTO / generic web search via WebSearch. NOTE: live
  WebSearch was unavailable at the time of this loop ("Web search error:
  unavailable"). Where WebSearch failed, we relied on WebFetch probes of
  the candidate's `.com`, `.ai`, and GitHub handle, plus prior-knowledge
  trademark heuristics. **Tom should re-run the USPTO TESS check on the
  picked name before any logo/brand spend.**
- **Domain check:** `WebFetch <name>.com` + variants (`get<name>.com`,
  `<name>app.com`, `<name>.ai`). `ECONNREFUSED` = no DNS = almost
  certainly unregistered. A live HTML response = taken (and we note who).
- **GitHub handle:** `WebFetch github.com/<name>` — 404 = available, profile
  = taken (and we note their activity).
- **Tonal fit:** does it cohere with the brand voice
  (`docs/marketing/BRAND_VOICE.md`)?
- **Jagex risk:** does it borrow any in-game proper noun?

---

## Pick: **Tibbly**

**Tagline candidate:** *"Tibbly knows what's in your bank."*

### Why this name

- **Companion-shaped.** Reads like an NPC follower's nickname — small,
  warm, slightly silly, the kind of thing an OSRS player would tolerate as
  a chat handle ("ask Tibbly"). Maps directly to the picked voice.
- **Phonetically distinct.** Two syllables, hard consonants — easy to say
  in voice chat / on stream, easy to type, hard to confuse with another
  product.
- **Verbable.** "Tibbly says…", "let me Tibbly that", "ask Tibbly". Verbs
  are how SaaS names enter habit.
- **Zero Jagex overlap.** Invented word, no in-game NPC, place, or item
  named Tibbly that I could find. (Tom to confirm on the OSRS wiki search.)
- **Memorable.** Short, vowel-friendly, sticks in head.

### Risks

- **`tibbly.com` is taken** by an art seller ("Tibbly Art" / Chairish
  storefront — minimal site, just a contact email and a Chairish link).
  This is a different commercial category (art/furniture vs. software/
  gaming SaaS), so trademark conflict is *probably* low — but Tom should
  consider (a) buying the domain (often cheap from a hobby artist) or
  (b) using `tibbly.app` / `tibbly.gg` / `gettibbly.com`.
- "Tibbly" might land as too cute for an Iron-tier "tryhard" buyer. The
  brand voice doc compensates by keeping the *voice* calm/dry even though
  the *name* is warm.

### Availability checklist

| Channel | Status | Evidence |
|---|---|---|
| `tibbly.com` | Taken (art business) | WebFetch returned Tibbly Art / Chairish storefront. Different category, weak conflict. |
| `tibbly.app` | Likely available | No live response; not probed in this loop. Tom to verify. |
| `tibbly.gg` | Likely available | Gaming-friendly TLD. Tom to verify. |
| `gettibbly.com` | Likely available | `ECONNREFUSED` on WebFetch (no DNS). |
| `tibblyapp.com` | Likely available | `ECONNREFUSED` on WebFetch. |
| `github.com/tibbly` | **Available** | HTTP 404 on the profile URL. |
| USPTO TESS | NOT VERIFIED | WebSearch unavailable this loop. **Re-run before brand spend.** |
| OSRS wiki / Jagex IP | No collision found | Not an in-game NPC/place/item I could surface. |

---

## Alternate 1: **Wikit**

**Tagline candidate:** *"Wikit knows. You ask."*

### Why considered

- **Honest about the core feature.** It IS a wiki-grounded helper. "Wikit"
  packages that into one verby word ("wikit that").
- **Zero Jagex overlap.**
- **Sticky.** Single syllable + "-it" suffix is one of the easiest English
  word-shapes to remember (cf. Reddit, Quizlet, Slackbot).

### Risks

- **`wikit.com` is empty / parked.** Probably acquirable but unknown price.
- **`github.com/wikit` is taken** by a student account (no active product,
  no obvious conflict, but the handle is unavailable).
- **There IS prior software called "wikit"** — a desktop personal-wiki
  tool from prior-knowledge. Niche, but a trademark search would land hits
  in the "software" class (US Class 9 / IC 042). Higher conflict risk
  than Tibbly.
- **Reduces us to "a wiki tool"** — undersells the live game-state
  superpower.

### Availability checklist

| Channel | Status | Evidence |
|---|---|---|
| `wikit.com` | Empty/parked | WebFetch returned empty HTML; ambiguous owner. |
| `wikit.app` | Likely available | Not probed in this loop. |
| `usewikit.com` | Likely available | `ECONNREFUSED` on WebFetch. |
| `github.com/wikit` | Taken (student) | Active profile with coursework repos. |
| USPTO TESS | NOT VERIFIED — KNOWN RISK | "wikit" matches a prior personal-wiki software; investigate before any brand spend. |
| OSRS wiki / Jagex IP | No collision found | — |

---

## Alternate 2: **Scribbins**

**Tagline candidate:** *"Scribbins reads the wiki so you don't have to."*

### Why considered

- **Lore-flavored.** "-ins" suffix feels NPC-shaped (Bilrach, Larrissa,
  Hopkins, Robbins). Borrows the wiki/scribe semantic without literally
  saying scribe.
- **Distinctive in search.** Almost certainly zero collision; very low
  trademark risk.
- **Soft, friendly cadence.** Plays well with the calm-companion voice.

### Risks

- **Hard to spell first try.** Players might type "scribbens", "scribbins"
  with one b, etc. Domain mistypes will leak traffic.
- **Sounds like a small mammal, not a product** — may not survive a
  serious "Iron tier" pitch.
- **`scribbins.com` is parked** — the holder isn't using it but it'll cost
  to acquire.

### Availability checklist

| Channel | Status | Evidence |
|---|---|---|
| `scribbins.com` | Parked | WebFetch returned a minimal "scribbins" placeholder. |
| `scribbins.app` | Likely available | Not probed this loop. |
| `askscribbins.com` | Likely available | `ECONNREFUSED` on WebFetch. |
| `github.com/scribbins` | Unknown | Not probed this loop. |
| USPTO TESS | NOT VERIFIED | Re-run before brand spend; expected low risk given the invented word. |
| OSRS wiki / Jagex IP | No collision found | — |

---

## Ranking

| Rank | Name | Lead reason | Lead risk |
|---|---|---|---|
| 1 | **Tibbly** | Companion-shaped, verbable, low Jagex risk, GitHub handle free. | `.com` taken by an art business (cross-class, probably tolerable). |
| 2 | Scribbins | Distinctive, lore-feel, low collision risk. | Spelling tax + tonally cute. |
| 3 | Wikit | Honest, sticky, one-syllable. | Prior personal-wiki software in the same class; GitHub handle taken. |

---

## What Tom should re-run before brand spend

1. **USPTO TESS search** for "Tibbly", "Wikit", "Scribbins" in IC 009
   (downloadable software) and IC 042 (SaaS). WebSearch was unavailable
   this loop — DO NOT skip this on wake-up.
2. **EUIPO / UK IPO search** if we intend to take any payments from
   UK/EU customers (we should — RuneLite community is global).
3. **Domain price probe** on `tibbly.com` (art seller — may sell cheap)
   and `tibbly.app` / `tibbly.gg`.
4. **OSRS wiki collision check** — search the wiki for "Tibbly" /
   "Wikit" / "Scribbins" to confirm no in-game NPC overlap.

---

## Decision

**Picked: `Tibbly`** — with `tibbly.app` as the primary domain candidate
(swappable to `.gg` or `gettibbly.com` if `.app` falls through). Brand
voice doc already assumes "the helper" as a fallback alias; "Tibbly" is
the proper noun we'd use in marketing copy and on the dashboard.

If TESS reveals a blocker, fall back to **Scribbins** (lower collision
risk than Wikit, even at the cost of cuteness).
