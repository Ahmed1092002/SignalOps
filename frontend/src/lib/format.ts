const UTC_OPTIONS: Intl.DateTimeFormatOptions = {
  timeZone: "UTC",
  month: "short",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
};

export function formatUtcTimestamp(iso: string): string {
  return `${new Intl.DateTimeFormat("en-US", UTC_OPTIONS).format(new Date(iso))} UTC`;
}

export function formatUtcDate(iso: string): string {
  return new Intl.DateTimeFormat("en-US", {
    timeZone: "UTC",
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(iso));
}

export function formatUtcTime(iso: string): string {
  return new Intl.DateTimeFormat("en-US", {
    timeZone: "UTC",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(iso));
}

export function formatRelative(iso: string, now: number = Date.now()): string {
  const diffMs = now - new Date(iso).getTime();
  const future = diffMs < 0;
  const diffSec = Math.round(Math.abs(diffMs) / 1000);

  let value: number;
  let unit: string;
  if (diffSec < 60) {
    value = diffSec;
    unit = "s";
  } else if (diffSec < 3600) {
    value = Math.round(diffSec / 60);
    unit = "m";
  } else if (diffSec < 86400) {
    value = Math.round(diffSec / 3600);
    unit = "h";
  } else if (diffSec < 2592000) {
    value = Math.round(diffSec / 86400);
    unit = "d";
  } else {
    value = Math.round(diffSec / 2592000);
    unit = "mo";
  }
  return future ? `in ${value}${unit}` : `${value}${unit} ago`;
}

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null) return "—";
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  const restMinutes = minutes % 60;
  if (hours < 24) return restMinutes > 0 ? `${hours}h ${restMinutes}m` : `${hours}h`;
  const days = Math.floor(hours / 24);
  const restHours = hours % 24;
  return restHours > 0 ? `${days}d ${restHours}h` : `${days}d`;
}

export function formatMeanDuration(seconds: number | null | undefined): string {
  if (seconds == null) return "—";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest > 0 ? `${hours}h ${rest}m` : `${hours}h`;
}

export function secondsBetween(fromIso: string, toIso: string): number {
  return Math.max(0, Math.round((new Date(toIso).getTime() - new Date(fromIso).getTime()) / 1000));
}

export function minutesAgoIso(minutes: number, now: number = Date.now()): string {
  return new Date(now - minutes * 60_000).toISOString();
}

export function hoursAgoIso(hours: number, now: number = Date.now()): string {
  return minutesAgoIso(hours * 60, now);
}

export function daysAgoIso(days: number, now: number = Date.now()): string {
  return minutesAgoIso(days * 24 * 60, now);
}

export function greeting(date: Date = new Date()): string {
  const hour = date.getHours();
  if (hour < 5) return "Burning the midnight oil";
  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
}
