"use client";

import { AuthenticatedShell } from "@/components/app/authenticated-shell";
import { SessionProvider } from "@/components/app/session-provider";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <SessionProvider>
      <AuthenticatedShell>{children}</AuthenticatedShell>
    </SessionProvider>
  );
}
