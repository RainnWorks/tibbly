# Companion Visual Bible + Asset Commissioning Plan

Status: greenlit, ready to commission against
Owner: product (Tom), art direction (agent r-loop-mplus15)
Linear: [RAI-64](https://linear.app/rainnworks/issue/RAI-64/companion-visual-bible-asset-commissioning-plan)
Related: [EMBODIED_COMPANION.md](./EMBODIED_COMPANION.md), [SOCIAL_COMPANION.md](./SOCIAL_COMPANION.md), [../marketing/BRAND_VOICE.md](../marketing/BRAND_VOICE.md), [../research/osrs-wiki/licensing.md](../research/osrs-wiki/licensing.md)

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

## 2. The four starter forms

The player picks one of four starter forms on first install, then
picks one of four personality archetypes (per
[EMBODIED_COMPANION.md section 5](./EMBODIED_COMPANION.md#5-personality-and-memory-system)).
Form and archetype are two independent dials. The default pairing for
each starter is set below because most players will accept the default
pairing, but the runtime config separates them so a player can put any
archetype on any form.

The recommended primary starter that ships at M-COMP-1 is **The Wiki
Veteran**, the hooded humanoid in a deliberately not-OSRS art style.
The other three follow at M-COMP-2 / M-COMP-3.

### 2.1 The Wiki Veteran (primary, ships first)

**Visual identity.** A small hooded humanoid roughly 32 pixels tall
at @1x. Modern flat-shaded vector look rendered to a pixel grid.
Three-tone shading per region (highlight, midtone, shadow). Hood up
by default, faceshadow heavy enough that the face reads as
"presence, not portrait". One tintable accent color on the inner
hood lining, the satchel strap, and the lantern handle. Player picks
the tint on first install. Default tint is a warm wiki-veteran amber
roughly `#f3c75a` (the existing og-card.svg gold), with five
alternates available (sage green, dusk violet, terracotta, deep teal,
chalk white).

Silhouette test passes. At 32 pixels tall the hood plus the
satchel plus a small lantern at the hip reads as a coherent
shape against the OSRS scene. The lantern is the silhouette
anchor: it is the thing a player describes when they tell a friend
what their Tibbly looks like.

The art reads as "not a Jagex NPC" at one glance. This is
load-bearing for [hub safety, see section 8](#8-hub--jagex-risk). No
2.5D isometric wedge, no chunky OSRS character outline, no
washed-out OSRS palette. The Wiki Veteran is on the same scene as
the player, but they look like they walked in from a different
illustration.

**Personality archetype paired by default.** Dry wiki nerd. The
voice the [brand voice doc](../marketing/BRAND_VOICE.md) leads
with: calm, lore-literate, casually authoritative, ribs you gently
when you ask the third time how to get to Lumbridge. The form
fits the voice because a hooded scholar holding a lantern is the
trope-correct silhouette for "the friend who already read the
wiki".

**Why this form pairs with this personality.** The hooded humanoid
can read books (the read pose lands without disbelief), can point
at a quest target (a finger-point silhouette is visible at 32x32 in
a way that a fox or a wisp cannot match), and can sit cross-legged
with the lantern set down beside them. Every reactive animation in
[EMBODIED_COMPANION.md section 4](./EMBODIED_COMPANION.md#4-visual-and-presence-design)
has a clean staging for this form.

**Anti-examples (what this is NOT).**

- Not a Jagex NPC. Not a re-skin of Hans, the Wise Old Man, or any
  named OSRS character. If a hub reviewer can name the inspiration,
  we redraw.
- Not a fantasy-generic hooded assassin. No daggers, no glowing eyes
  visible in the hood shadow, no skull motifs. We are friendly, not
  edgy.
- Not a cartoon Saturday-morning mascot. No giant eyes, no rounded
  baby proportions, no kawaii cheekspots.
- Not a Clippy with a hood. No exclamation-mark thought bubble, no
  flailing arms, no interruption posture.
- Not the Hollow Knight protagonist (close to our silhouette risk
  band). Different proportion, different mood, different palette,
  no horns.

**Reference art (north stars for the artist).**

- The cloaked traveller in Hyper Light Drifter
  ([gamedeveloper.com case study](https://www.gamedeveloper.com/business/the-ultra-modern-stylings-of-hyper-light-drifter))
  for the deliberately not-Jagex flat-shaded palette discipline.
- Slynyrd's human walk cycle tutorial
  ([slynyrd.com/blog/2024/5/24/pixelblog-50-human-walk-cycle](https://www.slynyrd.com/blog/2024/5/24/pixelblog-50-human-walk-cycle))
  for the 4-frame walk cycle skeleton we expect on top of an
  8-direction atlas.
- Pedro Medeiros's idle and walk animation principles
  ([lospec.com/pixel-art-tutorials/author/pedro-medeiros](https://lospec.com/pixel-art-tutorials/author/pedro-medeiros))
  as the canonical animation easing reference.
- Juanjo Marmol's pixel-art walk cycle character study on ArtStation
  ([artstation.com/artwork/qWZez](https://www.artstation.com/artwork/qWZez))
  for the cloth-flow on the hood and cape between frames.
- The wandering scholar character archetype from Pyre by
  Supergiant ([game press kit on supergiantgames.com](https://www.supergiantgames.com/games/pyre/))
  for the lantern-and-satchel silhouette logic.

### 2.2 The Fox (soft confused friend)

**Visual identity.** A small fox-like creature, four-legged, roughly
22 pixels tall at the shoulder. Three-tone shaded fur with two
accent patches the player can tint (ear tips and tail tip). Larger
ears than realistic, no anime eyes, no human face. Reads as "small
animal companion that visibly listens". One ear can twitch as part of
the idle cycle. The tail is the most expressive body part: low and
swishing means content, up and bristled means surprise, curled
around the body means sit-AFK.

The fox is the form most adjacent to OSRS pet silhouettes. We have
to draw the line carefully. It is not the Bloodhound, not the Baby
Mole, not the Vorki. Larger ears, longer legs, deliberately different
palette range. If the silhouette overlaps with any known OSRS pet at
fifty paces, we redraw.

**Personality archetype paired by default.** Soft confused friend.
The voice asks more questions than it answers. Helpful but tentative.
Tom's note: "the soft confused friend is the option for players who
want the companion to feel like a younger sibling who is genuinely
curious about the game".

**Why this form pairs with this personality.** A small animal that
tilts its head when puzzled communicates the soft-confused archetype
without needing a single word. The head-tilt at a weird item from
[EMBODIED_COMPANION.md section 4](./EMBODIED_COMPANION.md#4-visual-and-presence-design)
is the canonical Fox frame.

**Anti-examples.**

- Not the Bloodhound, Baby Mole, Vorki, or any RuneScape pet. If a
  veteran player squints and names the pet, we redraw.
- Not a Pokemon. No type-icon framing, no overworld sprite quoting
  Gen 5.
- Not a memey "doge". No tongue out, no "such friend".
- Not a generic stock fox vector. Asymmetric tail rest, ear nick on
  one side, one paw slightly forward in idle. Personality through
  asymmetry.

**Reference art.**

- The fox character studies on the Pixel Art Tutorials catalogue at
  [slynyrd.com/pixelblog-catalogue](https://www.slynyrd.com/pixelblog-catalogue)
  for body-shape proportion at small pixel scale.
- The Spiritfarer animal companions
  ([thunder-lotus.com/spiritfarer](https://thunder-lotus.com/spiritfarer/))
  for soft-confused-friend body language without humanising the face.
- Ori and the Will of the Wisps creature studies
  ([orithegame.com](https://www.orithegame.com/will-of-the-wisps/))
  for "small creature that listens" idle posture without facial
  detail.
- The fox companion in Tunic
  ([tunicgame.com](https://tunicgame.com/)) for proportion and palette
  restraint at small pixel scale.
- Sara Drasner's pixel-art animation breakdowns
  ([sarah.dev](https://sarah.dev/)) for the tail-swish loop pattern.

### 2.3 The Wisp (abstract option)

**Visual identity.** A moth-with-a-face wisp. Roughly 20 pixels of
glow plus 4 pixels of moth body suspended inside the glow. The
"face" is two tiny eye-pixels that the player will read as eyes only
because of the body language around them. No mouth. Wings flap on a
2-frame cycle. The glow is the player's tintable accent. Surrounding
fuzz around the wisp body fades with distance from the centre, so
the silhouette is soft, not crisp.

The wisp is the form most likely to read as "uncanny" if done
wrong, and the form most likely to read as "magical" if done right.
This is the discipline gate. The glow must not pulse on a regular
cycle. Regular pulse reads as a notification icon. The wisp glow
is hand-keyed to look like a moth's wings catching candlelight, not
like a load-spinner.

**Personality archetype paired by default.** Sardonic veteran.
Small flame, dry wit, sees a lot, says little. The wisp talks in
short sentences and lets the player fill the silence. It is the
quietest of the four starters by design.

**Why this form pairs with this personality.** The wisp's small
silhouette and refusal to humanise the face match a personality
that does not need to perform. The sardonic veteran says little; the
wisp shows little; both feel grown-up.

**Anti-examples.**

- Not a Pokemon Litwick. Different proportion, different palette, no
  candle base.
- Not a Spyro firefly. We are not bouncy. We are still.
- Not a fairy with anime eyes. The eyes are two pixels, not two
  ovals.
- Not a generic blue glowing orb. The moth body is visible inside
  the glow. The form has a body, not just a light.

**Reference art.**

- The lantern-spirit characters in Spiritfarer
  ([thunder-lotus.com/spiritfarer](https://thunder-lotus.com/spiritfarer/))
  for glow-around-a-body silhouette discipline.
- The Sealight character studies on Lospec
  ([lospec.com/gallery](https://lospec.com/gallery)) for moth and
  small-flame palette principles.
- Pedro Medeiros's particle and glow tutorials
  ([80.lv/articles/pixel-animation-tutorial-by-pedro-medeiros](https://80.lv/articles/pixel-animation-tutorial-by-pedro-medeiros))
  for the non-uniform glow cycle.
- The Hollow Knight grub and dreamer characters
  ([hollowknight.com](https://www.hollowknight.com/)) for the
  "glow with face" discipline at small scale.
- Studio Ghibli soot sprites (Susuwatari) from Spirited Away
  ([ghibli.jp](https://www.ghibli.jp/)) as the cultural reference
  for "small floating creature with eyes but no mouth that feels
  alive".

### 2.4 The Golem (earnest helper)

**Visual identity.** A small wooden golem roughly 28 pixels tall.
Slow, deliberate, blocky proportions. Three slats of wood for the
torso visible through a sash. Two carved circles for eyes. The
accent the player tints is the moss growing on one shoulder and the
glow inside the eye sockets. Walks with a slight forward lean as if
each step takes intent. The earnest helper voice is the perfect fit
because the golem visibly tries hard.

**Personality archetype paired by default.** Earnest helper. Warm,
sincere, never sarcastic, never preachy. The opposite of the wisp.
Where the wisp is dry, the golem is direct. Where the wisp says
little, the golem offers a small extra of detail.

**Why this form pairs with this personality.** A small wooden figure
that takes a beat before each action visually maps to a personality
that thinks before it speaks. Earnest helper is the archetype
players pick when they want a companion that will never make them
feel dumb. The golem cannot raise an eyebrow because it does not
have one.

**Anti-examples.**

- Not the Wise Old Man. Not Hans. Not a Jagex character with wood
  swapped for cloth. We are not in the OSRS character roster.
- Not a Minecraft golem. Different proportion, more carved detail,
  no Mojang-style face.
- Not a steampunk automaton. No brass, no gears, no pipework.
- Not a Pokemon Sudowoodo. We are wood, not "rock pretending to be
  wood".

**Reference art.**

- The forest-spirit characters in Princess Mononoke (Kodama)
  ([ghibli.jp](https://www.ghibli.jp/)) for the wooden-golem stillness
  and forward-lean walk.
- The figurines in Death's Door
  ([acidnerve.com](https://acidnerve.com/)) for the carved-but-warm
  silhouette discipline.
- The dryad and tree-folk studies on Lospec gallery
  ([lospec.com/gallery](https://lospec.com/gallery)).
- The companion automaton in Brothers: A Tale of Two Sons
  ([starbreeze.com](https://www.starbreeze.com/games/)) for the slow
  deliberate gait.
- The wooden-doll character studies in Hyper Light Drifter
  ([heartmachine.com](https://heartmachine.com/)) for the small
  earnest figure at low resolution.

## 3. The animation atlas, what frames each starter ships at MVP

The atlas is the same shape for all four starters. The artist
commissions against this exact list. Engineering pre-allocates the
atlas slots before the first commission lands. The atlas is laid out
in Aseprite with the frame names below as layer tags so the runtime
can index by name, not by pixel offset.

Total per starter at MVP: 58 frames. Times four starters at full
launch: 232 frames. Dimensions: 32 by 32 pixels at @1x, with @2x and
@3x atlases generated by the artist directly (not by the runtime,
because nearest-neighbour upscaling at runtime introduces visible
seams on subpixel motion).

### 3.1 Walking cycle, 8 directions, 3 frames each (24 frames)

The 8 directions match the existing OSRS character facing octants
(N, NE, E, SE, S, SW, W, NW). 3 frames per direction = 24 frames.
The middle frame is the contact pose where the leading foot is on
the ground. The other two frames are the pass and the high-foot.

This is the frame budget that supports the smooth follow behaviour
in [EMBODIED_COMPANION.md section 4](./EMBODIED_COMPANION.md#4-visual-and-presence-design).
The runtime interpolates between the three keyframes per direction
based on the companion's velocity. At slow speed the cycle plays at
6 frames per second; at sprint speed the cycle plays at 12 frames
per second. This is the canonical Slynyrd 4-frame-on-8-direction
pattern, simplified to 3 frames for the smaller pixel budget
(see [slynyrd.com/blog/2024/5/24/pixelblog-50-human-walk-cycle](https://www.slynyrd.com/blog/2024/5/24/pixelblog-50-human-walk-cycle)).

### 3.2 Idle cycle in each direction, 2 frames each (16 frames)

8 directions, 2 frames per direction. The two frames are the
"breath-in" and "breath-out" pose. Cycle plays at 1.5 frames per
second. This is the canonical floor-presence of the companion when
nothing else is happening. Without this, the companion looks like
a still PNG. With it, the companion looks alive.

### 3.3 Look-at poses, 8 directions, 1 frame each (8 frames)

The companion turns its head toward an interest target without
moving its body. Used when the player examines an item, when an NPC
dialogue starts, when a milestone fires. One frame per direction
because the head-turn snap is intentional: the snap is the visual
beat that tells the player "the companion noticed".

### 3.4 Read pose, 2 frames

Companion sits cross-legged with a book in hand. The two frames are
"book open" and "book turn page". Used during long player idle
(over 20 seconds) and when the companion is producing a long
answer (visible during the latency window before the speech bubble
opens).

### 3.5 Sit pose, 2 frames

Companion sits with arms wrapped around knees. The two frames are
"sit settled" and "small sway". Used during deeper AFK (over 90
seconds without input). Distinct from the read pose: read implies
the companion is doing something, sit implies the companion is
waiting.

### 3.6 Surprise pose, 2 frames

Companion reacts to a drop, a pet milestone, a quest completion, a
diary tier unlock. The two frames are "snap to attention" and
"settle". Plays once on event, not on a cycle. Optional small
particle pop scheduled at the same tick by the runtime (handled
outside the atlas).

### 3.7 Yawn pose, 2 frames

Companion plays one yawn at the 5-minute idle threshold. The two
frames are "wide yawn" and "close mouth". Plays once, not on a
cycle. The yawn is a small humanising beat that signals the
companion is bored, which is itself a personality cue. The yawn
never plays during chat.

### 3.8 Speak pose, 2 frames

Mouth open and mouth closed. Cycles at 4 frames per second only
during a speech-bubble reveal. Synced to the per-character reveal
in the bubble (see section 4). This is the only frame in the atlas
that the runtime cycles based on data outside the companion's own
state (the chat reveal cursor).

### 3.9 Atlas packing notes for the artist

Deliver as a single Aseprite source file per starter with each pose
above on its own layer tag (e.g. `walk_n`, `walk_ne`, `idle_s`,
`look_e`, `read`, `sit`, `surprise`, `yawn`, `speak`). Final exports
are PNG atlases at @1x (32x32 per cell), @2x (64x64), @3x (96x96).
Source file plus three atlases delivered as a single zip.

Total deliverables per starter: 58 frames, 1 Aseprite source, 3
PNG atlases, 1 metadata JSON file with the frame index mapped to
pose names. The metadata JSON is the artist's responsibility because
they know which frame they put where; engineering writes the schema
the artist fills in.

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

The bubble is not skinned per starter. The bubble is Tibbly's voice
surface, and the voice does not change with form. The form is the
delivery system; the typography is the voice.

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

## 6. The asset commission plan

The plan covers the experiment-fidelity commission that ships
M-COMP-1, the full launch-fidelity commission that ships M-COMP-2
and M-COMP-3, the vendor candidates, the brief template that goes
out, and the interim solution if commissioning slips.

### 6.1 Vendor candidates

The OSRS pixel-art community does not surface a tidy list of
"commissionable veterans" through public web search. The community
operates through Discord channels (OSRS Art and OSRS Pixel Art
servers) and through individual artists' DMs on Twitter and
DeviantArt. The candidates below are real artists with public
portfolios in the adjacent space (pixel-art game character work
with walk cycles and small-creature sprites). Each has a portfolio
URL. Day rates are estimated from public information; confirm in
the actual commissioning email.

For first contact use the candidate's listed contact channel. If
none is listed, message through their portfolio platform.

1. **Pedro Medeiros (MiniBoss / Skytorn / Celeste pixel artist)**.
   [studiominiboss on Tumblr](https://www.tumblr.com/studiominiboss),
   [Patreon](https://www.patreon.com/saint11),
   [pixel-art tutorial catalogue on Lospec](https://lospec.com/pixel-art-tutorials/author/pedro-medeiros).
   Industry tier. The animation easing reference everyone else
   learns from. Likely day rate USD 600 to USD 800 if he takes the
   work. Probability he takes a contract this small is low; worth
   asking because the Wiki Veteran is squarely his idiom.
2. **Slynyrd (Pedro Junqueira)**. [slynyrd.com](https://www.slynyrd.com/),
   [pixelblog catalogue](https://www.slynyrd.com/pixelblog-catalogue).
   Public tutorial archive shows mastery of the 8-direction human
   walk cycle which is exactly our spec. Commissions surface
   occasionally through his contact page. Estimated day rate USD
   400 to USD 600.
3. **Juanjo Marmol (dreco)**. [dreco on ArtStation](https://dreco.artstation.com/),
   [walk-cycle character study](https://www.artstation.com/artwork/qWZez).
   Public ArtStation portfolio of pixel-art walk cycles for
   character work. Open to commissions per his ArtStation profile.
   Estimated day rate USD 300 to USD 500.
4. **Chantal Allanson**. [walk-cycle character study on
   ArtStation](https://www.artstation.com/artwork/GeVY4W). Pixel-art
   walk-cycle work on ArtStation. Less prominent profile, more
   likely to accept a smaller contract. Estimated day rate USD 250
   to USD 400.
5. **Open posting on PixelJoint forum**. [pixeljoint forum thread
   on sprite commission pricing](http://pixeljoint.com/forum/forum_posts.asp?TID=20340).
   PixelJoint is the canonical community forum for pixel artists.
   A "commission wanted" thread will surface multiple candidates
   inside 48 hours; the standard range observed on the forum is USD
   15 to USD 30 per hour for character sprite work, which at our 58
   frame budget per starter (assuming 2 hours per keyframe for
   walking, 1 hour per non-cycled pose) maps to roughly USD 1100 to
   USD 1500 per starter for a mid-tier artist.

Search did not surface a named OSRS-community-specific pixel
artist with a clean public portfolio of commissionable
character-sprite work suitable for our use. The closest in-community
talent is concentrated in the OSRS Art Discord servers
([primebattlegamers OSRS art community guide](https://primebattlegamers.com/osrs-art-the-ultimate-guide-to-old-school-runescapes-vibrant-creative-community-in-2026/))
and not surfaced through search. Direct outreach via the OSRS Art
Discord and the OSRS Pixel Art Discord (both linked from the
r/2007scape sidebar) is the recommended secondary channel after the
five candidates above. Doc the outreach in the agent log when it
happens.

### 6.2 Budget

**M-COMP-1 experiment fidelity (one starter, the Wiki Veteran).**
USD 500 to USD 1000. Defended as follows. The Wiki Veteran at MVP
is 58 frames. At a fair mid-tier sprite rate of USD 15 to USD 30
per hour and an average production time of 1.5 hours per frame
(per
[2dwillneverdie.com cost-of-sprites breakdown](https://2dwillneverdie.com/blog/how-much-do-sprites-cost/)),
the labor cost lands between USD 1300 and USD 2600 at uncapped
rates. We capped the M-COMP-1 brief at USD 1000 by reducing the
walk-cycle to 2 frames per direction (16 frames total instead of
24) for the experiment ship. The full walking cycle backfills at
M-COMP-2.

The M-COMP-1 budget also covers Aseprite source delivery and the 3
PNG atlas exports at @1x, @2x, @3x. No animation easing curves
delivered (engineering handles those at runtime through the state
machine).

**Full launch fidelity (all four starters).** USD 4000 to USD 6000
for the remaining art. The Wiki Veteran's missing walk-cycle
frames (8 frames) backfill at M-COMP-2 for USD 200 to USD 400.
The three other starters at the full 58-frame spec each cost USD
1100 to USD 1500. Stretch animations (alternate look-at poses,
seasonal hood tint variants, a celebration pose for diary
milestones) add USD 500 to USD 1000 per starter.

The total budget envelope (USD 4000 to USD 6000 for launch beyond
the M-COMP-1 spend, plus the M-COMP-1 spend of USD 500 to USD 1000)
maps to a worst-case all-in of USD 7000. The
[EMBODIED_COMPANION.md section 6 cost note](./EMBODIED_COMPANION.md#6-technical-architecture)
of GBP 1500 to GBP 3000 was conservative; we expect to spend at
the higher end of that range plus 50 percent because we are
including the second wave of stretch animations the spec did not
originally enumerate.

If the M-COMP-1 experiment does not move the needle, the spend
caps at USD 1000 and we never touch the launch budget. The decision
gate at the end of the M-COMP-1 week is the budget gate.

### 6.3 Brief template the artist receives

The template below is what we paste into the first contact email.
It is calibrated to the Wiki Veteran starter. The same template is
adapted by changing the starter name and reference art for the
other three.

```
Subject: Commission: pixel-art companion sprite for Tibbly (OSRS overlay)

Hi {Artist},

I'm commissioning a pixel-art character sprite for a paid product
called Tibbly (an in-client AI companion for Old School RuneScape,
shipping as a RuneLite overlay plugin). The companion is a small
hooded humanoid that walks beside the player. I want to commission
the first of four starter forms, which we are calling The Wiki
Veteran. Brief follows.

THE FORM

A small hooded humanoid, roughly 32 pixels tall at @1x. Modern
flat-shaded vector look rendered to a pixel grid. Three-tone shading
per region (highlight, midtone, shadow). Hood up by default with
heavy faceshadow. One tintable accent color on the inner hood
lining, the satchel strap, and the lantern handle. Carries a small
lantern on the hip.

The art reads as "not a Jagex NPC" at one glance. This is
load-bearing. Please do not reference any OSRS character or NPC in
the silhouette. References below for the art direction.

REFERENCE ART (north stars, not assets to copy)

- Hyper Light Drifter cloaked traveller for flat-shaded palette.
- Slynyrd's human walk cycle tutorial.
  https://www.slynyrd.com/blog/2024/5/24/pixelblog-50-human-walk-cycle
- Pedro Medeiros's animation easing tutorials on Lospec.
- The wandering scholar archetype from Pyre (Supergiant).

DELIVERABLES

58 frames total in a single Aseprite source, plus three PNG atlases
at @1x (32x32 per cell), @2x (64x64), @3x (96x96), plus one
metadata JSON file mapping frame index to pose name.

Frame breakdown:
- Walking cycle, 8 directions, 3 frames each (24 frames)
- Idle cycle, 8 directions, 2 frames each (16 frames)
- Look-at poses, 8 directions, 1 frame each (8 frames)
- Read pose (sitting cross-legged with book, 2 frames)
- Sit pose (knees-to-chest, 2 frames)
- Surprise pose (snap to attention, 2 frames)
- Yawn pose (2 frames)
- Speak pose (mouth open / closed, 2 frames)

LICENSING

Work-for-hire under a written contract. Exclusive license to
Rainnworks Ltd for use in the Tibbly product and its marketing. You
keep portfolio rights with credit to "Tibbly by Rainnworks". You
may show the work on your portfolio and on social once we ship.

BUDGET

USD 500 to USD 1000 fixed-price for the M-COMP-1 spec above. I am
open to splitting the spec into a smaller M-COMP-1 deliverable (16
walking frames at 2 frames per direction instead of 3) at the
lower end of that range, with the remaining 8 walking frames
optionally backfilled at the same rate later. State your preference.

TIMELINE

Two weeks from acceptance. M-COMP-1 ships internally on a fixed
date. If you can deliver inside one week we can offer a 10 percent
premium on the agreed fixed price.

FORMATS

- Aseprite source (.ase) with each pose on its own layer tag
- PNG atlases at @1x, @2x, @3x
- Metadata JSON (schema attached)

WHAT WE LOVE IN YOUR PORTFOLIO

{Two specific pieces from the artist's portfolio, named, with
URLs. Personal note. We do not send the same email to every
candidate.}

If you're interested I'd love to chat over a 30-minute call to walk
through the spec and the references. Reply with a time that suits
or a rough quote and I'll come back inside the day.

Thanks,
Tom (Rainnworks)
```

### 6.4 Interim solution if commission falls through

If the M-COMP-1 commission cannot be placed inside the experiment
week, we ship placeholder art generated by Stable Diffusion XL Turbo
with the PixelArtXL LoRA
([huggingface.co/nerijs/pixel-art-xl](https://huggingface.co/nerijs/pixel-art-xl)),
hand-cleaned in Aseprite ([aseprite.org](https://www.aseprite.org/)).
The placeholder is labeled in the dashboard as "Placeholder art,
commissioned replacement coming soon". The placeholder is
deliberately less polished so dogfooders read it as temporary, not as
the shipping art.

We do not ship AI-generated art as the final companion. The full
launch art is hand-commissioned without exception. The placeholder
is the bridge that lets us test the visual presence hypothesis in
the M-COMP-1 week without blocking on commission lead time.

The placeholder generation pipeline takes one to two hours per
starter through the tools listed above. The cleanup hand-pass takes
another four to eight hours. The output is acceptable for internal
dogfooding and is not acceptable for the marketing site or for
sharing with external testers. This is recorded in the dashboard
config flag `companion.art_quality` as "placeholder" vs
"commissioned".

## 7. The marketing surface for the companion

The companion is the marketing grab. The visual bible is the
brief that goes to the artist who will draw the GIFs the marketing
site embeds. The five magical-moment GIFs below are the marketing
site's hero loop, the pricing-page anchor, and the social-share
catnip.

### 7.1 The hero-page magical moment (the 4-second loop)

The marketing site hero shows a 4-second loop in which the player
walks across the Lumbridge bridge with the companion (Wiki Veteran)
trotting beside them. At second 2, the player hovers over an
unidentified herb in their inventory. The companion does the
look-at snap. A speech bubble appears that reads "ranarr. high
alch is 195. you want to clean this." at the Inter Tight body
size with the "195" set in IBM Plex Mono. The bubble fades out at
second 3.5. The loop restarts.

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
   Companion is already sitting on the bridge railing (the sit pose,
   used as a stationary spawn frame). On player arrival, the
   companion stands, walks to the player, and says "welcome back.
   you said no spoilers on monkey madness 2, still on?" The bubble
   plays at 4-second total length. The "remembering" thinking pose
   from section 5.4 plays in the 800 ms before the bubble opens.

2. **Quiet competence mid-task.** Player hovers a herb (the hero
   loop from section 7.1, isolated and slowed to 6 seconds).

3. **A shaped relationship.** Player asks "how do I get to Lumbridge
   again". Companion responds "home teleport. third time today.
   want me to set a quick shortcut?" The companion plays the
   yawn-then-return idle so the gentle ribbing reads as patient,
   not exasperated.

4. **Live boss tip.** Player is fighting Vorkath. Companion plays
   the look-at snap toward the boss's tile, then says "switch to
   protect magic. fireball spawn in two ticks." The "in two ticks"
   sets in IBM Plex Mono so the timing fact reads as data.

5. **Dialogue driven by current state.** Player walks into the
   bank. Companion plays the look-at toward the bank booth, then
   says "you have 27 sharks and a full prayer pot stack. that's
   enough for two zulrah kills before you'd want to bank." The
   sentence is lifted from the existing
   [BRAND_VOICE.md sample exchange](../marketing/BRAND_VOICE.md#3-do-s)
   so the marketing copy matches the in-game voice exactly.

Each GIF is 6 seconds or less. Each is under 500 KB. Each renders
the Wiki Veteran at marketing-quality fidelity. Each is captioned
with one line of body copy underneath.

### 7.3 The first-install "ask Tibbly" CTA

When the player installs the plugin and starts RuneLite for the
first time, the companion spawns at the Lumbridge spawn point next
to them and plays the read pose. After 3 seconds (long enough that
the player looks at the companion and not at the chat), the speech
bubble fades in with "hi. i'm tibbly. pick how i look, then ask me
anything." The dashboard link is rendered as a clickable affordance
on the bubble.

This is the only bubble in the whole product that auto-opens. Every
other bubble fires from a player or state event. The first-install
bubble is the exception because the player has no prior context for
the companion existing. The bubble auto-dismisses after 8 seconds
or on first player input, whichever comes first.

The first-install CTA also fires the form picker (the four-starter
chooser from section 2). The chooser renders inside the chat
panel, not as a modal overlay, so the chooser feels like Tibbly
showing the player options, not like a setup wizard.

## 8. Hub and Jagex risk

The art style declares "this is not a Jagex asset" at one glance.
That declaration is load-bearing for hub safety. Every choice in
this document gets stress-tested against [the licensing posture
documented in research/osrs-wiki/licensing.md](../research/osrs-wiki/licensing.md).

The decisions, made explicit.

The companion's sprite atlas is hand-commissioned original art under
work-for-hire to Rainnworks. We own it. It is not derivative of any
Jagex sprite. It does not use the OSRS palette, the OSRS character
silhouette wedge, or the OSRS UI chrome on the sprite itself.

We do NOT use OSRS Wiki sprites in the companion. We do NOT hot-link
to oldschool.runescape.wiki/images. The `isForbiddenAssetUrl` guard
in `packages/osrs-assets/src` (from
[research/osrs-wiki/_SUMMARY.md](../research/osrs-wiki/_SUMMARY.md))
enforces this in code. The companion atlas is loaded from the
plugin's packaged resources, not from any wiki URL.

We DO use RuneStar CC0 fonts and RuneLite BSD-2 icons for the UI
chrome around the companion (the chat panel border, the
inventory-quoting glyphs in the speech bubble, the dashboard form
chooser). These are catalogued in `packages/osrs-assets/` and
licensed per [research/osrs-wiki/licensing.md](../research/osrs-wiki/licensing.md).
The bubble and panel use the existing chrome so the companion feels
native to the RuneLite client. The companion itself is in a
deliberately different visual register so a hub reviewer sees a
distinct entity, not a clone of any in-game NPC.

The "this is what a hub reviewer sees" frame test. Stand the
companion (Wiki Veteran) next to Hans the greeter in the Lumbridge
castle yard. The visual difference is immediate: Tibbly is at a
different shading register, a different silhouette outline, a
different palette band, and is clearly an overlay character. A
reviewer can tell at a glance that Tibbly is not depicted by Jagex.
We commission against this frame test and we ship the frame test
into the dogfood handoff so the dogfooders can confirm the
read.

For the marketing site the companion always appears against
backgrounds that are clearly Tibbly territory, not in-game
screenshots that crop tightly around the companion. The hero loop
is a deliberately rendered scene; we do not stitch the companion
onto raw OSRS gameplay footage as the marketing first impression.
The compositing reads as "ours" not as "borrowed".

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
If a starter does not resonate post-launch, we swap the archetype
assigned to it without re-commissioning art. The Wiki Veteran can
ship paired with dry-wiki-nerd at launch and re-paired with
sardonic-veteran a month later based on dogfood signal.

If the visual style does not work for one starter, we re-commission
that one starter without throwing the others away. The atlas is
per-starter and the runtime loads each atlas independently. The
budget envelope assumes the worst case of one re-commission across
the four launch starters. We do not bake in a global art swap.

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
