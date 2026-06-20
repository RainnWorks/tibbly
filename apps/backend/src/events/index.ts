/**
 * Public surface of the analytics event subsystem (RAI-37).
 *
 * Consumers should `import { getDefaultBus, attachEventPersister } from "../events"`
 * rather than reach into `bus.ts` / `persist.ts` directly.
 */
export * from "./bus";
export * from "./types";
export { attachEventPersister, persistEvent, countEventsOfType } from "./persist";
export {
  aggregateOnce,
  startAggregationCron,
  toDateKey,
  type AggregateResult,
  type AggregateWindow,
  type CronHandle,
  type CronOptions,
} from "./aggregate";
