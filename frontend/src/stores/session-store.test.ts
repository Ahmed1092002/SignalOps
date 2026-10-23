import { beforeEach, describe, expect, it } from "vitest";
import { useSessionStore } from "@/stores/session-store";

describe("useSessionStore", () => {
  beforeEach(() => {
    useSessionStore.getState().clearSession();
  });

  it("defaults to anonymous status so public and auth pages render immediately without blocking", () => {
    expect(useSessionStore.getState().status).toBe("anonymous");
    expect(useSessionStore.getState().accessToken).toBeNull();
  });

  it("updates status to authenticated when session is established", () => {
    useSessionStore.getState().setSession({
      accessToken: "access-token-123",
      user: {
        id: "user-1",
        email: "user@example.com",
        fullName: "Test User",
      },
    });

    expect(useSessionStore.getState().status).toBe("authenticated");
    expect(useSessionStore.getState().accessToken).toBe("access-token-123");
  });

  it("clears session back to anonymous status", () => {
    useSessionStore.getState().setSession({
      accessToken: "access-token-123",
      user: {
        id: "user-1",
        email: "user@example.com",
        fullName: "Test User",
      },
    });
    expect(useSessionStore.getState().status).toBe("authenticated");

    useSessionStore.getState().clearSession();
    expect(useSessionStore.getState().status).toBe("anonymous");
    expect(useSessionStore.getState().accessToken).toBeNull();
  });

  it("markBootstrapped resolves to authenticated if token present, anonymous otherwise", () => {
    useSessionStore.getState().markBootstrapped();
    expect(useSessionStore.getState().status).toBe("anonymous");

    useSessionStore.getState().setSession({
      accessToken: "token-abc",
      user: {
        id: "user-2",
        email: "user2@example.com",
        fullName: "User Two",
      },
    });
    useSessionStore.getState().markBootstrapped();
    expect(useSessionStore.getState().status).toBe("authenticated");
  });
});
