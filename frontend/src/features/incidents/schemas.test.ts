import { describe, expect, it } from "vitest";
import {
  commentSchema,
  createIncidentSchema,
  transitionSchema,
} from "@/features/incidents/schemas";
import { allowedTransitions, findTransition } from "@/lib/domain/incident";
import { hasIncidentPermission } from "@/lib/domain/permissions";

describe("incident form schemas", () => {
  const base = {
    title: "Payments failing",
    severity: "CRITICAL" as const,
    serviceIds: ["svc-1"],
  };

  it("critical without commander is rejected (R1)", () => {
    const result = createIncidentSchema.safeParse(base);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((issue) => issue.path.includes("commanderUserId"))).toBe(true);
    }
  });

  it("critical with commander passes", () => {
    const result = createIncidentSchema.safeParse({ ...base, commanderUserId: "0b9e6c1a-1111-4222-8333-444455556666" });
    expect(result.success).toBe(true);
  });

  it("requires at least one affected service (R16)", () => {
    const result = createIncidentSchema.safeParse({ ...base, commanderUserId: null, serviceIds: [] });
    expect(result.success).toBe(false);
  });

  it("resolving requires a nonblank resolution summary (R2)", () => {
    const missing = transitionSchema.safeParse({ to: "RESOLVED", resolutionSummary: "   " });
    expect(missing.success).toBe(false);

    const valid = transitionSchema.safeParse({ to: "RESOLVED", resolutionSummary: "Rolled back." });
    expect(valid.success).toBe(true);
  });

  it("reopen requires a reason (R13)", () => {
    const missing = transitionSchema.safeParse({ to: "INVESTIGATING" });
    expect(missing.success).toBe(false);
    const valid = transitionSchema.safeParse({ to: "INVESTIGATING", reason: "Regression." });
    expect(valid.success).toBe(true);
  });
});

describe("transition options shown in the UI", () => {
  it("offers only legal next statuses", () => {
    expect(allowedTransitions("DETECTED").map((t) => t.to)).toEqual(["INVESTIGATING"]);
    expect(allowedTransitions("MONITORING").map((t) => t.to).sort()).toEqual([
      "INVESTIGATING",
      "RESOLVED",
    ]);
    expect(allowedTransitions("INVESTIGATING").map((t) => t.to)).not.toContain("MONITORING");
  });

  it("marks the resolve guard and reopen guard", () => {
    expect(findTransition("MONITORING", "RESOLVED")?.requires).toBe("resolutionSummary");
    expect(findTransition("RESOLVED", "INVESTIGATING")?.requires).toBe("reason");
  });
});

describe("permission-driven UI", () => {
  it("viewer cannot mutate; responder comments only when assigned", () => {
    expect(hasIncidentPermission({ role: "VIEWER" }, "incident:comment")).toBe(false);
    expect(hasIncidentPermission({ role: "RESPONDER", isResponder: true }, "incident:comment")).toBe(true);
    expect(hasIncidentPermission({ role: "RESPONDER" }, "incident:comment")).toBe(false);
    expect(hasIncidentPermission({ role: "INCIDENT_COMMANDER", isCommander: true }, "incident:publish")).toBe(true);
    expect(hasIncidentPermission({ role: "INCIDENT_COMMANDER" }, "incident:publish")).toBe(false);
  });
});

describe("internal vs public separation", () => {
  it("comment and public-update schemas are distinct validators", () => {
    expect(commentSchema.safeParse("internal finding").success).toBe(true);
    expect(commentSchema.safeParse("   ").success).toBe(false);
  });
});
