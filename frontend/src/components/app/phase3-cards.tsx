"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Loader2, Paperclip, Plus, Square, Trash2 } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { currentSubject } from "@/components/app/permission-gate";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  AttachmentView,
  createFollowUpTask,
  downloadAttachment,
  listAttachments,
  listFollowUpTasks,
  toggleFollowUpTask,
  uploadAttachment,
} from "@/lib/api/phase3";
import { formatUtcTimestamp } from "@/lib/format";
import { qk } from "@/lib/query-keys";
import { useSessionStore } from "@/stores/session-store";
import type { IncidentView } from "@/lib/types";

export function FollowUpTasksCard({ incident }: { incident: IncidentView }) {
  const queryClient = useQueryClient();
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const user = useSessionStore((s) => s.user);
  const subject = currentSubject(incident, user?.id ?? null);
  const canManage =
    subject.role === "OWNER" ||
    subject.role === "ADMIN" ||
    (subject.role === "INCIDENT_COMMANDER" && subject.isCommander);
  const [title, setTitle] = useState("");

  const tasksQuery = useQuery({
    queryKey: qk.incidents.tasks(activeOrgId ?? "?", incident.id),
    queryFn: ({ signal }) => listFollowUpTasks(incident.id, signal),
    enabled: Boolean(activeOrgId),
  });

  function invalidate() {
    void queryClient.invalidateQueries({
      queryKey: qk.incidents.tasks(activeOrgId ?? "?", incident.id),
    });
    void queryClient.invalidateQueries({
      queryKey: qk.incidents.detail(activeOrgId ?? "?", incident.id),
    });
  }

  const createMutation = useMutation({
    mutationFn: () => createFollowUpTask(incident.id, { title: title.trim(), assigneeUserId: user?.id ?? null }),
    onSuccess: () => {
      setTitle("");
      invalidate();
      toast.success("Follow-up task added");
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "Could not add task."),
  });

  const toggleMutation = useMutation({
    mutationFn: (taskId: string) => toggleFollowUpTask(incident.id, taskId),
    onSuccess: invalidate,
    onError: (error) => toast.error(error instanceof Error ? error.message : "Could not update task."),
  });

  const tasks = tasksQuery.data ?? [];
  const openCount = tasks.filter((task) => task.status === "OPEN").length;

  return (
    <section className="rounded-lg border bg-card">
      <h2 className="border-b px-4 py-2.5 text-sm font-semibold">
        Follow-up tasks ({openCount} open)
      </h2>
      <div className="space-y-3 p-4">
        {tasksQuery.isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
        {tasks.length === 0 && !tasksQuery.isLoading && (
          <p className="text-sm text-muted-foreground">
            No follow-up tasks yet — capture post-incident actions here.
          </p>
        )}
        <ul className="space-y-1.5">
          {tasks.map((task) => {
            const mayToggle =
              canManage ||
              task.assigneeUserId === user?.id ||
              task.status === "DONE";
            return (
              <li key={task.id} className="flex items-center justify-between gap-2 text-sm">
                <button
                  type="button"
                  className="flex min-w-0 items-center gap-2 text-left focus-visible:outline-2 focus-visible:outline-ring"
                  onClick={() => mayToggle && toggleMutation.mutate(task.id)}
                  disabled={toggleMutation.isPending}
                  aria-label={(task.status === "OPEN" ? "Complete task: " : "Reopen task: ") + task.title}
                >
                  {task.status === "DONE" ? (
                    <Check className="h-4 w-4 shrink-0 text-svc-operational" aria-hidden="true" />
                  ) : (
                    <Square className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                  )}
                  <span className={task.status === "DONE" ? "truncate text-muted-foreground line-through" : "truncate"}>
                    {task.title}
                  </span>
                </button>
                {task.dueAt && task.status === "OPEN" && (
                  <span className="shrink-0 text-num text-xs text-muted-foreground">
                    due {formatUtcTimestamp(task.dueAt)}
                  </span>
                )}
              </li>
            );
          })}
        </ul>
        {canManage && (
          <form
            className="flex gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              if (title.trim()) createMutation.mutate();
            }}
          >
            <Label htmlFor="task-title" className="sr-only">
              New follow-up task
            </Label>
            <Input
              id="task-title"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="e.g. Add saturation alerting"
              className="h-9"
            />
            <Button type="submit" size="sm" variant="outline" disabled={createMutation.isPending || !title.trim()}>
              {createMutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
              ) : (
                <Plus className="h-4 w-4" aria-hidden="true" />
              )}
              <span className="sr-only">Add task</span>
            </Button>
          </form>
        )}
      </div>
    </section>
  );
}

export function AttachmentsCard({ incident }: { incident: IncidentView }) {
  const queryClient = useQueryClient();
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const user = useSessionStore((s) => s.user);
  const subject = currentSubject(incident, user?.id ?? null);
  const canUpload =
    subject.role === "OWNER" ||
    subject.role === "ADMIN" ||
    (subject.role === "INCIDENT_COMMANDER" && subject.isCommander);
  const fileRef = useRef<HTMLInputElement>(null);

  const attachmentsQuery = useQuery({
    queryKey: qk.incidents.attachments(activeOrgId ?? "?", incident.id),
    queryFn: ({ signal }) => listAttachments(incident.id, signal),
    enabled: Boolean(activeOrgId),
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => uploadAttachment(incident.id, file),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: qk.incidents.attachments(activeOrgId ?? "?", incident.id),
      });
      void queryClient.invalidateQueries({
        queryKey: qk.incidents.detail(activeOrgId ?? "?", incident.id),
      });
      toast.success("Attachment uploaded");
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "Upload failed."),
  });

  const attachments = attachmentsQuery.data ?? [];
  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  const handleDownload = async (attachment: AttachmentView) => {
    setDownloadingId(attachment.id);
    try {
      await downloadAttachment(incident.id, attachment.id, attachment.filename);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Download failed.");
    } finally {
      setDownloadingId(null);
    }
  };

  return (
    <section className="rounded-lg border bg-card">
      <h2 className="border-b px-4 py-2.5 text-sm font-semibold">
        Attachments ({attachments.length})
      </h2>
      <div className="space-y-3 p-4">
        {attachments.length === 0 && (
          <p className="text-sm text-muted-foreground">
            No attachments. Files up to 5 MB are stored with the incident.
          </p>
        )}
        <ul className="space-y-1.5">
          {attachments.map((attachment) => (
            <li key={attachment.id} className="flex items-center justify-between gap-2 text-sm">
              <button
                type="button"
                onClick={() => void handleDownload(attachment)}
                disabled={downloadingId === attachment.id}
                className="flex min-w-0 items-center gap-2 text-primary hover:underline focus-visible:outline-2 focus-visible:outline-ring disabled:opacity-50 text-left"
              >
                {downloadingId === attachment.id ? (
                  <Loader2 className="h-3.5 w-3.5 shrink-0 animate-spin" aria-hidden="true" />
                ) : (
                  <Paperclip className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                )}
                <span className="truncate">{attachment.filename}</span>
              </button>
              <span className="text-num shrink-0 text-xs text-muted-foreground">
                {Math.max(1, Math.round(attachment.sizeBytes / 1024))} KB
              </span>
            </li>
          ))}
        </ul>
        {canUpload && (
          <>
            <input
              ref={fileRef}
              type="file"
              className="sr-only"
              aria-label="Choose attachment to upload"
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) uploadMutation.mutate(file);
                event.target.value = "";
              }}
            />
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={uploadMutation.isPending}
              onClick={() => fileRef.current?.click()}
            >
              {uploadMutation.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />
              ) : (
                <Paperclip className="mr-2 h-4 w-4" aria-hidden="true" />
              )}
              Upload attachment
            </Button>
          </>
        )}
      </div>
    </section>
  );
}

export function MaintenanceWindowsCard() {
  const queryClient = useQueryClient();
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const [open, setOpen] = useState(false);
  const windowsQuery = useQuery({
    queryKey: ["organization", activeOrgId ?? "?", "maintenance"],
    queryFn: async ({ signal }) => {
      const { listMaintenanceWindows } = await import("@/lib/api/phase3");
      return listMaintenanceWindows(signal);
    },
    enabled: Boolean(activeOrgId),
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      const { deleteMaintenanceWindow } = await import("@/lib/api/phase3");
      return deleteMaintenanceWindow(id);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["organization", activeOrgId ?? "?", "maintenance"] });
      void queryClient.invalidateQueries({ queryKey: ["organization", activeOrgId ?? "?", "dashboard"] });
      toast.success("Maintenance window removed");
    },
  });

  const windows = windowsQuery.data ?? [];

  return (
    <section className="rounded-lg border bg-card">
      <h2 className="border-b px-4 py-2.5 text-sm font-semibold">Maintenance windows</h2>
      <div className="space-y-3 p-4">
        {windows.length === 0 && (
          <p className="text-sm text-muted-foreground">
            None scheduled. Active windows override derived status to MAINTENANCE.
          </p>
        )}
        <ul className="space-y-1.5 text-sm">
          {windows.map((window) => (
            <li key={window.id} className="flex items-center justify-between gap-2">
              <div className="min-w-0">
                <p className="truncate font-medium">{window.title}</p>
                <p className="text-num text-xs text-muted-foreground">
                  {formatUtcTimestamp(window.startsAt)} → {formatUtcTimestamp(window.endsAt)}
                  {window.active && " · active"}
                </p>
              </div>
              <button
                type="button"
                aria-label={"Delete window " + window.title}
                className="text-muted-foreground hover:text-destructive focus-visible:outline-2 focus-visible:outline-ring"
                onClick={() => deleteMutation.mutate(window.id)}
              >
                <Trash2 className="h-4 w-4" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
        <Button variant="outline" size="sm" onClick={() => setOpen(true)}>
          <Plus className="mr-1 h-4 w-4" aria-hidden="true" /> Schedule maintenance
        </Button>
        {open && (
          <MaintenanceForm
            onClose={() => setOpen(false)}
            onCreated={() => {
              setOpen(false);
              void queryClient.invalidateQueries({
                queryKey: ["organization", activeOrgId ?? "?", "maintenance"],
              });
              void queryClient.invalidateQueries({
                queryKey: ["organization", activeOrgId ?? "?", "dashboard"],
              });
            }}
          />
        )}
      </div>
    </section>
  );
}

function MaintenanceForm({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: () => void;
}) {
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const servicesQuery = useQuery({
    queryKey: ["organization", activeOrgId ?? "?", "services", { includeArchived: false }],
    queryFn: async ({ signal }) => {
      const { listServices } = await import("@/lib/api/services");
      return listServices(false, signal);
    },
    enabled: Boolean(activeOrgId),
  });
  const [serviceId, setServiceId] = useState("");
  const [title, setTitle] = useState("");
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");

  const mutation = useMutation({
    mutationFn: async () => {
      const { createMaintenanceWindow } = await import("@/lib/api/phase3");
      return createMaintenanceWindow({
        serviceId,
        title: title.trim(),
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
      });
    },
    onSuccess: () => {
      toast.success("Maintenance scheduled");
      onCreated();
    },
    onError: (error) =>
      toast.error(error instanceof Error ? error.message : "Could not schedule maintenance."),
  });

  return (
    <form
      className="grid gap-2 rounded-md border p-3"
      onSubmit={(event) => {
        event.preventDefault();
        mutation.mutate();
      }}
    >
      <div className="grid gap-1.5">
        <Label htmlFor="maintenance-service">Service</Label>
        <select
          id="maintenance-service"
          value={serviceId}
          onChange={(event) => setServiceId(event.target.value)}
          className="h-9 rounded-md border border-input bg-transparent px-2 text-sm focus-visible:outline-2 focus-visible:outline-ring"
          required
        >
          <option value="">Select a service…</option>
          {servicesQuery.data?.map((service) => (
            <option key={service.id} value={service.id}>
              {service.name}
            </option>
          ))}
        </select>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="maintenance-title">Title</Label>
        <Input
          id="maintenance-title"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          required
        />
      </div>
      <div className="grid gap-1.5 sm:grid-cols-2">
        <div className="grid gap-1.5">
          <Label htmlFor="maintenance-start">Starts</Label>
          <Input
            id="maintenance-start"
            type="datetime-local"
            value={startsAt}
            onChange={(event) => setStartsAt(event.target.value)}
            required
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="maintenance-end">Ends</Label>
          <Input
            id="maintenance-end"
            type="datetime-local"
            value={endsAt}
            onChange={(event) => setEndsAt(event.target.value)}
            required
          />
        </div>
      </div>
      <div className="flex justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" size="sm" disabled={mutation.isPending || !serviceId || !title.trim()}>
          {mutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
          Schedule
        </Button>
      </div>
    </form>
  );
}
