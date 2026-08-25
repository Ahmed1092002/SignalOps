"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Archive, Loader2, Plus } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { PageHeader } from "@/components/app/page-header";
import { PermissionGate } from "@/components/app/permission-gate";
import { ServiceStatusBadge } from "@/components/app/badges";
import { ErrorState, TableSkeleton, EmptyState } from "@/components/app/states";
import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
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
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { archiveService, createService, listServices, updateService } from "@/lib/api/services";
import { dashboardSummary } from "@/lib/api/dashboard";
import { fieldErrorsOf } from "@/lib/api-client";
import { qk } from "@/lib/query-keys";
import { formatUtcTimestamp } from "@/lib/format";
import { useSessionStore } from "@/stores/session-store";
import { serviceSchema, type ServiceInput } from "@/features/incidents/schemas";
import { MaintenanceWindowsCard } from "@/components/app/phase3-cards";

export default function ServicesPage() {
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const [includeArchived, setIncludeArchived] = useState(false);

  const servicesQuery = useQuery({
    queryKey: qk.services.list(activeOrgId ?? "?", includeArchived),
    queryFn: ({ signal }) => listServices(includeArchived, signal),
    enabled: Boolean(activeOrgId),
  });
  const dashboardQuery = useQuery({
    queryKey: qk.dashboard.summary(activeOrgId ?? "?"),
    queryFn: ({ signal }) => dashboardSummary(signal),
    enabled: Boolean(activeOrgId),
  });

  const healthByName = new Map(
    (dashboardQuery.data?.serviceHealth ?? []).map((entry) => [entry.serviceId, entry])
  );

  return (
    <div>
      <PageHeader
        title="Services"
        description="Your organization's service catalog. Status is derived from active incidents."
        actions={
          <PermissionGate permission="service:manage" fallback={null}>
            <ServiceDialog trigger={<Button><Plus className="mr-1 h-4 w-4" aria-hidden="true" /> New service</Button>} />
          </PermissionGate>
        }
      />

      <label className="mb-3 flex items-center gap-2 text-sm text-muted-foreground">
        <Switch checked={includeArchived} onCheckedChange={setIncludeArchived} />
        Include archived
      </label>

      {servicesQuery.isLoading && <TableSkeleton rows={5} columns={4} />}
      {servicesQuery.isError && (
        <ErrorState error={servicesQuery.error} onRetry={() => servicesQuery.refetch()} />
      )}
      {servicesQuery.data && servicesQuery.data.length === 0 && (
        <EmptyState
          title="No services yet"
          description="Services represent the parts of your system that can be affected by incidents."
        />
      )}

      <div className="mt-6">`n        <MaintenanceWindowsCard />`n      </div>`n`n      {servicesQuery.data && servicesQuery.data.length > 0 && (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/40 text-left text-xs uppercase tracking-wider text-muted-foreground">
                <th scope="col" className="px-4 py-2.5 font-medium">Name</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Team</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Status</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Public</th>
                <th scope="col" className="px-4 py-2.5 font-medium">Created</th>
                <th scope="col" className="px-4 py-2.5 font-medium"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {servicesQuery.data.map((service) => {
                const health = healthByName.get(service.id);
                return (
                  <tr key={service.id} className="border-b last:border-0">
                    <td className="px-4 py-2.5">
                      <p className="font-medium">{service.name}</p>
                      {service.description && (
                        <p className="line-clamp-1 text-xs text-muted-foreground">{service.description}</p>
                      )}
                    </td>
                    <td className="px-4 py-2.5 text-muted-foreground">{service.owningTeam || "—"}</td>
                    <td className="px-4 py-2.5">
                      {service.archivedAt ? (
                        <span className="text-xs text-muted-foreground">Archived</span>
                      ) : health ? (
                        <ServiceStatusBadge status={health.status} />
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </td>
                    <td className="px-4 py-2.5 text-muted-foreground">{service.isPublic ? "Yes" : "No"}</td>
                    <td className="px-4 py-2.5 text-num text-muted-foreground">
                      {formatUtcTimestamp(service.createdAt)}
                    </td>
                    <td className="px-4 py-2.5">
                      <PermissionGate permission="service:manage" fallback={null}>
                        <div className="flex justify-end gap-2">
                          <ServiceDialog
                            trigger={<Button variant="outline" size="sm">Edit</Button>}
                            service={service}
                          />
                          {!service.archivedAt && (
                            <ArchiveButton serviceId={service.id} serviceName={service.name} />
                          )}
                        </div>
                      </PermissionGate>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function ServiceDialog({
  trigger,
  service,
}: {
  trigger: React.ReactNode;
  service?: { id: string; name: string; description: string; owningTeam: string; isPublic: boolean };
}) {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const form = useForm<ServiceInput>({
    resolver: zodResolver(serviceSchema),
    defaultValues: {
      name: service?.name ?? "",
      description: service?.description ?? "",
      owningTeam: service?.owningTeam ?? "",
      isPublic: service?.isPublic ?? true,
    },
  });

  const mutation = useMutation({
    mutationFn: (values: ServiceInput) =>
      service
        ? updateService(service.id, values)
        : createService({
            name: values.name,
            description: values.description || undefined,
            owningTeam: values.owningTeam || undefined,
            isPublic: values.isPublic,
          }),
    onSuccess: async () => {
      setOpen(false);
      await queryClient.invalidateQueries({ queryKey: ["organization", activeOrgId] });
      toast.success(service ? "Service updated" : "Service created");
    },
    onError: (error) => {
      const fields = fieldErrorsOf(error);
      for (const [field, message] of Object.entries(fields)) {
        form.setError(field as keyof ServiceInput, { message });
      }
      toast.error(error instanceof Error ? error.message : "Could not save the service.");
    },
  });

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <div onClick={() => setOpen(true)} role="presentation">{trigger}</div>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{service ? "Edit service" : "Create service"}</DialogTitle>
          <DialogDescription>
            Status is derived automatically from active incidents — it is never set by hand.
          </DialogDescription>
        </DialogHeader>
        <form
          className="grid gap-4"
          noValidate
          onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
        >
          <div className="grid gap-1.5">
            <Label htmlFor="service-name">Name</Label>
            <Input id="service-name" required {...form.register("name")} />
            {form.formState.errors.name && (
              <p role="alert" className="text-xs text-destructive">{form.formState.errors.name.message}</p>
            )}
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="service-description">Description</Label>
            <Textarea id="service-description" rows={2} {...form.register("description")} />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="service-team">Owning team</Label>
            <Input id="service-team" {...form.register("owningTeam")} />
          </div>
          <label className="flex items-center gap-2 text-sm">
            <Switch
              checked={form.watch("isPublic") ?? true}
              onCheckedChange={(checked) => form.setValue("isPublic", checked)}
            />
            Show on the public status page (when Phase 2 arrives)
          </label>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
              {service ? "Save changes" : "Create service"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function ArchiveButton({ serviceId, serviceName }: { serviceId: string; serviceName: string }) {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const mutation = useMutation({
    mutationFn: () => archiveService(serviceId),
    onSuccess: async () => {
      setOpen(false);
      await queryClient.invalidateQueries({ queryKey: ["organization", activeOrgId] });
      toast.success("Service archived");
    },
    onError: (error) => {
      setOpen(false);
      toast.error(
        error instanceof Error && (error as { code?: string }).code === "SERVICE_HAS_ACTIVE_INCIDENTS"
          ? "This service has active incidents and cannot be archived."
          : error instanceof Error
            ? error.message
            : "Could not archive the service."
      );
    },
  });

  return (
    <>
      <Button variant="outline" size="sm" onClick={() => setOpen(true)}>
        <Archive className="mr-1 h-3.5 w-3.5" aria-hidden="true" /> Archive
      </Button>
      <AlertDialog open={open} onOpenChange={setOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Archive “{serviceName}”?</AlertDialogTitle>
            <AlertDialogDescription>
              Archived services leave active views and derived status. Services with active
              incidents cannot be archived.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={(event) => {
                event.preventDefault();
                mutation.mutate();
              }}
            >
              {mutation.isPending ? "Archiving…" : "Archive"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
