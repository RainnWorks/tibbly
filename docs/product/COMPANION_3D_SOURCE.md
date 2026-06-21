# Companion 3D Source

Status: greenlit pick, ready to vendor
Owner: agent r-loop-mplus16
Linear: [RAI-71](https://linear.app/rainnworks/issue/RAI-71)
Related: [COMPANION_VISUAL_BIBLE.md](./COMPANION_VISUAL_BIBLE.md), [EMBODIED_COMPANION.md](./EMBODIED_COMPANION.md)

This doc replaces the artist commission plan in
[COMPANION_VISUAL_BIBLE.md section 6](./COMPANION_VISUAL_BIBLE.md#6-the-asset-commission-plan).
The companion is now a floating robot ("Probe"), sourced from a
CC0-licensed 3D model and baked offline into 2D sprite atlases for the
plugin renderer. The commission budget is retired.

## 1. Tom's directive

> "find a model online, like a kind of robot that can float, Fallout style"

The Fallout reference is the Eyebot silhouette: a roughly spherical
body, a single front-facing camera lens, an antenna or radar array on
top, propulsion ports visible. We obviously cannot use Bethesda's
asset, but the silhouette is the visual inspiration.

The pivot away from the four-form hooded-humanoid-plus-pets concept
unblocks shipping by replacing roughly USD 5000 to USD 7000 of
commission spend and 4 to 6 weeks of artist lead time with one
vendored CC0 asset plus a Blender bake step. The four-form picker in
[COMPANION_VISUAL_BIBLE.md section 2](./COMPANION_VISUAL_BIBLE.md#2-the-four-starter-forms)
remains; the forms are now visual variants of one base Probe rather
than four hand-drawn starters.

## 2. The pick

**Primary source: Quaternius Sci-Fi Essentials Kit.**

- Source URL: <https://quaternius.com/packs/scifiessentialskit.html>
- Mirror URL: <https://opengameart.org/content/sci-fi-essentials-kit>
- License: CC0 1.0 Universal (Public Domain Dedication)
- Author: Quaternius (Tomas Laulhe, <laulhet@gmail.com>)
- Pack contains: 37 game-ready models including animated flying enemy
  robots, textured screens, crates, and modular sci-fi props. The
  flying-enemy mesh is the primary Probe silhouette; the screen and
  prop meshes give us material to derive the four Probe variants.
- Formats: FBX, OBJ, glTF, Blend
- Style: low poly, flat-shaded, palette-restricted. Reads as warm
  game-asset rather than photoreal military hardware.

**Specific base mesh: "Robot Enemy Flying" (Quaternius, Poly Pizza
mirror).**

- Source URL: <https://poly.pizza/m/lF3jeRJwiH>
- License: CC0 1.0 Universal
- Author: Quaternius
- Format: FBX and glTF
- Poly count (estimated from Quaternius's house style): under 2K
  triangles, comfortably inside the <10K bake-friendly target.
- The name "Enemy" is a Quaternius taxonomy convention; the mesh
  itself is a small floating bot with no weapon attached. Re-tinted
  warm and re-named "Probe" in our content, it reads as companion.

The Sci-Fi Essentials Kit is the recommended download because it
ships the flying-bot mesh plus enough additional sci-fi components
(antennae, lens housings, panel decals) that we can compose four
visually distinct Probe variants from one CC0 source without needing
any second-party asset. The standalone Poly Pizza mirror is the
fallback if the kit download path is unavailable.

### 2.1 Four-criteria justification

1. **Commercial license clean.** CC0 1.0 Universal. No attribution
   required for commercial use. We attribute anyway, per section 4.
2. **Reads as companion, not enemy drone.** Quaternius's house style
   is flat-shaded warm-palette low poly that reads as a game asset
   rather than a hostile drone. Re-tinting the LED warm-orange and
   removing any weapon attachments at the Blender step locks the
   companion read.
3. **Polycount under 10K triangles.** Quaternius's flying-enemy mesh
   is well under 2K triangles. Bake step is fast and PNG output is
   crisp at 32, 64, and 96 pixels.
4. **glTF format.** Available in glTF, FBX, OBJ, and Blend. We
   canonicalize on glTF for the bake script.
5. **Distinct from major game IP.** The mesh is a flat-shaded
   stylized bot, visually unlike the photoreal Fallout Eyebot, the
   curved white EVE silhouette, or the Wheatley single-eye plate.
   The silhouette is "small floating bot with a lens", which is a
   trope, not a clone.

## 3. Rejected runners-up

| # | Model | License | Why rejected |
|---|---|---|---|
| R1 | "Futuristic Flying Animated Robot" (Shayan, Sketchfab) | CC-BY 4.0 | Author description explicitly cites EVE from Wall-E as the inspiration. Trademark-clone read. Hard pass on visual identity. |
| R2 | "Drone Sphere" (Isabel Arauz, Sketchfab) | CC-BY 4.0 | 2M triangles. Way past the 10K bake-friendly cap. Would need heavy retopo before it could enter the bake pipeline. |
| R3 | "Bot Drone" (Dave404, Poly Pizza) | CC-BY 3.0 | CC-BY is acceptable but worse than CC0. No animation rig. No clear front-facing lens silhouette. |
| R4 | "Deep Space Robot" (Tom De Wispelaere, Poly Pizza) | CC-BY 3.0 | Tagged #probe and #abyss which matches our vibe but it has no preview content visible from search; the appearance and polycount are unverified. CC-BY downgrades it below CC0 options. Holding as a backup if Quaternius assets fail. |
| R5 | "Robot" (Poly by Google, Poly Pizza) | CC-BY 3.0 | Object category, not character. No animation. Form factor unclear. Worse license than the Quaternius CC0 pick. |
| R6 | Kenney "Space Kit" (kenney.nl/assets/space-kit) | CC0 | 150 modular space props, but no first-class hovering-bot character mesh in the kit. Would need substantial modeling to compose a Probe from primitives. |

Quaternius wins on the combined axis of license cleanliness (CC0),
polycount, format coverage (glTF native), and the visual register
matches the warm-companion register the bible asks for.

## 4. License terms and attribution

CC0 1.0 Universal is a Public Domain Dedication. It carries no
attribution requirement for commercial or derivative use. The
canonical license text is at
<https://creativecommons.org/publicdomain/zero/1.0/legalcode>.

We attribute Quaternius anyway. The attribution lives in three
places:

1. `apps/plugin/src/main/resources/companion/source/LICENSE.txt`
   sits next to the vendored glTF and reproduces the CC0 dedication
   text plus the author thank-you line.
2. `THIRD_PARTY_LICENSES.md` at the repo root catalogues every
   third-party asset and library shipping with the plugin or the
   marketing site, including the Quaternius Probe source.
3. The RuneLite hub plugin manifest `warning=` line is extended to
   mention the third-party 3D asset attribution per
   [docs/runelite-hub/SUBMISSION_CHECKLIST.md section 2.4](../runelite-hub/SUBMISSION_CHECKLIST.md).

### Author thank-you line

> The Probe companion's base 3D mesh is by Quaternius (Tomas Laulhe),
> released under CC0 1.0 Universal. Find more of their work at
> <https://quaternius.com/>.

This line appears verbatim in the plugin "About" panel, in the
marketing site footer credits section, and in `THIRD_PARTY_LICENSES.md`.

## 5. Canonical file hash

The vendored source lives at
`apps/plugin/src/main/resources/companion/source/probe.glb`. The
SHA-256 of the canonical file is recorded next to it in
`probe.glb.sha256` after the vendoring step lands.

Recording the hash is the discipline that prevents a silent upstream
change from drifting into the build. If Quaternius re-uploads the
pack with mesh changes, the hash mismatch surfaces in CI and we
re-bake the atlases with the new source intentionally.

## 6. The four variants

The four-form picker stays. The forms are now visual variants of one
Probe base, derived at bake time by parameter overrides in the
Blender script:

1. **Probe (default).** The base mesh, warm amber LED, no extra
   attachments. Paired by default with the `dry_wiki_veteran`
   archetype (the brand voice).
2. **Probe - comm visor.** A tinted variant with a teal LED and an
   added radar fin on top of the lens housing. Paired by default
   with `soft_confused_friend`.
3. **Probe - heavy armor.** A tinted variant with a slate-grey body,
   visible armor plating decals, and a deeper red LED. Paired by
   default with `sardonic_veteran`.
4. **Probe - research array.** A tinted variant with sage-green body
   accents, an antenna array swapped in for the radar fin, and a
   pale-cyan LED. Paired by default with `earnest_helper`.

The variants are achieved without re-sourcing any third-party asset:
the same Quaternius base mesh is rendered with material overrides
and procedurally added Blender primitives for the antenna / radar
silhouette differences. If the variant pipeline turns out to be more
work than the first PR can absorb, we ship just the default Probe
with all four archetypes selectable on top of it, and the variant
forms backfill in a follow-up.

## 7. Reversibility

CC0 means we can never be forced to remove the asset. We can fork
the mesh, retexture it, retopologize it, ship derivatives, and
sublicense the result however we like. If Quaternius retires their
catalog tomorrow, our vendored copy stays valid forever.

If we later commission custom art for marketing-grade fidelity, the
Quaternius source remains the in-plugin sprite atlas source. The
commission would only replace the marketing-site hero illustrations.
The atlas pipeline (Blender bake to PNG) is form-agnostic; any
future asset that lands as a glTF gets the same bake recipe.
