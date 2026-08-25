"use client";

import { AuthenticatedShell } from "@/components/app/authenticated-shell";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return <AuthenticatedShell>{children}</AuthenticatedShell>;
}
