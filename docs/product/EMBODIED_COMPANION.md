# Embodied Companion: Tibbly as a presence, not a panel

Status: strategic spec, awaiting greenlight
Owner: product (Tom)
Last updated: 2026-06-21
Related: HUB_RELEASE_STRATEGY.md, DATA_FLOW.md, LICENSING.md, REPO_SPLIT.md

## 1. Concept articulation

Tibbly today is a chat sidebar. The proposal in this doc is to give Tibbly a body inside the RuneLite client: a small, hand-drawn companion that walks alongside your character, watches the same world you watch, and speaks to you through a speech bubble above its head. It is rendered entirely by the open-source RuneLite plugin as a screen overlay. It cannot click, cannot move your character, cannot read pixels off other peoples screens, and never touches the game client itself. It is a presence, not an automation.

The pitch in the language of an OSRS YouTuber:

> "So I'm doing Hard Diaries on my GIM, and Tibbly is this little hooded figure trotting beside me. Halfway through Dragon Slayer 2 I forget where Robert the Strong even is, and Tibbly goes, 'You skipped him last Tuesday, want me to dig out the wiki bit?' She remembers I told her not to spoil quest dialogue. She remembers I prefer GP-per-hour over XP-per-hour. After the run she says, 'good session, you killed 113 vorkath, that's your second best week'. She doesn't play the game for me. She is just there, and she knows me."

The three core moments that have to land:

1. **Recognition on log-in.** You return after a week away. The companion is in the lumbridge spawn area waiting, and the first thing it says references something specific you did last time. Not a generic greeting.
2. **Quiet competence mid-task.** You hover over a herb you cannot identify. The companion glances at your inventory, head-tilts, and says the cleaned name and the high-alch value without you asking. No prompt, no command. It saw what you saw.
3. **A shaped relationship.** A month in, the companion sounds like itself, not like a generic assistant. It has in-jokes with you. It uses your nickname. It refuses to spoil the quest you said not to spoil. It is recognizably yours.

What this is NOT, said plainly:

- Not a bot. It does not click, walk, eat, drink, prayer-flick, or send any input event to the game.
- Not an in-game NPC. Jagex did not add it to the game. It exists only in your RuneLite overlay.
- Not a Discord bot. There is no shared text channel. The conversation lives where the game lives.
- Not a voice assistant. Default mode is text in a speech bubble. Voice can come later if it earns its place.

The strategic axis this shifts is the comparison axis. "AI chat sidebar for OSRS" competes with Quest Helper, with the wiki, and with the player's existing Discord. "Your OSRS companion who happens to know everything" competes with nothing currently on the table. The category becomes one player, one companion, one subscription, the same way Pokemon Go is one trainer with one buddy.

## 2. Precedent analysis

We are not the first product to put a small persistent entity next to a player. The literature is large enough that we can avoid each of its known failure modes. Eight precedents, what we steal, what we deliberately avoid.

**Pokemon Go buddy system.** The mechanic that worked: the buddy walks beside you on the world map and surfaces small rewards from being together (hearts, candy, postcards). The mechanic that fails: the buddy never reacts to what you actually do in the moment, so it feels decorative within a week. What we steal: the daily-presence loop, the idea that simply existing alongside the player accumulates a relationship score that the player can see and care about. What we avoid: a fixed walking-distance counter. Our equivalent has to react to in-game context, not to a step count.

**Tamagotchi and modern virtual pet apps (Bondee, Finch).** What works: low-stakes daily check-ins, the companion expressing simple emotional states that map to the players self-reported mood. What fails: the chore loop. If you forget to feed it, you feel guilt, then resentment, then you delete the app. What we steal: small daily emotional states the companion expresses through idle animation. What we avoid: any need state that punishes neglect. Tibbly cannot die, cannot get sad, cannot guilt-trip you.

**Final Fantasy XIV chocobo companion.** What works: a creature that travels with you across an MMO world, helps in combat, and visibly levels up with you. The bond is reinforced because the chocobo is materially useful in fights. What fails: outside combat it is mostly decorative. What we steal: visible presence in a persistent world. What we avoid: any in-game mechanical effect, because that would cross the Jagex ToS line.

**World of Warcraft companion pets.** Decorative-only, no behaviour beyond following. Result: the average player ignores them. The lesson is sharp. Visual presence without conversational hooks is wallpaper.

**Replika and Character.ai.** Chat-only relationships at scale. What works: long-term memory and shaped personality drive real attachment and real revenue. What fails: there is no shared world between you and the entity, so the conversation has nothing concrete to anchor on, and conversations drift into either roleplay or pseudo-therapy. What we steal: the memory and personalization spine. What we avoid: open-ended conversation with no anchor. Tibbly has the game to anchor against.

**Stardew Valley animal companions plus Persona social-link system.** Stardew uses a heart-meter that you can see, which both motivates and slightly cheapens the relationship. Persona hides the social rank behind events that feel narrative, which feels deeper but is opaque. We will steal Stardew's heart-meter pattern but with no number visible, and Persona's idea that the relationship reveals new conversational beats at thresholds.

**Existing OSRS pets (Bloodhound, Heron, Baby Mole).** Players already know what it feels like to have a small creature trotting behind their character in this exact game. The animation cadence, the follow distance, the way the pet snaps to the player on teleport: all already encoded in player muscle memory. We steal that cadence directly. New visual, identical-feel motion.

**Clippy.** Clippy failed not because the idea was bad but because Clippy interrupted, did not learn, and could not be made to shut up. What we steal: the small persistent character anchored to the work surface. What we avoid: unsolicited interruption. Tibbly speaks when spoken to, and otherwise emotes silently.

## 3. The strategic case

The strongest objection to Tibbly as it stands today is "I already have Quest Helper and the wiki." It is the right objection. Both of those tools are excellent, free, and already installed on the players machine. An AI chat sidebar is a marginal upgrade over them at best, because it competes on the same axis: information delivery. The embodied companion sidesteps this entire axis. Quest Helper is not your friend. Quest Helper does not remember you. Quest Helper does not have a name your kid likes. Tibbly does.

This widens the addressable market. The "wants a co-pilot" cohort is real but small and price-sensitive, because they are comparing against free tools. The "wants a companion" cohort is larger and less price-sensitive, because they are comparing against streaming subscriptions and games. Pokemon Go Plus accessories cost more than our top tier and people buy them in the millions. People pay for relationship, not for information.

Pricing also gets easier. A £7 to £49 per month subscription FEELS strange for an AI search tool because it competes with a free wiki. The same subscription feels reasonable for a relationship, in the same way that £15 per month for a Discord Nitro that mostly does cosmetics feels reasonable. The subjective price ceiling for "a thing that remembers me and talks to me" is materially higher than for "a thing that answers questions about the game".

This shape is hub-defensible. The plugin still does no automation, sends no input events, and does not exfiltrate game state to anyone but the players own backend session. The companion is a screen overlay that draws a sprite at a calculated tile offset and renders text in a speech bubble. The same egress posture we already documented in HUB_RELEASE_STRATEGY.md applies unchanged. The companion does not introduce any new ToS surface area.

It is also moat-defensible. The visible part is the sprite and the speech bubble, which are openly rendered by the open-source plugin. The valuable part is the personality system, the memory store, the safety classifier, and the routing logic that decides what the companion knows and when, all of which live behind our backend. A clone of the plugin gets you a sprite that walks beside you and says nothing.

## 4. Visual and presence design

Recommendation: option (e) with a strong default. The player picks from a small set of starter forms when they first install. The default form is a small hooded humanoid in a deliberately non-OSRS art style, hand-drawn, flat-shaded, two-frame walk. The other starters are a small fox-like creature in OSRS pet silhouette, a Moth-like wisp with a face, and a small wooden golem. Four starters total at launch.

Reasoning, blunt:

- Pure humanoid in OSRS style (option a) reads as a Jagex asset to the average player and is legally radioactive. We have not licensed Jagex's art. We will not use it.
- Pure animal pet in OSRS style (option b) is safe and recognizable but caps emotional expressiveness. Animals cannot read books or point. A hooded humanoid can.
- Pure abstract orb or wisp (option c) is cheap to commission and visually clean, but carries personality poorly. People bond with faces.
- Player customization across the four starters (option e) solves two problems at once. Each player gets to declare what Tibbly looks like to them, which is the same psychological commitment device Stardew exploits when you name your farm. And we cover four art directions cheaply by commissioning a smaller animation set per starter.

The recommended default starter is the hooded humanoid in a deliberately non-Jagex art style. Modern flat-shaded vector with a single accent color the player can tint. Reads as "this is not a Jagex NPC" at one glance. Looks like the player picked it.

Idle animations, five for launch:

1. Looking around. Head turns at slow random intervals.
2. Reading a book. Sits cross-legged when the player has been still for more than 20 seconds. Closes the book when the player moves.
3. Fidget with hood. Adjusts the hood as a small motion that breaks the still-frame feeling.
4. Sitting when player AFK. Different pose from book pose. Triggered by 90 seconds of no player input.
5. Scratching head when the player asks something it could not parse cleanly. Plays right before the speech bubble appears.

Reactive animations, five for launch:

1. Looks at the NPC the player is currently in dialogue with. Snaps head to the NPC tile.
2. Points at quest target when Tibbly was asked "where do I go". A finger-point in the direction of the next tile.
3. Head-tilts at a weird item. Triggered when the player hovers over a rare drop or a quest item.
4. Sleeps when the player has been bank-standing for 2 minutes. Tiny "Z" particles. Wakes when the player moves or types.
5. Looks at what the player examines. When the player right-clicks Examine on something, the companion glances at it before speaking.

Follow behaviour:

- Default distance is two to three tiles next to the player, never on the same tile.
- Pathfinding is a local A-star against the walkable scene mesh that RuneLite already exposes through its scene API.
- On long-distance player movement (run, fairy ring, glory teleport, spirit tree, fairy ring loop), the companion teleports to catch up. The catch-up plays a small puff-of-smoke animation so the teleport reads as intentional and not as a bug.
- The companion never blocks a tile the player wants to walk onto, because it is overlay-only. It does not exist to the game.

Where chat lives:

- Short replies (under 80 characters): speech bubble above the companion sprite. Auto-dismisses after a few seconds, scaled to length.
- Longer replies: an anchored floating panel attached to the sprite, draggable, dismissible.
- Click the sprite to open a focused chat view in a panel anchored to the entity, similar to the current sidebar, but spatially attached to the world.
- Sidebar chat panel stays. Power users live there. The companion is additive.

## 5. Personality and memory system

The personality is the product. The visual gets you to install. The personality gets you to renew.

Three inputs shape personality, in declining player effort:

1. **Starter archetype.** On first install, the player picks one of four archetypes after they pick the visual form. Suggested archetypes at launch: dry wiki nerd, earnest helper, sardonic veteran, soft confused friend. The archetype is a short authored system-prompt prefix in the routing layer.
2. **Explicit player instructions.** At any point the player can tell Tibbly how to behave. "Never spoil quests." "Stop using British slang." "Call me Boaty, not Tom." These commands are issued in the same chat channel as everything else. No settings menu required. The backend appends each instruction to a voice-style-notes list in the players companion profile.
3. **Implicit adaptation.** A cheap background pass runs at the end of each chat session. It reads the last twenty interactions and updates the voice-style-notes list opportunistically when the player corrected the companion or expressed a clear preference. It does not store anything the player did not say or do.

Memory model. A server-side table called companion_profile, keyed by user_id and osrs_account_id, with these fields:

- personality_archetype: one of the four starters.
- voice_style_notes: a short list of authored and inferred style strings.
- remembered_facts: a short list of player-volunteered or player-context-derived facts. "Plays GIM with Boatface and Cabbageman." "Hates spoilers." "Currently grinding vorkath."
- relationship_state: an opaque small struct of counters that the player never sees, used to gate which conversational beats unlock. The Stardew heart-meter, hidden.

Memory write path. After each chat session, an asynchronous job runs a cheap-model pass over the transcript and proposes up to three additions to remembered_facts. Each proposal is validated against a small rule set (no PII beyond what the player volunteered, no medical or financial data, no information about other people the player mentioned). Validated additions are appended. The voice-style-notes list is regenerated nightly from the latest twenty interactions, so it stays small and current.

Speech style. Each chat turn assembles a system prompt prefix from the archetype string plus the current voice-style-notes plus any active explicit instructions. The result is short, usually under 300 tokens, and feeds into the routing layer with the players current question. This is where the token-economy work in TOOL_ECONOMY.md pays off: the personality preamble has to be tight or it eats into the response budget.

Explicit player controls, surfaced in the in-game chat itself, not buried in config:

- `/tibbly forget` clears remembered_facts.
- `/tibbly speak more dryly` appends to voice-style-notes.
- `/tibbly call me Boaty not Tom` appends a name preference.
- `/tibbly start over` resets archetype, voice-style-notes, and remembered_facts. Hard reset, with a confirmation step.

Privacy. The player owns their companion_profile. The GDPR Article 15 export endpoint already documented in DATA_FLOW.md cascades through this table. The GDPR Article 17 deletion endpoint cascades through it. The /tibbly start over command is the friendly version of the right to be forgotten, scoped just to the companion, and leaves the rest of the account intact.

The PG-13 problem. A non-trivial fraction of players will, on their first session, attempt to get the companion to say things it should not say. Sexual, violent, racist, self-harm, suicide ideation, real-world threats. Two layers:

1. A small classifier pass on every output before the speech bubble renders. If the output trips a category, it is replaced with a short refusal voiced in the current archetype, paired with a hand-up emote on the sprite. The refusal does not lecture. "Not this one, sorry" plays better than a paragraph.
2. A repeat-attempt counter on the input side. Three serious attempts in a session triggers a soft cooldown where the companion goes quiet for a short window. Visible as the companion sitting down with arms folded. No account-level punishment unless escalation criteria documented separately in TRUST_AND_SAFETY.md are met.

The classifier does not break the magic because it never speaks in a voice that is not the companions. The refusals are themselves part of the personality.

## 6. Technical architecture

Plugin-side, all open MIT under the existing RainnWorks/osrs-llm-helper-plugin repo as defined in REPO_SPLIT.md.

- `EmbodiedCompanionRenderer.kt`, new file, around 300 lines. Subscribes to the existing per-tick overlay update path and draws the companion sprite at the calculated world tile, with sub-tile interpolation so the motion is smooth rather than tile-jumpy. Uses the existing RuneLite Overlay and OverlayManager APIs. No new dependencies.
- `CompanionPathfinder.kt`, new file, around 400 lines. A local A-star against the walkable scene mesh that the client already maintains. Exposes `nextWalkTile(currentTile, playerTile)` and `teleportTo(playerTile)`. Recomputes the path when the player moves, with a one-tick debounce.
- `CompanionStateMachine.kt`, new file, around 200 lines. Five states: Idle, Walking, LookingAt, Listening, Speaking. Transitions are deterministic given player and chat events. Logged for debugging through the existing logging path.
- Sprite atlas, loaded from packaged plugin resources. Commissioned art, not Jagex sprites, licensed under the same MIT plus art-license model already in LICENSING.md.
- Speech bubble and anchored chat panel are Swing components drawn on top of the canvas. Same approach the sidebar already uses.
- All outbound traffic still goes through the existing `EgressGate`. One new outbound payload type, `CompanionInteractionEvent`, that carries the players chat and a small context envelope (tile coordinates, current interface mode, last examined object id, archetype hint). No additional egress surface area.

Backend-side, closed proprietary under the existing rowm/osrs-llm-helper-backend repo as defined in REPO_SPLIT.md.

- `companion_profile` table on the existing Postgres, keyed by `user_id` and `osrs_account_id`. Schema above.
- `companion_interactions` event log, append-only, indexed by `user_id` and `created_at`. Cheap to write, cheap to summarize nightly. Retention 90 days by default, configurable by the player in dashboard.
- An asynchronous job runs at the end of each chat session that does the memory-extraction pass. It uses the cheaper model on the routing layer to propose `remembered_facts` additions. It is rate-limited per user.
- A nightly job per user that compacts the voice-style-notes list to the latest twenty interactions worth of signal. Runs in the cheap window of the day.
- The chat path REUSES the existing /v1/chat WebSocket. The plugin sends the same chat payload shape it already sends. The backend recognizes the `companion` mode bit on the session and applies the personality preamble. No new endpoint, no new auth, no new billing line item.

Asset pipeline. Cost-honest:

- Four starter forms, each with idle (five animations), walking (eight directions, two frames each), and reactive (five animations, four directions). Roughly ninety to one hundred sprites per starter, four starters, so approximately four hundred sprites for launch.
- Recommended commission split: one experienced OSRS-veteran pixel artist for the two OSRS-pet-silhouette starters, one modern hand-drawn vector artist for the hooded humanoid and the golem. Both contractors under work-for-hire with exclusive license to Tibbly.
- Realistic commission budget: £1,500 to £3,000 total. Lower end if we phase the starters across M-COMP-2 and M-COMP-3. Higher end if we want all four at M-COMP-2 ship.
- Stretch animations (sleeping pose, book-reading pose, hand-up safety pose) are an extra £300 to £500 per starter.

## 7. Engineering roadmap

Three milestones, ordered for cheapest learning first.

**M-COMP-1, static sprite, no behaviour.** Around one week. Renders the default starter sprite at a fixed tile offset from the player. Speech bubble pops above its head when chat replies arrive on the WebSocket. No pathfinding, no animations beyond a two-frame idle. Ships behind a config flag, available only to a small dogfood cohort. Purpose: prove that the existence of the visual presence shifts player perception of Tibbly. If the dogfooders use the sidebar less and the companion more, we know the bet is real.

**M-COMP-2, follow plus idle behaviour.** Around two to three weeks. The pathfinder lands. The state machine lands. All five idle animations and all five reactive animations land for the default starter. Long-distance teleport-and-puff lands. This is the minimum viable companion. Released to Pro tier as an opt-in experiment. Marketing site gets a thirty-second clip of Tibbly trotting through Lumbridge.

**M-COMP-3, personality plus memory.** Around two to three weeks. The backend companion_profile lands. Starter archetype picker lands in the dashboard and in-game on first run. The four `/tibbly` commands land. The end-of-chat memory-extraction job lands. The safety classifier on outputs lands. This is the milestone that makes paying customers love it. Released to all paid tiers.

Total focused work: five to seven weeks for the headline feature. Two engineers across these milestones is comfortable. One engineer doing it solo is feasible but slips closer to nine weeks.

Sequencing trade-off. The mobile-companion mode in the existing roadmap can either pause for these five to seven weeks, or run in parallel under a second engineer. The recommendation in this doc is to pause the mobile work for one cycle. The embodied companion is the stronger marketing story, and the mobile work shares no engine code with it.

## 8. Risks

Jagex ToS. The companion is overlay-only. It synthesizes no input, mutates no game state, automates no behaviour. The existing gradle gates `:checkNoHttpServer` and `:checkMcpServerGated` continue to prove this in CI. There is still residual risk that Jagex's third-party-client team chooses to disallow overlay characters as a category, irrespective of behaviour. Mitigations: explicit opt-in toggle in settings, clear "Tibbly companion (overlay)" label on the sprite, a one-time disclosure on first run, and a documented kill-switch where a config flag pulled from the backend can disable rendering for all clients on short notice.

Hub maintainers. They have historically been cautious about new overlay categories. Mitigation: sideload-first per HUB_RELEASE_STRATEGY.md, gather player-week numbers and community signal, then submit to the hub once tolerance is evidenced. Specifically, ship M-COMP-1 and M-COMP-2 as sideload-only, do not submit until after M-COMP-3 has been in the wild for two weeks.

Visual quality. Bad pixel art kills the magic. There is no recovering from a launch where the companion looks janky. Mitigation: commission named OSRS-community pixel artists with public portfolios. Hold a review gate at the storyboard stage and again at the first-frame-final stage before paying out. Do not ship M-COMP-2 until the art is good. Slip the date instead.

Uncanny-valley and parasocial attachment. Players will form genuine attachment to the companion. A small fraction will form attachment that is unhealthy. Mitigation: a clear AI disclosure on first interaction, restated when the player asks the companion "are you real" sincerely (which the classifier detects). The companion is allowed to be warm. It is not allowed to claim to be a person. Documented in TRUST_AND_SAFETY.md.

Speech-style abuse. Players will try to get the companion to say slurs. Mitigation: the safety classifier above, plus a small "speak this aloud" allowlist that the classifier consults before high-risk outputs render. Refused outputs replaced with a brief in-character refusal and the hand-up emote. No silent failure, no error toast.

## 9. Open questions for Tom

- **Q-28**. Visual archetype. Recommendation in this doc: option (e), four starter forms, default to the hooded humanoid in a non-OSRS art style. Alternatives: ship only the OSRS-pet-silhouette starter for first launch and add humanoids later, or ship only the humanoid and add pets later. Are we committing to four-at-launch or single-at-launch?
- **Q-29**. Roadmap commitment. Recommendation: greenlight M-COMP-1 as a one-week experiment with a hard decision gate at the end of the week before committing to M-COMP-2 and M-COMP-3. Alternative: commit to the full five-to-seven week roadmap up front and stop the mobile work for the duration. Which?
- **Q-30**. Tier placement. Recommendation: ship the companion to all paid tiers including Hobbyist (£7). The companion is the relationship product and gating it above Hobbyist would weaken the funnel. Alternative: reserve it as a Pro plus feature to drive upgrades. Which?
- **Q-31**. Parasocial posture. Recommendation: lean warm but with a hard disclosure floor. The companion can be a friend but cannot pretend to be human. Alternative: more conservative, the companion explicitly framed as "your assistant" with no relational language. The first is the bigger product, the second is the safer brand. Which?

## 10. Reversibility

Every part of this proposal is additive. The existing sidebar chat panel stays. If the M-COMP-1 experiment does not move the needle, the renderer and state machine are turned off by a config flag and nothing else in the product regresses. The current users see the same Tibbly they have today.

The personality memory model is GDPR-compliant from day one because it composes onto the existing `/v1/me` Article 15 and Article 17 cascades documented in DATA_FLOW.md. The companion_profile table is one more row in the cascade, not a new export endpoint.

No marketing claim depends on the companion shipping. The product as it stands today is still the product if the companion is rolled back. The cost of trying this is bounded by the commission budget plus the engineering weeks, both of which are small relative to the strategic upside if the bet lands.
