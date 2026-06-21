Companion sprite atlas drop location.

NOTE (2026-06-21, RAI-71 pivot + RAI-73 framing fix): the canonical
atlas layout is now the Probe variant tree under
companion/robot-default/<variant>/<size>px/<pose>_<index>.png owned by
CompanionSpriteAtlas.kt. The four legacy `Starter` enum values
(veteran, fox, wisp, golem) survive only as the persisted-config keys
backing the four player-facing personalities (wiki-veteran / soft-
confused-friend / sardonic-veteran / earnest-helper). The visual is
always the same Probe. See:
  - apps/plugin/docs/CONFIG.md (player-facing copy)
  - docs/product/COMPANION_VISUAL_BIBLE.md §2 (source of truth)

Legacy spec (kept for reference until the pre-pivot atlas files are
removed by the engineering follow-up):

The plugin looks for PNGs under:

  companion/<starter>/<state>_<direction>_<index>.png

Starter ids:  veteran, fox, wisp, golem
State ids:    idle, walking, look_at, read, sit, surprise, yawn, speak
Direction ids: south, south_west, west, north_west, north, north_east, east, south_east

Example: companion/veteran/walking_north_0.png is frame 0 of the
veteran's walking animation, facing north.

The plugin always falls back to a flat-coloured placeholder so the
renderer still works without any PNGs present. Commission artwork
lands in a separate PR (RAI-65 follow-up).
