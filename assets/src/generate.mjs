// Panels, typography, marks and connectors are authored SVG; item sprites are bundled PNGs.
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL(".", import.meta.url));
const C = { bg: "#251B07", card: "#1B1305", ink: "#FAF2E0", gold: "#E8B33C" };
const font = (family, weight, file) =>
  `@font-face{font-family:'${family}';font-weight:${weight};src:url(data:font/ttf;base64,${readFileSync(root + "fonts/" + file).toString("base64")}) format('truetype');}`;
const css =
  font("Instrument Sans", 400, "InstrumentSans-Regular.ttf") +
  font("Instrument Sans", 600, "InstrumentSans-SemiBold.ttf") +
  font("IBM Plex Mono", 400, "IBMPlexMono-Regular.ttf") +
  `
text{font-family:'Instrument Sans';fill:${C.ink}} .heading{font-weight:600} .label{font-family:'IBM Plex Mono';letter-spacing:2.5px} .muted{fill:${C.ink};fill-opacity:.55} .gold{fill:${C.gold}}
.card{fill:${C.card};stroke:${C.ink};stroke-opacity:.1;stroke-width:1} .wire{fill:none;stroke:${C.gold};stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
`;
const esc = (s) => s.replaceAll("&", "&amp;").replaceAll("<", "&lt;");
const text = (x, y, s, size, cls = "", extra = "") =>
  `<text x="${x}" y="${y}" font-size="${size}" class="${cls}" ${extra}>${esc(s)}</text>`;
const lines = (x, y, ss, size, leading, cls = "") =>
  `<text x="${x}" y="${y}" font-size="${size}" class="${cls}">${ss.map((s, i) => `<tspan x="${x}" dy="${i ? leading : 0}">${esc(s)}${i < ss.length - 1 ? " " : ""}</tspan>`).join("")}</text>`;
const card = (x, y, w, h, extra = "") =>
  `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="12" class="card" ${extra}/>`;
const probe = (x, y, r, stroke = 3) =>
  `<circle cx="${x}" cy="${y}" r="${r}" fill="none" stroke="${C.gold}" stroke-width="${stroke}"/><circle cx="${x}" cy="${y}" r="${r * 0.29}" fill="${C.gold}"/>`;
const svg = (w, h, title, desc, body) =>
  `<?xml version="1.0" encoding="UTF-8"?>\n<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-labelledby="title desc"><title id="title">${esc(title)}</title><desc id="desc">${esc(desc)}</desc><defs><style>${css}</style><marker id="arrow" viewBox="0 0 12 12" refX="10" refY="6" markerWidth="10" markerHeight="10" orient="auto"><path d="M2 2 L10 6 L2 10" fill="none" stroke="${C.gold}" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></marker></defs>${body}</svg>\n`;
const background = (w, h) => `<rect width="${w}" height="${h}" fill="${C.bg}"/>`;
// One contour: a circular probe whose lower-left edge becomes a speech tail.
const icon = `<rect width="1024" height="1024" rx="224" fill="${C.bg}"/><path d="M512 232 A264 264 0 1 1 365 715 L244 780 L285 641 A264 264 0 0 1 512 232Z" fill="none" stroke="${C.gold}" stroke-width="64" stroke-linejoin="round"/><circle cx="512" cy="496" r="80" fill="${C.gold}"/>`;
writeFileSync(
  root + "icon.svg",
  svg(
    1024,
    1024,
    "Tibbly",
    "A gold probe ring with a central dot and a speech-bubble tail on a dark brown rounded square.",
    icon,
  ),
);
// Embed the original Wiki PNGs at an integer scale with nearest-neighbour sampling.
// optimizeSpeed is the SVG nearest-neighbour hint supported by the resvg pipeline.
// PNG IHDR dimensions keep differently sized item sprites centered without distortion.
const item = (name, x, y, size, filename = name.replaceAll(" ", "_") + ".png") => {
  const png = readFileSync(root + "items/" + filename);
  const w = png.readUInt32BE(16) * 2;
  const h = png.readUInt32BE(20) * 2;
  return `<g><title>${esc(name)}</title><image x="${x + (size - w) / 2}" y="${y + (size - h) / 2}" width="${w}" height="${h}" image-rendering="optimizeSpeed" href="data:image/png;base64,${png.toString("base64")}"/></g>`;
};
const slot = (x, y, size) =>
  `<rect x="${x}" y="${y}" width="${size}" height="${size}" rx="3" fill="#352C1C" stroke="#645238" stroke-width="2"/><path d="M${x + 2} ${y + size - 2} V${y + 2} H${x + size - 2}" fill="none" stroke="#FAF2E0" stroke-opacity=".08"/>`;
let hero = background(1800, 1000);
hero += card(48, 48, 800, 904);
hero += `<path d="M48 160 H848" stroke="${C.ink}" stroke-opacity=".1"/>`;
hero += probe(112, 105, 24, 3) + text(158, 123, "Tibbly", 52, "heading");
hero += card(740, 76, 64, 60);
// Vector gear button: avoids platform-specific emoji substitution.
hero += `<g transform="translate(772 106)" fill="none" stroke="${C.ink}" stroke-width="3" stroke-linejoin="round" aria-label="⚙"><path d="M-6-19 H6 L8-13 L13-10 L19-11 L24-1 L19 3 L18 9 L21 14 L13 21 L8 17 H2 L-3 21 L-12 16 L-11 10 L-15 5 L-21 4 L-22-7 L-16-10 L-13-15 L-13-20Z" transform="scale(.8)"/><circle r="7"/></g>`;
hero += text(88, 208, "YOU", 25, "label muted");
hero += `<rect x="88" y="232" width="720" height="176" rx="12" fill="${C.ink}" fill-opacity=".08"/>`;
hero += lines(120, 298, ["Make me a Vorkath tab", "from my bank."], 52, 61);
hero += text(88, 466, "TIBBLY", 25, "label gold");
hero += `<rect x="88" y="490" width="720" height="296" rx="12" fill="${C.gold}" fill-opacity=".10" stroke="${C.gold}" stroke-opacity=".25"/>`;
hero += lines(
  120, 552,
  ["Done. Your Vorkath tab", "has your best ranged gear,", "laid out as you wear it."],
  52, 72,
);
hero += card(88, 838, 522, 76) +
  `<path d="M116 861 V891" stroke="${C.ink}" stroke-opacity=".55" stroke-width="2"/>`;
hero += `<rect x="630" y="838" width="178" height="76" rx="12" fill="${C.gold}"/>` +
  text(719, 888, "Send", 39, "heading", 'text-anchor="middle" style="fill:#251B07"');
// The single connection makes the result of the chat explicit.
hero += `<path class="wire" d="M808 620 H920"/><circle cx="808" cy="620" r="5" fill="${C.gold}"/>`;
// Bank Tags tab, with the familiar worn-equipment silhouette beside a 4 × 7 inventory.
hero += card(920, 48, 832, 904);
hero += text(960, 108, "BANK TAB", 30, "label gold");
hero += text(960, 170, "Vorkath", 56, "heading");
hero += text(1640, 108, "RUNELITE", 23, "label muted", 'text-anchor="end"');
hero += `<path d="M1694 88 L1714 108 M1714 88 L1694 108" stroke="${C.ink}" stroke-opacity=".55" stroke-width="3" stroke-linecap="round"/>`;
hero += `<path d="M920 202 H1752" stroke="${C.ink}" stroke-opacity=".1"/><path d="M960 202 H1172" stroke="${C.gold}" stroke-width="3"/>`;
hero += text(1136, 262, "EQUIPMENT", 27, "label muted", 'text-anchor="middle"');
hero += text(1536, 262, "INVENTORY", 27, "label muted", 'text-anchor="middle"');
hero += `<path d="M1320 292 V912" stroke="${C.ink}" stroke-opacity=".1"/>`;
const equipment = [
  { position: "Head", name: "Armadyl helmet", col: 1, row: 0 },
  { position: "Cape", name: "Ava's assembler", col: 0, row: 1 },
  { position: "Neck", name: "Necklace of anguish", col: 1, row: 1 },
  { position: "Ammo", name: "Ruby dragon bolts (e)", col: 2, row: 1, filename: "Ruby_dragon_bolts_(e)_5.png" },
  { position: "Weapon", name: "Dragon hunter crossbow", col: 0, row: 2 },
  { position: "Body", name: "Armadyl chestplate", col: 1, row: 2 },
  { position: "Shield", name: "Dragonfire ward", col: 2, row: 2 },
  { position: "Legs", name: "Armadyl chainskirt", col: 1, row: 3 },
  { position: "Hands", name: "Barrows gloves", col: 0, row: 4 },
  { position: "Feet", name: "Pegasian boots", col: 1, row: 4 },
  { position: "Ring", name: "Archers ring (i)", col: 2, row: 4 },
];
hero += '<g id="equipment">';
for (const { position, name, col, row, filename } of equipment) {
  const x = 974 + col * 114;
  const y = 320 + row * 116;
  hero += `<g aria-label="${position}: ${name}">${slot(x, y, 96)}${item(name, x, y, 96, filename)}</g>`;
}
hero += '</g>';
const inventory = [
  "Divine ranging potion(4)", "Extended super antifire(4)",
  "Extended super antifire(4)", "Rune pouch",
  ...Array(4).fill("Prayer potion(4)"),
  ...Array(20).fill("Manta ray"),
];
hero += '<g id="inventory">';
for (const [i, name] of inventory.entries()) {
  const x = 1364 + (i % 4) * 88;
  const y = 296 + Math.floor(i / 4) * 88;
  hero += `<g aria-label="Slot ${i + 1}: ${name}">${slot(x, y, 80)}${item(name, x, y, 80)}</g>`;
}
hero += '</g>';
writeFileSync(
  root + "hero.svg",
  svg(
    1800,
    1000,
    "Tibbly creates a Vorkath bank tab from your bank",
    "The player asks: Make me a Vorkath tab from my bank. Tibbly answers: Done. Your Vorkath tab has your best ranged gear, laid out as you wear it. A thin gold line connects the answer to a RuneLite Bank Tags tab named Vorkath. On the left, eleven items occupy their worn-equipment positions: Armadyl helmet, Ava's assembler, Necklace of anguish, Ruby dragon bolts (e), Dragon hunter crossbow, Armadyl chestplate, Dragonfire ward, Armadyl chainskirt, Barrows gloves, Pegasian boots and Archers ring (i). On the right, a four-column, seven-row inventory contains a Divine ranging potion(4), two Extended super antifire(4), a Rune pouch, four Prayer potion(4) and twenty Manta rays, in that order.",
    hero,
  ),
);
let flow = background(1800, 700);
const box = (x, y, w, title, caption, accent = false) =>
  card(x, y, w, 168, accent ? `style="stroke:${C.gold};stroke-opacity:1"` : "") +
  text(x + 28, y + 66, title, 40, "heading") +
  text(x + 28, y + 113, caption, 28, "muted");
flow += box(48, 130, 310, "Tibbly panel", "you ask");
flow += box(438, 130, 422, "Tibbly backend", "picks tools from your words");
flow += box(940, 130, 290, "Model", "via OpenRouter");
flow += box(1442, 130, 310, "Answer", "back in the panel");
flow += box(940, 464, 438, "RuneLite plugin", "reads your game", true);
// Unlabelled first two links are the obvious left-to-right main path.
flow += `<g class="wire" marker-end="url(#arrow)"><path d="M358 214 H422"/><path d="M860 214 H924"/><path d="M1230 214 H1426"/><path d="M1302 214 V388 Q1302 404 1286 404 H1232 V448"/><path d="M1004 464 V314"/></g>`;
flow += `<circle cx="1302" cy="214" r="6" fill="${C.gold}"/>`;
flow += text(1338, 174, "READY", 23, "label muted", 'text-anchor="middle"');
flow += lines(1330, 334, ["NEEDS", "GAME STATE"], 23, 34, "label gold");
flow += text(974, 389, "GAME STATE", 23, "label gold", 'text-anchor="end"');
writeFileSync(
  root + "how-it-works.svg",
  svg(
    1800,
    700,
    "How Tibbly works",
    "Tibbly panel: you ask. Tibbly backend: picks tools from your words. Model: via OpenRouter. If the model needs game state, it asks the RuneLite plugin, which reads your game and returns the requested state to the model. Otherwise, or once ready, the answer goes back in the panel.",
    flow,
  ),
);
console.log("Generated icon.svg, hero.svg and how-it-works.svg.");
