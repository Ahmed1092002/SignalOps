"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, CheckCheck } from "lucide-react";
import { useEffect, useState } from "react";
import {
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  unreadCount,
} from "@/lib/api/notifications";
import { formatRelative } from "@/lib/format";
import { qk } from "@/lib/query-keys";
import { useSessionStore } from "@/stores/session-store";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { EmptyState } from "@/components/app/states";

export function NotificationBell() {
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);

  const countQuery = useQuery({
    queryKey: qk.notifications.unread(activeOrgId ?? "?"),
    queryFn: ({ signal }) => unreadCount(signal),
    enabled: Boolean(activeOrgId),
    refetchInterval: 60_000,
  });

  const listQuery = useQuery({
    queryKey: qk.notifications.list(activeOrgId ?? "?"),
    queryFn: ({ signal }) => listNotifications(0, 12, signal),
    enabled: Boolean(activeOrgId) && open,
  });

  // Phase 2 realtime: the WS client bumps this query key on /user/queue/notifications.
  useEffect(() => {
    const onSignal = () => {
      void queryClient.invalidateQueries({ queryKey: ["notifications"] });
    };
    window.addEventListener("signalops:notification", onSignal);
    return () => window.removeEventListener("signalops:notification", onSignal);
  }, [queryClient]);

  const markRead = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });
  const markAll = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  const unread = countQuery.data ?? 0;

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label={"Notifications" + (unread > 0 ? ", " + unread + " unread" : "")}
          className="relative inline-flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-2 focus-visible:outline-ring"
        >
          <Bell className="h-4 w-4" aria-hidden="true" />
          {unread > 0 && (
            <span
              aria-hidden="true"
              className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-white"
            >
              {unread > 9 ? "9+" : unread}
            </span>
          )}
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80">
        <div className="flex items-center justify-between px-2 py-1.5">
          <DropdownMenuLabel className="p-0">Notifications</DropdownMenuLabel>
          {unread > 0 && (
            <button
              type="button"
              className="inline-flex items-center gap-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-ring"
              onClick={() => markAll.mutate()}
            >
              <CheckCheck className="h-3.5 w-3.5" aria-hidden="true" /> Mark all read
            </button>
          )}
        </div>
        <DropdownMenuSeparator />
        {listQuery.data && listQuery.data.items.length === 0 && (
          <div className="p-2">
            <EmptyState
              title="Nothing yet"
              description="Assignments, Critical escalations, and published updates appear here."
              className="border-0 py-6"
            />
          </div>
        )}
        {listQuery.data?.items.map((notification) => (
          <button
            key={notification.id}
            type="button"
            className={
              "flex w-full flex-col items-start gap-0.5 border-b px-3 py-2.5 text-left transition-colors last:border-0 hover:bg-accent/60 focus-visible:outline-2 focus-visible:outline-ring " +
              (notification.readAt ? "opacity-60" : "")
            }
            onClick={() => {
              if (!notification.readAt) markRead.mutate(notification.id);
            }}
          >
            <span className="flex w-full items-center justify-between gap-2">
              <span className="truncate text-sm font-medium">{notification.title}</span>
              {!notification.readAt && (
                <span aria-label="unread" className="h-2 w-2 shrink-0 rounded-full bg-primary" />
              )}
            </span>
            <span className="text-xs text-muted-foreground">{notification.body}</span>
            <span className="text-num text-[11px] text-muted-foreground">
              {formatRelative(notification.createdAt)}
            </span>
          </button>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
