# Tibbly README image sources

The three release images are in `assets/`:

| Image              | Pixels      | Source             |
| ------------------ | ----------- | ------------------ |
| `icon-1024.png`    | 1024 × 1024 | `icon.svg`         |
| `hero.png`         | 1800 × 1000 | `hero.svg`         |
| `how-it-works.png` | 1800 × 700  | `how-it-works.svg` |

All artwork is vector-authored, including the probe, gear, original sprite-like
key piece and pendant, text, and arrows. No image-generation model was used.
`prompts.md` preserves the exact supplied design brief. The hero is an illustrated
panel using the requested branding and controls from `TibblyPanel.kt` and
`ChatPanel.kt`.

## Rebuild

From the repository root, with Node.js and npm installed:

```sh
npm ci --prefix assets/src
node assets/src/generate.mjs
node assets/src/render.mjs
```

`generate.mjs` contains the editable geometry, text, colors and item art. It writes
three self-contained SVGs, embedding the bundled fonts for browser previews.
`render.mjs` rasterizes those SVGs with the locked version of resvg and explicitly
loads the bundled fonts; system fonts and network access are not used to render.
To rasterize a directly edited SVG, run only `render.mjs`.

```sh
node assets/src/render.mjs --previews
```

This also writes review copies under `/tmp/tibbly-images-review/`: the icon at
16, 32 and 128 px, the hero at 390 and 900 px wide, and the workflow at 900 px wide.
The icon has transparent corners outside its rounded brown background.

## Fonts

Bundled under `fonts/`, with each family's SIL Open Font License:

- Instrument Sans Regular (400) and SemiBold (600), by Instrument.
- IBM Plex Mono Regular (400), by IBM.

The static TTFs came from the Google Fonts stylesheet:
<https://fonts.googleapis.com/css2?family=Instrument+Sans:wght@400;600&family=IBM+Plex+Mono:wght@400;500>

Exact download URLs:

- Regular: <https://fonts.gstatic.com/s/instrumentsans/v4/pximypc9vsFDm051Uf6KVwgkfoSxQ0GsQv8ToedPibnr-yp2JGEJOH9npSTF-Qf1.ttf>
- SemiBold: <https://fonts.gstatic.com/s/instrumentsans/v4/pximypc9vsFDm051Uf6KVwgkfoSxQ0GsQv8ToedPibnr-yp2JGEJOH9npSQb_gf1.ttf>
- Mono: <https://fonts.gstatic.com/s/ibmplexmono/v20/-F63fjptAgt5VM-kVkqdyU8n5ig.ttf>
- Instrument license: <https://raw.githubusercontent.com/google/fonts/main/ofl/instrumentsans/OFL.txt>
- IBM license: <https://raw.githubusercontent.com/google/fonts/main/ofl/ibmplexmono/OFL.txt>

## Visual review

The final PNGs were reviewed with `view_image`, including the icon at actual
16 px and 32 px and the hero at 390 px and 900 px wide. The workflow has five
boxes, an explicit conditional branch after Model, and separate request and
return arrows. The RuneLite plugin box has a gold border. All prescribed chat
text, node titles and captions are retained in SVG text elements.
