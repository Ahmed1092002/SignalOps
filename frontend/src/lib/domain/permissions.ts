export const ROLES = [
  "OWNER",
  "ADMIN",
  "INCIDENT_COMMANDER",
  "RESPONDER",
  "VIEWER",
] as const;
export type Role = (typeof ROLES)[number];

export const roleLabel: Record<Role, string> = {
  OWNER: "Owner",
  ADMIN: "Admin",
  INCIDENT_COMMANDER: "Incident Commander",
  RESPONDER: "Responder",
  VIEWER: "Viewer",
};

export const COMMANDER_ELIGIBLE_ROLES: readonly Role[] = ["OWNER", "ADMIN", "INCIDENT_COMMANDER"];

export type IncidentPermission =
  | "incident:create"
  | "incident:edit"
  | "incident:transition"
  | "incident:severity"
  | "incident:participants"
  | "incident:comment"
  | "incident:publish"
  | "incident:archive";

export type OrgPermission = "org:manage" | "member:manage" | "service:manage" | "audit:read";

export interface PermissionSubject {
  role: Role;
  isCommander?: boolean;
  isResponder?: boolean;
}

const ORG_MATRIX: Record<Role, readonly OrgPermission[]> = {
  OWNER: ["org:manage", "member:manage", "service:manage", "audit:read"],
  ADMIN: ["org:manage", "member:manage", "service:manage", "audit:read"],
  INCIDENT_COMMANDER: [],
  RESPONDER: [],
  VIEWER: [],
};

export function hasOrgPermission(subject: PermissionSubject, permission: OrgPermission): boolean {
  return ORG_MATRIX[subject.role].includes(permission);
}

export function isCommanderEligible(role: Role): boolean {
  return COMMANDER_ELIGIBLE_ROLES.includes(role);
}

export function hasIncidentPermission(
  subject: PermissionSubject,
  permission: IncidentPermission
): boolean {
  const { role, isCommander = false, isResponder = false } = subject;
  const orgWide = role === "OWNER" || role === "ADMIN";
  const commanding = isCommander;

  switch (permission) {
    case "incident:create":
      return orgWide || role === "INCIDENT_COMMANDER";
    case "incident:edit":
    case "incident:transition":
    case "incident:severity":
    case "incident:participants":
    case "incident:publish":
    case "incident:archive":
      return orgWide || commanding;
    case "incident:comment":
      return orgWide || commanding || (role === "RESPONDER" && isResponder);
    default: {
      const exhaustive: never = permission;
      return exhaustive;
    }
  }
}

export function permissionExplanation(
  subject: PermissionSubject,
  permission: IncidentPermission | OrgPermission
): string {
  const { role, isCommander = false, isResponder = false } = subject;
  if (role === "VIEWER") return "Your role has read-only access.";
  if (permission === "audit:read") return "Only Owners and Admins can view the audit log.";
  if (permission === "incident:comment" && role === "RESPONDER" && !isResponder && !isCommander) {
    return "Responders can comment only on incidents they are assigned to.";
  }
  if (
    permission === "incident:transition" ||
    permission === "incident:severity" ||
    permission === "incident:participants" ||
    permission === "incident:publish" ||
    permission === "incident:archive"
  ) {
    if (role === "RESPONDER") return "Only the Incident Commander or an Admin can do this.";
    if (role === "INCIDENT_COMMANDER" && !isCommander) {
      return "Incident Commanders can manage only incidents they command.";
    }
  }
  return "You do not have permission to perform this action.";
}
