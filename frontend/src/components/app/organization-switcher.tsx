"use client";

import { Check, ChevronsUpDown } from "lucide-react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { useSessionStore } from "@/stores/session-store";

export function OrganizationSwitcher() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const memberships = useSessionStore((s) => s.memberships);
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const setActiveOrg = useSessionStore((s) => s.setActiveOrg);

  const active = memberships.find((m) => m.organizationId === activeOrgId);

  function switchTo(organizationId: string) {
    if (organizationId === activeOrgId) return;
    setActiveOrg(organizationId);
    queryClient.clear();
    router.push("/dashboard");
  }

  if (memberships.length === 0) return null;

  return (
    <div className="px-3">
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="outline"
            role="combobox"
            aria-label="Switch active organization"
            className="h-9 w-full justify-between px-3 font-normal"
          >
            <span className="truncate">{active?.organizationName ?? "Select organization"}</span>
            <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" aria-hidden="true" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-56">
          <DropdownMenuLabel>Organizations</DropdownMenuLabel>
          <DropdownMenuSeparator />
          {memberships
            .filter((m) => m.status === "ACTIVE")
            .map((membership) => (
              <DropdownMenuItem
                key={membership.organizationId}
                onSelect={() => switchTo(membership.organizationId)}
                className="gap-2"
              >
                <Check
                  className={cn(
                    "h-4 w-4",
                    membership.organizationId === activeOrgId ? "opacity-100" : "opacity-0"
                  )}
                  aria-hidden="true"
                />
                <span className="truncate">{membership.organizationName}</span>
                <span className="ml-auto text-xs text-muted-foreground">{membership.role.replace("_", " ").toLowerCase()}</span>
              </DropdownMenuItem>
            ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
