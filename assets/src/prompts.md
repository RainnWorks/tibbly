# Image prompts and design brief

No image-model prompts were submitted. Panels, text, lines and marks are SVG;
the revised hero embeds bundled OSRS Wiki item sprites. See `generate.mjs` for
the complete deterministic drawing instructions. The latest hero brief below
supersedes the hero section of the original three-image brief.

## Current hero brief

```text
# Redo the Tibbly README hero: "make me a bank tab"

The current `assets/hero.png` shows a chat answer. Replace it with the product's best feature: Tibbly builds a Bank Tags tab of the player's best gear for a job, laid out the way they will wear it. Keep the same branding, fonts, generator (`assets/src/generate.mjs`) and render pipeline (`assets/src/render.mjs`) already in this repo. Do not change the icon or how-it-works images.

## Hero content (1800x1000, readable at 900 px wide and on a phone)
One idea: you ask, and a bank tab appears.

Left: the Tibbly side panel (same style as now: probe + "Tibbly" title, gear button, "Send" button).
- Player: "Make me a Vorkath tab from my bank."
- Tibbly: "Done. Your Vorkath tab has your best ranged gear, laid out as you wear it."

Right: a RuneLite bank window showing the new tab. Label strip at top: gold label "BANK TAB" and title "Vorkath". Inside, RuneLite's layout: worn equipment on the left in the equipment-screen shape, 28 inventory slots (4 columns x 7 rows) on the right. A thin gold line joins Tibbly's answer to the tab.

Equipment (equipment-screen positions):
- Head: Armadyl helmet
- Cape: Ava's assembler
- Neck: Necklace of anguish
- Ammo: Ruby dragon bolts (e)
- Weapon: Dragon hunter crossbow
- Body: Armadyl chestplate
- Shield: Dragonfire ward
- Legs: Armadyl chainskirt
- Hands: Barrows gloves
- Feet: Pegasian boots
- Ring: Archers ring (i)

Inventory, in order: Divine ranging potion(4), Extended super antifire(4), Extended super antifire(4), Rune pouch, Prayer potion(4) x4, then Manta ray for the remaining 20 slots.

## Item art
Use the real item sprites from the OSRS Wiki, e.g. https://oldschool.runescape.wiki/images/Dragon_hunter_crossbow.png (underscores for spaces; check each URL returns image/png; send a User-Agent header). Download them once into `assets/src/items/` and embed them from there so the build is reproducible offline. Scale them with nearest-neighbour so they stay crisp. Add a line to `assets/src/README.md` crediting the OSRS Wiki (CC BY-NC-SA 3.0) and Jagex for the item sprites. No image generation is needed.

## Rules
- Draw all text, lines and panels as SVG in the generator, as now.
- Labels, not sentences, outside the two chat bubbles.
- Update `assets/src/prompts.md` and the hero alt/desc text in the generator.
- Check the result with view_image at full size and at 900 px wide.
- Only write inside this worktree (and /tmp). Commit on the current branch. Do not push.
```

## Original three-image brief

```text
# Tibbly README images

Make three images for the README of Tibbly, a RuneLite plugin for Old School RuneScape. Tibbly is a chat panel inside RuneLite. The player asks a question, and the answer uses their live game state: inventory, bank, quest log and gear.

## Branding (copy this exactly)
Reference page: /tmp/claude-0/-home-orca-orca-workspaces-tibbly-polish/d48ad493-beda-4144-9631-9e6188439ff9/scratchpad/mk/rainn.works Tibbly.dc.html
Screenshot of it: /tmp/claude-0/-home-orca-orca-workspaces-tibbly-polish/d48ad493-beda-4144-9631-9e6188439ff9/scratchpad/mk/tibbly.png
- Background #251B07, cards #1B1305 with a 1px border rgba(250,242,224,0.1) and 12px radius
- Text #FAF2E0, muted text rgba(250,242,224,0.55), gold accent #E8B33C
- Type: Instrument Sans (600 for headings), IBM Plex Mono in uppercase with wide tracking for labels
- The mark is "the probe": a gold ring with a gold dot at its centre on dark brown

## Files to write
- assets/icon-1024.png
- assets/hero.png
- assets/how-it-works.png
- assets/src/ holding every source (SVG, HTML, scripts) and prompts.md with the exact image prompts, so the images can be remade

## 1. Icon (assets/icon-1024.png)
It is shown on GitHub at 128 px and in the RuneLite sidebar at 16 to 24 px. Show the recognizable object and its one action: the probe (gold ring and dot) plus one small sign that it is talking, for example a speech-bubble tail on the ring. Use a rounded-square #251B07 background. It must still read at 32 px and at 16 px, so check it at both sizes and remove any detail that disappears.

## 2. Hero (assets/hero.png, about 1800x1000, readable at 900 px wide and on a phone)
Its job is to show the product doing its job. Show the thing the player touches and the result together: the Tibbly side panel and the game state the answer came from.
- Left: the Tibbly side panel as it appears in RuneLite. Title bar "Tibbly" with a ⚙ button. A chat with the player's question "Stuck on Dragon Slayer II after the Vorkath cutscene. Where do I go?" and Tibbly's answer "The dragon key piece is in your bank, tab 3. Take your digsite pendant back to the Lithkren vault." Below it, a text box and a "Send" button.
- Right: the pieces of game state the answer used, as labelled cards with thin gold lines to the answer: "BANK · TAB 3" (with a key piece item), "QUEST · DRAGON SLAYER II", "INVENTORY" (with a digsite pendant).
- One idea only. Use labels, not sentences. No marketing copy in the image.
- Item art can be an image-model drawing in an OSRS style or plain sprite-like shapes. All text must be drawn as SVG.

## 3. How it works (assets/how-it-works.png, about 1800x700)
Follow one question from start to finish, left to right, in at most 5 boxes, with these exact labels:
1. "Tibbly panel" (small caption: "you ask")
2. "Tibbly backend" (caption: "picks tools from your words")
3. "Model" (caption: "via OpenRouter")
4. One branch after "Model": if it needs game state, an arrow goes to "RuneLite plugin" (caption: "reads your game") and a return arrow goes back to "Model"; otherwise an arrow goes to 5.
5. "Answer" (caption: "back in the panel")
Make "RuneLite plugin" stand out (gold border), because it is the surprising step: the game state stays in RuneLite and the model asks the plugin for it during the answer.

## Rules
- Use image generation only for drawings of objects (for example item art). Draw all text, arrows and marks as SVG and composite them, because image models misspell text.
- Keep every source in assets/src/, including prompts.md.
- Check every image with view_image before you finish, including the icon at 32 px and 16 px.
- Commit on your branch and do not push.
```
