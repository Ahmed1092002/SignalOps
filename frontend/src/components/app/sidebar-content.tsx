"use client";

import { Boxes, LayoutDashboard, ScrollText, Settings, Siren, Users } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { ConnectionIndicator } from "@/components/app/connection-indicator";
import { OrganizationSwitcher } from "@/components/app/organization-switcher";
import { SignalOpsMark } from "@/components/app/session-provider";
import { cn } from "@/lib/utils";
import { activeMembership } from "@/stores/session-store";
import { useUiStore } from "@/stores/ui-store";

const NAV_ITEMS = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/incidents", label: "Incidents", icon: Siren },
  { href: "/services", label: "Services", icon: Boxes },
  { href: "/team", label: "Team", icon: Users },
  { href: "/audit", label: "Audit Log", icon: ScrollText },
  { href: "/settings", label: "Settings", icon: Settings },
];

export function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const setCreateOpen = useUiStore((s) => s.setCreateIncidentOpen);
  const membership = activeMembership();
  const canCreate = membership?.role !== "VIEWER";

  return (
    <div className="flex h-full flex-col gap-4 py-4">
      <div className="flex items-center gap-2.5 px-4">
        <SignalOpsMark className="h-8 w-8" />
        <div className="leading-tight">
          <div className="text-sm font-semibold">SignalOps</div>
          <div className="text-[11px] text-muted-foreground">Incident Management</div>
        </div>
      </div>
      <OrganizationSwitcher />
      <nav aria-label="Primary" className="flex flex-col gap-1 px-3">
        {NAV_ITEMS.map((item) => {
          const active = pathname === item.href || pathname.startsWith(item.href + "/");
          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onNavigate}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-ring",
                active
                  ? "bg-sidebar-accent text-sidebar-accent-foreground shadow-[inset_2px_0_0_0] shadow-primary"
                  : "text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground"
              )}
            >
              <item.icon className="h-4 w-4 shrink-0" aria-hidden="true" />
              {item.label}
            </Link>
          );
        })}
      </nav>
      <div className="mt-auto flex flex-col gap-3 px-4">
        <ConnectionIndicator />
        {canCreate && (
          <button
            type="button"
            onClick={() => {
              setCreateOpen(true);
              onNavigate?.();
            }}
            className="inline-flex h-9 w-full items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90 focus-visible:outline-2 focus-visible:outline-ring"
          >
            + Create Incident
          </button>
        )}
      </div>
    </div>
  );
}
