"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { PageHeader } from "@/components/app/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { updateProfile } from "@/lib/api/auth";
import { useSessionStore } from "@/stores/session-store";
import { profileSchema, type ProfileInput } from "@/features/incidents/schemas";

export default function SettingsPage() {
  const user = useSessionStore((s) => s.user);
  const memberships = useSessionStore((s) => s.memberships);
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const active = memberships.find((m) => m.organizationId === activeOrgId);

  const form = useForm<ProfileInput>({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: user?.fullName ?? "", currentPassword: "", newPassword: "" },
  });

  useEffect(() => {
    if (user) form.setValue("fullName", user.fullName);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.fullName]);

  const mutation = useMutation({
    mutationFn: (values: ProfileInput) =>
      updateProfile({
        fullName: values.fullName,
        currentPassword: values.currentPassword || undefined,
        newPassword: values.newPassword || undefined,
      }),
    onSuccess: async () => {
      form.setValue("currentPassword", "");
      form.setValue("newPassword", "");
      toast.success("Profile updated");
    },
    onError: (error) =>
      toast.error(error instanceof Error ? error.message : "Could not update profile."),
  });

  return (
    <div className="max-w-2xl">
      <PageHeader title="Settings" description="Your profile and organization context." />

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Profile</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            className="grid gap-4"
            noValidate
            onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
          >
            <div className="grid gap-1.5">
              <Label htmlFor="settings-name">Full name</Label>
              <Input id="settings-name" {...form.register("fullName")} />
              {form.formState.errors.fullName && (
                <p role="alert" className="text-xs text-destructive">
                  {form.formState.errors.fullName.message}
                </p>
              )}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="settings-email">Email</Label>
              <Input id="settings-email" value={user?.email ?? ""} disabled readOnly />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="settings-current-password">Current password</Label>
              <Input
                id="settings-current-password"
                type="password"
                autoComplete="current-password"
                {...form.register("currentPassword")}
              />
              {form.formState.errors.currentPassword && (
                <p role="alert" className="text-xs text-destructive">
                  {form.formState.errors.currentPassword.message}
                </p>
              )}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="settings-new-password">New password</Label>
              <Input
                id="settings-new-password"
                type="password"
                autoComplete="new-password"
                aria-describedby="new-password-hint"
                {...form.register("newPassword")}
              />
              <p id="new-password-hint" className="text-xs text-muted-foreground">
                Leave blank to keep your current password.
              </p>
              {form.formState.errors.newPassword && (
                <p role="alert" className="text-xs text-destructive">
                  {form.formState.errors.newPassword.message}
                </p>
              )}
            </div>
            <div>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
                Save changes
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {active && (
        <Card className="mt-6">
          <CardHeader>
            <CardTitle className="text-base">Organization</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-1.5 text-sm">
            <div className="flex justify-between gap-2">
              <span className="text-muted-foreground">Name</span>
              <span className="font-medium">{active.organizationName}</span>
            </div>
            <div className="flex justify-between gap-2">
              <span className="text-muted-foreground">Slug</span>
              <span className="text-num">{active.organizationSlug}</span>
            </div>
            <div className="flex justify-between gap-2">
              <span className="text-muted-foreground">Your role</span>
              <span>{active.role.replace("_", " ")}</span>
            </div>
            <p className="mt-2 text-xs text-muted-foreground">
              Organization settings management (rename, ownership) is available to Owners and
              Admins via the API; the settings UI arrives with a later milestone.
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
