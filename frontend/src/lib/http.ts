import { API_BASE_URL } from "@/lib/env";
import { ApiError } from "@/lib/types";
import { useSessionStore } from "@/stores/session-store";

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  signal?: AbortSignal;
  /** Skip the Authorization header (auth endpoints). */
  anonymous?: boolean;
  /** Skip the X-Organization-Id header (auth, invitations/accept). */
  skipOrgHeader?: boolean;
  /** Internal: marks a retried request so a 401 does not loop. */
  isRetry?: boolean;
}

let refreshInFlight: Promise<boolean> | null = null;

function buildHeaders(options: RequestOptions): HeadersInit {
  const state = useSessionStore.getState();
  const headers: Record<string, string> = {
    Accept: "application/json",
    "X-Requested-With": "XMLHttpRequest",
  };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (!options.anonymous && state.accessToken) {
    headers["Authorization"] = `Bearer ${state.accessToken}`;
  }
  if (!options.skipOrgHeader && state.activeOrgId) {
    headers["X-Organization-Id"] = state.activeOrgId;
  }
  return headers;
}

async function parseError(response: Response): Promise<ApiError> {
  let code = "INTERNAL_ERROR";
  let message = "An unexpected error occurred.";
  let details: Record<string, unknown>[] = [];
  let requestId: string | null = response.headers.get("X-Request-Id");
  try {
    const body = await response.json();
    if (body && typeof body === "object") {
      code = typeof body.code === "string" ? body.code : code;
      message = typeof body.message === "string" ? body.message : message;
      if (Array.isArray(body.details)) details = body.details;
      if (typeof body.requestId === "string") requestId = body.requestId;
    }
  } catch {
    if (response.status === 0 || !response.status) {
      code = "NETWORK_ERROR";
      message = "Cannot reach the SignalOps backend.";
    }
  }
  if (response.status >= 500) {
    message = "The backend encountered an error. Please try again.";
  }
  return new ApiError(code, message, response.status, details, requestId);
}

/**
 * Attempts to restore/renew the session via the HttpOnly refresh cookie.
 * Single-flight: concurrent 401s wait for one refresh, then retry once each.
 */
export async function refreshSession(): Promise<boolean> {
  if (refreshInFlight) {
    return refreshInFlight;
  }
  refreshInFlight = (async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
        method: "POST",
        credentials: "include",
        headers: {
          Accept: "application/json",
          "X-Requested-With": "XMLHttpRequest",
        },
      });
      if (!response.ok) {
        return false;
      }
      const data = (await response.json()) as {
        accessToken: string;
        user: { id: string; email: string; fullName: string };
      };
      useSessionStore.getState().setSession({
        accessToken: data.accessToken,
        user: data.user,
      });
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

export async function http<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? "GET",
    headers: buildHeaders(options),
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
    credentials: "include",
  });

  if (response.status === 401 && !options.anonymous && !options.isRetry) {
    const refreshed = await refreshSession();
    if (refreshed) {
      return http<T>(path, { ...options, isRetry: true });
    }
    useSessionStore.getState().clearSession();
    throw new ApiError("UNAUTHENTICATED", "Your session has expired.", 401);
  }

  if (!response.ok) {
    throw await parseError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export function isAuthPath(path: string): boolean {
  return path.startsWith("/auth/");
}

export { buildHeaders };
