import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { downloadAttachment } from "@/lib/api/phase3";
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
});
