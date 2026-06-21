/**
 * Thin fetch wrapper.
 *
 * Reads VITE_BACKEND_URL at build time. RAI-27 wired the real endpoints
 * — this is the single chokepoint every route uses so we never sprinkle
 * `fetch()` calls or hard-coded URLs across components.
 *
 * Auth model: we attach the saved session token as `Authorization:
 * Bearer …` AND send `credentials: 'include'` so a forthcoming
 * cookie-based session works without route-level changes. The backend
 * accepts either an `x-user-id` header or `Authorization: Bearer
 * <userId>` (see `apps/backend/src/api/_auth.ts`).
 */

import { getSessionToken } from "./auth";

export type ApiOptions = RequestInit & {
  /** When true, send the saved session token as Authorization: Bearer. */
  readonly authenticated?: boolean;
};

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

/**
 * Resolve the backend base URL. Falls back to "/api" so the dev server
 * can proxy to a locally running backend without any config.
 */
export function getBackendUrl(): string {
  const fromEnv = import.meta.env.VITE_BACKEND_URL;
  if (typeof fromEnv === "string" && fromEnv.length > 0) {
    return fromEnv.replace(/\/+$/, "");
  }
  return "/api";
}

/**
 * Issue a request against the backend, returning parsed JSON.
 *
 * @throws {ApiError} when the response is non-2xx.
 */
export async function apiFetch<T = unknown>(
  path: string,
  options: ApiOptions = {},
): Promise<T> {
  const { authenticated, headers, ...rest } = options;

  const url = path.startsWith("http")
    ? path
    : `${getBackendUrl()}${path.startsWith("/") ? path : `/${path}`}`;

  const composedHeaders: Record<string, string> = {
    Accept: "application/json",
    ...(rest.body && !(rest.body instanceof FormData)
      ? { "Content-Type": "application/json" }
      : {}),
    ...(headers as Record<string, string> | undefined),
  };

  if (authenticated) {
    const token = getSessionToken();
    if (token) {
      composedHeaders["Authorization"] = `Bearer ${token}`;
    }
  }

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
