import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { Resvg } from "@resvg/resvg-js";
const root = fileURLToPath(new URL(".", import.meta.url));
const fonts = [
  "InstrumentSans-Regular.ttf",
  "InstrumentSans-SemiBold.ttf",
  "IBMPlexMono-Regular.ttf",
].map((f) => root + "fonts/" + f);
function render(source, output, width) {
  const png = new Resvg(readFileSync(root + source), {
    font: { fontFiles: fonts, loadSystemFonts: false, defaultFontFamily: "Instrument Sans" },
    fitTo: { mode: "width", value: width },
  })
    .render()
    .asPng();
  writeFileSync(output, png);
  console.log(output);
}
render("icon.svg", root + "../icon-1024.png", 1024);
render("hero.svg", root + "../hero.png", 1800);
render("how-it-works.svg", root + "../how-it-works.png", 1800);
// Optional review exports are disposable and never mixed with release assets.
if (process.argv.includes("--previews")) {
  const dir = "/tmp/tibbly-images-review";
  mkdirSync(dir, { recursive: true });
  for (const size of [16, 32, 128]) render("icon.svg", `${dir}/icon-${size}.png`, size);
  for (const size of [390, 900]) render("hero.svg", `${dir}/hero-${size}.png`, size);
  render("how-it-works.svg", `${dir}/how-it-works-900.png`, 900);
}
