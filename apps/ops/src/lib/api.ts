/**
 * Thin fetch wrapper for the ops console.
 *
 * Every request:
 *   - Sends `credentials: 'include'` so the ops_session cookie rides
 *     along (set by /admin/login).
 *   - Attaches `x-admin-email` from the cached operator email so the
 *     legacy admin gate (apps/backend/src/api/admin/_gate.ts) accepts it.
 *
 * The 401 path is the auth boundary: callers handle 401 by routing the
 * user to /login. The QueryClient does NOT retry 401s.
 */

import { getOperatorEmail } from "./auth";

export class ApiError extends Error {
  public readonly status: number;
  public readonly body: unknown;

  public constructor(status: number, message: string, body: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

export type ApiOptions = RequestInit;

export function getBackendUrl(): string {
  const fromEnv = (import.meta as { env?: { VITE_BACKEND_URL?: string } }).env
    ?.VITE_BACKEND_URL;
  if (typeof fromEnv === "string" && fromEnv.length > 0) {
    return fromEnv.replace(/\/+$/, "");
  }
  // Same-origin: backend serves the ops SPA AND owns /admin/* routes, so
  // requests like apiFetch("/admin/login") land directly on the backend
  // mount without any prefix. The legacy "/api" default was a phantom —
  // backend never mounted routes under that prefix.
  return "";
}

export async function apiFetch<T = unknown>(
  path: string,
  options: ApiOptions = {},
): Promise<T> {
  const { headers, ...rest } = options;

  const base = getBackendUrl();
  // Build the final URL without doubling a separator. `base` may be
  // empty (same-origin, no prefix), an absolute http(s) URL, or a
  // root-relative prefix like "/api". jsdom's fetch refuses relative
  // URLs, so when we have a window we resolve against its origin.
  const origin =
    base.startsWith("http") || typeof window === "undefined"
      ? ""
      : window.location.origin;
  const normalisedBase = base === "" || base === "/" ? "" : base;
  const normalisedPath = path.startsWith("/") ? path : `/${path}`;
  const url = path.startsWith("http")
    ? path
    : `${origin}${normalisedBase}${normalisedPath}`;

  const email = getOperatorEmail();

  const composedHeaders: Record<string, string> = {
    Accept: "application/json",
    ...(rest.body && !(rest.body instanceof FormData)
      ? { "Content-Type": "application/json" }
      : {}),
    ...(email ? { "x-admin-email": email } : {}),
    ...(headers as Record<string, string> | undefined),
  };

  const response = await fetch(url, {
    credentials: "include",
    ...rest,
    headers: composedHeaders,
  });
  const contentType = response.headers.get("content-type") ?? "";
  const isJson = contentType.includes("application/json");
  const payload = isJson
    ? await response.json().catch(() => null)
    : await response.text().catch(() => "");

  if (!response.ok) {
    throw new ApiError(
      response.status,
      `Request to ${path} failed with ${response.status}`,
      payload,
    );
  }

  return payload as T;
}
