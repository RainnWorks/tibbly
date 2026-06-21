Companion sprite atlas drop location.

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
