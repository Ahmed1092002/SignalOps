import { describe, expect, it } from "vitest";
import {
  deriveServiceStatus,
  severityToServiceStatus,
  worstServiceStatus,
} from "@/lib/domain/service-status";

describe("derived service status (R10)", () => {
  it("maps severities to the documented public statuses", () => {
    expect(severityToServiceStatus("CRITICAL")).toBe("MAJOR_OUTAGE");
    expect(severityToServiceStatus("HIGH")).toBe("PARTIAL_OUTAGE");
    expect(severityToServiceStatus("MEDIUM")).toBe("DEGRADED_PERFORMANCE");
    expect(severityToServiceStatus("LOW")).toBe("OPERATIONAL");
  });

  it("picks the worst active incident", () => {
    expect(worstServiceStatus(["OPERATIONAL", "PARTIAL_OUTAGE", "DEGRADED_PERFORMANCE"])).toBe(
      "PARTIAL_OUTAGE"
    );
    expect(worstServiceStatus([])).toBe("OPERATIONAL");
  });

  it("derives MAJOR_OUTAGE when a CRITICAL incident is active", () => {
    expect(deriveServiceStatus(["MEDIUM", "CRITICAL"])).toBe("MAJOR_OUTAGE");
  });

  it("keeps LOW-severity incidents from degrading the public page", () => {
    expect(deriveServiceStatus(["LOW"])).toBe("OPERATIONAL");
    expect(deriveServiceStatus(["LOW", "LOW"])).toBe("OPERATIONAL");
  });

  it("recovers to OPERATIONAL when nothing is active", () => {
    expect(deriveServiceStatus([])).toBe("OPERATIONAL");
  });
});
