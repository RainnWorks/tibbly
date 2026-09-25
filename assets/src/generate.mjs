// All artwork, typography, marks and connectors are authored SVG.
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
const key = `<g shape-rendering="crispEdges"><path d="M8 18 H24 V8 H48 V16 H60 V36 H50 V48 H38 V62 H48 V74 H36 V66 H24 V78 H12 V64 H20 V52 H28 V40 H16 V32 H8Z" fill="#795323"/><path d="M12 18 H28 V12 H46 V20 H54 V34 H44 V46 H32 V60 H40 V66 H32 V60 H24 V70 H18 V62 H24 V50 H34 V36 H20 V28 H12Z" fill="#C79B54"/><path d="M26 20 H42 V32 H26Z" fill="#251B07"/><path d="M14 18 H24 V22 H14Z M30 12 H44 V16 H30Z M22 50 H30 V56 H22Z" fill="#E8C780"/></g>`;
const pendant = `<g shape-rendering="crispEdges"><path d="M10 6 H18 V14 H10Z M18 14 H26 V30 H18Z M26 30 H34 V46 H26Z M58 6 H66 V14 H58Z M50 14 H58 V30 H50Z M42 30 H50 V46 H42Z M30 42 H46 V54 H30Z" fill="#C99A43"/><path d="M26 52 H50 V60 H58 V80 H50 V88 H26 V80 H18 V60 H26Z" fill="#E8B33C"/><path d="M28 58 H48 V64 H52 V76 H44 V82 H30 V76 H24 V64Z" fill="#AB3D32"/><path d="M28 58 H42 V66 H28 V74 H24 V64Z" fill="#DF7960"/><path d="M42 66 H52 V76 H44 V82 H32 V76 H42Z" fill="#722E24"/></g>`;
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
let hero = background(1800, 1000);
hero += card(48, 48, 1080, 904);
hero += `<path d="M48 160 H1128" stroke="${C.ink}" stroke-opacity=".1"/>`;
hero += probe(112, 105, 24, 3) + text(158, 123, "Tibbly", 52, "heading");
hero += card(1020, 76, 64, 60);
// Vector gear button: avoids platform-specific emoji substitution.
hero += `<g transform="translate(1052 106)" fill="none" stroke="${C.ink}" stroke-width="3" stroke-linejoin="round" aria-label="⚙"><path d="M-6-19 H6 L8-13 L13-10 L19-11 L24-1 L19 3 L18 9 L21 14 L13 21 L8 17 H2 L-3 21 L-12 16 L-11 10 L-15 5 L-21 4 L-22-7 L-16-10 L-13-15 L-13-20Z" transform="scale(.8)"/><circle r="7"/></g>`;
hero += text(98, 201, "YOU", 25, "label muted");
hero += `<rect x="98" y="224" width="980" height="210" rx="12" fill="${C.ink}" fill-opacity=".08"/>`;
hero += lines(
  132,
  282,
  ["Stuck on Dragon Slayer II", "after the Vorkath cutscene.", "Where do I go?"],
  52,
  61,
);
hero += text(98, 478, "TIBBLY", 25, "label gold");
hero += `<rect x="98" y="502" width="980" height="284" rx="12" fill="${C.gold}" fill-opacity=".10" stroke="${C.gold}" stroke-opacity=".25"/>`;
hero += lines(
  132,
  561,
  [
    "The dragon key piece is in your",
    "bank, tab 3. Take your digsite",
    "pendant back to the Lithkren",
    "vault.",
  ],
  52,
  61,
);
hero +=
  card(98, 838, 782, 76) +
  `<path d="M126 861 V891" stroke="${C.ink}" stroke-opacity=".55" stroke-width="2"/>`;
hero +=
  `<rect x="900" y="838" width="178" height="76" rx="12" fill="${C.gold}"/>` +
  text(989, 888, "Send", 39, "heading", 'text-anchor="middle" style="fill:#251B07"');
hero += `<g class="wire" stroke-opacity=".75"><path d="M1256 324 H1190 V550 H1078"/><path d="M1256 547 H1220 V611 H1078"/><path d="M1256 770 H1160 V733 H1078"/></g><g fill="${C.gold}"><circle cx="1078" cy="550" r="5"/><circle cx="1078" cy="611" r="5"/><circle cx="1078" cy="733" r="5"/></g>`;
hero += card(1256, 230, 496, 188) + text(1288, 276, "BANK · TAB 3", 35, "label gold");
hero +=
  `<g transform="translate(1288 295) scale(1.17)">${key}</g>` +
  lines(1401, 335, ["Dragon key", "piece"], 40, 43, "heading");
hero +=
  card(1256, 453, 496, 188) +
  text(1288, 496, "QUEST", 35, "label gold") +
  text(1288, 553, "Dragon Slayer II", 40, "heading");
hero +=
  `<circle cx="1296" cy="598" r="7" fill="${C.gold}"/>` +
  text(1318, 606, "IN PROGRESS", 28, "label muted");
hero += card(1256, 676, 496, 188) + text(1288, 720, "INVENTORY", 35, "label gold");
hero +=
  `<g transform="translate(1287 737) scale(1.05)">${pendant}</g>` +
  lines(1401, 783, ["Digsite", "pendant"], 40, 43, "heading");
writeFileSync(
  root + "hero.svg",
  svg(
    1800,
    1000,
    "Tibbly in RuneLite",
    "A branded illustration of the Tibbly panel. The player asks: Stuck on Dragon Slayer II after the Vorkath cutscene. Where do I go? Tibbly answers: The dragon key piece is in your bank, tab 3. Take your digsite pendant back to the Lithkren vault. Gold lines connect the answer to bank tab 3, the Dragon Slayer II quest, and a digsite pendant in the inventory.",
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
