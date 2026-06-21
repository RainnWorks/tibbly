/**
 * Canned OSRS game-state fixtures the fake-plugin returns from tool calls.
 *
 * Why fixtures: the real plugin pulls state from a live RuneLite client,
 * which we cannot run inside Bun. These canned snapshots cover the same
 * surface area as the Tier 0 unblockers (account identity, raid layout,
 * target projectiles, active prayers, farming summary, farming patches)
 * plus the legacy bank / inventory / quest / slayer fixtures.
 *
 * Each fixture is a frozen JSON-serialisable object. The tool-responder
 * maps tool names to the matching fixture and ships it back across the
 * WSS as the `output` field of `tool_call_result`. Tests can override an
 * individual fixture for assertion-heavy cases via the responder's
 * `override(name, value)` API.
 *
 * Shape stability: these are NOT typed against `shared-types` because
 * tool I/O schemas live in the plugin Kotlin side today, not in the
 * shared package. When we port tool schemas into shared-types the
 * fixtures should be re-keyed; until then we mirror the names the
 * plugin's [ToolDispatcher] exposes.
 */

export interface GameStateFixtures {
  account_identity: AccountIdentity;
  bank_tab: BankTab;
  inventory: Inventory;
  active_prayers: ActivePrayers;
  current_quest: CurrentQuest;
  slayer_task: SlayerTask;
  farming_summary: FarmingSummary;
  farming_patches: FarmingPatch[];
  raid_layout: RaidLayout;
  target_projectiles: TargetProjectiles;
  combat_stats: CombatStats;
  world_state: WorldState;
}

export interface AccountIdentity {
  /** RuneLite-visible display name; null when logged out. */
  displayName: string | null;
  /** "main" | "ironman" | ... — matches osrs_account_type enum. */
  accountType: string;
  combatLevel: number;
  totalLevel: number;
  membership: { isMember: boolean; daysRemaining: number | null };
}

export interface BankTab {
  tabIndex: number;
  items: ReadonlyArray<{ id: number; name: string; quantity: number; geValueGp: number }>;
  totalGp: number;
}

export interface Inventory {
  freeSlots: number;
  items: ReadonlyArray<{ id: number; name: string; quantity: number; slot: number }>;
}

export interface ActivePrayers {
  prayerPoints: { current: number; max: number };
  active: ReadonlyArray<{ name: string; drainRate: number }>;
}

export interface CurrentQuest {
  /** Short label that mirrors Wiki style. */
  name: string;
  /** Free-text step description as the quest log would show it. */
  step: string;
  /** Lower-case `started`, `in_progress`, `completed`. */
  status: string;
}

export interface SlayerTask {
  master: string;
  monster: string;
  amountRemaining: number;
  location: string;
}

export interface FarmingSummary {
  patchesReady: number;
  patchesGrowing: number;
  patchesDiseased: number;
  patchesEmpty: number;
}

export interface FarmingPatch {
  location: string;
  /** "herb" | "tree" | "allotment" | ... */
  kind: string;
  state: "empty" | "growing" | "ready" | "diseased" | "dead";
  /** Whats actually planted. Null when empty. */
  contents: string | null;
  /** Real-world seconds until next state change; null when not growing. */
  secondsUntilNext: number | null;
}

export interface RaidLayout {
  raid: "cox" | "tob" | "toa";
  rooms: ReadonlyArray<{ kind: string; orderInRaid: number }>;
}

export interface TargetProjectiles {
  /** Each entry describes one projectile incoming in the next few ticks. */
  incoming: ReadonlyArray<{
    sourceNpc: string;
    style: "magic" | "ranged" | "melee";
    ticksUntilHit: number;
  }>;
}

export interface CombatStats {
  hp: { current: number; max: number };
  attack: number;
  strength: number;
  defence: number;
  magic: number;
  ranged: number;
  prayer: number;
}

export interface WorldState {
  worldId: number;
  region: string;
  /** Where the player is. Game coords + named location for the LLM. */
  location: { x: number; y: number; z: number; name: string };
  /** Game tick count since login. Useful for tying snapshots together. */
  tickCount: number;
}

/**
 * Default fixture pack — keep this realistic. Test scenarios assert on
 * specific fields (bank-tab 3 must contain "Coal", farming patch at
 * "Catherby" must be ready, etc.) so changing values requires updating
 * the matching scenario.
 */
export const DEFAULT_FIXTURES: GameStateFixtures = Object.freeze<GameStateFixtures>({
  account_identity: {
    displayName: "Tibbly Test",
    accountType: "main",
    combatLevel: 126,
    totalLevel: 2277,
    membership: { isMember: true, daysRemaining: 274 },
  },
  bank_tab: {
    tabIndex: 3,
    items: [
      { id: 453, name: "Coal", quantity: 27500, geValueGp: 158 },
      { id: 440, name: "Iron ore", quantity: 8200, geValueGp: 99 },
      { id: 444, name: "Gold ore", quantity: 1230, geValueGp: 86 },
    ],
    totalGp: 27500 * 158 + 8200 * 99 + 1230 * 86,
  },
  inventory: {
    freeSlots: 6,
    items: [
      { id: 385, name: "Shark", quantity: 12, slot: 0 },
      { id: 2434, name: "Prayer potion(4)", quantity: 4, slot: 1 },
      { id: 12695, name: "Super combat potion(4)", quantity: 2, slot: 2 },
      { id: 4587, name: "Dragon scimitar", quantity: 1, slot: 3 },
    ],
  },
  active_prayers: {
    prayerPoints: { current: 79, max: 99 },
    active: [
      { name: "Piety", drainRate: 1 },
      { name: "Protect from Magic", drainRate: 0.5 },
    ],
  },
  current_quest: {
    name: "Desert Treasure II",
    step: "Speak to the Strange Old Man at Edgeville.",
    status: "in_progress",
  },
  slayer_task: {
    master: "Konar quo Maten",
    monster: "Aviansie",
    amountRemaining: 138,
    location: "God Wars Dungeon",
  },
  farming_summary: {
    patchesReady: 2,
    patchesGrowing: 3,
    patchesDiseased: 0,
    patchesEmpty: 1,
  },
  farming_patches: [
    {
      location: "Catherby",
      kind: "herb",
      state: "ready" as const,
      contents: "Ranarr weed",
      secondsUntilNext: null,
    },
    {
      location: "Falador",
      kind: "tree",
      state: "growing" as const,
      contents: "Magic tree",
      secondsUntilNext: 7200,
    },
    {
      location: "Hosidius",
      kind: "allotment",
      state: "ready" as const,
      contents: "Watermelon",
      secondsUntilNext: null,
    },
    {
      location: "Ardougne",
      kind: "herb",
      state: "empty" as const,
      contents: null,
      secondsUntilNext: null,
    },
  ],
  raid_layout: {
    raid: "tob" as const,
    rooms: [
      { kind: "Maiden", orderInRaid: 1 },
      { kind: "Bloat", orderInRaid: 2 },
      { kind: "Nylocas", orderInRaid: 3 },
      { kind: "Sotetseg", orderInRaid: 4 },
      { kind: "Xarpus", orderInRaid: 5 },
      { kind: "Verzik", orderInRaid: 6 },
    ],
  },
  target_projectiles: {
    incoming: [
      { sourceNpc: "Verzik Vitur", style: "magic" as const, ticksUntilHit: 3 },
      { sourceNpc: "Nylocas", style: "ranged" as const, ticksUntilHit: 2 },
    ],
  },
  combat_stats: {
    hp: { current: 91, max: 99 },
    attack: 99,
    strength: 99,
    defence: 99,
    magic: 99,
    ranged: 99,
    prayer: 99,
  },
  world_state: {
    worldId: 302,
    region: "uk",
    location: { x: 3200, y: 3200, z: 0, name: "Varrock square" },
    tickCount: 12_345,
  },
});

/**
 * Names of every fixture, in the order a chat would typically ask for
 * them. Useful as the default `allowedTools` list for the fake-plugin.
 */
export const ALL_FIXTURE_KEYS: ReadonlyArray<keyof GameStateFixtures> = Object.freeze([
  "account_identity",
  "combat_stats",
  "inventory",
  "bank_tab",
  "active_prayers",
  "current_quest",
  "slayer_task",
  "farming_summary",
  "farming_patches",
  "raid_layout",
  "target_projectiles",
  "world_state",
]);
