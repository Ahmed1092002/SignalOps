"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { fetchMe } from "@/lib/api/auth";
import { refreshSession } from "@/lib/http";
import { useSessionStore } from "@/stores/session-store";

/**
 * Restores the session from the HttpOnly refresh cookie on mount, then guards
 * protected trees. Renders a controlled bootstrap state — protected content is
 * never flashed to unauthenticated users.
 */
export function SessionProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const status = useSessionStore((s) => s.status);
  const setSession = useSessionStore((s) => s.setSession);
  const setMemberships = useSessionStore((s) => s.setMemberships);
  const markBootstrapped = useSessionStore((s) => s.markBootstrapped);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const refreshed = await refreshSession();
      if (cancelled) return;
      if (!refreshed) {
        markBootstrapped();
        return;
      }
      try {
        const me = await fetchMe();
        if (cancelled) return;
        setMemberships(me.memberships);
        const user = useSessionStore.getState().user;
        if (user) setSession({ accessToken: useSessionStore.getState().accessToken ?? "", user });
      } catch {
        useSessionStore.getState().clearSession();
      } finally {
        if (!cancelled) markBootstrapped();
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const onError = (event: PromiseRejectionEvent) => {
      const status = (event.reason as { status?: number }).status;
      if (status === 401) {
        queryClient.clear();
      }
    };
    window.addEventListener("unhandledrejection", onError);
    return () => window.removeEventListener("unhandledrejection", onError);
  }, [queryClient, router]);

  if (status === "bootstrapping") {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-3" role="status" aria-live="polite">
          <SignalOpsMark className="h-10 w-10 animate-pulse text-primary" />
          <span className="text-sm text-muted-foreground">Restoring session…</span>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}

export function SignalOpsMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" fill="none" className={className} aria-hidden="true">
      <rect width="32" height="32" rx="7" className="fill-primary" />
      <path
        d="M7 20 L12 20 L14.5 11 L18 24 L20.5 16 L25 16"
        stroke="white"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
    </svg>
  );
}
