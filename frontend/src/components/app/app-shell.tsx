"use client";

import { Menu, Plus, Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useRef } from "react";
import { ConnectionIndicator } from "@/components/app/connection-indicator";
import { NotificationBell } from "@/components/app/notification-bell";
import { OrganizationSwitcher } from "@/components/app/organization-switcher";
import { ProfileMenu } from "@/components/app/profile-menu";
import { ThemeToggle } from "@/components/app/theme-toggle";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { SidebarContent } from "@/components/app/sidebar-content";
import { useUiStore } from "@/stores/ui-store";

export function AppShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const sidebarOpen = useUiStore((s) => s.sidebarOpen);
  const setSidebarOpen = useUiStore((s) => s.setSidebarOpen);
  const setCreateOpen = useUiStore((s) => s.setCreateIncidentOpen);
  const searchRef = useRef<HTMLInputElement>(null);

  function onSearchSubmit(event: React.FormEvent) {
    event.preventDefault();
    const q = searchRef.current?.value.trim();
    if (q) {
      router.push("/incidents?q=" + encodeURIComponent(q));
    } else {
      router.push("/incidents");
    }
  }

  return (
    <div className="flex min-h-dvh bg-background">
      <aside className="sticky top-0 hidden h-dvh w-60 shrink-0 border-r border-sidebar-border bg-sidebar lg:block">
        <SidebarContent />
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b bg-background/95 px-3 backdrop-blur md:px-4">
          <Sheet open={sidebarOpen} onOpenChange={setSidebarOpen}>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Open navigation menu">
                <Menu className="h-5 w-5" />
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="w-64 bg-sidebar p-0">
              <SheetTitle className="sr-only">Navigation</SheetTitle>
              <SidebarContent onNavigate={() => setSidebarOpen(false)} />
            </SheetContent>
          </Sheet>

          <form onSubmit={onSearchSubmit} role="search" className="hidden max-w-sm flex-1 sm:block">
            <div className="relative">
              <Search
                className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
                aria-hidden="true"
              />
              <Input
                ref={searchRef}
                type="search"
                placeholder="Search incidents…"
                aria-label="Search incidents"
                className="pl-8"
              />
            </div>
          </form>

          <div className="ml-auto flex items-center gap-1.5">
            <OrganizationSwitcher />
            <NotificationBell />
            <ConnectionIndicator />
            <ThemeToggle />
            <ProfileMenu />
            <Button size="sm" className="ml-1" onClick={() => setCreateOpen(true)}>
              <Plus className="mr-1 h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">Create Incident</span>
              <span className="sm:hidden">New</span>
            </Button>
          </div>
        </header>

        <main className="flex-1 px-4 py-6 md:px-6 lg:px-8">{children}</main>
      </div>
    </div>
  );
}
