export const SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const;
export type Severity = (typeof SEVERITIES)[number];

export const severityToken: Record<Severity, string> = {
  LOW: "var(--severity-low)",
  MEDIUM: "var(--severity-medium)",
  HIGH: "var(--severity-high)",
  CRITICAL: "var(--severity-critical)",
};

export const severityLabel: Record<Severity, string> = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  CRITICAL: "Critical",
};

export const severityDefinition: Record<Severity, string> = {
  LOW: "Minor issue, negligible user impact, workaround exists.",
  MEDIUM: "Noticeable degradation of non-critical function or a limited user subset.",
  HIGH: "Major functionality degraded or unavailable for many users; revenue or SLA risk.",
  CRITICAL: "Critical service fully down, data-loss or security event. All hands.",
};

export function severityRank(severity: Severity): number {
  return SEVERITIES.indexOf(severity);
}

export function isMoreSevere(a: Severity, b: Severity): boolean {
  return severityRank(a) > severityRank(b);
}
