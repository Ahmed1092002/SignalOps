export const INCIDENT_STATUSES = [
  "DETECTED",
  "INVESTIGATING",
  "IDENTIFIED",
  "MONITORING",
  "RESOLVED",
] as const;
export type IncidentStatus = (typeof INCIDENT_STATUSES)[number];

export const ACTIVE_STATUSES: readonly IncidentStatus[] = [
  "DETECTED",
  "INVESTIGATING",
  "IDENTIFIED",
  "MONITORING",
];

export const incidentStatusLabel: Record<IncidentStatus, string> = {
  DETECTED: "Detected",
  INVESTIGATING: "Investigating",
  IDENTIFIED: "Identified",
  MONITORING: "Monitoring",
  RESOLVED: "Resolved",
};

export const incidentStatusToken: Record<IncidentStatus, string> = {
  DETECTED: "var(--inc-detected)",
  INVESTIGATING: "var(--inc-investigating)",
  IDENTIFIED: "var(--inc-identified)",
  MONITORING: "var(--inc-monitoring)",
  RESOLVED: "var(--inc-resolved)",
};

export type TransitionRequirement = "reason" | "resolutionSummary";

export interface IncidentTransition {
  to: IncidentStatus;
  requires?: TransitionRequirement;
}

const ALLOWED_TRANSITIONS: Record<IncidentStatus, readonly IncidentTransition[]> = {
  DETECTED: [{ to: "INVESTIGATING" }],
  INVESTIGATING: [{ to: "IDENTIFIED" }],
  IDENTIFIED: [{ to: "MONITORING" }],
  MONITORING: [
    { to: "RESOLVED", requires: "resolutionSummary" },
    { to: "INVESTIGATING", requires: "reason" },
  ],
  RESOLVED: [{ to: "INVESTIGATING", requires: "reason" }],
};

export function allowedTransitions(from: IncidentStatus): readonly IncidentTransition[] {
  return ALLOWED_TRANSITIONS[from];
}

export function isTransitionAllowed(from: IncidentStatus, to: IncidentStatus): boolean {
  return ALLOWED_TRANSITIONS[from].some((t) => t.to === to);
}

export function findTransition(
  from: IncidentStatus,
  to: IncidentStatus
): IncidentTransition | undefined {
  return ALLOWED_TRANSITIONS[from].find((t) => t.to === to);
}

export function isActiveStatus(status: IncidentStatus): boolean {
  return ACTIVE_STATUSES.includes(status);
}
