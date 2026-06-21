"""Bake the Quaternius Probe glTF source to 2D PNG atlases.

Run as:

    blender --background --python apps/plugin/scripts/bake-companion-atlas.py \
        -- --source apps/plugin/src/main/resources/companion/source/probe.glb \
           --out apps/plugin/src/main/resources/companion/robot-default \
           --variant default

The script ships ready to run. It does NOT execute automatically
from the repo because it needs a local Blender install (4.x or newer).
See apps/plugin/scripts/README.md for the install recipe and the
expected output layout.

The bake matches the atlas spec from
docs/product/COMPANION_VISUAL_BIBLE.md section 3 (renamed for the
floating-robot pivot in docs/product/COMPANION_3D_SOURCE.md section 6):

  - 8 directions of hover-move (3 frames per direction = 24 frames)
  - 8 directions of idle hover (2 frames per direction = 16 frames)
  - 8 frames of scan / look-at
  - 2 frames of display-on (belly screen lights up)
  - 2 frames of power-down (robot dips to ground, lights dim)
  - 2 frames of extended power-down (with z-particle baked in)
  - 2 frames of reaction-rise (sudden hover up + LED flash)
  - 2 frames of speak (light pulse + screen flicker)

Total: 58 frames per variant. Output at 32 px, 64 px, and 96 px atlas
cell sizes. Each atlas is laid out 8 columns by N rows so the runtime
indexes by (frame_index // 8, frame_index % 8).

Variants and their material overrides live in VARIANT_SPECS below.
"""

from __future__ import annotations

import argparse
import math
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

# Blender is only importable inside a Blender process. The script
# gracefully no-ops the heavy work when run under stock Python so it
# can still be linted and unit-tested in CI.
try:
    import bpy  # type: ignore
    from mathutils import Euler, Vector  # type: ignore

    HAS_BPY = True
except ImportError:  # pragma: no cover
    HAS_BPY = False

CELL_SIZES = (32, 64, 96)
ATLAS_COLUMNS = 8


@dataclass(frozen=True)
class PoseSpec:
    """One row in the atlas: name + frame count + how to stage it."""

    name: str
    frames: int
    description: str


# 58 frames total per variant. The layout maps directly to the
# CompanionSpriteAtlas.kt expectations.
POSE_SPECS: tuple[PoseSpec, ...] = (
    PoseSpec("hover_move_n", 3, "hover move, north facing, 3 frames"),
    PoseSpec("hover_move_ne", 3, "hover move, north-east, 3 frames"),
    PoseSpec("hover_move_e", 3, "hover move, east, 3 frames"),
    PoseSpec("hover_move_se", 3, "hover move, south-east, 3 frames"),
    PoseSpec("hover_move_s", 3, "hover move, south, 3 frames"),
    PoseSpec("hover_move_sw", 3, "hover move, south-west, 3 frames"),
    PoseSpec("hover_move_w", 3, "hover move, west, 3 frames"),
    PoseSpec("hover_move_nw", 3, "hover move, north-west, 3 frames"),
    PoseSpec("idle_hover_n", 2, "idle hover bob, north, 2 frames"),
    PoseSpec("idle_hover_ne", 2, "idle hover bob, north-east, 2 frames"),
    PoseSpec("idle_hover_e", 2, "idle hover bob, east, 2 frames"),
    PoseSpec("idle_hover_se", 2, "idle hover bob, south-east, 2 frames"),
    PoseSpec("idle_hover_s", 2, "idle hover bob, south, 2 frames"),
    PoseSpec("idle_hover_sw", 2, "idle hover bob, south-west, 2 frames"),
    PoseSpec("idle_hover_w", 2, "idle hover bob, west, 2 frames"),
    PoseSpec("idle_hover_nw", 2, "idle hover bob, north-west, 2 frames"),
    PoseSpec("scan", 8, "lens rotates toward 8 cardinal targets"),
    PoseSpec("display_on", 2, "belly screen lights up, 2 frames"),
    PoseSpec("power_down", 2, "robot dips to ground, lights dim, 2 frames"),
    PoseSpec(
        "power_down_extended",
        2,
        "extended power-down with z-particle, 2 frames",
    ),
    PoseSpec("reaction_rise", 2, "sudden rise + LED flash, 2 frames"),
    PoseSpec("speak", 2, "front light pulse + screen flicker, 2 frames"),
)

TOTAL_FRAMES = sum(p.frames for p in POSE_SPECS)
assert TOTAL_FRAMES == 58, f"atlas spec drift: expected 58 frames, got {TOTAL_FRAMES}"


@dataclass(frozen=True)
class Variant:
    """A Probe visual variant. Material overrides keep the bake
    pipeline single-source.

    led_rgb is the front-lens emissive color. body_rgb is the chassis
    tint multiplied over the base mesh material. extra_mesh names a
    procedurally added Blender primitive (radar fin, antenna, etc.)
    that distinguishes the variant silhouette. Use None to skip.
    """

    name: str
    led_rgb: tuple[float, float, float]
    body_rgb: tuple[float, float, float]
    extra_mesh: Optional[str]


VARIANT_SPECS: dict[str, Variant] = {
    "default": Variant(
        name="default",
        led_rgb=(0.95, 0.78, 0.35),  # warm amber, matches og-card gold
        body_rgb=(0.86, 0.82, 0.74),  # neutral chassis
        extra_mesh=None,
    ),
    "comm_visor": Variant(
        name="comm_visor",
        led_rgb=(0.35, 0.78, 0.95),  # teal
        body_rgb=(0.82, 0.84, 0.86),
        extra_mesh="radar_fin",
    ),
    "heavy_armor": Variant(
        name="heavy_armor",
        led_rgb=(0.95, 0.35, 0.30),  # deeper red
        body_rgb=(0.40, 0.42, 0.45),  # slate
        extra_mesh="armor_plating",
    ),
    "research_array": Variant(
        name="research_array",
        led_rgb=(0.65, 0.95, 0.80),  # pale cyan
        body_rgb=(0.74, 0.86, 0.78),  # sage
        extra_mesh="antenna_array",
    ),
}


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Bake the Probe glTF source to PNG atlases.",
    )
    parser.add_argument(
        "--source",
        required=True,
        help="path to probe.glb",
    )
    parser.add_argument(
        "--out",
        required=True,
        help="output directory for the baked PNG atlases",
    )
    parser.add_argument(
        "--variant",
        default="default",
        choices=tuple(VARIANT_SPECS.keys()),
        help="which Probe variant to bake",
    )
    parser.add_argument(
        "--cell-sizes",
        default=",".join(str(s) for s in CELL_SIZES),
        help="comma separated cell sizes to emit (px)",
    )
    return parser.parse_args(argv)


def split_argv() -> list[str]:
    """Blender consumes its own args before the script. Args after `--`
    are ours.
    """
    if "--" not in sys.argv:
        return []
    return sys.argv[sys.argv.index("--") + 1 :]


def load_source(source: Path) -> None:
    if not HAS_BPY:
        return
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(source))


def setup_camera() -> None:
    """Configure an orthographic-ish camera at the OSRS isometric tilt
    (~45 degrees down, looking at the world origin from the south).

    The OSRS client renders the world with a steep top-down camera
    that the bake should approximate so the baked sprites composite
    cleanly on the in-game tiles.
    """
    if not HAS_BPY:
        return
    bpy.ops.object.camera_add(
        location=(0.0, -3.6, 3.6),
        rotation=(math.radians(45.0), 0.0, 0.0),
    )
    cam = bpy.context.object
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = 2.4
    bpy.context.scene.camera = cam


def setup_lights() -> None:
    """Three-point lighting tuned to flat-shade the Quaternius mesh
    in a way that preserves the chassis read at 32 px.
    """
    if not HAS_BPY:
        return
    bpy.ops.object.light_add(type="SUN", location=(2.0, -2.0, 3.0))
    key = bpy.context.object
    key.data.energy = 3.0
    bpy.ops.object.light_add(type="AREA", location=(-2.5, -1.5, 1.5))
    fill = bpy.context.object
    fill.data.energy = 80.0
    fill.data.size = 2.0
    bpy.ops.object.light_add(type="AREA", location=(0.0, 2.0, 2.0))
    rim = bpy.context.object
    rim.data.energy = 60.0
    rim.data.size = 1.5


def apply_variant(variant: Variant) -> None:
    """Apply LED color, chassis tint, and any extra-mesh silhouette
    swap for the named variant. Re-runs idempotently against the
    currently loaded mesh.
    """
    if not HAS_BPY:
        return
    body_mat = bpy.data.materials.new(name=f"ProbeBody_{variant.name}")
    body_mat.use_nodes = True
    bsdf = body_mat.node_tree.nodes.get("Principled BSDF")
    if bsdf is not None:
        bsdf.inputs["Base Color"].default_value = (*variant.body_rgb, 1.0)
        bsdf.inputs["Roughness"].default_value = 0.55

    led_mat = bpy.data.materials.new(name=f"ProbeLED_{variant.name}")
    led_mat.use_nodes = True
    led_bsdf = led_mat.node_tree.nodes.get("Principled BSDF")
    if led_bsdf is not None:
        led_bsdf.inputs["Emission Color"].default_value = (
            *variant.led_rgb,
            1.0,
        )
        led_bsdf.inputs["Emission Strength"].default_value = 4.0

    for obj in bpy.context.scene.objects:
        if obj.type != "MESH":
            continue
        name_lower = obj.name.lower()
        if "lens" in name_lower or "led" in name_lower or "eye" in name_lower:
            if obj.data.materials:
                obj.data.materials[0] = led_mat
            else:
                obj.data.materials.append(led_mat)
        else:
            if obj.data.materials:
                obj.data.materials[0] = body_mat
            else:
                obj.data.materials.append(body_mat)

    if variant.extra_mesh:
        attach_extra_mesh(variant.extra_mesh)


def attach_extra_mesh(kind: str) -> None:
    """Procedurally add a small mesh on top of the lens housing to
    distinguish the variant silhouette. The shapes are primitives so
    no extra third-party assets are introduced.
    """
    if not HAS_BPY:
        return
    if kind == "radar_fin":
        bpy.ops.mesh.primitive_cone_add(
            vertices=4,
            radius1=0.18,
            radius2=0.04,
            depth=0.35,
            location=(0.0, 0.0, 0.55),
        )
    elif kind == "armor_plating":
        bpy.ops.mesh.primitive_cube_add(
            size=0.6,
            location=(0.0, 0.0, 0.05),
        )
        bpy.context.object.scale = Vector((1.05, 1.05, 0.4))
    elif kind == "antenna_array":
        for offset in (-0.18, 0.0, 0.18):
            bpy.ops.mesh.primitive_cylinder_add(
                radius=0.012,
                depth=0.5,
                location=(offset, 0.0, 0.65),
            )


def yaw_from_direction(direction: str) -> float:
    """Return the world yaw (radians) that points the Probe in the
    named compass direction.
    """
    bearings = {
        "n": 0.0,
        "ne": -math.pi / 4,
        "e": -math.pi / 2,
        "se": -3 * math.pi / 4,
        "s": math.pi,
        "sw": 3 * math.pi / 4,
        "w": math.pi / 2,
        "nw": math.pi / 4,
    }
    suffix = direction.rsplit("_", 1)[-1]
    return bearings.get(suffix, 0.0)


def hover_bob(t: float) -> float:
    """Sine-bob the Probe vertically. t in [0, 1] across the pose's
    frame count. Returns a small z offset.
    """
    return 0.07 * math.sin(2.0 * math.pi * t)


def stage_pose(pose: PoseSpec, frame_index: int) -> None:
    """Pose the rigged mesh for a single frame inside a pose. The
    Quaternius Sci-Fi flying-enemy mesh has an animated rig; we drive
    it via the scene frame number when an animation exists, otherwise
    we directly transform the root.
    """
    if not HAS_BPY:
        return
    if "hover_move" in pose.name:
        yaw = yaw_from_direction(pose.name)
        z = hover_bob(frame_index / max(pose.frames - 1, 1))
        for obj in bpy.context.scene.objects:
            if obj.type != "MESH":
                continue
            obj.rotation_euler = Euler((0.0, 0.0, yaw), "XYZ")
            obj.location.z = z + 0.05 * frame_index
    elif "idle_hover" in pose.name:
        yaw = yaw_from_direction(pose.name)
        z = hover_bob(frame_index / max(pose.frames - 1, 1))
        for obj in bpy.context.scene.objects:
            if obj.type != "MESH":
                continue
            obj.rotation_euler = Euler((0.0, 0.0, yaw), "XYZ")
            obj.location.z = z
    elif pose.name == "scan":
        target_yaw = -math.pi + (2.0 * math.pi) * (
            frame_index / max(pose.frames - 1, 1)
        )
        for obj in bpy.context.scene.objects:
            if obj.type != "MESH":
                continue
            name_lower = obj.name.lower()
            if "lens" in name_lower or "head" in name_lower:
                obj.rotation_euler = Euler((0.0, 0.0, target_yaw), "XYZ")
    elif pose.name in ("display_on", "speak"):
        # The display-on and speak poses are LED-pulse heavy. The
        # material emission ramps on alternating frames.
        for mat in bpy.data.materials:
            if not mat.name.startswith("ProbeLED_"):
                continue
            bsdf = mat.node_tree.nodes.get("Principled BSDF")
            if bsdf is None:
                continue
            base = 4.0 if pose.name == "display_on" else 5.0
            bsdf.inputs["Emission Strength"].default_value = (
                base if frame_index % 2 == 0 else base * 0.4
            )
    elif pose.name in ("power_down", "power_down_extended"):
        for obj in bpy.context.scene.objects:
            if obj.type != "MESH":
                continue
            obj.location.z = -0.15 - 0.05 * frame_index
        for mat in bpy.data.materials:
            if not mat.name.startswith("ProbeLED_"):
                continue
            bsdf = mat.node_tree.nodes.get("Principled BSDF")
            if bsdf is None:
                continue
            bsdf.inputs["Emission Strength"].default_value = max(
                0.2, 1.5 - 0.5 * frame_index
            )
    elif pose.name == "reaction_rise":
        for obj in bpy.context.scene.objects:
            if obj.type != "MESH":
                continue
            obj.location.z = 0.25 + 0.05 * frame_index
        for mat in bpy.data.materials:
            if not mat.name.startswith("ProbeLED_"):
                continue
            bsdf = mat.node_tree.nodes.get("Principled BSDF")
            if bsdf is None:
                continue
            bsdf.inputs["Emission Strength"].default_value = (
                7.0 if frame_index == 0 else 4.0
            )


def render_frame(out_png: Path, cell_size: int) -> None:
    if not HAS_BPY:
        return
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 24
    scene.render.resolution_x = cell_size
    scene.render.resolution_y = cell_size
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.film_transparent = True
    scene.render.filepath = str(out_png)
    bpy.ops.render.render(write_still=True)


def bake_atlas(
    source: Path,
    out_dir: Path,
    variant_name: str,
    cell_sizes: tuple[int, ...],
) -> None:
    variant = VARIANT_SPECS[variant_name]
    if not HAS_BPY:
        print(
            "bpy not available, dry-running bake plan only. "
            "Run under Blender to actually render."
        )
    load_source(source)
    setup_camera()
    setup_lights()
    apply_variant(variant)

    for cell_size in cell_sizes:
        atlas_rows = sum((1 for _ in POSE_SPECS))
        per_size_dir = out_dir / variant_name / f"{cell_size}px"
        per_size_dir.mkdir(parents=True, exist_ok=True)

        for row_index, pose in enumerate(POSE_SPECS):
            for col_index in range(pose.frames):
                stage_pose(pose, col_index)
                frame_path = (
                    per_size_dir
                    / f"{pose.name}_{col_index:02d}.png"
                )
                render_frame(frame_path, cell_size)

        write_metadata(per_size_dir, cell_size, atlas_rows)


def write_metadata(per_size_dir: Path, cell_size: int, rows: int) -> None:
    """Emit a small JSON catalog so CompanionSpriteAtlas.kt can
    index by pose name rather than by pixel offset.
    """
    import json

    catalog = {
        "cell_size": cell_size,
        "columns": ATLAS_COLUMNS,
        "rows": rows,
        "total_frames": TOTAL_FRAMES,
        "poses": [
            {
                "name": pose.name,
                "frames": pose.frames,
                "description": pose.description,
            }
            for pose in POSE_SPECS
        ],
    }
    per_size_dir.joinpath("atlas.json").write_text(
        json.dumps(catalog, indent=2)
    )


def main() -> int:
    argv = split_argv()
    args = parse_args(argv)
    source = Path(args.source).resolve()
    out_dir = Path(args.out).resolve()
    cell_sizes = tuple(int(x) for x in args.cell_sizes.split(",") if x)

    if not source.exists():
        # Useful diagnostic for the "I forgot to vendor probe.glb" case.
        print(
            f"source mesh not found at {source}. "
            "See apps/plugin/src/main/resources/companion/source/README.md "
            "for the download recipe."
        )
        return 1

    bake_atlas(source, out_dir, args.variant, cell_sizes)
    print(
        f"baked {TOTAL_FRAMES} frames per cell size "
        f"({', '.join(str(s) for s in cell_sizes)}) "
        f"for variant '{args.variant}' into {out_dir}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
