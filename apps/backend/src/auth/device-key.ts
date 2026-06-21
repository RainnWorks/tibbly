/**
 * Device-key hashing helpers (RAI-18).
 *
 * The plugin generates a long-lived `deviceKey` on first launch and stores it
 * in the local RuneLite profile dir. The backend never persists the raw key —
 * only an Argon2id digest. Verification on subsequent calls (pairing claim,
 * WebSocket handshake) hashes the supplied key against the stored digest.
 *
 * Argon2id parameters intentionally lean towards "interactive" (≈40ms on a
 * laptop) — these calls happen rarely (one claim per device, one WS auth per
 * session) so we can afford strong settings without affecting throughput.
 *
 * Per docs/research/libraries/auth.md the device key never crosses log
 * boundaries; this module exposes only `hashDeviceKey` / `verifyDeviceKey`
 * and never accepts a logger.
 */
import argon2 from "argon2";

/**
 * Argon2id parameters. Memory cost is the dominant defence against GPU
 * attacks; 64 MiB is the OWASP minimum recommendation as of 2026.
 */
const ARGON2_OPTIONS = {
  type: argon2.argon2id,
  memoryCost: 1 << 16, // 64 MiB
  timeCost: 3,
  parallelism: 4,
} as const;

/**
 * Hash a raw device key. Returns a self-describing `$argon2id$…` string that
 * is safe to store in the `devices.device_key_hash` / `pairing_codes.device_key_hash`
 * column.
 *
 * Throws if `deviceKey` is empty — the plugin must never request hashing of
 * a missing key.
 */
export async function hashDeviceKey(deviceKey: string): Promise<string> {
  if (!deviceKey || deviceKey.length === 0) {
    throw new Error("hashDeviceKey: empty deviceKey");
  }
  return argon2.hash(deviceKey, ARGON2_OPTIONS);
}

/**
 * Verify a raw device key against a previously-stored hash. Returns
 * `true` iff the key matches. Catches argon2's "malformed hash" errors and
 * returns `false` rather than throwing so callers can treat verification as
 * a boolean.
 */
export async function verifyDeviceKey(hash: string, deviceKey: string): Promise<boolean> {
  if (!hash || !deviceKey) return false;
  try {
    return await argon2.verify(hash, deviceKey);
  } catch {
    return false;
  }
}
