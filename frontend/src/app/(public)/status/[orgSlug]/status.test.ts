import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchPublicStatus } from "./page";

describe("public status fetchPublicStatus", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("returns not_found status when backend responds with 404", async () => {
    fetchMock.mockResolvedValueOnce(new Response("Not Found", { status: 404 }));

    const result = await fetchPublicStatus("unknown-org");
    expect(result).toEqual({ status: "not_found" });
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining("/public/status/unknown-org"),
      expect.objectContaining({
        headers: { Accept: "application/json" },
        next: { revalidate: 30 },
      })
    );
  });

  it("returns unavailable status when backend responds with 500 error", async () => {
    fetchMock.mockResolvedValueOnce(new Response("Server Error", { status: 500 }));

    const result = await fetchPublicStatus("broken-org");
    expect(result).toEqual({ status: "unavailable" });
  });

  it("returns unavailable status when network fetch throws", async () => {
    fetchMock.mockRejectedValueOnce(new Error("Network failed"));

    const result = await fetchPublicStatus("offline-org");
    expect(result).toEqual({ status: "unavailable" });
  });

  it("returns success status with data when backend responds with 200", async () => {
    const payload = {
      organizationName: "Acme Corp",
      overallStatus: "OPERATIONAL",
      services: [],
      activeIncidents: [],
      pastIncidents: [],
    };
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify(payload), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const result = await fetchPublicStatus("acme");
    expect(result).toEqual({
      status: "success",
      data: payload,
    });
  });
});
