# Companion source mesh

This directory holds the canonical 3D source the
`apps/plugin/scripts/bake-companion-atlas.py` script reads.

## Vendor the asset

The Probe companion's base mesh is the Quaternius Sci-Fi Essentials
Kit (CC0). Download from one of:

- <https://quaternius.com/packs/scifiessentialskit.html> (preferred, pack form)
- <https://opengameart.org/content/sci-fi-essentials-kit> (mirror)
- <https://poly.pizza/m/lF3jeRJwiH> (standalone flying-bot model)

Extract the flying-bot mesh. Rename the glTF file to `probe.glb` and
drop it next to this README. Then record the canonical hash:

```bash
shasum -a 256 probe.glb > probe.glb.sha256
```

The hash is committed so CI can detect a silent upstream change.

## License

CC0 1.0 Universal. See [LICENSE.txt](./LICENSE.txt) for the full
dedication. Attribution is not required but we give it anyway, per
[../../../../docs/product/COMPANION_3D_SOURCE.md section 4](../../../../docs/product/COMPANION_3D_SOURCE.md).

## Why this is not pre-committed

The Quaternius download path requires accepting a browser EULA-style
"download" button click. The vendoring is a one-time human action;
the file then lives in this directory and is committed alongside the
hash.

In the meantime the `bake-companion-atlas.py` script ships ready to
run against `probe.glb` once it lands here. The plugin's
`CompanionSpriteAtlas.kt` falls back to its `PlaceholderAtlas` while
the source is absent so the test suite stays green.

## When the source changes

If a new version of the Quaternius mesh lands, replace `probe.glb`,
re-record `probe.glb.sha256`, and re-run the bake script to regenerate
the PNG atlases under `apps/plugin/src/main/resources/companion/robot-default/`.

Document the change in
[`docs/product/COMPANION_3D_SOURCE.md`](../../../../docs/product/COMPANION_3D_SOURCE.md)
section 5 (canonical hash).
