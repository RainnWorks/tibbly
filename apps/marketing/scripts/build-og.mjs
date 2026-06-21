#!/usr/bin/env node
/**
 * Build-time conversion: public/og-card.svg → public/og-card.png (1200x630).
 *
 * og:image needs to be a raster on most social platforms (Facebook/iMessage
 * refuse to render SVG cards; Twitter/X accepts SVG inconsistently). We keep
 * the SVG as the editable source and emit a PNG alongside it at build time.
 *
 * Skips silently with a non-zero "not built" warning if @resvg/resvg-js or
 * sharp isn't installed — both are optional dev deps. The HTML references
 * the SVG too, so the page still renders cleanly when the PNG is absent.
 *
 * Run via `bun run build:og` (see package.json) or as a postinstall hook.
 */
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const svgPath = join(__dirname, "..", "public", "og-card.svg");
const pngPath = join(__dirname, "..", "public", "og-card.png");

if (!existsSync(svgPath)) {
  console.error(`[build-og] missing source: ${svgPath}`);
  process.exit(1);
}

const svg = readFileSync(svgPath);

async function tryResvg() {
  const mod = await import("@resvg/resvg-js");
  const Resvg = mod.Resvg ?? mod.default?.Resvg;
  if (!Resvg) throw new Error("@resvg/resvg-js exported no Resvg constructor");
  const resvg = new Resvg(svg, {
    fitTo: { mode: "width", value: 1200 },
    font: { loadSystemFonts: true },
  });
  return resvg.render().asPng();
}

async function trySharp() {
  const mod = await import("sharp");
  const sharp = mod.default ?? mod;
  return await sharp(svg, { density: 144 }).resize(1200, 630).png().toBuffer();
}

let png;
try {
  png = await tryResvg();
  console.log("[build-og] rendered via @resvg/resvg-js");
} catch (e1) {
  try {
    png = await trySharp();
    console.log("[build-og] rendered via sharp");
  } catch (e2) {
    console.warn(
      "[build-og] neither @resvg/resvg-js nor sharp is installed; " +
        "skipping PNG emit. The SVG og-card will still serve.",
    );
    console.warn(`[build-og]   resvg: ${e1.message}`);
    console.warn(`[build-og]   sharp: ${e2.message}`);
    process.exit(0);
  }
}

writeFileSync(pngPath, png);
console.log(`[build-og] wrote ${pngPath} (${png.length} bytes)`);
