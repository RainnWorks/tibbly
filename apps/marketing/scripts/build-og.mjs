#!/usr/bin/env node
/**
 * Build-time conversion: public/og-card*.svg → public/og-card*.png (1200x630).
 *
 * og:image needs to be a raster on most social platforms (Facebook/iMessage
 * refuse to render SVG cards; Twitter/X accepts SVG inconsistently). We keep
 * each SVG as the editable source and emit a PNG sibling at build time.
 *
 * Variants rendered:
 *   - og-card.svg            → og-card.png            (chat-window theme)
 *   - og-card-companion.svg  → og-card-companion.png  (companion-centric)
 *
 * The companion variant is the default og:image used by index.html. The
 * chat-window variant stays as a sibling reference for sharing the
 * "shape of a chat" demo screenshot directly.
 *
 * Skips silently with a non-zero "not built" warning if @resvg/resvg-js or
 * sharp isn't installed (both are optional dev deps). The HTML references
 * the SVGs too, so the page still renders cleanly when the PNGs are absent.
 *
 * Run via `bun run build:og` (see package.json) or as a postinstall hook.
 */
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { join, dirname, basename } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const publicDir = join(__dirname, "..", "public");

/**
 * Each variant we want to render. `name` is the file stem; the script
 * reads `<name>.svg` and writes `<name>.png`.
 */
const VARIANTS = [
  { name: "og-card", label: "chat-window" },
  { name: "og-card-companion", label: "companion" },
];

async function renderResvg(svg) {
  const mod = await import("@resvg/resvg-js");
  const Resvg = mod.Resvg ?? mod.default?.Resvg;
  if (!Resvg) throw new Error("@resvg/resvg-js exported no Resvg constructor");
  const resvg = new Resvg(svg, {
    fitTo: { mode: "width", value: 1200 },
    font: { loadSystemFonts: true },
  });
  return resvg.render().asPng();
}

async function renderSharp(svg) {
  const mod = await import("sharp");
  const sharp = mod.default ?? mod;
  return await sharp(svg, { density: 144 }).resize(1200, 630).png().toBuffer();
}

let resolvedRenderer = null;

async function chooseRenderer(sampleSvg) {
  if (resolvedRenderer) return resolvedRenderer;
  try {
    const png = await renderResvg(sampleSvg);
    resolvedRenderer = { fn: renderResvg, name: "@resvg/resvg-js" };
    // Discard the sample render so we re-render through the dispatcher below.
    void png;
    return resolvedRenderer;
  } catch (e1) {
    try {
      const png = await renderSharp(sampleSvg);
      resolvedRenderer = { fn: renderSharp, name: "sharp" };
      void png;
      return resolvedRenderer;
    } catch (e2) {
      console.warn(
        "[build-og] neither @resvg/resvg-js nor sharp is installed; " +
          "skipping PNG emit. The SVGs still serve.",
      );
      console.warn(`[build-og]   resvg: ${e1.message}`);
      console.warn(`[build-og]   sharp: ${e2.message}`);
      return null;
    }
  }
}

let firstSvg = null;
for (const v of VARIANTS) {
  const svgPath = join(publicDir, `${v.name}.svg`);
  if (!existsSync(svgPath)) {
    console.error(`[build-og] missing source: ${svgPath}`);
    process.exit(1);
  }
  if (!firstSvg) firstSvg = readFileSync(svgPath);
}

const renderer = await chooseRenderer(firstSvg);
if (!renderer) {
  process.exit(0);
}
console.log(`[build-og] using renderer: ${renderer.name}`);

for (const v of VARIANTS) {
  const svgPath = join(publicDir, `${v.name}.svg`);
  const pngPath = join(publicDir, `${v.name}.png`);
  const svg = readFileSync(svgPath);
  const png = await renderer.fn(svg);
  writeFileSync(pngPath, png);
  console.log(
    `[build-og] wrote ${basename(pngPath)} (${png.length} bytes, ${v.label})`,
  );
}
