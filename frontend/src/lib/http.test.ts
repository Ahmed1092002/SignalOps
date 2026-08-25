import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http, refreshSession } from "@/lib/http";
import { useSessionStore } from "@/stores/session-store";

const okJson = (body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json", ...headers },
  });

describe("http client", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    useSessionStore.getState().clearSession();
    useSessionStore.getState().markBootstrapped();
    fetchMock.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sends bearer token and organization header", async () => {
    useSessionStore.getState().setSession({
      accessToken: "token-1",
      user: { id: "u1", email: "a@b.c", fullName: "A" },
    });
    useSessionStore.getState().setActiveOrg("org-1");
    fetchMock.mockResolvedValue(okJson({ ok: true }));

    await http("/incidents");

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["Authorization"]).toBe("Bearer token-1");
    expect(init.headers["X-Organization-Id"]).toBe("org-1");
    expect(init.headers["X-Requested-With"]).toBe("XMLHttpRequest");
    expect(init.credentials).toBe("include");
  });

  it("omits organization header when skipOrgHeader is set", async () => {
    useSessionStore.getState().setSession({
      accessToken: "token-1",
      user: { id: "u1", email: "a@b.c", fullName: "A" },
    });
    useSessionStore.getState().setActiveOrg("org-1");
    fetchMock.mockResolvedValue(okJson({ ok: true }));

    await http("/invitations/accept", { method: "POST", body: { token: "t" }, skipOrgHeader: true });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["X-Organization-Id"]).toBeUndefined();
  });

  it("refreshes once on 401 and retries the original request", async () => {
    useSessionStore.getState().setSession({
      accessToken: "expired",
      user: { id: "u1", email: "a@b.c", fullName: "A" },
    });
    useSessionStore.getState().setActiveOrg("org-1");

    fetchMock.mockImplementation((url: string) => {
      if (String(url).endsWith("/auth/refresh")) {
        return Promise.resolve(okJson({ accessToken: "fresh", user: { id: "u1", email: "a@b.c", fullName: "A" } }));
      }
      return Promise.resolve(
        new Response(JSON.stringify({ ok: true }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        })
      );
    });

    // First call: 401 -> refresh -> retry succeeds.
    let call = 0;
    fetchMock.mockImplementation((url: string, init: RequestInit) => {
      if (String(url).endsWith("/auth/refresh")) {
        return Promise.resolve(okJson({ accessToken: "fresh", user: { id: "u1", email: "a@b.c", fullName: "A" } }));
      }
      call += 1;
      const header = (init.headers as Record<string, string>)["Authorization"];
      if (call === 1) {
        return Promise.resolve(
          new Response(JSON.stringify({ code: "UNAUTHENTICATED" }), { status: 401 })
        );
      }
      return Promise.resolve(okJson({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0, echoedAuth: header }));
    });

    const result = (await http("/incidents")) as unknown as { echoedAuth: string };
    expect(result.echoedAuth).toBe("Bearer fresh");
    expect(call).toBe(2);
  });

  it("clears the session and does not retry forever when refresh fails", async () => {
    useSessionStore.getState().setSession({
      accessToken: "expired",
      user: { id: "u1", email: "a@b.c", fullName: "A" },
    });
    fetchMock.mockImplementation((url: string) => {
      if (String(url).endsWith("/auth/refresh")) {
        return Promise.resolve(new Response(JSON.stringify({}), { status: 401 }));
      }
      return Promise.resolve(new Response(JSON.stringify({}), { status: 401 }));
    });

    await expect(http("/incidents")).rejects.toMatchObject({ code: "UNAUTHENTICATED" });
    expect(useSessionStore.getState().accessToken).toBeNull();
    expect(useSessionStore.getState().status).toBe("anonymous");

    const refreshCalls = fetchMock.mock.calls.filter((call) =>
      String(call[0]).endsWith("/auth/refresh")
    );
    expect(refreshCalls).toHaveLength(1);
  });

  it("parses the typed error envelope", async () => {
    useSessionStore.getState().setSession({
      accessToken: "token",
      user: { id: "u1", email: "a@b.c", fullName: "A" },
    });
    useSessionStore.getState().setActiveOrg("org-1");
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "INVALID_INCIDENT_TRANSITION",
          message: "Incident cannot move from RESOLVED to IDENTIFIED.",
          details: [],
          requestId: "req-9",
        }),
        { status: 409, headers: { "Content-Type": "application/json" } }
      )
    );

    await expect(http("/incidents/x/transitions", { method: "POST", body: {} })).rejects.toMatchObject({
      code: "INVALID_INCIDENT_TRANSITION",
      status: 409,
      requestId: "req-9",
    });
  });

  it("refresh is single-flight: parallel 401s trigger exactly one refresh", async () => {
    useSessionStore.getState().setSession({
      accessToken: "expired",
      user: { id: "u1", email: "a@b.c", fullName: "A" },
    });
    let refreshCount = 0;
    fetchMock.mockImplementation((url: string, init?: { headers?: Record<string, string> }) => {
      if (String(url).endsWith("/auth/refresh")) {
        refreshCount += 1;
        return Promise.resolve(
          okJson({ accessToken: "fresh-" + refreshCount, user: { id: "u1", email: "a@b.c", fullName: "A" } })
        );
      }
      const header = (init?.headers as Record<string, string>)?.["Authorization"] ?? "";
      if (header.includes("expired")) {
        return Promise.resolve(new Response("{}", { status: 401 }));
      }
      return Promise.resolve(okJson({ ok: true }));
    });

    await Promise.all([http("/incidents"), http("/services"), http("/dashboard/summary")]);
    expect(refreshCount).toBe(1);
  });

  it("refresh sends credentials and the CSRF custom header", async () => {
    fetchMock.mockResolvedValue(new Response("{}", { status: 401 }));
    await refreshSession();
    const [url, init] = fetchMock.mock.calls.find((call) =>
      String(call[0]).endsWith("/auth/refresh")
    )!;
    expect(String(url)).toContain("/auth/refresh");
    expect(init!.credentials).toBe("include");
    expect((init!.headers as Record<string, string>)["X-Requested-With"]).toBe("XMLHttpRequest");
    expect(init!.headers!["Cookie"]).toBeUndefined();
  });
});
