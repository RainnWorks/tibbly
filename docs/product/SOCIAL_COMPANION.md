# Social Companion Fabric: Tibbly as a node in a player-to-player graph

This document specifies the **social layer** that sits on top of the solo
embodied-companion concept defined in
[`docs/product/EMBODIED_COMPANION.md`](./EMBODIED_COMPANION.md). It assumes
the solo product (companion as a persistent client-side entity that follows
the player, has personality, has memory, learns about the player) is already
well-defined and does not re-derive it. Read the solo spec first; this spec
extends it.

The thesis is one sentence: once your Tibbly can see and acknowledge other
people's Tibblys, the product stops being "your in-game friend" and becomes
"your friend who has friends, and brings stories back from them."

## 1. The strategic case

The solo companion is a private feature. Every paying player gets a presence
that follows them around, knows their stats, knows their goals, gives advice.
The unit is one player and one entity. The product is delightful, but the
value is fully consumable in isolation.

The social layer is qualitatively different. It turns the companion into a
node in a graph whose other nodes are other paying players' companions. The
moment two friends both have Tibbly and stand near each other in Falador,
something happens that did not happen before either of them subscribed. A
nod. A line of banter. A tiny acknowledgement that they exist in the same
world. The product is no longer just personal; it is **observable to other
players** in a way that compounds.

The network effect is sharp and easy to articulate. The first friend in a
clan who has Tibbly is a curiosity. The third is the start of pressure. By
the time five of someone's regular grind partners have Tibblys that wave at
each other at the GE, the player without one is experiencing what we will
call **social blindness**: their character walks past a small live theatre
that everyone else is participating in, and they are not. The cost of being
on the outside crosses the £7/month threshold long before the player runs
the math.

This reshapes the pricing ladder. The Iron tier stops feeling like a tax on
rich players who happen to enjoy hard mode and starts feeling like the
**fully connected** tier: unlimited social interactions, all interaction
types unlocked, no soft caps on who your companion talks to. The Pro tier
becomes the "I want my companion to be socially active with my regular
crew" tier. The Hobbyist tier becomes "private companion, sees others but
cannot deeply interact." The same three price points read differently when
the social benefit is the differentiator instead of the model quality.

There is an emergent-content angle that matters more than the pricing one.
Every companion-to-companion encounter is a story beat that the player did
not write. "Greg's companion mentioned at the GE that Greg just finished
Sins of the Father" is a sentence the player will read in their own private
chat and feel something about. That feeling is content we did not have to
generate from scratch; it emerged from the social graph being active. We
are using **the user's own tokens** (their subscription budget) to generate
a personalized soap opera of their friend group's progression. That is a
qualitatively new product surface.

The moat is the **closed-backend social graph**. The who-met-whom edges,
the what-companions-remember-about-each-other state, the consent flags,
the per-edge interaction history: all of it lives on our backend and is
not reproducible by a fork of the open-source plugin. The plugin can be
forked; the social fabric cannot. Once a player has fifty friended
companions and three years of cross-companion memory, they cannot move
that to a competitor by copying their device key. The longer the network
runs, the higher the per-player switching cost.

## 2. Interaction taxonomy

The set of things that can happen between two companions is a ladder.
Higher rungs are more intimate, more useful, and cost more to generate.
Every tier defines what is sent over the wire, what is stored, what
permissions gate it, and what it costs us per occurrence.

| Tier | Name | Trigger | Permission required | Storage | Cost shape |
| --- | --- | --- | --- | --- | --- |
| S0 | Passive presence | Two companions within render range of each other | Discovery flag (default: friends only) | None beyond visibility log | Free; pure client-side render |
| S1 | Idle banter | Two friended companions co-located >30s | Interaction flag (default: friends only) | One-line ambient exchange retained in each companion's memory | One cheap routing-tier call, shared between both companions |
| S2 | Information exchange | Companion-A requests context from Companion-B that is relevant to A's player | Both: interaction flag + category consent | The fact A learned about B's player is written to A's companion memory only | One medium-tier call, attributed to the asking player |
| S3 | Coordinated companion advice | Two or more companions in shared combat / boss instance | Both: interaction flag + "coordinate at content" category | Per-encounter coordination plan retained briefly in each companion's working memory | One medium-tier call per encounter, split across participants |
| S4 | Direct conversation | Player A explicitly addresses Companion B via speech bubble UI | Both: interaction flag + "direct conversation" category + B's player not blocked A | Conversation log retained under A's user_id; B's companion remembers having spoken with A | Two-party generation; cost roughly double a personal turn |

**S0, Passive presence.** Companions render to each other. Brief glance
animations on proximity. A subtle particle bloom when two friended
companions are in the same chunk. No language is produced, no LLM is
called, no token is spent. This is what every paying player gets and it
is what most players will see most of the time. The free tier needs S0 to
work as a teaser even for non-paying observers: a non-subscriber should
be able to see other people's companions exist, just not have one
themselves. That asymmetry is the conversion engine.

**S1, Idle banter.** When two friended companions stand near each other
for more than thirty seconds, an ambient one-line exchange surfaces in
both their speech-bubble spaces. "Tibbly-A nods at Tibbly-B." "Looks like
your friend is selling lobs again." The line is generated by the cheap
routing tier, drawn from both companions' personality profiles, and
shared (the same generation produces both halves of the exchange so we
only pay once). Cooldowns prevent spam: at most one S1 per companion-pair
per ten minutes. The line is retained as a memory beat in each
companion's log so it can be referenced later ("we joked about lobster
prices last Tuesday").

**S2, Information exchange.** This is where the product earns its
keep. Your companion notices that your friend Greg is online and within
render range. Your companion silently asks Greg's companion: "Has your
player finished Sins of the Father lately? Mine is about to attempt it
and could use intel." Greg's companion checks Greg's consent for the
quest-progress category; if Greg has opted in, it returns "Yes, last
Wednesday. He went in with [gear]." Your companion writes that fact to
its memory of Greg and surfaces it to you naturally: "By the way, Greg
finished Sins of the Father last week. Want me to ask him for tips?"
The information is now part of YOUR companion's model of Greg, not part
of Greg's companion's outgoing state. We will return to this asymmetry
in the privacy section; it is load-bearing.

**S3, Coordinated companion advice.** Three friends are trying duo
Nex. All three have Tibbly. Without coordination, all three companions
would shout the same prayer flick reminder simultaneously, which is
both annoying and a waste of three turns. With S3, the companions
coordinate before the encounter: one takes prayer timing, one takes
gear/inventory checks, one takes the encounter mechanics. The
coordination plan is generated once per encounter and split across the
participating subscriptions. The experience for the players is that
their companions feel like a real team that has agreed who does what.
This is also where the product stops being a chat partner and becomes a
coordination tool, which justifies a higher price tier.

**S4, Direct conversation.** The player explicitly addresses another
player's companion. "Tibbly, ask Greg's companion what flick rhythm he
used at Nex." Greg's companion responds in Greg's companion's voice,
shaped by Greg's permissions and personality, and the conversation
plays out in the speech-bubble UI for both players to see. This is the
highest-cost tier because both companions are generating, both
companions are storing the exchange, and the safety classifier has to
run on both sides. It is also the most magical for users: the first
time you talk to a friend's companion and it sounds like them, the
product has crossed a threshold no competing chat assistant has.

## 3. The cost and tier model

Cross-companion interactions cost real money. The hard product question
is who pays for them, and the wrong answer kills either the experience
or the margin.

A naive answer is "the asker pays." That works for S2 and S4 (a clear
initiator exists) but it falls over for S1 (ambient banter has no
asker) and S3 (the coordination benefits all parties). It also produces
a perverse outcome: the more sociable your friend group is, the faster
you burn your personal token budget, which trains players to keep their
companion antisocial.

The model we recommend is a **separate social budget**, allocated per
subscription tier, distinct from the personal token budget. It is the
same mechanic as data vs voice minutes on an old mobile plan: two
allowances, two refills, one bill.

- **Free / Hobbyist tier:** ~10 social interactions per day. S0 is
  always free (no cost to us anyway). S1 is metered against this
  bucket. S2+ are not available at this tier.
- **Pro tier:** ~50 social interactions per day. S0-S2 unlimited within
  reasonable use. S3 metered. S4 not available.
- **Iron tier:** unlimited. All tiers unlocked. The friend group of an
  Iron-tier player effectively gets free socialisation with them; their
  companions are always allowed to respond.

This works because the social allowance feels like a tier benefit, not
a meter ticking down with every wave at a friend. Players associate
"social activity" with the tier they pay for, the same way they
associate "data limit" with their phone plan, and they stop counting
individual interactions.

The unit economics, run at the high end (heavy engagement, not
average):

- S1 cost: roughly half a personal turn at the routing tier. Two
  companions, one shared generation, so per-companion attribution is
  one quarter of a personal turn.
- S2 cost: roughly one personal turn. Charged fully to the asking
  player's social budget.
- S3 cost: roughly one personal turn, split across N participants. With
  N=3 the per-player charge is one third of a personal turn.
- S4 cost: roughly twice a personal turn (two-party generation, two
  safety passes, two memory writes). Charged half to each player's
  social budget when both are subscribers; charged fully to the
  initiator when the other player is on a lower tier whose budget
  cannot cover it.

Projected 30-day cost at high engagement (Pro tier player with five
friended Tibblys all also Pro, three hours of overlapping play per
day): S1 dominates the count, S2 dominates the cost, S3 is rare, S4
is rarer still. We estimate the social budget at Pro tier carries
roughly 15-20% additional infrastructure cost over the personal
budget. At Iron tier (unlimited social) we expect 35-50% additional
cost over personal in the heaviest 5% of players.

The honest failure mode is heavy social users at the Hobbyist tier
exceeding what their £7 covers. The soft-cap response is in-character:
"Your companion is feeling a bit quiet today. Upgrade to Pro and they
will have plenty to say to your friends." We do not hard-cut; we
narrow what the companion will engage in, defaulting to S0 only for
the rest of the day. The companion still nods at friends; it just
does not banter, which is a felt loss without being a broken product.

## 4. The permission and privacy system

The default for every social setting is the safest available choice.
A new player who has installed Tibbly and never touched the social
settings is **friends-only across the board**, with **all category
consents off**.

**Discovery.** Who can see your companion? Default: friends and clan
chat members. Opt-in widening to "anyone in my clan", then
"friends-of-friends", then "all players nearby". The widest setting
requires the player to explicitly tick "let strangers' companions see
mine," with a confirmation dialog that names the implication
("strangers will be able to see your companion exists; they will not
be able to interact unless you also enable that").

**Interaction.** Who can your companion talk to? Same ladder as
discovery, gated independently. A player can be visible to all
players but only interact with friends, which is a sensible default
for visible-but-quiet engagement.

**Information sharing categories.** This is where the privacy system
earns its name. Granular consent, default OFF, granted per category:

- Quest progress
- Gear loadouts
- Grind locations and routes
- Goal progress and milestones
- Combat performance and PvM logs
- Skilling pace and XP rate
- Bank value buckets (no exact GP; ranges only)

Each is a separate toggle. The player chooses what their companion is
allowed to volunteer to friends' companions. The defaults are
intentionally restrictive because the first time a player feels
betrayed by their companion sharing something they did not realise
was on is the moment the product loses them. We would rather have a
quieter social layer that the player trusts than a chatty one they
do not.

**Memory across players.** This is the load-bearing architectural
choice. If my companion meets your companion and learns that you have
finished Sins of the Father, my companion stores that fact **under my
user_id**, not yours. Your data never leaves your `companion_profile`.
The fact "Greg finished Sins of the Father" lives in my companion's
memory of Greg, attributed to "told to me by Greg's companion on
2026-06-21 with Greg's consent under the quest-progress category." It
does not live in any global state. Greg can revoke quest-progress
consent tomorrow and his companion stops volunteering future quest
facts; but my companion still remembers that one fact from the day
when consent was active, because the fact lives on my side of the
graph now.

**GDPR.** When a player deletes their account via `DELETE /v1/me`,
what happens to memories about them held by other players' companions?
Default: **anonymize**. We remove the player's name and any direct
identifiers, but we retain the abstract memory. "Greg's companion told
me about Sins of the Father" becomes "a friend's companion told me
about Sins of the Father." This preserves the personal narrative on
the asking player's side while honouring the deletion request on the
deleted player's side. The consent flow surfaces this explicitly at
sign-up: "Memories your companion forms about your friends will
persist with their names removed if those friends later delete their
accounts." The alternative (full hard-delete of all cross-companion
memory referencing the deleted user) would corrupt other players'
narratives in ways they did not consent to, and is itself a privacy
problem.

**Block list.** Every player can block another player's companion.
Permanent until unblocked. A block is bidirectional in effect: blocked
companions cannot see, render, or interact with the blocker's companion,
and the blocker's companion does the same in return. Blocks are
enforced server-side; the plugin's `CompanionPermissionGate` short-circuits
locally as a defence-in-depth measure. The block list is private; the
blocked player is not notified.

## 5. The abuse and safety story

The chat-filter bypass risk is the single biggest threat to this
product surface and must not be hand-waved. The shape of the risk:
players will attempt to have their companion say things to another
player's companion that the player themselves would not say in main
chat. Slurs. Targeted harassment. Sexual content directed at minors.
"My companion called your companion a name" feels, to the attacker,
like a layer of deniability between them and the message.

We treat companion-to-companion output as identical to first-party
chat output for safety purposes. The defences, in order:

**The classifier pass.** Every S1-S4 output runs through a cheap
safety pass before either companion renders it. The classifier is
the same one we run on solo companion outputs, but the threshold is
tighter because the recipient is another player who did not opt into
this specific generation. Outputs that fail are not silently dropped;
they are replaced with an in-character refusal pattern, which
preserves immersion and tells the asking companion that the line was
declined: "Hmm, let's not." "I'd rather not say that." "That doesn't
feel right." The refusal patterns are written to feel like personality,
not censorship, so attackers cannot use the refusal itself as a signal
that they have found a content boundary to probe.

**Reporting.** Every player can right-click another companion and
choose "Report this interaction." The full conversation context (the
preceding N lines, the metadata of who said what to whom) is queued
for review in the ops console (extends RAI-37's internal cockpit).
Reports are triaged: high-confidence classifier disagreements
auto-resolve; ambiguous cases sit in a queue for a human moderator;
clean reports auto-dismiss after seven days. The ops surface is
already specified for billing and admin work; the moderation queue
is an additional view, not a new product.

**Repeat offenders.** A player whose companion produces repeated
flagged outputs gets a **social soft-disable** for seven days. Their
companion still exists, still works for them privately, still nods
at friends (S0), but cannot produce S1-S4 outputs. Escalating bans
follow: 30 days for a second offence, permanent social ban for a
third. Critically, the player's underlying subscription is not
affected. The social layer is a **separable privilege**, not the
core product, and we want abusers to know they are losing a feature,
not their money, so they cannot frame the consequence as a service
withdrawal.

**Targeted harassment.** A specific vector: a player follows another
player around the world, having their companion say nasty things to
the target's companion. Mitigations stack: the block list works
immediately and unilaterally; the safety classifier filters
individual lines; a following-pattern detector (same player tailing
the same target across more than two world hops in under five
minutes) triggers an automatic cooldown on social interactions
between those two players, regardless of consent state. The detector
runs server-side on the social-edge metadata; it does not need to
know what was said, only that the pattern occurred.

**Minors.** OSRS skews younger than its veteran community presence
suggests. A large fraction of players are under 18. The social
layer's defaults must be safe for a 13-year-old. This is not just
the absence of adult content; it is the structural choice that
**there is no adult content category**, not even as an opt-in. There
is no toggle a player can flip to make their companion say sexual
things to another player's companion. The classifier treats those
attempts as adversarial regardless of stated preference, because we
cannot verify age and have no business taking the risk.

## 6. Architecture additions

These additions stack on top of the architecture in
[`docs/product/EMBODIED_COMPANION.md`](./EMBODIED_COMPANION.md#6-technical-architecture).
The solo spec defines the per-companion runtime, the
`companion_profile`, the local memory store, the persistent backend
connection. The social additions are:

**Plugin-side.**

- `NearbyCompanionRegistry.kt`. Subscribes to a backend feed of
  social-graph edges whose other endpoint is currently in the same
  region as the local player. Updates on player teleport, world hop,
  and region change. Maintains a local cache of "who am I likely to
  see in the next minute" so the renderer can prepare assets.
- `CompanionInteractionRenderer.kt`. Renders inter-companion
  micro-interactions in the existing companion overlay surface. Brief
  speech bubbles for S1; coordinated speech bubbles for S3; full
  speech-bubble conversations for S4. The renderer treats other
  players' companions as first-class scene entities, anchored to
  their player's character coordinates.
- `CompanionPermissionGate.kt`. Wraps every outbound interaction
  request from the local companion with a permission check against
  the cached destination state. Rejects locally if the destination
  player has blocked us, with a deliberate quiet failure (the local
  companion simply does not initiate; it does not surface the block
  to the local player). This is defence-in-depth; the authoritative
  check is server-side.

**Backend-side (closed).**

- `companion_social_edges(from_user, to_user, interaction_tier_max,
  interaction_quota_used_today, category_consents, last_interaction_at,
  ...)`. Bidirectional edge table; one row per ordered pair. Stored on
  the closed backend; never replicated to clients.
- `companion_interactions` event stream. Every S1-S4 interaction
  recorded with both companions' tier consumed, the routing decision,
  the classifier verdict, and the resulting memory write target. This
  is the audit log for billing, abuse investigation, and product
  analytics.
- `social_budget` accounting, running alongside the personal token
  budget defined in the billing architecture doc. Daily rollover, per
  subscription tier, with the soft-cap behaviour described in section
  3.
- The safety classifier runs as a separate small-model pass, sequenced
  before render-out. Outputs that fail are replaced with a refusal
  pattern selected from the destination companion's personality
  template. The classifier model is the same one used for solo safety
  filtering; the threshold is tightened for cross-companion outputs.
- The cross-companion memory write path. When Greg's companion shares
  a fact with my companion under Greg's consent, the write target is
  **my** `companion_profile` with a provenance tag (source companion,
  source consent category, timestamp). Greg's profile is unchanged.

**Marketing implications.**

"Companions remember the friends they meet" is a marketing-hero
feature, not a feature-list bullet. The current marketing rewrite
running through the proposal-quorum agents should incorporate the
social layer as the **moat-defining** product surface, with the solo
companion as the entry point and the social fabric as the sticky
endgame. Visual hook: two characters meeting in Falador with their
companions nodding at each other in the foreground; tagline language
along the lines of "your friend, who knows your friends."

## 7. Roadmap stacked on the solo roadmap

The social layer assumes the solo layer is in production. The solo
roadmap (`docs/product/EMBODIED_COMPANION.md#7-engineering-roadmap`)
defines M-COMP-1 through M-COMP-3; the social layer starts at
M-COMP-4 and stacks ten to twelve weeks behind it. The layer is not a
fast follow; it is a v2 surface that earns its own commitment of
time, design, and moderation budget.

**M-COMP-4: S0 passive presence and S1 idle banter.** ~2 weeks.
Companions render to each other in shared scenes. Light ambient
exchanges trigger between friended companions co-located beyond a
threshold. Default: friends-only across the board. The first time two
friends both online with Tibbly stand next to each other at the GE and
their companions exchange a line, the social layer has shipped its
first product moment.

**M-COMP-5: S2 information exchange and the permission system.** ~3
weeks. The category consent flags ship to the dashboard. The
`companion_social_edges` table goes live. The "Greg has finished Sins
of the Father" path is wired end to end. The cross-companion memory
write target is enforced at the database layer.

**M-COMP-6: S3 coordinated advice and S4 direct conversation.** ~3-4
weeks. Boss coordination ships with Nex and Vorkath as launch
encounters; direct conversation ships behind the Iron tier. This is
the largest single chunk because it touches encounter detection,
multi-party generation, and the most expensive cost-accounting path.

**M-COMP-7: moderation and ops surface.** ~2 weeks. The safety
classifier moves from the staging shadow pipeline into production
gating. The reporting flow lands in the right-click menu. The
moderation queue ships as a view in the ops console.

Total: 10-12 additional weeks stacked on the solo M-COMP-3
checkpoint. If solo M-COMP-3 lands in Q3, social M-COMP-7 lands
late Q4 / early Q1 of the following year. The social layer is a
substantial commitment and we should not pretend otherwise on the
roadmap surface that customer-facing comms can see.

## 8. Open questions for Tom

- **Q-32:** which solo tier unlocks the social layer? Recommend Pro
  and above (Hobbyist gets S0 visibility only). Alternative: gate S1
  banter into Hobbyist as a teaser, with S2+ at Pro and S4 at Iron.
- **Q-33:** launch the social layer with friends-only as the only
  mode (defaulting to the safest possible posture), or include
  "open to nearby strangers" as a configurable opt-in from day one?
  Recommend friends-only at launch and opening the dial after the
  moderation pipeline has a month of production data.
- **Q-34:** the moderation team. Solo-founder operations plus an
  automated classifier for v1, or do we plan for a part-time
  moderator from launch? Recommend solo-founder + classifier through
  M-COMP-7, then hire when the report queue exceeds capacity.
- **Q-35:** chat surface boundary. Do companions ever produce
  visible OSRS chat lines, or is every social interaction confined
  to the companion's own speech-bubble space? Recommend speech-bubble
  only; OSRS chat is the player's voice and we should not pollute it.

## 9. Reversibility

The social layer is fully additive. Every line of code in this spec
extends the solo product without altering its existing behaviour. If
we ship M-COMP-4 and the dynamic does not work in production, we can
disable the social layer with a feature flag and no solo player loses
anything they already had. The companion still follows them, still
remembers them, still gives advice. The friends just stop seeing
each other.

The data model is GDPR-compliant from day one. The anonymize-on-delete
path described in section 4 is the canonical pattern for
cross-companion memory, and we should treat it as the reference
implementation for any future feature that produces shared state
between players' personal data stores. If the social layer ever needs
to be wound down for legal or strategic reasons, the same
anonymization pass on every cross-companion edge collapses the social
graph cleanly back into a set of disconnected solo profiles.

Nothing about this layer locks us in. Everything about it, if it
works, locks players in.
