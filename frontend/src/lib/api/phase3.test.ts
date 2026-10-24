import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { downloadAttachment, uploadAttachment } from "@/lib/api/phase3";
import { useSessionStore } from "@/stores/session-store";

describe("phase3 downloadAttachment", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    useSessionStore.getState().clearSession();
    useSessionStore.getState().markBootstrapped();
    fetchMock.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("fetches attachment with authentication and triggers download", async () => {
    useSessionStore.getState().setSession({
      accessToken: "token-abc",
      user: { id: "user-1", email: "user@example.com", fullName: "User One" },
    });
    useSessionStore.getState().setActiveOrg("org-456");

    const blob = new Blob(["test file content"], { type: "text/plain" });
    const response = new Response(blob, {
      status: 200,
      headers: {
        "Content-Disposition": 'attachment; filename="report.txt"',
      },
    });
    fetchMock.mockResolvedValue(response);

    const createObjectURLMock = vi.fn().mockReturnValue("blob:mock-url");
    const revokeObjectURLMock = vi.fn();
    const clickMock = vi.fn();
    const removeMock = vi.fn();

    const mockAnchor = {
      href: "",
      download: "",
      click: clickMock,
      remove: removeMock,
    };

    const mockDocument = {
      createElement: vi.fn().mockReturnValue(mockAnchor),
      body: {
        appendChild: vi.fn(),
      },
    };

    const mockWindow = {
      URL: {
        createObjectURL: createObjectURLMock,
        revokeObjectURL: revokeObjectURLMock,
      },
    };

    vi.stubGlobal("window", mockWindow);
    vi.stubGlobal("document", mockDocument);

    await downloadAttachment("inc-123", "att-789", "report.txt");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("/incidents/inc-123/attachments/att-789/download");
    expect(init.headers["Authorization"]).toBe("Bearer token-abc");
    expect(init.headers["X-Organization-Id"]).toBe("org-456");
    expect(init.headers["X-Requested-With"]).toBe("XMLHttpRequest");
    expect(init.credentials).toBe("include");

    expect(createObjectURLMock).toHaveBeenCalledTimes(1);
    expect(mockDocument.createElement).toHaveBeenCalledWith("a");
    expect(mockAnchor.download).toBe("report.txt");
    expect(mockAnchor.href).toBe("blob:mock-url");
    expect(mockDocument.body.appendChild).toHaveBeenCalledWith(mockAnchor);
    expect(clickMock).toHaveBeenCalledTimes(1);
    expect(removeMock).toHaveBeenCalledTimes(1);
    expect(revokeObjectURLMock).toHaveBeenCalledWith("blob:mock-url");
  });

  it("throws formatted error when download fails", async () => {
    useSessionStore.getState().setSession({
      accessToken: "token-abc",
      user: { id: "user-1", email: "user@example.com", fullName: "User One" },
    });
    useSessionStore.getState().setActiveOrg("org-456");

    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ message: "Attachment not found." }), {
        status: 404,
        headers: { "Content-Type": "application/json" },
      })
    );

    await expect(
      downloadAttachment("inc-123", "nonexistent", "missing.txt")
    ).rejects.toThrow("Attachment not found.");
  });

  it("refreshes once on 401 and retries the download request", async () => {
    useSessionStore.getState().setSession({
      accessToken: "stale-token",
      user: { id: "user-1", email: "user@example.com", fullName: "User One" },
    });
    useSessionStore.getState().setActiveOrg("org-456");

    const blob = new Blob(["retried download content"], { type: "text/plain" });

    fetchMock
      .mockResolvedValueOnce(new Response("Unauthorized", { status: 401 }))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            accessToken: "new-token",
            user: { id: "user-1", email: "user@example.com", fullName: "User One" },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      )
      .mockResolvedValueOnce(new Response(blob, { status: 200 }));

    const mockAnchor = { href: "", download: "", click: vi.fn(), remove: vi.fn() };
    vi.stubGlobal("document", {
      createElement: vi.fn().mockReturnValue(mockAnchor),
      body: { appendChild: vi.fn() },
    });
    vi.stubGlobal("window", {
      URL: { createObjectURL: vi.fn().mockReturnValue("blob:new-url"), revokeObjectURL: vi.fn() },
    });

    await downloadAttachment("inc-123", "att-456", "fresh.txt");

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[0][0]).toContain("/attachments/att-456/download");
    expect(fetchMock.mock.calls[1][0]).toContain("/auth/refresh");
    expect(fetchMock.mock.calls[2][0]).toContain("/attachments/att-456/download");
    expect(fetchMock.mock.calls[2][1].headers["Authorization"]).toBe("Bearer new-token");
    expect(useSessionStore.getState().accessToken).toBe("new-token");
  });

  it("clears the session and does not loop when refresh fails on 401", async () => {
    useSessionStore.getState().setSession({
      accessToken: "stale-token",
      user: { id: "user-1", email: "user@example.com", fullName: "User One" },
    });

    fetchMock
      .mockResolvedValueOnce(new Response("Unauthorized", { status: 401 }))
      .mockResolvedValueOnce(new Response("Refresh expired", { status: 401 }));

    await expect(downloadAttachment("inc-123", "att-456", "file.txt")).rejects.toThrow();

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(useSessionStore.getState().accessToken).toBeNull();
    expect(useSessionStore.getState().status).toBe("anonymous");
  });

  it("does not loop infinitely when retried download also returns 401", async () => {
    useSessionStore.getState().setSession({
      accessToken: "stale-token",
      user: { id: "user-1", email: "user@example.com", fullName: "User One" },
    });

    fetchMock
      .mockResolvedValueOnce(new Response("Unauthorized", { status: 401 }))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            accessToken: "new-token",
            user: { id: "user-1", email: "user@example.com", fullName: "User One" },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      )
      .mockResolvedValueOnce(new Response("Still unauthorized", { status: 401 }));

    await expect(downloadAttachment("inc-123", "att-456", "file.txt")).rejects.toThrow();

    // 1 original + 1 refresh + 1 retry = exactly 3 calls
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("uploadAttachment refreshes once on 401 and retries with FormData", async () => {
    useSessionStore.getState().setSession({
      accessToken: "stale-token",
      user: { id: "user-1", email: "user@example.com", fullName: "User One" },
    });
    useSessionStore.getState().setActiveOrg("org-456");

    const uploadedView = {
      id: "att-uploaded",
      filename: "upload.png",
      contentType: "image/png",
      sizeBytes: 123,
      createdAt: "2026-10-24T12:00:00Z",
    };

    fetchMock
      .mockResolvedValueOnce(new Response("Unauthorized", { status: 401 }))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            accessToken: "new-token",
            user: { id: "user-1", email: "user@example.com", fullName: "User One" },
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(uploadedView), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        })
      );

    const file = new File(["image data"], "upload.png", { type: "image/png" });
    const result = await uploadAttachment("inc-123", file);

    expect(result).toEqual(uploadedView);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    const retryCall = fetchMock.mock.calls[2];
    expect(retryCall[1].headers["Authorization"]).toBe("Bearer new-token");
    expect(retryCall[1].headers["Content-Type"]).toBeUndefined(); // Browser sets multipart boundary
    expect(retryCall[1].credentials).toBe("include");
  });
});
