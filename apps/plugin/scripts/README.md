# Plugin scripts

Scripts that pre-process assets for the RuneLite plugin. They run
offline and commit their output to the repo so the runtime path stays
lean.

## bake-companion-atlas.py

Bakes the vendored 3D Probe mesh (Quaternius, CC0, see
[../../../docs/product/COMPANION_3D_SOURCE.md](../../../docs/product/COMPANION_3D_SOURCE.md))
into 2D PNG sprite atlases for the
`CompanionSpriteAtlas.kt` renderer.

### Prereqs

1. Install Blender 4.x or newer. macOS:

   ```bash
   brew install --cask blender
   ```

   Linux:

   ```bash
   sudo apt install blender
   ```

   Verify with:

   ```bash
   blender --version
   ```

2. Vendor the source mesh. The Quaternius Sci-Fi Essentials Kit (CC0)
   is the canonical source. Download from
   <https://quaternius.com/packs/scifiessentialskit.html>, extract
   the flying-bot glTF, rename it to `probe.glb`, and drop it at
   `apps/plugin/src/main/resources/companion/source/probe.glb`.

   Then record the hash for reproducibility:

   ```bash
   cd apps/plugin/src/main/resources/companion/source
   shasum -a 256 probe.glb > probe.glb.sha256
   ```

### Bake the default variant

From the repo root:

```bash
blender --background --python apps/plugin/scripts/bake-companion-atlas.py \
    -- \
    --source apps/plugin/src/main/resources/companion/source/probe.glb \
    --out apps/plugin/src/main/resources/companion/robot-default \
    --variant default
```

This writes one PNG per atlas frame per cell size to
`apps/plugin/src/main/resources/companion/robot-default/default/<size>px/`
plus an `atlas.json` per cell size describing the pose layout.

### Bake the other variants

Repeat with `--variant comm_visor`, `--variant heavy_armor`,
`--variant research_array`. Each variant overrides the LED color, the
chassis tint, and adds a small Blender primitive (radar fin / armor
plating / antenna array) to the silhouette so the player can tell
them apart at a glance.

```bash
for v in default comm_visor heavy_armor research_array; do
  blender --background --python apps/plugin/scripts/bake-companion-atlas.py \
    -- \
    --source apps/plugin/src/main/resources/companion/source/probe.glb \
    --out apps/plugin/src/main/resources/companion/robot-default \
    --variant "$v"
done
```

### Output layout

```
apps/plugin/src/main/resources/companion/robot-default/
├── default/
│   ├── 32px/
│   │   ├── atlas.json
│   │   ├── hover_move_n_00.png
│   │   ├── hover_move_n_01.png
│   │   └── ...
│   ├── 64px/
│   └── 96px/
├── comm_visor/
├── heavy_armor/
└── research_array/
```

Each `atlas.json` describes the pose names and frame counts so
`CompanionSpriteAtlas.kt` can resolve frames by pose name rather than
by hard-coded pixel offsets.

### Atlas spec

58 frames per variant. The pose list matches
[../../../docs/product/COMPANION_VISUAL_BIBLE.md section 3](../../../docs/product/COMPANION_VISUAL_BIBLE.md)
as renamed for the floating-robot pivot:

| Pose | Frames | Use |
|---|---|---|
| `hover_move_n` through `hover_move_nw` | 8 x 3 = 24 | Compass movement, 3 frames per direction |
| `idle_hover_n` through `idle_hover_nw` | 8 x 2 = 16 | Stationary bob, 2 frames per direction |
| `scan` | 8 | Lens rotates toward 8 cardinal scan targets |
| `display_on` | 2 | Belly screen lights up (replaces "read") |
| `power_down` | 2 | Robot dips to ground, lights dim (replaces "sit") |
| `power_down_extended` | 2 | Extended power-down with z-particle (replaces "yawn") |
| `reaction_rise` | 2 | Sudden rise + LED flash (replaces "surprise") |
| `speak` | 2 | Front light pulse + screen flicker |

### When the source changes

Re-record `probe.glb.sha256`, re-run the bake for all four variants,
commit the changed PNG output and the new hash.

### Why this isn't auto-run in CI

Blender is a heavy runtime that adds 1+ GB to the CI image and the
bake output is deterministic enough that committing it directly is
simpler than re-baking on every push. The `PlaceholderAtlas`
fallback in `CompanionSpriteAtlas.kt` keeps tests green when the
baked PNGs are absent (e.g. fresh clone before the Probe ships).
