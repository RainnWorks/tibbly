/**
 * Shared auth fixture for RAI-39 tests.
 *
 * Two helpers:
 *   - `seedDevice(handle, opts)` — creates a `users` row + `devices` row
 *     bound to a raw device key, returns the raw key + userId + deviceId
 *     so the test can mint a real `Authorization: Bearer <rawKey>` header.
 *   - `OPS_SESSION_SECRET` + `signSession(email)` — build a valid
 *     `Cookie: ops_session=…` header for admin tests.
 */
import { hashDeviceKey } from "../src/auth/device-key";
import { signOpsSession } from "../src/api/admin/_gate";
import { devices, users } from "../src/db/schema";
import type { TestDbHandle } from "./_db-fixture";

/** Predictable 32-byte secret for admin JWT tests. */
export const OPS_SESSION_SECRET: Uint8Array = new TextEncoder().encode(
  "test-ops-secret-must-be-at-least-32-bytes-yes",
);

export interface SeededDevice {
  userId: string;
  deviceId: string;
  rawDeviceKey: string;
}

export interface SeedDeviceInput {
  userId?: string;
  deviceId?: string;
  rawDeviceKey?: string;
  email?: string | null;
  stripeCustomerId?: string | null;
}

/**
 * Insert `users` + `devices` rows so the test can authenticate as that
 * user via `Authorization: Bearer <rawDeviceKey>`.
 *
 * Defaults are deterministic so tests stay readable; pass overrides when
 * a test needs to seed multiple users in the same DB.
 */
export async function seedDevice(
  handle: TestDbHandle,
  input: SeedDeviceInput = {},
): Promise<SeededDevice> {
  const userId = input.userId ?? `user_seed_${Math.random().toString(36).slice(2, 10)}`;
  const deviceId = input.deviceId ?? `dev_seed_${Math.random().toString(36).slice(2, 10)}`;
  const rawDeviceKey =
    input.rawDeviceKey ?? `raw-key-${Math.random().toString(36).slice(2)}-${Date.now().toString(36)}`;

  const deviceKeyHash = await hashDeviceKey(rawDeviceKey);

  await handle.db.insert(users).values({
    id: userId,
    email: input.email ?? null,
    stripeCustomerId: input.stripeCustomerId ?? null,
  });

  await handle.db.insert(devices).values({
    id: deviceId,
    userId,
    deviceKeyHash,
  });

  return { userId, deviceId, rawDeviceKey };
}

/** Build a `Authorization: Bearer <raw>` headers object. */
export function bearerHeaders(rawDeviceKey: string): Record<string, string> {
  return { authorization: `Bearer ${rawDeviceKey}` };
}

/** Build a `Cookie: ops_session=<jwt>` header for admin gate tests. */
export async function opsSessionCookieHeader(
  email: string,
  secret: Uint8Array = OPS_SESSION_SECRET,
): Promise<{ cookie: string }> {
  const jwt = await signOpsSession(email, secret);
  return { cookie: `ops_session=${jwt}` };
}

export { signOpsSession };
