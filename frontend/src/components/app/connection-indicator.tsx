"use client";

import { Radio } from "lucide-react";
import { useEffect, useState } from "react";
import { WS_URL } from "@/lib/env";
import { onConnectionStateChange, type ConnectionState } from "@/lib/ws-client";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

export function ConnectionIndicator() {
  const [state, setState] = useState<ConnectionState>(WS_URL ? "connecting" : "disconnected");

  useEffect(() => {
    if (!WS_URL) return;
    return onConnectionStateChange(setState);
  }, []);

  const label = WS_URL ? (state === "connected" ? "Live" : state === "connecting" ? "Connecting…" : "Reconnecting…") : "REST";
  const color =
    state === "connected"
      ? "text-svc-operational"
      : state === "connecting"
        ? "text-severity-medium"
        : "text-muted-foreground";

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span
            role="status"
            aria-label={"Realtime connection: " + label}
            className={"inline-flex h-8 items-center gap-1.5 rounded-md px-2 text-xs " + color}
          >
            <Radio className="h-3.5 w-3.5" aria-hidden="true" />
            <span className="hidden md:inline">{label}</span>
          </span>
        </TooltipTrigger>
        <TooltipContent>
          {WS_URL
            ? state === "connected"
              ? "Connected to live updates. Events refetch authoritative REST data."
              : "Reconnecting with backoff — data refreshes on demand meanwhile."
            : "Set NEXT_PUBLIC_WS_URL to enable live updates (backend /ws)."}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
