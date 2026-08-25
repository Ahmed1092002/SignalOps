"use client";
import { useParams } from "next/navigation";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Archive, ChevronDown, Loader2, Send } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { IncidentStatusBadge, SeverityBadge } from "@/components/app/badges";
import { ConflictDialog } from "@/components/app/conflict-dialog";
import { currentSubject } from "@/components/app/permission-gate";
import { CardSkeleton, EmptyState, ErrorState } from "@/components/app/states";
import { Badge } from "@/components/ui/badge";
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import {
  addComment,
  addResponder,
  archiveIncident,
  changeSeverity,
  getIncident,
  listComments,
  listPublicUpdates,
  publishPublicUpdate,
  setCommander,
  removeResponder,
  transitionIncident,
} from "@/lib/api/incidents";
import { listMembers } from "@/lib/api/organizations";
import { currentVersionOf } from "@/lib/api-client";
import {
  allowedTransitions,
  incidentStatusLabel,
  type IncidentStatus,
} from "@/lib/domain/incident";
import { isCommanderEligible } from "@/lib/domain/permissions";
import { SEVERITIES, severityLabel } from "@/lib/domain/severity";
import { formatDuration, formatRelative, formatUtcTimestamp } from "@/lib/format";
import { qk } from "@/lib/query-keys";
import type { IncidentView } from "@/lib/types";
import { FollowUpTasksCard, AttachmentsCard } from "@/components/app/phase3-cards";
import { useSessionStore } from "@/stores/session-store";

export default function IncidentDetailPage() {
  const params = useParams<{ id: string }>();
  const incidentId = params.id;
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const queryClient = useQueryClient();

  const detailQuery = useQuery({
    queryKey: qk.incidents.detail(activeOrgId ?? "?", incidentId),
    queryFn: ({ signal }) => getIncident(incidentId, signal),
    enabled: Boolean(activeOrgId),
  });
  const commentsQuery = useQuery({
    queryKey: qk.incidents.comments(activeOrgId ?? "?", incidentId),
    queryFn: ({ signal }) => listComments(incidentId, signal),
    enabled: Boolean(activeOrgId),
  });
  const updatesQuery = useQuery({
    queryKey: qk.incidents.publicUpdates(activeOrgId ?? "?", incidentId),
    queryFn: ({ signal }) => listPublicUpdates(incidentId, signal),
    enabled: Boolean(activeOrgId),
  });

  const incident = detailQuery.data?.incident;
  const now = useNow(30_000);

  if (detailQuery.isLoading) {
    return (
      <div className="space-y-4" aria-busy="true">
        <Skeleton className="h-6 w-64" />
        <Skeleton className="h-10 w-96 max-w-full" />
        <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
          <CardSkeleton lines={8} />
          <CardSkeleton lines={6} />
        </div>
      </div>
    );
  }
  if (detailQuery.isError) {
    return (
      <ErrorState
        error={detailQuery.error}
        onRetry={() => detailQuery.refetch()}
        title="Could not load this incident"
      />
    );
  }
  if (!incident) return null;

  function invalidateIncidentData() {
    const detailKey = qk.incidents.detail(activeOrgId ?? "?", incidentId);
    void queryClient.invalidateQueries({ queryKey: detailKey });
    void queryClient.invalidateQueries({ queryKey: qk.incidents.comments(activeOrgId ?? "?", incidentId) });
    void queryClient.invalidateQueries({ queryKey: qk.incidents.publicUpdates(activeOrgId ?? "?", incidentId) });
    void queryClient.invalidateQueries({ queryKey: ["organization", activeOrgId, "incidents"] });
    void queryClient.invalidateQueries({ queryKey: ["organization", activeOrgId, "dashboard"] });
  }

  return (
    <div>
      <nav aria-label="Breadcrumb" className="mb-2 text-sm text-muted-foreground">
        <Link href="/incidents" className="hover:underline focus-visible:outline-2 focus-visible:outline-ring">
          Incidents
        </Link>
        <span aria-hidden="true"> / </span>
        <span className="text-num">{incident.key}</span>
      </nav>

      <div className="flex flex-wrap items-center gap-2">
        <SeverityBadge severity={incident.severity} />
        <IncidentStatusBadge status={incident.status} />
        {incident.archivedAt && <Badge variant="outline">Archived</Badge>}
        <span className="text-num text-xs text-muted-foreground" title="Current version">
          v{incident.version}
        </span>
        <span className="text-xs text-muted-foreground" aria-live="polite">
          {incident.durationSeconds != null
            ? "Resolved in " + formatDuration(incident.durationSeconds)
            : "Open for " + formatDuration(
                Math.max(0, Math.round((now - new Date(incident.createdAt).getTime()) / 1000))
              )}
        </span>
        <div className="ml-auto flex items-center gap-2">
          <IncidentActions incident={incident} onChanged={invalidateIncidentData} />
        </div>
      </div>

      <h1 className="mt-2 text-2xl font-semibold tracking-tight">{incident.title}</h1>

      <div className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-w-0 space-y-4">
          <Card title="Context">
            <p className="whitespace-pre-wrap text-sm text-muted-foreground">
              {incident.description || "No description provided."}
            </p>
          </Card>

          <ComposerCard incident={incident} onChanged={invalidateIncidentData} />

          <Card title={"Internal comments (" + (commentsQuery.data?.length ?? 0) + ")"}>
            <CommentsList comments={commentsQuery.data ?? []} />
          </Card>

          <Card title={"Timeline (" + (detailQuery.data?.timeline.length ?? 0) + ")"}>
            <Timeline entries={detailQuery.data?.timeline ?? []} />
          </Card>
        </div>

        <aside className="space-y-4">
          <Card title="Affected services">
            {incident.services.length === 0 ? (
              <p className="text-sm text-muted-foreground">None recorded.</p>
            ) : (
              <ul className="space-y-2">
                {incident.services.map((service) => (
                  <li key={service.id} className="flex items-center justify-between gap-2 text-sm">
                    <span className="truncate">{service.name}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <ParticipantsCard incident={incident} onChanged={invalidateIncidentData} />`n`n          <FollowUpTasksCard incident={incident} />`n`n          <AttachmentsCard incident={incident} />

          <Card title="Details">
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between gap-2">
                <dt className="text-muted-foreground">Created</dt>
                <dd className="text-num">{formatUtcTimestamp(incident.createdAt)}</dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt className="text-muted-foreground">Updated</dt>
                <dd className="text-num">{formatRelative(incident.updatedAt)}</dd>
              </div>
              {incident.acknowledgedAt && (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">Acknowledged</dt>
                  <dd className="text-num">{formatRelative(incident.acknowledgedAt)}</dd>
                </div>
              )}
              {incident.resolvedAt && (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">Resolved</dt>
                  <dd className="text-num">{formatUtcTimestamp(incident.resolvedAt)}</dd>
                </div>
              )}
              {incident.resolutionSummary && (
                <div>
                  <dt className="text-muted-foreground">Resolution</dt>
                  <dd className="mt-1 whitespace-pre-wrap rounded-md border bg-muted/40 p-2 text-sm">
                    {incident.resolutionSummary}
                  </dd>
                </div>
              )}
            </dl>
          </Card>

          <Card title={"Public updates (" + (updatesQuery.data?.length ?? 0) + ")"}>
            <PublicUpdates updates={updatesQuery.data ?? []} />
          </Card>
        </aside>
      </div>
    </div>
  );
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-lg border bg-card">
      <h2 className="border-b px-4 py-2.5 text-sm font-semibold">{title}</h2>
      <div className="p-4">{children}</div>
    </section>
  );
}

function Timeline({ entries }: { entries: { id: string; type: string; actor: { fullName: string } | null; occurredAt: string; payload: Record<string, unknown> }[] }) {
  if (entries.length === 0) {
    return <EmptyState title="No timeline events" className="py-8" />;
  }
  function describe(entry: { type: string; payload: Record<string, unknown> }): string {
    const from = entry.payload.from as string | undefined;
    const to = entry.payload.to as string | undefined;
    const body = entry.payload.body as string | undefined;
    const source = entry.payload.source as string | undefined;
    switch (entry.type) {
      case "STATUS_CHANGED":
        return "Status changed" + (from && to ? ": " + from + " → " + to : "");
      case "SEVERITY_CHANGED":
        return "Severity changed" + (from && to ? ": " + from + " → " + to : "");
      case "INCIDENT_RESOLVED":
        return "Incident resolved" + (body ? " — " + body : "");
      case "INCIDENT_REOPENED":
        return "Incident reopened" + (body ? " — " + body : "");
      case "COMMENT_ADDED":
        return "Comment: " + (body ?? "");
      case "PUBLIC_UPDATE_PUBLISHED":
        return "Public update: " + (body ?? "");
      case "RESPONDER_ASSIGNED":
        return "Responder assigned" + (entry.payload.userId ? "" : "");
      case "INCIDENT_CREATED":
        return "Incident created" + (entry.payload.key ? " as " + entry.payload.key : "");
      case "DETECTION_SOURCE":
        return "Detected via " + (source ?? "monitoring") + (entry.payload.snippet ? ": " + entry.payload.snippet : "");
      default:
        return entry.type.replaceAll("_", " ").toLowerCase();
    }
  }
  return (
    <ol className="relative space-y-4 border-l pl-4" aria-live="polite">
      {entries.map((entry) => (
        <li key={entry.id} className="relative">
          <span
            aria-hidden="true"
            className="absolute -left-[21px] top-1.5 h-2.5 w-2.5 rounded-full border-2 border-background bg-primary"
          />
          <div className="flex flex-wrap items-baseline justify-between gap-x-3">
            <p className="text-sm">{describe(entry)}</p>
            <time dateTime={entry.occurredAt} className="text-num text-xs text-muted-foreground">
              {formatUtcTimestamp(entry.occurredAt)}
            </time>
          </div>
          {entry.actor && <p className="text-xs text-muted-foreground">by {entry.actor.fullName}</p>}
        </li>
      ))}
    </ol>
  );
}

function IncidentActions({ incident, onChanged }: { incident: IncidentView; onChanged: () => void }) {
  const [conflictOpen, setConflictOpen] = useState(false);
  const [conflictVersion, setConflictVersion] = useState<number | null>(null);
  const [transitionTarget, setTransitionTarget] = useState<IncidentStatus | null>(null);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const user = useSessionStore((s) => s.user);
  const subject = currentSubject(incident, user?.id ?? null);
  const canOperate =
    subject.role === "OWNER" ||
    subject.role === "ADMIN" ||
    (subject.role === "INCIDENT_COMMANDER" && subject.isCommander);

  function handleMutationError(error: unknown) {
    if (currentVersionOf(error) !== null || (error as { code?: string }).code === "STALE_WRITE") {
      setConflictVersion(currentVersionOf(error));
      setConflictOpen(true);
      return;
    }
    toast.error(error instanceof Error ? error.message : "The change could not be saved.");
  }

  const transitionMutation = useMutation({
    mutationFn: (input: { to: IncidentStatus; resolutionSummary?: string; reason?: string }) =>
      transitionIncident(incident.id, { ...input, version: incident.version }),
    onSuccess: async (updated) => {
      setTransitionTarget(null);
      onChanged();
      toast.success("Status moved to " + incidentStatusLabel[updated.status]);
    },
    onError: handleMutationError,
  });

  const severityMutation = useMutation({
    mutationFn: (severity: string) =>
      changeSeverity(incident.id, { severity, version: incident.version }),
    onSuccess: async () => {
      onChanged();
      toast.success("Severity updated");
    },
    onError: handleMutationError,
  });

  const archiveMutation = useMutation({
    mutationFn: () => archiveIncident(incident.id, incident.version),
    onSuccess: async () => {
      setArchiveOpen(false);
      onChanged();
      toast.success("Incident archived", { description: "It no longer appears in active views." });
    },
    onError: handleMutationError,
  });

  if (!canOperate) {
    return null;
  }

  const transitions = allowedTransitions(incident.status);

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" size="sm" disabled={Boolean(incident.archivedAt)}>
            Actions
            <ChevronDown className="ml-1 h-4 w-4" aria-hidden="true" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-52">
          <DropdownMenuLabel>Move status</DropdownMenuLabel>
          {transitions.map((transition) => (
            <DropdownMenuItem
              key={transition.to}
              onSelect={() => setTransitionTarget(transition.to)}
            >
              {incident.status === "RESOLVED" ? "Reopen" : "Move to"}{" "}
              {incidentStatusLabel[transition.to]}
              {transition.to === "RESOLVED" && "…"}
            </DropdownMenuItem>
          ))}
          <DropdownMenuSeparator />
          <DropdownMenuLabel>Severity</DropdownMenuLabel>
          {SEVERITIES.filter((level) => level !== incident.severity).map((level) => (
            <DropdownMenuItem
              key={level}
              disabled={severityMutation.isPending}
              onSelect={() => {
                if (level === "CRITICAL" && !incident.commander) {
                  toast.error("Assign an Incident Commander before setting Critical severity.");
                  return;
                }
                severityMutation.mutate(level);
              }}
            >
              Set {severityLabel[level]}
            </DropdownMenuItem>
          ))}
          <DropdownMenuSeparator />
          {!incident.archivedAt && (
            <DropdownMenuItem
              className="text-destructive focus:text-destructive"
              onSelect={() => setArchiveOpen(true)}
            >
              <Archive className="mr-2 h-4 w-4" aria-hidden="true" /> Archive incident
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>

      <TransitionDialog
        incident={incident}
        target={transitionTarget}
        pending={transitionMutation.isPending}
        onClose={() => setTransitionTarget(null)}
        onSubmit={(values) => transitionMutation.mutate(values)}
      />

      <AlertDialog open={archiveOpen} onOpenChange={setArchiveOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Archive this incident?</AlertDialogTitle>
            <AlertDialogDescription>
              Archived incidents become read-only, leave dashboards and the derived service
              status, and remain searchable under the archived filter. This cannot be undone from
              the UI.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              onClick={(event) => {
                event.preventDefault();
                archiveMutation.mutate();
              }}
            >
              {archiveMutation.isPending ? "Archiving…" : "Archive"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <ConflictDialog
        open={conflictOpen}
        currentVersion={conflictVersion}
        onReload={() => {
          setConflictOpen(false);
          onChanged();
        }}
        onKeep={() => setConflictOpen(false)}
      />
    </>
  );
}

function TransitionDialog({
  incident,
  target,
  pending,
  onClose,
  onSubmit,
}: {
  incident: IncidentView;
  target: IncidentStatus | null;
  pending: boolean;
  onClose: () => void;
  onSubmit: (values: { to: IncidentStatus; resolutionSummary?: string; reason?: string }) => void;
}) {
  const [summary, setSummary] = useState("");
  const [error, setError] = useState<string | null>(null);

  const requiresSummary = target === "RESOLVED";
  const requiresReason = target === "INVESTIGATING" && incident.status === "RESOLVED";
  const isReopen = incident.status === "RESOLVED";

  function submit() {
    if (requiresSummary && !summary.trim()) {
      setError("A resolution summary is required to resolve this incident.");
      return;
    }
    if (requiresReason && !summary.trim()) {
      setError("A reason is required to reopen this incident.");
      return;
    }
    setError(null);
    onSubmit({
      to: target as IncidentStatus,
      resolutionSummary: requiresSummary ? summary : undefined,
      reason: requiresReason ? summary : undefined,
    });
  }

  return (
    <Dialog
      open={target !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {isReopen ? "Reopen incident" : "Move to " + (target ? incidentStatusLabel[target] : "")}
          </DialogTitle>
          <DialogDescription>
            {requiresSummary
              ? "Describe what fixed the incident. This is stored with the incident and shown in the timeline."
              : requiresReason
                ? "Explain why this incident is being reopened or returned to investigating."
                : "Confirm the status change for this incident."}
          </DialogDescription>
        </DialogHeader>
        {(requiresSummary || requiresReason) && (
          <div className="grid gap-1.5">
            <Label htmlFor="transition-text">
              {requiresSummary ? "Resolution summary" : "Reason"}{" "}
              <span aria-hidden="true" className="text-destructive">*</span>
            </Label>
            <Textarea
              id="transition-text"
              rows={4}
              value={summary}
              autoFocus
              aria-required="true"
              aria-invalid={Boolean(error)}
              onChange={(event) => setSummary(event.target.value)}
              placeholder={
                requiresSummary
                  ? "e.g. Rolled back deploy #482; error rate back to normal."
                  : "e.g. Error rate regressed during monitoring."
              }
            />
            {error && (
              <p role="alert" className="text-xs text-destructive">
                {error}
              </p>
            )}
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={pending}>
            {pending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
            Confirm
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ComposerCard({ incident, onChanged }: { incident: IncidentView; onChanged: () => void }) {
  const [internalBody, setInternalBody] = useState("");
  const [publicBody, setPublicBody] = useState("");
  const user = useSessionStore((s) => s.user);
  const subject = currentSubject(incident, user?.id ?? null);
  const canComment = subject.role === "OWNER" || subject.role === "ADMIN" || subject.isCommander || subject.isResponder;
  const canPublish = subject.role === "OWNER" || subject.role === "ADMIN" || (subject.role === "INCIDENT_COMMANDER" && subject.isCommander);

  const commentMutation = useMutation({
    mutationFn: () => addComment(incident.id, internalBody.trim()),
    onSuccess: () => {
      setInternalBody("");
      onChanged();
      toast.success("Comment added");
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "Could not post comment."),
  });

  const publishMutation = useMutation({
    mutationFn: () => publishPublicUpdate(incident.id, publicBody.trim()),
    onSuccess: () => {
      setPublicBody("");
      onChanged();
      toast.success("Public update published", {
        description: "It is now part of the immutable customer history.",
      });
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "Could not publish update."),
  });

  return (
    <section className="rounded-lg border bg-card">
      <Tabs defaultValue="internal">
        <div className="border-b px-4 pt-3">
          <TabsList>
            <TabsTrigger value="internal" disabled={!canComment}>
              Internal comment
            </TabsTrigger>
            <TabsTrigger value="public" disabled={!canPublish}>
              Public update
            </TabsTrigger>
          </TabsList>
        </div>
        <TabsContent value="internal" className="p-4">
          {!canComment ? (
            <p className="text-sm text-muted-foreground">
              {subject.role === "VIEWER"
                ? "Your role has read-only access."
                : "Responders can comment only on incidents they are assigned to."}
            </p>
          ) : (
            <form
              className="grid gap-2"
              onSubmit={(event) => {
                event.preventDefault();
                if (internalBody.trim()) commentMutation.mutate();
              }}
            >
              <Label htmlFor="comment-body" className="sr-only">
                Internal comment
              </Label>
              <Textarea
                id="comment-body"
                rows={3}
                value={internalBody}
                onChange={(event) => setInternalBody(event.target.value)}
                placeholder="Share an internal investigation update…"
                aria-describedby="comment-visibility"
              />
              <p id="comment-visibility" className="text-xs text-muted-foreground">
                Internal only — never shown on any public surface.
              </p>
              <div className="flex justify-end">
                <Button type="submit" size="sm" disabled={commentMutation.isPending || !internalBody.trim()}>
                  {commentMutation.isPending ? (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />
                  ) : (
                    <Send className="mr-2 h-4 w-4" aria-hidden="true" />
                  )}
                  Post comment
                </Button>
              </div>
            </form>
          )}
        </TabsContent>
        <TabsContent value="public" className="p-4">
          {!canPublish ? (
            <p className="text-sm text-muted-foreground">
              Only Owners, Admins, and the commanding Incident Commander can publish public updates.
            </p>
          ) : (
            <form
              className="grid gap-2"
              onSubmit={(event) => {
                event.preventDefault();
                if (publicBody.trim()) publishMutation.mutate();
              }}
            >
              <Label htmlFor="public-body" className="sr-only">
                Public update
              </Label>
              <Textarea
                id="public-body"
                rows={3}
                value={publicBody}
                onChange={(event) => setPublicBody(event.target.value)}
                placeholder="Customer-safe message about the current state…"
                aria-describedby="public-visibility"
              />
              <p id="public-visibility" className="text-xs text-muted-foreground">
                Published updates are immutable and customer-safe. Corrections require a new update.
              </p>
              <div className="flex justify-end">
                <Button type="submit" size="sm" disabled={publishMutation.isPending || !publicBody.trim()}>
                  {publishMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
                  Publish update
                </Button>
              </div>
            </form>
          )}
        </TabsContent>
      </Tabs>
    </section>
  );
}

function ParticipantsCard({ incident, onChanged }: { incident: IncidentView; onChanged: () => void }) {
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const user = useSessionStore((s) => s.user);
  const subject = currentSubject(incident, user?.id ?? null);
  const canOperate =
    subject.role === "OWNER" ||
    subject.role === "ADMIN" ||
    (subject.role === "INCIDENT_COMMANDER" && subject.isCommander);

  const membersQuery = useQuery({
    queryKey: qk.organizations.members(activeOrgId ?? "?"),
    queryFn: ({ signal }) => listMembers(0, 200, signal),
    enabled: canOperate,
  });

  const addMutation = useMutation({
    mutationFn: (responderUserId: string) =>
      addResponder(incident.id, { responderUserId, version: incident.version }),
    onSuccess: () => {
      onChanged();
      toast.success("Responder assigned");
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "Could not assign responder."),
  });

  const commanderMutation = useMutation({
    mutationFn: (commanderUserId: string) =>
      setCommander(incident.id, { commanderUserId, version: incident.version }),
    onSuccess: () => {
      onChanged();
      toast.success("Commander assigned");
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "Could not assign commander."),
  });

  const removeMutation = useMutation({
    mutationFn: (responderUserId: string) => removeResponder(incident.id, responderUserId, incident.version),
    onSuccess: () => {
      onChanged();
      toast.success("Responder removed");
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "Could not remove responder."),
  });

  const members = membersQuery.data?.items ?? [];
  const eligibleCommanders = members.filter(
    (m) => m.status === "ACTIVE" && isCommanderEligible(m.role)
  );
  
  const candidateResponders = members.filter(
    (m) =>
      m.status === "ACTIVE" &&
      m.userId !== incident.commander?.userId &&
      !incident.responders.some((existing) => existing.userId === m.userId)
  );

  return (
    <Card title="Command">
      <div className="space-y-4 text-sm">
        <div>
          <p className="mb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
            Incident Commander
          </p>
          {canOperate && (
            <Select
              value={incident.commander?.userId}
              onValueChange={(value) => commanderMutation.mutate(value)}
            >
              <SelectTrigger aria-label="Change Incident Commander" className="mb-2">
                <SelectValue placeholder={incident.severity === "CRITICAL" ? "Required — select a commander" : "Assign commander…"} />
              </SelectTrigger>
              <SelectContent>
                {eligibleCommanders.map((member) => (
                  <SelectItem key={member.memberId} value={member.userId}>
                    {member.fullName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
          {incident.commander ? (
            <div className="flex items-center justify-between gap-2">
              <span>{incident.commander.fullName}</span>
              {incident.severity === "CRITICAL" && (
                <span className="text-xs text-muted-foreground">
                  Required while Critical
                </span>
              )}
            </div>
          ) : (
            <p className="text-muted-foreground">Not assigned</p>
          )}
        </div>

        <div>
          <p className="mb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
            Responders
          </p>
          {incident.responders.length === 0 ? (
            <p className="text-muted-foreground">None assigned</p>
          ) : (
            <ul className="space-y-1.5">
              {incident.responders.map((responder) => (
                <li key={responder.userId} className="flex items-center justify-between gap-2">
                  <span className="truncate">{responder.fullName}</span>
                  {canOperate && (
                    <button
                      type="button"
                      className="text-xs text-muted-foreground hover:text-destructive focus-visible:outline-2 focus-visible:outline-ring"
                      onClick={() => removeMutation.mutate(responder.userId)}
                      aria-label={"Remove responder " + responder.fullName}
                      disabled={removeMutation.isPending}
                    >
                      Remove
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>

        {canOperate && (
          <div className="grid gap-2">
            {candidateResponders.length > 0 && (
              <Select onValueChange={(value) => addMutation.mutate(value)}>
                <SelectTrigger aria-label="Assign a responder">
                  <SelectValue placeholder="Assign a responder…" />
                </SelectTrigger>
                <SelectContent>
                  {candidateResponders.map((member) => (
                    <SelectItem key={member.memberId} value={member.userId}>
                      {member.fullName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </div>
        )}
      </div>
    </Card>
  );
}

function PublicUpdates({
  updates,
}: {  updates: { id: string; message: string; statusAtPublish: IncidentStatus; publishedAt: string }[];
}) {
  if (updates.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No public updates published yet. This incident stays off any public surface until one is
        published.
      </p>
    );
  }
  return (
    <ol className="space-y-3">
      {updates.map((update) => (
        <li key={update.id} className="rounded-md border bg-muted/40 p-2.5">
          <div className="mb-1 flex items-center justify-between gap-2 text-xs text-muted-foreground">
            <IncidentStatusBadge status={update.statusAtPublish} />
            <time dateTime={update.publishedAt} className="text-num">
              {formatUtcTimestamp(update.publishedAt)}
            </time>
          </div>
          <p className="text-sm">{update.message}</p>
        </li>
      ))}
    </ol>
  );
}

function CommentsList({
  comments,
}: {
  comments: { id: string; author: { fullName: string }; body: string; createdAt: string }[];
}) {
  if (comments.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No internal comments yet. Use the composer above to post the first investigation update.
      </p>
    );
  }
  return (
    <ul className="space-y-3">
      {comments.map((comment) => (
        <li key={comment.id} className="rounded-md border p-3">
          <div className="mb-1 flex items-baseline justify-between gap-2">
            <p className="text-sm font-medium">{comment.author.fullName}</p>
            <time dateTime={comment.createdAt} className="text-num text-xs text-muted-foreground">
              {formatUtcTimestamp(comment.createdAt)}
            </time>
          </div>
          <p className="whitespace-pre-wrap text-sm text-muted-foreground">{comment.body}</p>
        </li>
      ))}
    </ul>
  );
}
function useNow(intervalMs: number): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(timer);
  }, [intervalMs]);
  return now;
}
