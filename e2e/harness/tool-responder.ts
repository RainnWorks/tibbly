/**
 * Tool dispatcher for the fake-plugin.
 *
 * When the backend issues a `tool_call_request`, the fake-plugin hands the
 * `name` and `input` here. We match by name, optionally honour the input
 * (e.g. `bank_tab(tabIndex)` swaps the fixture's tabIndex), and return a
 * synchronous output the harness ships back as `tool_call_result`.
 *
 * Tests can register one-off overrides with `responder.override("name", v)`
 * before they trigger the chat — the override is consumed by the next call
 * with that name. This is enough for the scenarios shipped here; if a more
 * elaborate test wants per-input branching it can `setHandler(name, fn)`.
 */
import { DEFAULT_FIXTURES, type GameStateFixtures } from "./game-state-fixtures";

export type ToolHandler = (input: unknown) => unknown | Promise<unknown>;

export interface ToolResponder {
  /** Resolve a tool call to its output. Throws if the tool name is unknown. */
  dispatch(name: string, input: unknown): Promise<unknown>;
  /** Override the next call to `name` with a fixed value. Single-shot. */
  override(name: string, value: unknown): void;
  /** Replace the handler for `name` permanently. */
  setHandler(name: string, handler: ToolHandler): void;
  /** Inspect what's been dispatched so far. Scenario-friendly. */
  callLog(): ReadonlyArray<{ name: string; input: unknown; output: unknown }>;
  /** Reset overrides + log. */
  reset(): void;
}

export interface ResponderOptions {
  fixtures?: Partial<GameStateFixtures>;
}

export function createToolResponder(options: ResponderOptions = {}): ToolResponder {
  const fixtures: GameStateFixtures = {
    ...DEFAULT_FIXTURES,
    ...(options.fixtures as GameStateFixtures | undefined),
  };

  const handlers = new Map<string, ToolHandler>(defaultHandlers(fixtures));
  const overrides = new Map<string, unknown>();
  const log: Array<{ name: string; input: unknown; output: unknown }> = [];

  return {
    async dispatch(name, input) {
      const override = overrides.get(name);
      if (override !== undefined) {
        overrides.delete(name);
        log.push({ name, input, output: override });
        return override;
      }
      const handler = handlers.get(name);
      if (!handler) {
        throw new Error(`tool-responder: no handler registered for "${name}"`);
      }
      const output = await handler(input);
      log.push({ name, input, output });
      return output;
    },
    override(name, value) {
      overrides.set(name, value);
    },
    setHandler(name, handler) {
      handlers.set(name, handler);
    },
    callLog() {
      return log;
    },
    reset() {
      overrides.clear();
      log.length = 0;
    },
  };
}

function defaultHandlers(fixtures: GameStateFixtures): Array<[string, ToolHandler]> {
  return [
    ["account_identity", () => fixtures.account_identity],
    ["combat_stats", () => fixtures.combat_stats],
    ["inventory", () => fixtures.inventory],
    [
      "bank_tab",
      (input) => {
        // Allow `bank_tab(tabIndex)` to swap the index in the fixture.
        const asObj = (input ?? {}) as { tabIndex?: number };
        const tabIndex =
          typeof asObj.tabIndex === "number" ? asObj.tabIndex : fixtures.bank_tab.tabIndex;
        return { ...fixtures.bank_tab, tabIndex };
      },
    ],
    ["active_prayers", () => fixtures.active_prayers],
    ["current_quest", () => fixtures.current_quest],
    ["slayer_task", () => fixtures.slayer_task],
    ["farming_summary", () => fixtures.farming_summary],
    ["farming_patches", () => fixtures.farming_patches],
    ["raid_layout", () => fixtures.raid_layout],
    ["target_projectiles", () => fixtures.target_projectiles],
    ["world_state", () => fixtures.world_state],
  ];
}
