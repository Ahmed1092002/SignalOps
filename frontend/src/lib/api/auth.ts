import { http } from "@/lib/http";
import type {
  AuthResponse,
  MeResponse,
  ProfileView,
} from "@/lib/types";
import { useSessionStore } from "@/stores/session-store";

export async function login(email: string, password: string): Promise<AuthResponse> {
  const response = await http<AuthResponse>("/auth/login", {
    method: "POST",
    body: { email, password },
    anonymous: true,
  });
  useSessionStore.getState().setSession({
    accessToken: response.accessToken,
    user: response.user,
  });
  try {
    const me = await fetchMe();
    useSessionStore.getState().setMemberships(me.memberships);
  } catch {
    // me fetch can be handled by shell
  }
  return response;
}

export async function register(input: {
  email: string;
  password: string;
  fullName: string;
  organizationName: string;
}): Promise<AuthResponse> {
  const response = await http<AuthResponse>("/auth/register", {
    method: "POST",
    body: input,
    anonymous: true,
  });
  useSessionStore.getState().setSession({
    accessToken: response.accessToken,
    user: response.user,
  });
  try {
    const me = await fetchMe();
    useSessionStore.getState().setMemberships(me.memberships);
  } catch {
    // me fetch can be handled by shell
  }
  return response;
}

export async function fetchMe(): Promise<MeResponse> {
  return http<MeResponse>("/auth/me");
}

export async function logout(): Promise<void> {
  try {
    await http<void>("/auth/logout", { method: "POST", skipOrgHeader: true });
  } finally {
    useSessionStore.getState().clearSession();
  }
}

export async function updateProfile(input: {
  fullName?: string;
  currentPassword?: string;
  newPassword?: string;
}): Promise<ProfileView> {
  return http<ProfileView>("/users/me", { method: "PATCH", body: input });
}

export async function acceptInvitation(
  token: string
): Promise<{ organizationId: string; organizationName: string; role: string }> {
  return http("/invitations/accept", { method: "POST", body: { token }, skipOrgHeader: true });
}
