import { http } from "@/lib/http";
import type { PageResponse } from "@/lib/types";

export interface NotificationView {
  id: string;
  type: string;
  title: string;
  body: string;
  entityType: string | null;
  entityId: string | null;
  readAt: string | null;
  createdAt: string;
}

export async function listNotifications(
  page = 0,
  size = 15,
  signal?: AbortSignal
): Promise<PageResponse<NotificationView>> {
  return http<PageResponse<NotificationView>>(
    `/notifications?page=${page}&size=${size}`,
    { signal }
  );
}

export async function unreadCount(signal?: AbortSignal): Promise<number> {
  return http<number>("/notifications/unread-count", { signal });
}

export async function markNotificationRead(id: string): Promise<void> {
  return http<void>(`/notifications/${id}/read`, { method: "POST" });
}

export async function markAllNotificationsRead(): Promise<void> {
  return http<void>("/notifications/read-all", { method: "POST" });
}
