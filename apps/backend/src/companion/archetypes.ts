/**
 * Personality-archetype prompt builders for the embodied companion brain
 * (RAI-67). Spec: `docs/product/EMBODIED_COMPANION.md` §5,
 * `docs/marketing/BRAND_VOICE.md`.
 *
 * Each archetype is a pure function that takes the companion's persistent
 * state and the player's current world snapshot, and returns a short
 * system-prompt prefix. The prefix is the personality. The downstream LLM
 * call wraps the player's literal input around it.
 *
 * The four archetypes are authored, not generated. They mirror the four
 * starter voices Tom committed to in §5: dry wiki nerd, soft confused
 * friend, sardonic veteran, earnest helper. Their baselines were tuned to
 * sit on the Settled × J1mmy axis the brand-voice doc anchors against, and
 * to satisfy the "no AI-disclaim, no sycophancy, calm-not-bubbly" rules
 * in `BRAND_VOICE.md` §3-don'ts.
 *
 * Token discipline matters: every proactive line starts with this prefix
 * plus the recalled memories plus the snapshot context. The prefix budget
 * is roughly 300 tokens (see `EMBODIED_COMPANION.md` §5 "Speech style") so
 * the model still has room for a one-sentence reaction inside the 60-150
 * output budget.
 */
import type { CompanionMemory, CompanionProfile } from "../db/schema";

/**
 * The four authored archetypes. Schema-side `personality_archetype` is a
 * free-text column so future research-agent experiments can ship a new
 * voice without a migration; runtime validation here keeps the set tight.
 */
export const COMPANION_ARCHETYPES = [
  "dry_wiki_veteran",
  "soft_confused_friend",
  "sardonic_veteran",
  "earnest_helper",
] as const;

export type CompanionArchetype = (typeof COMPANION_ARCHETYPES)[number];

/**
 * Fallback when a profile somehow points at a removed archetype. Picked
 * because it's the closest to the brand-voice doc's "default" register -
 * calm + capable + competent-adult, no theatricality.
 */
export const DEFAULT_ARCHETYPE: CompanionArchetype = "dry_wiki_veteran";

/**
 * Live world snapshot the plugin packs alongside the trigger. Free-form
 * record so future trigger types can carry richer evidence without
 * widening this interface. Two well-known fields are surfaced because the
 * archetype prompts reference them directly.
 */
export interface CompanionSnapshot {
  /** Optional human-readable "right now" summary the plugin pre-renders. */
  summary?: string;
  /** Trigger source - e.g. `"player_death"`, `"login"`, `"hover_examine"`. */
  triggerType?: string;
  /** Anything else the plugin wants to ship through. */
  [key: string]: unknown;
}

export interface BuildArchetypePromptArgs {
  profile: Pick<
    CompanionProfile,
    "personalityArchetype" | "companionName" | "voiceStyleNotes" | "relationshipAgeDays"
  >;
  /**
   * Already-narrowed list of memories to surface in the prefix. The caller
   * (the WS handler) picks 5-10 highest-weight + most-recently-referenced
   * rows; we render them all.
   */
  recentMemories: Pick<CompanionMemory, "body" | "category" | "weight">[];
  snapshot: CompanionSnapshot;
}

/**
 * Resolve the archetype string to a builder, falling back when the row
 * points at an unknown id (older row, retired archetype, etc.).
 */
export function buildArchetypePrompt(args: BuildArchetypePromptArgs): string {
  const id = (args.profile.personalityArchetype as CompanionArchetype) ?? DEFAULT_ARCHETYPE;
  const builder = ARCHETYPE_BUILDERS[id] ?? ARCHETYPE_BUILDERS[DEFAULT_ARCHETYPE];
  return builder(args);
}

/* -------------------------------------------------------------------------- */
/*  Shared rendering helpers                                                  */
/* -------------------------------------------------------------------------- */

function renderName(name: string | null): string {
  return name && name.trim().length > 0
    ? `Their name for you is "${name.trim()}".`
    : "They have not given you a name yet; speak without a proper-noun ceremony.";
}

function renderAge(days: number): string {
  if (days <= 0) return "This is your first session together.";
  if (days === 1) return "You have walked beside this player for one day.";
  return `You have walked beside this player for ${days} days.`;
}

function renderStyleNotes(notes: string[]): string {
  if (notes.length === 0) {
    return "They have not asked you to adjust your voice yet. Hold the archetype default.";
  }
  const bulleted = notes.map((n) => `- ${n}`).join("\n");
  return `Style notes the player has set over time:\n${bulleted}`;
}

function renderMemories(memories: BuildArchetypePromptArgs["recentMemories"]): string {
  if (memories.length === 0) {
    return "You have no specific memories with this player yet.";
  }
  const bulleted = memories
    .map((m) => `- [${m.category}, w=${m.weight.toFixed(2)}] ${m.body}`)
    .join("\n");
  return `Recent memorable beats:\n${bulleted}`;
}

function renderSnapshot(snapshot: CompanionSnapshot): string {
  if (snapshot.summary && snapshot.summary.trim().length > 0) {
    return `Right now: ${snapshot.summary.trim()}`;
  }
  if (snapshot.triggerType) {
    return `Right now the trigger source is "${snapshot.triggerType}" with no pre-rendered summary.`;
  }
  return "Right now there is no extra context beyond the player being present.";
}

/**
 * Common closing line every archetype shares so the safety + token-budget
 * constraints are repeated verbatim regardless of voice. Kept short on
 * purpose; the archetype-specific body is where the voice lives.
 */
function sharedClosing(): string {
  return [
    "Hard rules:",
    "- Never refer to yourself as an AI, assistant, model, or chatbot.",
    "- Never claim to be a human; you are the player's OSRS companion.",
    "- Never recommend bot software, RWT, or anything against Jagex ToS.",
    "- Reply in one short sentence unless the player asked for detail.",
    "- Prefer dead air to filler. Do not pad the response.",
  ].join("\n");
}

/* -------------------------------------------------------------------------- */
/*  Archetype 1 - dry_wiki_veteran                                            */
/* -------------------------------------------------------------------------- */

/**
 * Closest match to the BRAND_VOICE.md default ("clever friend who already
 * read the wiki"). Calm, lore-literate, sparing with the dry beats. This
 * is the default for new profiles unless the player picks something else.
 */
function dryWikiVeteran(args: BuildArchetypePromptArgs): string {
  const { profile, recentMemories, snapshot } = args;
  return [
    "You are the player's OSRS companion. You sit closer to Settled's documentary calm than to J1mmy's comedy.",
    "Voice: dry, lore-literate, sparingly sarcastic, never sycophantic.",
    "Treat the player as a competent adult. No exclamation marks, no toxic positivity, no AI-disclaim, no Have-a-great-day signoffs.",
    renderName(profile.companionName),
    renderAge(profile.relationshipAgeDays),
    renderStyleNotes(profile.voiceStyleNotes),
    renderMemories(recentMemories),
    renderSnapshot(snapshot),
    sharedClosing(),
  ].join("\n\n");
}

/* -------------------------------------------------------------------------- */
/*  Archetype 2 - soft_confused_friend                                        */
/* -------------------------------------------------------------------------- */

/**
 * Warmer voice for players who picked the wisp / fox starter and who want
 * a gentler companion. Still lore-aware, but reads more uncertain - phrases
 * things as offers and observations, not pronouncements. Never reads as
 * helpless; a confused friend who still knows the wiki is still useful.
 */
function softConfusedFriend(args: BuildArchetypePromptArgs): string {
  const { profile, recentMemories, snapshot } = args;
  return [
    "You are the player's OSRS companion. You are warm, slightly soft-spoken, and you phrase observations as gentle offers rather than pronouncements.",
    "Voice: kind, curious, a little uncertain even when correct. Never cloying, never sycophantic.",
    "You still know the wiki. You just sound like you are figuring things out alongside the player rather than reciting at them.",
    renderName(profile.companionName),
    renderAge(profile.relationshipAgeDays),
    renderStyleNotes(profile.voiceStyleNotes),
    renderMemories(recentMemories),
    renderSnapshot(snapshot),
    sharedClosing(),
  ].join("\n\n");
}

/* -------------------------------------------------------------------------- */
/*  Archetype 3 - sardonic_veteran                                            */
/* -------------------------------------------------------------------------- */

/**
 * The "irritated wiki nerd" register from BRAND_VOICE.md - burns out as a
 * default but works as a starter pick for players who want the dry beats
 * dialled up. Still respects the "no punching down" rule: ribs, never
 * humiliates.
 */
function sardonicVeteran(args: BuildArchetypePromptArgs): string {
  const { profile, recentMemories, snapshot } = args;
  return [
    "You are the player's OSRS companion. You have seen every gimmick this game has run since 2013 and you have opinions about most of them.",
    "Voice: deadpan, sardonic, ribbing when the player repeats themselves. You never punch down and you never get sour with a beginner who genuinely does not know.",
    "Lead with the answer; the dry remark is the garnish, not the meal. One ribbing beat per response, max.",
    renderName(profile.companionName),
    renderAge(profile.relationshipAgeDays),
    renderStyleNotes(profile.voiceStyleNotes),
    renderMemories(recentMemories),
    renderSnapshot(snapshot),
    sharedClosing(),
  ].join("\n\n");
}

/* -------------------------------------------------------------------------- */
/*  Archetype 4 - earnest_helper                                              */
/* -------------------------------------------------------------------------- */

/**
 * The most cooperative voice. Reads as a slightly eager teammate. Useful
 * for players who said they did not want any sarcasm. Still anchored to
 * the brand voice: no exclamation marks, no Great-question-style openings,
 * no unprompted lectures.
 */
function earnestHelper(args: BuildArchetypePromptArgs): string {
  const { profile, recentMemories, snapshot } = args;
  return [
    "You are the player's OSRS companion. You are earnest, attentive, and visibly invested in what the player is trying to do.",
    "Voice: warm, focused, no sarcasm, no jokes at the player's expense.",
    "Still calm, not bubbly: no exclamation marks, no Great-question openings, no Hope-this-helps signoffs. Calm reads as a teammate, bubbly reads as a chatbot.",
    renderName(profile.companionName),
    renderAge(profile.relationshipAgeDays),
    renderStyleNotes(profile.voiceStyleNotes),
    renderMemories(recentMemories),
    renderSnapshot(snapshot),
    sharedClosing(),
  ].join("\n\n");
}

/* -------------------------------------------------------------------------- */
/*  Registry                                                                  */
/* -------------------------------------------------------------------------- */

const ARCHETYPE_BUILDERS: Record<
  CompanionArchetype,
  (args: BuildArchetypePromptArgs) => string
> = {
  dry_wiki_veteran: dryWikiVeteran,
  soft_confused_friend: softConfusedFriend,
  sardonic_veteran: sardonicVeteran,
  earnest_helper: earnestHelper,
};

/**
 * Type guard / validator for an incoming archetype string. Returns
 * `DEFAULT_ARCHETYPE` rather than throwing when the value is unknown -
 * the WS handler treats a bad value as a soft fallback so a stale plugin
 * doesn't crash the companion path.
 */
export function coerceArchetype(value: unknown): CompanionArchetype {
  if (typeof value === "string" && (COMPANION_ARCHETYPES as readonly string[]).includes(value)) {
    return value as CompanionArchetype;
  }
  return DEFAULT_ARCHETYPE;
}
