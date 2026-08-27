"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { SeverityBadge } from "@/components/app/badges";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { cn } from "@/lib/utils";
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
import { Textarea } from "@/components/ui/textarea";
import { listMembers } from "@/lib/api/organizations";
import { createIncident } from "@/lib/api/incidents";
import { listServices } from "@/lib/api/services";
import { isCommanderEligible } from "@/lib/domain/permissions";
import { SEVERITIES, severityDefinition } from "@/lib/domain/severity";
import { qk } from "@/lib/query-keys";
import { fieldErrorsOf } from "@/lib/api-client";
import { useSessionStore } from "@/stores/session-store";
import { useUiStore } from "@/stores/ui-store";
import { createIncidentSchema, type CreateIncidentInput } from "@/features/incidents/schemas";

export function CreateIncidentDialog() {
  const open = useUiStore((s) => s.createIncidentOpen);
  const setOpen = useUiStore((s) => s.setCreateIncidentOpen);
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const router = useRouter();
  const queryClient = useQueryClient();

  const servicesQuery = useQuery({
    queryKey: qk.services.list(activeOrgId ?? "?", false),
    queryFn: ({ signal }) => listServices(false, signal),
    enabled: open && Boolean(activeOrgId),
  });
  const membersQuery = useQuery({
    queryKey: qk.organizations.members(activeOrgId ?? "?"),
    queryFn: ({ signal }) => listMembers(0, 200, signal),
    enabled: open && Boolean(activeOrgId),
  });

  const form = useForm<CreateIncidentInput>({
    resolver: zodResolver(createIncidentSchema),
    defaultValues: {
      title: "",
      description: "",
      severity: "MEDIUM",
      serviceIds: [],
      commanderUserId: null,
      responderUserIds: [],
    },
    mode: "onTouched",
  });

  const severity = form.watch("severity");
  const serviceIds = form.watch("serviceIds");
  const responderUserIds = form.watch("responderUserIds") ?? [];

  useEffect(() => {
    if (open) {
      form.reset({
        title: "",
        description: "",
        severity: "MEDIUM",
        serviceIds: [],
        commanderUserId: null,
        responderUserIds: [],
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const members = useMemo(() => membersQuery.data?.items ?? [], [membersQuery.data]);
  const eligibleCommanders = useMemo(
    () => members.filter((m) => m.status === "ACTIVE" && isCommanderEligible(m.role)),
    [members]
  );
  const availableResponders = useMemo(
    () => members.filter((m) => m.status === "ACTIVE" && m.userId !== form.watch("commanderUserId")),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [members, form.watch("commanderUserId")]
  );

  const mutation = useMutation({
    mutationFn: (values: CreateIncidentInput) =>
      createIncident({
        title: values.title,
        description: values.description ?? "",
        severity: values.severity,
        serviceIds: values.serviceIds,
        commanderUserId: values.commanderUserId ?? null,
        responderUserIds: values.responderUserIds ?? [],
      }),
    onSuccess: async (incident) => {
      setOpen(false);
      await queryClient.invalidateQueries({ queryKey: ["organization", activeOrgId] });
      toast.success("Incident created", { description: incident.key + " is now DETECTED." });
      router.push(`/incidents/${incident.id}`);
    },
    onError: (error) => {
      const fields = fieldErrorsOf(error);
      for (const [field, message] of Object.entries(fields)) {
        form.setError(field as keyof CreateIncidentInput, { message });
      }
      toast.error(error instanceof Error ? error.message : "Could not create the incident.");
    },
  });

  function toggleService(id: string) {
    const next = serviceIds.includes(id)
      ? serviceIds.filter((value) => value !== id)
      : [...serviceIds, id];
    form.setValue("serviceIds", next, { shouldValidate: true });
  }

  function toggleResponder(userId: string) {
    const next = responderUserIds.includes(userId)
      ? responderUserIds.filter((value) => value !== userId)
      : [...responderUserIds, userId];
    form.setValue("responderUserIds", next, { shouldValidate: true });
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="max-h-[92dvh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Declare Incident</DialogTitle>
          <DialogDescription>
            New incidents start in <strong>Detected</strong>. Critical incidents require an
            Incident Commander.
          </DialogDescription>
        </DialogHeader>
        <form
          onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
          noValidate
          className="grid gap-4"
        >
          <div className="grid gap-1.5">
            <Label htmlFor="incident-title">
              Title <span aria-hidden="true" className="text-destructive">*</span>
            </Label>
            <Input
              id="incident-title"
              placeholder="e.g. Elevated API latency in US-East"
              aria-required="true"
              aria-invalid={Boolean(form.formState.errors.title)}
              {...form.register("title")}
            />
            {form.formState.errors.title && (
              <p role="alert" className="text-xs text-destructive">
                {form.formState.errors.title.message}
              </p>
            )}
          </div>

          <fieldset className="grid gap-1.5">
            <legend className="text-sm font-medium">Severity</legend>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {SEVERITIES.map((level) => {
                const isSelected = severity === level;
                return (
                  <button
                    key={level}
                    type="button"
                    role="radio"
                    aria-checked={isSelected}
                    onClick={() => form.setValue("severity", level, { shouldValidate: true })}
                    className={cn(
                      "flex flex-col items-center justify-between rounded-lg border p-2.5 text-center transition-all cursor-pointer select-none",
                      isSelected
                        ? "border-primary bg-primary/10 ring-2 ring-primary/30 shadow-xs font-semibold"
                        : "border-border bg-card hover:bg-muted/60 hover:border-muted-foreground/30 opacity-75 hover:opacity-100"
                    )}
                  >
                    <SeverityBadge severity={level} />
                    <p className="mt-1.5 line-clamp-2 text-[11px] leading-tight text-muted-foreground">
                      {severityDefinition[level]}
                    </p>
                  </button>
                );
              })}
            </div>
          </fieldset>

          <div className="grid gap-1.5">
            <Label htmlFor="incident-description">Description</Label>
            <Textarea
              id="incident-description"
              rows={4}
              placeholder="Initial context, symptoms, and impact…"
              {...form.register("description")}
            />
          </div>

          <fieldset className="grid gap-1.5">
            <legend className="text-sm font-medium">
              Affected services <span aria-hidden="true" className="text-destructive">*</span>
            </legend>
            {servicesQuery.isLoading ? (
              <p className="text-sm text-muted-foreground">Loading services…</p>
            ) : (servicesQuery.data?.length ?? 0) === 0 ? (
              <p className="text-sm text-muted-foreground">
                No services yet — create one under Services first (an incident must affect at
                least one service).
              </p>
            ) : (
              <div className="grid gap-1.5 rounded-md border p-2.5 sm:grid-cols-2">
                {servicesQuery.data?.map((service) => (
                  <label key={service.id} className="flex items-center gap-2 text-sm">
                    <Checkbox
                      checked={serviceIds.includes(service.id)}
                      onCheckedChange={() => toggleService(service.id)}
                      aria-label={"Affect service " + service.name}
                    />
                    {service.name}
                  </label>
                ))}
              </div>
            )}
            {form.formState.errors.serviceIds && (
              <p role="alert" className="text-xs text-destructive">
                {form.formState.errors.serviceIds.message as string}
              </p>
            )}
          </fieldset>

          <div className="grid gap-1.5">
            <Label htmlFor="incident-commander">Incident Commander</Label>
            <Select
              value={form.watch("commanderUserId") ?? undefined}
              onValueChange={(value) => form.setValue("commanderUserId", value)}
            >
              <SelectTrigger id="incident-commander" aria-invalid={Boolean(form.formState.errors.commanderUserId)}>
                <SelectValue placeholder={severity === "CRITICAL" ? "Required for Critical" : "Optional"} />
              </SelectTrigger>
              <SelectContent>
                {eligibleCommanders.map((member) => (
                  <SelectItem key={member.memberId} value={member.userId}>
                    {member.fullName} ({member.role.replace("_", " ").toLowerCase()})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {severity === "CRITICAL" && (
              <p className="text-xs text-muted-foreground">
                Critical incidents must have a commander before they can be created.
              </p>
            )}
            {form.formState.errors.commanderUserId && (
              <p role="alert" className="text-xs text-destructive">
                {form.formState.errors.commanderUserId.message as string}
              </p>
            )}
          </div>

          {availableResponders.length > 0 && (
            <fieldset className="grid gap-1.5">
              <legend className="text-sm font-medium">Responders (optional)</legend>
              <div className="grid gap-1.5 rounded-md border p-2.5 sm:grid-cols-2">
                {availableResponders.map((member) => (
                  <label key={member.memberId} className="flex items-center gap-2 text-sm">
                    <Checkbox
                      checked={responderUserIds.includes(member.userId)}
                      onCheckedChange={() => toggleResponder(member.userId)}
                      aria-label={"Assign " + member.fullName + " as responder"}
                    />
                    {member.fullName}
                  </label>
                ))}
              </div>
            </fieldset>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
              Create Incident
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
