import { describe, expect, it } from "vitest";
import {
  allowedTransitions,
  findTransition,
  isTransitionAllowed,
  type IncidentStatus,
} from "@/lib/domain/incident";

describe("incident lifecycle transitions (R7/R13)", () => {
  it("allows exactly the documented forward paths", () => {
    expect(allowedTransitions("DETECTED").map((t) => t.to)).toEqual(["INVESTIGATING"]);
    expect(allowedTransitions("INVESTIGATING").map((t) => t.to)).toEqual(["IDENTIFIED"]);
    expect(allowedTransitions("IDENTIFIED").map((t) => t.to)).toEqual(["MONITORING"]);
    expect(allowedTransitions("MONITORING").map((t) => t.to).sort()).toEqual([
      "INVESTIGATING",
      "RESOLVED",
    ]);
    expect(allowedTransitions("RESOLVED").map((t) => t.to)).toEqual(["INVESTIGATING"]);
  });

  it("rejects impossible jumps", () => {
    expect(isTransitionAllowed("RESOLVED", "IDENTIFIED")).toBe(false);
    expect(isTransitionAllowed("DETECTED", "MONITORING")).toBe(false);
    expect(isTransitionAllowed("INVESTIGATING", "RESOLVED")).toBe(false);
    expect(isTransitionAllowed("RESOLVED", "RESOLVED")).toBe(false);
  });

  it("requires a resolution summary only when entering RESOLVED (R2)", () => {
    const resolve = findTransition("MONITORING", "RESOLVED");
    expect(resolve?.requires).toBe("resolutionSummary");
    expect(findTransition("MONITORING", "INVESTIGATING")?.requires).toBe("reason");
    expect(findTransition("RESOLVED", "INVESTIGATING")?.requires).toBe("reason");
    expect(findTransition("DETECTED", "INVESTIGATING")?.requires).toBeUndefined();
  });

  it("offers reopen only from RESOLVED (R13)", () => {
    const allSources = ["DETECTED", "INVESTIGATING", "IDENTIFIED", "MONITORING", "RESOLVED"] as const;
    expect(allSources.filter((s) => isTransitionAllowed(s, "RESOLVED"))).toEqual(["MONITORING"]);
    expect(allSources.filter((s) => isTransitionAllowed("RESOLVED" as IncidentStatus, s))).toEqual([
      "INVESTIGATING",
    ]);
  });
});
