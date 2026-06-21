# Companion Visual Bible + 3D Source Pipeline

Status: greenlit, ready to bake against
Owner: product (Tom), art direction (agent r-loop-mplus15), pipeline (agent r-loop-mplus16)
Linear: [RAI-64](https://linear.app/rainnworks/issue/RAI-64/companion-visual-bible-asset-commissioning-plan) (original), [RAI-71](https://linear.app/rainnworks/issue/RAI-71) (3D pivot)
Related: [COMPANION_3D_SOURCE.md](./COMPANION_3D_SOURCE.md), [EMBODIED_COMPANION.md](./EMBODIED_COMPANION.md), [SOCIAL_COMPANION.md](./SOCIAL_COMPANION.md), [../marketing/BRAND_VOICE.md](../marketing/BRAND_VOICE.md), [../research/osrs-wiki/licensing.md](../research/osrs-wiki/licensing.md)

**2026-06-21 pivot.** The original four hand-drawn starter forms (Wiki
Veteran hooded humanoid, Fox, Wisp, Golem) and the USD 5000+ artist
commission budget in section 6 are RETIRED. The companion is now a
floating robot ("Probe") sourced from a CC0 3D mesh by Quaternius and
baked offline into 2D sprite atlases via Blender. The pose catalog,
hub-safety analysis, marketing-shot list, and reversibility logic in
this doc remain valid; section 2 (forms) and section 6 (pipeline) are
rewritten in place. The full sourcing rationale is in
[COMPANION_3D_SOURCE.md](./COMPANION_3D_SOURCE.md).

This is the doc the asset commission goes against and the engineering
agent reads to know what sprite frames to render. It is the single
source of truth for everything visual about Tibbly the embodied
companion. Marketing, plugin engineering, and the artists we pay all
work to this document.

## 1. Tom's directive captured

Tom greenlit the full embodied companion build in two phrases. They
are the design floor. Every choice in this document gets measured
against them.

> "the marketing grab. It should make you want to pay."

> "alive. And should feel it."

Anchor those phrases. If a frame, a behaviour, a typeface, a vendor
choice does not push the experience toward "I want to pay for that"
and "that is alive", it is the wrong choice. The companion is not a
chat avatar that happens to walk. It is a presence the player wants in
their world enough to keep paying for it.

A practical filter that follows from those two phrases. Show a frame
of work to a stranger who plays OSRS and does not know Tibbly. If
they say "where do I get that", we are inside the directive. If they
say "neat" we are outside it. We commission against the first answer
or we slip the date.

## 2. One visual, four personalities (Probe + voices)

The player gets **one visual** (the floating Probe) and picks **one
of four personalities** on first install. The personality choice is
backed by a chassis-tint variant of the same Probe mesh so two
players' companions are visually distinguishable at a glance, but the
silhouette and animations are identical.

Personality picks one of four archetypes per
[EMBODIED_COMPANION.md section 5](./EMBODIED_COMPANION.md#5-personality-and-memory-system).
A separate voice-override dial lets the player put any archetype on
any personality tint, but the default pairing is set below because
most players will accept the default.

**Audit follow-up (RAI-72 / RAI-73, 2026-06-21).** The earlier framing
called these "four starters", which a cold OSRS player reads as "four
different companions to choose from". The reality is one visual with
four voices. This section is the source of truth: any plugin or
marketing copy that calls them "starters" is wrong. Use "personality"
or "voice". The legacy `Starter` enum in
[CompanionConfig.kt](../../apps/plugin/src/main/kotlin/co/rowm/osrsllm/companion/CompanionConfig.kt)
is the persisted-config key only; the player-facing label is
"Companion personality".

All four variants share the same Quaternius CC0 base mesh (a small
floating bot with a single front-facing lens and a stub antenna,
roughly Fallout-Eyebot silhouette). The variants differ in LED color,
chassis tint, and a small attached primitive (radar fin / armor
plating / antenna array) that distinguishes the silhouette at
glance-distance. All four are baked from one source through the
Blender pipeline in [section 6](#6-the-3d-source-and-bake-pipeline)
without re-sourcing any additional third-party asset.

The recommended primary variant that ships at M-COMP-1 is **Probe
(default)**, paired with the dry wiki nerd archetype. The other three
variants ship in the same PR because they cost only a parameter
override; the variant-pipeline follow-up referenced in
[COMPANION_3D_SOURCE.md section 6](./COMPANION_3D_SOURCE.md#6-the-four-variants)
covers any silhouette adjustments that emerge from dogfood.

### 2.1 Probe (default, ships first)

**Visual identity.** A small floating bot, roughly spherical chassis,
single front-facing lens, stub antenna on top. Rendered at 32 pixels
tall at @1x after the Blender bake. The source mesh is Quaternius
flat-shaded low poly so the rendered sprite reads as a clean game
asset rather than photoreal hardware. Warm amber LED at the lens
(roughly `#f3c75a`, the og-card.svg gold). Neutral chassis tint that
keeps the bot legible against both bright (Falador) and dim
(Brimhaven dungeon) tile palettes.

Silhouette test passes. A spherical bot with a single bright lens at
the front is the trope-correct "small floating companion" read at
32x32. The lens is the silhouette anchor: it is the thing a player
describes when they tell a friend what their Tibbly looks like.

The art reads as "obviously not a Jagex asset" at one glance. There
is no Jagex precedent for a floating modern robot in the OSRS world.
A robot in Falador trivially clears the
[hub-safety bar](#8-hub-and-jagex-risk).

**Personality archetype paired by default.** Dry wiki nerd. The
voice the [brand voice doc](../marketing/BRAND_VOICE.md) leads with:
calm, lore-literate, casually authoritative, ribs you gently when
you ask the third time how to get to Lumbridge. The form fits the
voice because a methodical scanning probe with a steady amber light
is the trope-correct silhouette for "the friend who already read
the wiki".

**Why this variant pairs with this personality.** A small scanning
probe can do every pose in the atlas (hover, scan, display facts on
its belly screen, power down to sleep, react with a sudden rise) in
a way a humanoid cannot fake at 32 px. Every reactive animation in
[EMBODIED_COMPANION.md section 4](./EMBODIED_COMPANION.md#4-visual-and-presence-design)
maps cleanly to a Probe pose.

**Anti-examples (what this is NOT).**

- Not the Fallout Eyebot itself. We use the silhouette as inspiration;
  the rendered Probe is flat-shaded low-poly, not Bethesda's photoreal
  asset.
- Not EVE from Wall-E. Different proportions, no curved white shell,
  no segmented face plate.
- Not Wheatley from Portal 2. Different silhouette, no single round
  faceplate with thick handlebars.
- Not BB-8. Different proportions, no rolling base, no two-tone shell.
- Not a hostile combat drone. No weapon attachments, warm LED, soft
  bob in idle hover, body language reads as curious not menacing.

**Reference art (north stars for the bake).**

- Quaternius's Sci-Fi Essentials Kit on
  ([opengameart.org/content/sci-fi-essentials-kit](https://opengameart.org/content/sci-fi-essentials-kit))
  for the source mesh and the flat-shaded palette.
- The Fallout Eyebot silhouette
  ([fallout.fandom.com/wiki/Eyebot](https://fallout.fandom.com/wiki/Eyebot))
  as the spherical-with-lens trope reference (silhouette only, we
  do not use Bethesda's asset).
- The Star Wars Probe Droid silhouette as the floating-with-antenna
  trope reference.
- Hyper Light Drifter's flat-shaded vector palette for the rendered
  sprite color discipline.

### 2.2 Probe - comm visor (soft confused friend)

**Visual identity.** The Probe base mesh tinted with a cool teal LED
and a small radar fin attached to the top of the lens housing. The
chassis stays neutral-warm so the variant reads as the same Probe in
a different mood, not as a different bot. The radar fin is the
silhouette anchor: it gives the Probe an upward sweep that
distinguishes it from the default at glance-distance.

**Personality archetype paired by default.** Soft confused friend.
The voice asks more questions than it answers. Helpful but tentative.
Tom's note: "the soft confused friend is the option for players who
want the companion to feel like a younger sibling who is genuinely
curious about the game".

**Why this variant pairs with this personality.** A scanning probe
with a tilted radar fin reads as "I am listening, I am uncertain, I
am asking". The teal LED softens the read further. The same scan
pose plays on every variant, but on the comm-visor variant the lens
sweeps wider so the body language reads as inquisitive rather than
methodical.

**Anti-examples.**

- Not a hostile UAV. No targeting reticle, no aggressive radar sweep.
- Not a CCTV camera. The fin is small, soft-edged, and bobs with
  the idle cycle so it never reads as static surveillance.

### 2.3 Probe - heavy armor (sardonic veteran)

**Visual identity.** The Probe base mesh tinted slate grey with
visible armor-plating decals on the chassis and a deeper red LED.
The plating is the silhouette anchor: the chassis reads slightly
chunkier than the default, like a probe that has seen things and
got reinforced. The deeper red LED reads as wisdom-aged rather than
hostile because the bot otherwise hovers and scans like the others.

**Personality archetype paired by default.** Sardonic veteran. Small
flame, dry wit, sees a lot, says little. The heavy-armor Probe talks
in short sentences and lets the player fill the silence. It is the
quietest of the four variants by design.

**Why this variant pairs with this personality.** A weather-beaten
probe with armor plating reads as "I have done this before". The
sardonic veteran says little; the heavy-armor Probe scans slowly
and pulses its lens calmly; both feel grown-up.

**Anti-examples.**

- Not a battle drone. No weapon attachments, no targeting indicators.
- Not a riot bot. The plating is decorative, not aggressive.
- Not a Terminator HK silhouette. The bot still hovers warmly and
  bobs in idle. The plating is a tint not a stance.

### 2.4 Probe - research array (earnest helper)

**Visual identity.** The Probe base mesh tinted sage green with a
small antenna array swapped in for the radar fin and a pale-cyan
LED. The antenna array is the silhouette anchor: three thin antenna
posts on top of the lens housing that bob slightly out of phase
during the idle cycle so the bot reads as "thinking, gathering".

**Personality archetype paired by default.** Earnest helper. Warm,
sincere, never sarcastic, never preachy. The opposite of the
heavy-armor variant. Where heavy-armor is dry, research-array is
direct. Where heavy-armor says little, research-array offers a small
extra of detail.

**Why this variant pairs with this personality.** A research-coded
Probe with antennae that twitch as it scans reads as "I am gathering
information for you". Earnest helper is the archetype players pick
when they want a companion that will never make them feel dumb. The
sage palette and pale LED keep the read warm.

**Anti-examples.**

- Not a science-fiction sci-rig robot. Antennae are small and bob
  with the idle cycle, never extending into a spider-leg silhouette.
- Not a "smart assistant" cartoon with a question mark over its head.
  The Probe communicates through the speech bubble, not through
  cartoon thought icons.

## 3. The animation atlas, what frames each variant ships at MVP

The atlas is the same shape for all four Probe variants. The Blender
bake script in [section 6](#6-the-3d-source-and-bake-pipeline)
generates exactly this frame layout from the Quaternius source. The
runtime in `CompanionSpriteAtlas.kt` indexes frames by pose name
plus sub-index rather than by pixel offset so the catalog can move
without breaking the renderer.

Total per variant at MVP: 58 frames. Times four variants at full
launch: 232 frames, all baked from one CC0 source. Dimensions: 32 by
32 pixels at @1x, with @2x (64 px) and @3x (96 px) atlases generated
directly by the bake script (not by the runtime, because
nearest-neighbour upscaling at runtime introduces visible seams on
subpixel motion).

The pose names below match the resource paths the runtime expects.

### 3.1 Hover-move cycle, 8 directions, 3 frames each (24 frames)

Pose names: `hover_move_n`, `hover_move_ne`, `hover_move_e`,
`hover_move_se`, `hover_move_s`, `hover_move_sw`, `hover_move_w`,
`hover_move_nw`. 3 frames per direction = 24 frames. The bot tilts
forward in the direction of travel and bobs slightly up-down so the
hover-move reads as motion, not as a still bot teleporting between
tiles.

This is the frame budget that supports the smooth follow behaviour
in [EMBODIED_COMPANION.md section 4](./EMBODIED_COMPANION.md#4-visual-and-presence-design).
The runtime interpolates between the three keyframes per direction
based on the companion's velocity. At slow speed the cycle plays at
6 frames per second; at sprint speed the cycle plays at 12 frames
per second.

### 3.2 Idle hover cycle in each direction, 2 frames each (16 frames)

Pose names: `idle_hover_n`, ..., `idle_hover_nw`. 8 directions, 2
frames per direction. The two frames are the "bob up" and "bob down"
pose. Cycle plays at 1.5 frames per second. This is the canonical
floor-presence of the companion when nothing else is happening.
Without this, the companion looks like a still PNG. With it, the
companion looks alive.

### 3.3 Scan pose, 8 frames

Pose name: `scan`. The Probe's lens rotates toward an interest
target without moving its body. Used when the player examines an
item, when an NPC dialogue starts, when a milestone fires. 8 frames
sweeping through the cardinal+diagonal targets. The lens-rotate snap
is intentional: the snap is the visual beat that tells the player
"the Probe noticed".

### 3.4 Display-on pose, 2 frames

Pose name: `display_on`. The Probe's belly screen lights up to
display a fact (high-alch value, GP/hour, prayer requirement). The
two frames are "screen warm" and "screen flicker". Used during long
player idle (over 20 seconds) and when the companion is producing a
long answer (visible during the latency window before the speech
bubble opens). Replaces the original "read pose".

### 3.5 Power-down pose, 2 frames

Pose name: `power_down`. The Probe dips toward the ground and dims
its lens. The two frames are "dip" and "settled". Used during deeper
AFK (over 90 seconds without input). Distinct from `display_on`:
display-on implies the Probe is showing something, power_down
implies the Probe is waiting. Replaces the original "sit pose".

### 3.6 Reaction rise pose, 2 frames

Pose name: `reaction_rise`. The Probe rises sharply and flashes its
LED in reaction to a drop, a pet milestone, a quest completion, a
diary tier unlock. The two frames are "rise" and "settle". Plays
once on event, not on a cycle. Optional small particle pop scheduled
at the same tick by the runtime (handled outside the atlas).
Replaces the original "surprise pose".

### 3.7 Extended power-down pose, 2 frames

Pose name: `power_down_extended`. The Probe plays one extended
power-down at the 5-minute idle threshold with a small "zzz"
particle effect baked in. The two frames are "deep dip" and "still".
Plays once, not on a cycle. Replaces the original "yawn pose" and
keeps the same role: a small humanising beat that signals the
companion is bored, which is itself a personality cue. Never plays
during chat.

### 3.8 Speak pose, 2 frames

Pose name: `speak`. The Probe's front lens pulses and the belly
screen flickers in time with the speech reveal. Cycles at 4 frames
per second only during a speech-bubble reveal. Synced to the
per-character reveal in the bubble (see section 4). This is the
only frame in the atlas that the runtime cycles based on data
outside the companion's own state (the chat reveal cursor).

### 3.9 Atlas packing notes for the bake

The Blender bake script (`apps/plugin/scripts/bake-companion-atlas.py`)
emits one PNG per frame per cell size into
`apps/plugin/src/main/resources/companion/robot-default/<variant>/<size>px/`
with file names of the form `<pose>_<index>.png` (zero-padded). An
`atlas.json` per directory describes the pose names and frame counts
so `CompanionSpriteAtlas.kt` can resolve frames by name.

Total deliverables per variant: 58 PNG frames per cell size, 3 cell
sizes (32, 64, 96 px), plus one `atlas.json` per cell size. The bake
recipe lives at `apps/plugin/scripts/README.md`.

## 4. The speech bubble and chat panel visual system

The bubble is where the personality renders. The art is the carrier
but the words are the product. The bubble must read as Tibbly's
voice the instant it appears.

### 4.1 Speech bubble shape

A rounded-rectangle bubble anchored 12 pixels above the companion's
head. The bubble points down at the companion's head with a small
2-pixel-wide tail offset to the left of center. The bubble grows
horizontally first up to a max width of 240 pixels, then wraps to a
second line, then a third. Max three lines visible at once. Anything
longer truncates to the panel (see section 4.3).

The bubble is OSRS-chat-window-inspired but distinct. The OSRS chat
box uses dark brown and gold. The Tibbly bubble uses near-black
`#0f0a05` background with a `#3a2a16` 1px border and `#f3c75a`
typography (matches the existing og-card.svg system). The corner
radius is 6 pixels. There is a soft inner glow on the border so the
bubble does not feel cut from cardstock.

The bubble is not skinned per personality. The bubble is Tibbly's
voice surface; the chassis tint differentiates the Probe at
silhouette distance but the speech container is shared. The
typography carries the voice.

### 4.2 Type system inside the bubble

Body copy: Inter Tight at 12 pixels (the brand body font from
[../marketing/IA.md](../marketing/IA.md)). Line height 1.2. Word
spacing slightly wide so the bubble reads as "calm" not "chatbot".

Structured advice (prayer name, world coord, gp value, GE price)
sets in IBM Plex Mono at 11 pixels with a slightly warmer color
(`#ffe7a8`). The mono switch is the visual cue that the companion
is quoting a fact, not making conversation. The Inter Tight to Plex
Mono switch is borrowed from the og-card.svg conventions and used
consistently across the marketing site, the dashboard, and the
in-game bubble.

No emoji in the bubble by default. Per
[BRAND_VOICE.md](../marketing/BRAND_VOICE.md), the bubble may include
one contextual emoji per roughly 50 replies. The classifier from
[EMBODIED_COMPANION.md section 5](./EMBODIED_COMPANION.md#5-personality-and-memory-system)
runs the same emoji policy on outputs before they render.

### 4.3 Long-form chat panel

If a reply exceeds three lines, the bubble shows the first three
lines plus a "read more" affordance. Clicking the bubble (or any
visible part of the companion) opens an anchored floating panel
attached to the companion. The panel is 400 pixels wide, expands
downward up to 480 pixels tall, and detaches into a draggable Swing
component when the player grabs its title bar.

The panel uses the same OSRS-chat-window frame the og-card.svg
shows: brown gradient body, gold border, mono title bar. Inside the
panel the same Inter Tight / Plex Mono pairing is used.

The panel is anchored by default. Detached is a power-user state.
Most players never detach. The first-install onboarding shows the
anchored state only.

### 4.4 Animation timing

Bubble fade-in: 200 ms ease-out. Bubble fade-out: 150 ms ease-in.

Text reveal inside the bubble: 24 characters per second. This is
deliberately faster than human reading speed because OSRS players
read at high speed during gameplay. A slower reveal reads as a
chatbot affectation. The classifier confirms the line is safe
before the reveal starts so the reveal is never interrupted.

Speak-pose cycle: 4 frames per second, exactly the duration of the
reveal. The companion's mouth closes the tick after the last
character reveals. This is the only synchronisation between the
sprite and the chat data the runtime maintains.

Auto-dismiss: bubbles under 40 characters auto-dismiss after 5
seconds. Longer bubbles stay until the player clicks them or until a
new bubble takes their place.

### 4.5 Accessibility

A high-contrast option swaps the bubble background to pure black,
the border to pure white, and the typography to a higher-contrast
gold. Available in the dashboard config and surfaceable in-game via
`/tibbly contrast high`. The high-contrast option respects the same
type system; only colors swap.

Text reveal speed is configurable on the same in-game command
(`/tibbly reveal slow`, `/tibbly reveal off`). When reveal is off,
the full line appears immediately; the speak-pose plays for the
average duration the line would have revealed at default speed.

Speech bubbles render at a minimum effective text size scaled by
the player's OSRS client zoom. At maximum zoom-out the bubble
typography never falls below 11 pixels of effective screen size.

## 5. Behavioural visual language

The whole point of the form is that you cannot do facial expression
at 32 by 32 pixels and have it read. Personality at this scale is
body language. The companion communicates through where it looks,
how it holds itself, and what it does in the seconds between
sentences.

### 5.1 Looking at what you look at

When the player examines an object, hovers a quest item, or starts
an NPC dialogue, the companion snaps its head toward the player's
target on the next tick. At 32 by 32 we cannot draw an eyeline. We
do the head-turn snap and the look-at frame. The player reads the
snap as the companion noticing.

This is the most-played frame in the whole atlas. The look-at frames
in section 3.3 fire on every examine, every hover-rest over 600
ms, every NPC dialogue start. We expect tens of look-at fires per
session for an engaged player. The snap reads as "the companion is
present" without any words being spent.

### 5.2 Presence-but-not-speaking (silence with character)

When the companion is silent, it is not still. The idle cycle
breath-in / breath-out from section 3.2 plays continuously. Every
roughly 8 seconds, with jitter to avoid a metronome read, the
companion plays a small head-turn to a random nearby tile, looks for
1.2 seconds, then returns. This is the "looking around" animation
from [EMBODIED_COMPANION.md section 4](./EMBODIED_COMPANION.md#4-visual-and-presence-design)
expressed in the atlas. The behaviour is the same across forms; only
the head pose differs.

If the player has been still for more than 20 seconds, the
companion sits cross-legged and reads (read pose, section 3.4). The
runtime does not announce this. The player notices the companion is
reading when they next look. The discovery is the value.

If the player has been still for more than 90 seconds, the
companion moves to the sit pose (section 3.5). The progression
read-then-sit is itself a personality cue. The companion is patient
but not pretending to work.

### 5.3 Unhappy or disagreeing

We cannot do a frown at 32 by 32. We do body language.

When the companion declines a request (PG-13 refusal, content
boundary, "can't help with that"), the speak pose plays once, the
companion's head tilts away from the player, the satchel hand goes
to the hip. The refusal line renders in the bubble. The hip-hand
holds for the duration of the refusal. The classifier from
[EMBODIED_COMPANION.md section 5](./EMBODIED_COMPANION.md#5-personality-and-memory-system)
chooses the refusal line; the visual is the same line every time.

When the companion disagrees with a player's gear or strategy
opinion (calmly, in voice), the companion does not change pose.
The speech-bubble carries the disagreement; the body stays neutral.
This is on purpose: disagreement at the body-language level reads
as judgement, and we are not a judgemental product.

### 5.4 Remembering

When the companion surfaces something it remembers about the player,
a distinct "thinking" idle pose plays for 800 ms before the speech
bubble fades in. For the humanoid forms (Wiki Veteran, Golem) the
pose is a small head-tilt with a finger to the chin. For the Fox,
the pose is ears-forward with the tail still. For the Wisp, the
glow brightens by 15 percent for the same window.

This is the only place in the atlas where the form-specific staging
intentionally diverges. The behaviour is the same across forms (a
small pre-speech beat that says "I am about to surface a memory")
but the visual is form-specific so the gesture reads correctly.

The thinking pose plays only when the line is going to reference a
remembered fact. The runtime tags lines with a `memory` flag at
generation time so the renderer knows when to play the pose. False
positives are worse than false negatives here. Better to skip the
thinking pose than to play it when the line is not actually a
memory.

## 6. The 3D source and bake pipeline

This section replaces the original USD 5000+ artist commission plan.
The pivot is captured in [COMPANION_3D_SOURCE.md](./COMPANION_3D_SOURCE.md).
The vendor lists, brief templates, and interim Stable-Diffusion
placeholder in the previous version of this section are RETIRED.

The new pipeline has three steps: vendor a CC0 3D mesh, run a Blender
script offline to bake it to 2D PNG atlases, commit the PNG output.
Total marginal cost for all four variants is the bake time on Tom's
local machine (estimated under one hour for the full set).

### 6.1 The source

The Probe base mesh is the Quaternius Sci-Fi Essentials Kit, released
under CC0 1.0 Universal. The standalone flying-bot model from the kit
sits at <https://poly.pizza/m/lF3jeRJwiH>. The full pick rationale
including the four-criteria ranking and the rejected runners-up lives
in [COMPANION_3D_SOURCE.md sections 2 and 3](./COMPANION_3D_SOURCE.md#2-the-pick).

### 6.2 The bake

`apps/plugin/scripts/bake-companion-atlas.py` is a Blender Python
script that:

1. Loads the vendored `probe.glb` from
   `apps/plugin/src/main/resources/companion/source/`.
2. Sets up a perspective camera at the OSRS isometric tilt (~45
   degrees down) and three-point lighting tuned for flat-shaded
   chassis reads at 32 px.
3. Applies one of four variant material overrides (LED color,
   chassis tint, optional extra primitive for the silhouette swap).
4. For each of the 22 poses, stages the mesh per frame and renders
   one PNG per cell size (32, 64, 96 px).
5. Writes an `atlas.json` per output directory describing the pose
   names and frame counts.

Run it via:

```bash
blender --background --python apps/plugin/scripts/bake-companion-atlas.py \
    -- \
    --source apps/plugin/src/main/resources/companion/source/probe.glb \
    --out apps/plugin/src/main/resources/companion/robot-default \
    --variant default
```

The full recipe (install, vendor, bake all four variants, output
layout) lives at `apps/plugin/scripts/README.md`.

### 6.3 The runtime

`apps/plugin/src/main/kotlin/co/rowm/osrsllm/companion/CompanionSpriteAtlas.kt`
loads the baked PNGs at startup. When the PNGs are absent (a fresh
clone before the source is vendored, or a CI environment without the
binary assets), the atlas falls back to a `PlaceholderAtlas` that
paints a deterministic geometric Probe silhouette. The fallback
keeps the test suite green and lets the renderer ship a recognizable
stand-in before the real bake lands.

The pose catalog inside `CompanionSpriteAtlas.kt` is the canonical
list. The Blender script asserts the same 58-frame total to catch
spec drift between the doc, the bake, and the runtime.

### 6.4 The four variants from one source

The four variant labels in [section 2](#2-one-visual-four-personalities-probe--voices)
(default, comm visor, heavy armor, research array) are baked from the
same `probe.glb` by passing `--variant` to the script. The
differences (LED hue, chassis tint, optional radar fin / armor
plating / antenna array) are parameter overrides + small Blender
primitives composed at bake time. No second-party 3D asset is
introduced.

If a variant's silhouette does not land in dogfood, we adjust the
parameter overrides in the script and re-bake. The source mesh
stays unchanged.

### 6.5 Budget

Zero dollars in art spend. The Quaternius asset is CC0; the bake
runs locally. The original USD 4000 to USD 7000 commission budget
is freed for engineering, marketing, or BYOK provider credits.

If marketing-grade hero illustrations (the GIFs in section 7)
require higher fidelity than the bake output can provide, we
commission those as one-shot illustrations later. The in-plugin
atlas remains the Quaternius bake forever; the commission would only
touch marketing surface art.
## 7. The marketing surface for the companion

The companion is the marketing grab. The five magical-moment shots
below are the marketing site's hero loop, the pricing-page anchor,
and the social-share catnip. The shots are voice-driven (the words
in the bubble do the work), so the floating-robot pivot does not
change the script; only the silhouette in each frame changes from a
hooded humanoid to the Probe.

### 7.1 The hero-page magical moment (the 4-second loop)

The marketing site hero shows a 4-second loop in which the player
walks across the Lumbridge bridge with the Probe hovering beside
them. At second 2, the player hovers over an unidentified herb in
their inventory. The Probe plays the scan pose so the lens snaps
toward the inventory slot. A speech bubble appears that reads
"ranarr. high alch is 195. you want to clean this." at the Inter
Tight body size with the "195" set in IBM Plex Mono. The bubble
fades out at second 3.5. The loop restarts.

This is the loop an OSRS YouTuber screenshots. It is short enough
to be a GIF, busy enough to be readable, and specific enough that
a stranger thinks "I want that". Render at 720p, 24 fps, under
500 KB as a WebP loop. The og-card.svg gold and brown system
applies to the chrome around the loop (the bubble, the corner
attribution, the "watch live" link).

### 7.2 The five magical-moment GIFs

The five moments from [EMBODIED_COMPANION.md section 1](./EMBODIED_COMPANION.md#1-concept-articulation),
each rendered as a marketing GIF that lands on the marketing site
as scroll-revealed content under the hero loop.

1. **The recognition on log-in.** Player teleports to Lumbridge.
   The Probe is already power-down on the bridge railing (the
   `power_down` pose, used as a stationary spawn frame). On player
   arrival, the Probe plays `reaction_rise` to lift back into hover
   and says "welcome back. you said no spoilers on monkey madness
   2, still on?" The bubble plays at 4-second total length. The
   "remembering" thinking beat from section 5.4 plays in the 800 ms
   before the bubble opens.

2. **Quiet competence mid-task.** Player hovers a herb (the hero
   loop from section 7.1, isolated and slowed to 6 seconds).

3. **A shaped relationship.** Player asks "how do I get to Lumbridge
   again". Probe responds "home teleport. third time today. want me
   to set a quick shortcut?" The Probe plays the extended-power-down
   idle so the gentle ribbing reads as patient, not exasperated.

4. **Live boss tip.** Player is fighting Vorkath. The Probe plays
   the `scan` pose toward the boss's tile, then says "switch to
   protect magic. fireball spawn in two ticks." The "in two ticks"
   sets in IBM Plex Mono so the timing fact reads as data.

5. **Dialogue driven by current state.** Player walks into the
   bank. The Probe plays `scan` toward the bank booth, then says
   "you have 27 sharks and a full prayer pot stack. that's enough
   for two zulrah kills before you'd want to bank." The sentence is
   lifted from the existing
   [BRAND_VOICE.md sample exchange](../marketing/BRAND_VOICE.md#3-do-s)
   so the marketing copy matches the in-game voice exactly.

Each GIF is 6 seconds or less. Each is under 500 KB. Each renders
the default Probe at marketing-quality fidelity. Each is captioned
with one line of body copy underneath.

### 7.3 The first-install "ask Tibbly" CTA

When the player installs the plugin and starts RuneLite for the
first time, the Probe spawns at the Lumbridge spawn point next to
them and plays the `display_on` pose. After 3 seconds (long enough
that the player looks at the Probe and not at the chat), the speech
bubble fades in with "hi. i'm tibbly. pick how i look, then ask me
anything." The dashboard link is rendered as a clickable affordance
on the bubble.

This is the only bubble in the whole product that auto-opens. Every
other bubble fires from a player or state event. The first-install
bubble is the exception because the player has no prior context for
the companion existing. The bubble auto-dismisses after 8 seconds
or on first player input, whichever comes first.

The first-install CTA also fires the personality picker (the
four-personality chooser from section 2). The chooser renders inside
the chat panel, not as a modal overlay, so it feels like Tibbly
showing the player the four voices, not like a setup wizard.

## 8. Hub and Jagex risk

The floating-robot pivot makes this section trivially safe. Jagex
has no precedent for a modern floating robot inside the OSRS world,
so the silhouette is incapable of being mistaken for a Jagex asset.
The art declaration "this is not a Jagex asset" is automatic, not
load-bearing.

The decisions, made explicit.

The Probe sprite atlas is baked from a CC0 3D mesh (Quaternius,
attribution in `THIRD_PARTY_LICENSES.md`). The CC0 dedication waives
all rights to the maximum extent permitted by law, so we hold a
legally-unencumbered license to use, modify, and ship derivatives
forever. The atlas is not derivative of any Jagex sprite. It does
not use the OSRS palette, the OSRS character silhouette wedge, or
the OSRS UI chrome on the sprite itself.

We do NOT use OSRS Wiki sprites in the Probe. We do NOT hot-link
to oldschool.runescape.wiki/images. The `isForbiddenAssetUrl` guard
in `packages/osrs-assets/src` (from
[research/osrs-wiki/_SUMMARY.md](../research/osrs-wiki/_SUMMARY.md))
enforces this in code. The Probe atlas is loaded from the plugin's
packaged resources, not from any wiki URL.

We DO use RuneStar CC0 fonts and RuneLite BSD-2 icons for the UI
chrome around the Probe (the chat panel border, the
inventory-quoting glyphs in the speech bubble, the dashboard form
chooser). These are catalogued in `packages/osrs-assets/` and
licensed per [research/osrs-wiki/licensing.md](../research/osrs-wiki/licensing.md).
The bubble and panel use the existing chrome so the Probe feels
native to the RuneLite client. The Probe itself is in a deliberately
different visual register (floating robot in a medieval-fantasy
world) so a hub reviewer sees a distinct entity, not a clone of any
in-game NPC.

The "this is what a hub reviewer sees" frame test. Stand the Probe
next to Hans the greeter in the Lumbridge castle yard. The visual
difference is immediate and absurd: Tibbly is a small sci-fi robot
hovering above the cobbles. A reviewer can tell at a glance that
Tibbly is not depicted by Jagex. The frame test ships into the
dogfood handoff so the dogfooders can confirm the read.

The Quaternius attribution flows through three places per the
[runelite-hub submission checklist](../runelite-hub/SUBMISSION_CHECKLIST.md):
the plugin manifest `warning=` line cites
`THIRD_PARTY_LICENSES.md`; the LICENSE.txt next to the vendored
source carries the CC0 dedication; the plugin "About" panel surfaces
the author thank-you line.

For the marketing site the Probe always appears against backgrounds
that are clearly Tibbly territory, not in-game screenshots that crop
tightly around the companion. The hero loop is a deliberately
rendered scene; we do not stitch the Probe onto raw OSRS gameplay
footage as the marketing first impression. The compositing reads as
"ours" not as "borrowed".

The disclosure copy from [research/osrs-wiki/licensing.md](../research/osrs-wiki/licensing.md)
("this project is not affiliated with or endorsed by Jagex Ltd.")
renders in the marketing footer and in the in-game first-install
disclosure that the plugin shows alongside Tibbly's first speech
bubble.

## 9. Reversibility

The bible is opinionated but every piece is replaceable.

Asset commissions are one-shot in the sense that we pay for a
finished atlas and live with it. Personality archetype assignments
are runtime config (per
[EMBODIED_COMPANION.md section 5](./EMBODIED_COMPANION.md#5-personality-and-memory-system)).
If a personality does not resonate post-launch, we swap the archetype
assigned to it without re-commissioning art. The wiki-veteran
personality can ship paired with dry-wiki-nerd at launch and
re-paired with sardonic-veteran a month later based on dogfood
signal.

If a chassis-tint variant does not land in dogfood, we adjust the
parameter overrides in the bake script and re-bake. The source mesh
stays unchanged so we never re-commission art for a single Probe
variant.

If the whole bible turns out to be wrong (the visual is fine but
the player attachment does not form, or the hub blocks the
overlay), the plugin code paths and the closed backend are
unchanged. The renderer flag from
[EMBODIED_COMPANION.md section 8](./EMBODIED_COMPANION.md#8-risks)
turns the companion off cleanly. The sidebar chat panel remains.
The product as it stands today is still the product if the
companion is rolled back.

The visual bible is the floor we commission against. The floor
moves only when the dogfood data says it should. We do not
re-spec mid-commission, we do not negotiate the brief with the
artist after the first frame lands, and we do not let the artist
suggest creative re-interpretations of the silhouette test without
a written change order. The discipline is what protects the
artist's time and our spend.

The marketing copy on the site evolves continuously. The companion
visual bible evolves quarterly at most, gated by ship-quality
evidence that a change improves the recognition test or the
conversion funnel. The doc as it stands today is the working
copy that ships M-COMP-1 against.
