import { describe, expect, it } from "vitest";
import {
  hasIncidentPermission,
  hasOrgPermission,
  isCommanderEligible,
  permissionExplanation,
  type PermissionSubject,
} from "@/lib/domain/permissions";

describe("permission matrix (07-security)", () => {
  const owner: PermissionSubject = { role: "OWNER" };
  const admin: PermissionSubject = { role: "ADMIN" };
  const commanderInCharge: PermissionSubject = { role: "INCIDENT_COMMANDER", isCommander: true };
  const commanderElsewhere: PermissionSubject = { role: "INCIDENT_COMMANDER" };
  const responderAssigned: PermissionSubject = { role: "RESPONDER", isResponder: true };
  const responderUnassigned: PermissionSubject = { role: "RESPONDER" };
  const viewer: PermissionSubject = { role: "VIEWER" };

  it("gives Owner and Admin organization-wide operational control", () => {
    for (const subject of [owner, admin]) {
      expect(hasIncidentPermission(subject, "incident:transition")).toBe(true);
      expect(hasIncidentPermission(subject, "incident:archive")).toBe(true);
      expect(hasIncidentPermission(subject, "incident:publish")).toBe(true);
    }
  });

  it("restricts Incident Commanders to incidents they command", () => {
    expect(hasIncidentPermission(commanderInCharge, "incident:transition")).toBe(true);
    expect(hasIncidentPermission(commanderElsewhere, "incident:transition")).toBe(false);
    expect(hasIncidentPermission(commanderInCharge, "incident:create")).toBe(true);
    expect(hasIncidentPermission(commanderElsewhere, "incident:create")).toBe(true);
  });

  it("lets Responders comment only on their assigned incidents (R4)", () => {
    expect(hasIncidentPermission(responderAssigned, "incident:comment")).toBe(true);
    expect(hasIncidentPermission(responderUnassigned, "incident:comment")).toBe(false);
    expect(hasIncidentPermission(responderAssigned, "incident:transition")).toBe(false);
    expect(hasIncidentPermission(responderAssigned, "incident:severity")).toBe(false);
  });

  it("never lets Viewers mutate (R3)", () => {
    const mutations = [
      "incident:create",
      "incident:edit",
      "incident:transition",
      "incident:severity",
      "incident:participants",
      "incident:comment",
      "incident:publish",
      "incident:archive",
    ] as const;
    for (const permission of mutations) {
      expect(hasIncidentPermission(viewer, permission)).toBe(false);
    }
  });

  it("limits audit log visibility to Owner and Admin", () => {
    expect(hasOrgPermission(owner, "audit:read")).toBe(true);
    expect(hasOrgPermission(admin, "audit:read")).toBe(true);
    expect(hasOrgPermission(commanderInCharge, "audit:read")).toBe(false);
    expect(hasOrgPermission(viewer, "audit:read")).toBe(false);
  });

  it("restricts commander candidacy to Owner/Admin/IC roles (R1)", () => {
    expect(isCommanderEligible("OWNER")).toBe(true);
    expect(isCommanderEligible("ADMIN")).toBe(true);
    expect(isCommanderEligible("INCIDENT_COMMANDER")).toBe(true);
    expect(isCommanderEligible("RESPONDER")).toBe(false);
    expect(isCommanderEligible("VIEWER")).toBe(false);
  });

  it("explains denials in human terms", () => {
    expect(permissionExplanation(viewer, "incident:comment")).toMatch(/read-only/i);
    expect(permissionExplanation(responderUnassigned, "incident:comment")).toMatch(/assigned/i);
    expect(permissionExplanation(commanderElsewhere, "incident:transition")).toMatch(/command/i);
    expect(permissionExplanation(responderUnassigned, "audit:read")).toMatch(/Owner|Admin/i);
  });
});
