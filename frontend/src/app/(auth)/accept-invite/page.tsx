"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { acceptInvitation } from "@/lib/api/auth";
import { useSessionStore } from "@/stores/session-store";

function AcceptContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const queryClient = useQueryClient();
  const token = searchParams.get("token") ?? "";
  const status = useSessionStore((s) => s.status);
  const setActiveOrg = useSessionStore((s) => s.setActiveOrg);
  const [done, setDone] = useState(false);

  const mutation = useMutation({
    mutationFn: () => acceptInvitation(token),
    onSuccess: async (response) => {
      setDone(true);
      setActiveOrg(response.organizationId);
      await queryClient.invalidateQueries({ queryKey: ["auth"] });
      toast.success("Joined " + response.organizationName, {
        description: "Role: " + response.role.replace("_", " ").toLowerCase(),
      });
      router.replace("/dashboard");
    },
    onError: (error) =>
      toast.error(error instanceof Error ? error.message : "This invitation is not valid."),
  });

  if (!token) {
    return (
      <Card className="w-full max-w-sm text-center">
        <CardHeader>
          <CardTitle>Invitation missing</CardTitle>
          <CardDescription>This link does not contain an invitation token.</CardDescription>
        </CardHeader>
      </Card>
    );
  }

  if (status === "anonymous") {
    return (
      <Card className="w-full max-w-sm text-center">
        <CardHeader>
          <CardTitle>Sign in to accept</CardTitle>
          <CardDescription>
            You need an account with the invited email address before accepting.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-2">
          <Button asChild>
            <Link href={"/login?next=" + encodeURIComponent("/accept-invite?token=" + token)}>
              Sign in
            </Link>
          </Button>
          <Button asChild variant="outline">
            <Link href="/register">Create an account</Link>
          </Button>
        </CardContent>
      </Card>
    );
  }

  if (done) {
    return (
      <Card className="w-full max-w-sm text-center" aria-live="polite">
        <CardHeader>
          <CardTitle>Invitation accepted</CardTitle>
          <CardDescription>Taking you to your dashboard…</CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card className="w-full max-w-sm text-center">
      <CardHeader>
        <CardTitle>Accept invitation</CardTitle>
        <CardDescription>
          You have been invited to join an organization as a member.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button onClick={() => mutation.mutate()} disabled={mutation.isPending} className="w-full">
          {mutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
          Accept invitation
        </Button>
      </CardContent>
    </Card>
  );
}

export default function AcceptInvitePage() {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-background p-4">
      <Suspense fallback={null}>
        <AcceptContent />
      </Suspense>
    </div>
  );
}
