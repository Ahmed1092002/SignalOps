"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, UserPlus } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { RoleBadge } from "@/components/app/badges";
import { PageHeader } from "@/components/app/page-header";
import { PermissionGate } from "@/components/app/permission-gate";
import { EmptyState, ErrorState, TableSkeleton } from "@/components/app/states";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  createInvitation,
  listInvitations,
  listMembers,
  revokeInvitation,
  updateMember,
} from "@/lib/api/organizations";
import { qk } from "@/lib/query-keys";
import { formatRelative } from "@/lib/format";
import type { Role } from "@/lib/types";
import { useSessionStore } from "@/stores/session-store";
import { inviteSchema, type InviteInput } from "@/features/incidents/schemas";

const INVITE_ROLES: Role[] = ["ADMIN", "INCIDENT_COMMANDER", "RESPONDER", "VIEWER"];

export default function TeamPage() {
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const queryClient = useQueryClient();
  const membersQuery = useQuery({
    queryKey: qk.organizations.members(activeOrgId ?? "?"),
    queryFn: ({ signal }) => listMembers(0, 200, signal),
    enabled: Boolean(activeOrgId),
  });
  const invitationsQuery = useQuery({
    queryKey: qk.organizations.invitations(activeOrgId ?? "?"),
    queryFn: ({ signal }) => listInvitations(signal),
    enabled: Boolean(activeOrgId),
  });

  const roleMutation = useMutation({
    mutationFn: (input: { memberId: string; role: Role }) =>
      updateMember(input.memberId, { role: input.role }),
    onSuccess: async (updated) => {
      await queryClient.invalidateQueries({ queryKey: qk.organizations.members(activeOrgId ?? "?") });
      toast.success("Role updated", { description: updated.fullName });
    },
    onError: (error) =>
      toast.error(error instanceof Error ? error.message : "Could not update the role."),
  });

  return (
    <div>
      <PageHeader
        title="Team"
        description="Members, roles, and pending invitations."
        actions={
          <PermissionGate permission="member:manage" fallback={null}>
            <InviteDialog />
          </PermissionGate>
        }
      />

      {membersQuery.isLoading && <TableSkeleton rows={5} columns={4} />}
      {membersQuery.isError && <ErrorState error={membersQuery.error} onRetry={() => membersQuery.refetch()} />}

      {membersQuery.data && (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/40 text-left text-xs uppercase tracking-wider text-muted-foreground">
                <th scope="col" className="px-4 py-2.5 font-medium">Name</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Email</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Role</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Status</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Joined</th>
              </tr>
            </thead>
            <tbody>
              {membersQuery.data.items.map((member) => (
                <tr key={member.memberId} className="border-b last:border-0">
                  <td className="px-4 py-2.5 font-medium">{member.fullName}</td>
                  <td className="px-4 py-2.5 text-muted-foreground">{member.email}</td>
                  <td className="px-4 py-2.5">
                    <PermissionGate permission="member:manage" fallback={null}>
                      <Select
                        value={member.role}
                        onValueChange={(value) =>
                          roleMutation.mutate({ memberId: member.memberId, role: value as Role })
                        }
                      >
                        <SelectTrigger className="h-8 w-44" aria-label={"Role of " + member.fullName}>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {(["OWNER", "ADMIN", "INCIDENT_COMMANDER", "RESPONDER", "VIEWER"] as Role[]).map(
                            (role) => (
                              <SelectItem key={role} value={role}>
                                {role.replace("_", " ")}
                              </SelectItem>
                            )
                          )}
                        </SelectContent>
                      </Select>
                    </PermissionGate>
                    {!isManager() && <RoleBadge role={member.role} />}
                  </td>
                  <td className="px-4 py-2.5">
                    <span
                      className={
                        member.status === "ACTIVE"
                          ? "text-xs font-medium text-svc-operational"
                          : "text-xs font-medium text-muted-foreground"
                      }
                    >
                      {member.status}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-muted-foreground">—</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h2 className="mb-3 mt-8 text-lg font-semibold">Pending invitations</h2>
      {invitationsQuery.data && invitationsQuery.data.length === 0 && (
        <EmptyState title="No pending invitations" className="py-8" />
      )}
      {invitationsQuery.data && invitationsQuery.data.length > 0 && (
        <ul className="grid gap-2 md:grid-cols-2">
          {invitationsQuery.data.map((invitation) => (
            <li key={invitation.id} className="rounded-lg border p-3">
              <div className="flex items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{invitation.email}</p>
                  <p className="text-xs text-muted-foreground">
                    {invitation.role.replace("_", " ")} · invited {formatRelative(invitation.createdAt)}
                  </p>
                </div>
                <PermissionGate permission="member:manage" fallback={null}>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={async () => {
                      await revokeInvitation(invitation.id);
                      await queryClient.invalidateQueries({
                        queryKey: qk.organizations.invitations(activeOrgId ?? "?"),
                      });
                      toast.success("Invitation revoked");
                    }}
                  >
                    Revoke
                  </Button>
                </PermissionGate>
              </div>
              {invitation.inviteToken && (
                <p className="mt-2 break-all rounded bg-muted/50 p-2 text-num text-xs text-muted-foreground">
                  Invite link: /accept-invite?token={invitation.inviteToken}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function isManager(): boolean {
  const membership = useSessionStore.getState().memberships.find((m) => m.status === "ACTIVE");
  return membership?.role === "OWNER" || membership?.role === "ADMIN";
}

function InviteDialog() {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const form = useForm<InviteInput>({
    resolver: zodResolver(inviteSchema),
    defaultValues: { email: "", role: "RESPONDER" },
  });

  const mutation = useMutation({
    mutationFn: (values: InviteInput) => createInvitation({ email: values.email, role: values.role }),
    onSuccess: async (invitation) => {
      setOpen(false);
      form.reset({ email: "", role: "RESPONDER" });
      await queryClient.invalidateQueries({
        queryKey: qk.organizations.invitations(activeOrgId ?? "?"),
      });
      toast.success("Invitation created", {
        description: invitation.inviteToken
          ? "Share the link shown in the pending list. Email delivery arrives in Phase 3."
          : undefined,
      });
    },
    onError: (error) =>
      toast.error(error instanceof Error ? error.message : "Could not create the invitation."),
  });

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <Button onClick={() => setOpen(true)}>
        <UserPlus className="mr-1 h-4 w-4" aria-hidden="true" /> Invite member
      </Button>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Invite a member</DialogTitle>
          <DialogDescription>
            A single-use invite link valid for 7 days. Email delivery arrives in Phase 3 — copy
            the link from the pending list.
          </DialogDescription>
        </DialogHeader>
        <form
          className="grid gap-4"
          noValidate
          onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        >
          <div className="grid gap-1.5">
            <Label htmlFor="invite-email">Email</Label>
            <Input id="invite-email" type="email" required {...form.register("email")} />
            {form.formState.errors.email && (
              <p role="alert" className="text-xs text-destructive">{form.formState.errors.email.message}</p>
            )}
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="invite-role">Role</Label>
            <Select
              defaultValue="RESPONDER"
              onValueChange={(value) => form.setValue("role", value as InviteInput["role"])}
            >
              <SelectTrigger id="invite-role">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {INVITE_ROLES.map((role) => (
                  <SelectItem key={role} value={role}>
                    {role.replace("_", " ")}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
              Create invitation
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
