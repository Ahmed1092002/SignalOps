"use client";

import { Client, type IMessage } from "@stomp/stompjs";
import { WS_URL } from "@/lib/env";
import { useSessionStore } from "@/stores/session-store";

export type ConnectionState = "disconnected" | "connecting" | "connected";

type Listener = (state: ConnectionState) => void;

let client: Client | null = null;
let currentOrgId: string | null = null;
const listeners = new Set<Listener>();

function setState(state: ConnectionState) {
  listeners.forEach((listener) => listener(state));
}

export function onConnectionStateChange(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function invalidate(queryClient: {
  invalidateQueries: (options: { queryKey: string[] }) => Promise<void>;
}) {
  const orgId = useSessionStore.getState().activeOrgId;
  if (!orgId) return;
  void queryClient.invalidateQueries({ queryKey: ["organization", orgId, "incidents"] });
  void queryClient.invalidateQueries({ queryKey: ["organization", orgId, "incident"] });
  void queryClient.invalidateQueries({ queryKey: ["organization", orgId, "services"] });
  void queryClient.invalidateQueries({ queryKey: ["organization", orgId, "dashboard"] });
}

/**
 * Phase 2 realtime: STOMP over the backend /ws endpoint. The access token rides the
 * CONNECT frame; received messages are treated purely as invalidation signals — REST
 * and PostgreSQL remain the source of truth.
 */
export function connectRealtime(
  queryClient: {
    invalidateQueries: (options: { queryKey: string[] }) => Promise<void>;
  },
  onNotification?: () => void
): void {
  if (!WS_URL) return;
  const orgId = useSessionStore.getState().activeOrgId;
  const token = useSessionStore.getState().accessToken;
  if (!orgId || !token) return;
  if (client && currentOrgId === orgId && client.connected) return;
  disconnectRealtime();
  currentOrgId = orgId;

  client = new Client({
    brokerURL: WS_URL,
    reconnectDelay: 3000,
    beforeConnect: () => {
      const latest = useSessionStore.getState().accessToken;
      if (client && latest) {
        client.connectHeaders = { Authorization: "Bearer " + latest };
      }
    },
    onConnect: () => {
      setState("connected");
      client?.subscribe("/topic/org." + orgId + ".incidents", () => invalidate(queryClient));
      client?.subscribe("/topic/org." + orgId + ".services", () => invalidate(queryClient));
      client?.subscribe(
        "/user/queue/notifications",
        (message: IMessage) => {
          onNotification?.();
          void message;
        }
      );
      // Recover anything missed while offline.
      invalidate(queryClient);
    },
    onWebSocketClose: () => setState("disconnected"),
    onStompError: () => setState("disconnected"),
  });
  client.activate();
}

export function disconnectRealtime(): void {
  if (client) {
    client.deactivate();
    client = null;
  }
  setState("disconnected");
}
