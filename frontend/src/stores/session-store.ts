"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { MembershipDto, UserDto } from "@/lib/types";

export type SessionStatus = "bootstrapping" | "authenticated" | "anonymous";

interface SessionState {
  status: SessionStatus;
  accessToken: string | null;
  user: UserDto | null;
  memberships: MembershipDto[];
  activeOrgId: string | null;
  setSession: (input: {
    accessToken: string;
    user: UserDto;
    memberships?: MembershipDto[];
  }) => void;
  setMemberships: (memberships: MembershipDto[]) => void;
  setActiveOrg: (organizationId: string) => void;
  clearSession: () => void;
  markBootstrapped: () => void;
}

/**
 * Security model (docs/phase-0/07-security.md):
 * - the access token lives in memory only (never persisted);
 * - the refresh token is an HttpOnly cookie owned by the backend;
 * - only the selected organization ID is persisted, for convenience.
 */
export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      status: "anonymous",
      accessToken: null,
      user: null,
      memberships: [],
      activeOrgId: null,
      setSession: ({ accessToken, user, memberships }) =>
        set((state) => {
          const membershipsValue =
            memberships ?? state.memberships;
          const stillMember =
            state.activeOrgId &&
            membershipsValue.some(
              (m) => m.organizationId === state.activeOrgId && m.status === "ACTIVE"
            );
          const firstActive = membershipsValue.find((m) => m.status === "ACTIVE");
          return {
            status: "authenticated",
            accessToken,
            user,
            memberships: membershipsValue,
            activeOrgId: stillMember ? state.activeOrgId : (firstActive ? firstActive.organizationId : null),
          };
        }),
      setMemberships: (memberships) =>
        set((state) => {
          const stillMember =
            state.activeOrgId &&
            memberships.some(
              (m) => m.organizationId === state.activeOrgId && m.status === "ACTIVE"
            );
          const firstActive = memberships.find((m) => m.status === "ACTIVE");
          return {
            memberships,
            activeOrgId: stillMember ? state.activeOrgId : (firstActive ? firstActive.organizationId : null),
            status: state.accessToken ? state.status : state.status,
          };
        }),
      setActiveOrg: (organizationId) => set({ activeOrgId: organizationId }),
      clearSession: () =>
        set({
          status: "anonymous",
          accessToken: null,
          user: null,
          memberships: [],
          activeOrgId: null,
        }),
      markBootstrapped: () =>
        set((state) => ({
          status: state.accessToken ? "authenticated" : "anonymous",
        })),
    }),
    {
      name: "signalops.session",
      partialize: (state) => ({ activeOrgId: state.activeOrgId }),
    }
  )
);

export function activeMembership(): MembershipDto | null {
  const { memberships, activeOrgId } = useSessionStore.getState();
  return (
    memberships.find(
      (m) => m.organizationId === activeOrgId && m.status === "ACTIVE"
    ) ?? null
  );
}
