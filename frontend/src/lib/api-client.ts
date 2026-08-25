import { ApiError } from "@/lib/types";

export * from "@/lib/types";

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError;
}

export function isStaleWrite(value: unknown): value is ApiError {
  return isApiError(value) && value.code === "STALE_WRITE";
}

export function currentVersionOf(value: unknown): number | null {
  if (isApiError(value)) {
    const entry = value.details.find((d) => typeof d.currentVersion === "number");
    if (entry) return entry.currentVersion as number;
  }
  return null;
}

export function fieldErrorsOf(value: unknown): Record<string, string> {
  if (!isApiError(value)) return {};
  const result: Record<string, string> = {};
  for (const detail of value.details) {
    if (typeof detail.field === "string" && typeof detail.message === "string") {
      result[detail.field] = detail.message;
    }
  }
  return result;
}
