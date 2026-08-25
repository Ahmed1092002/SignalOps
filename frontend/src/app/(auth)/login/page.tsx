"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { SignalOpsMark } from "@/components/app/session-provider";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { login } from "@/lib/api/auth";
import { useSessionStore } from "@/stores/session-store";

const loginSchema = z.object({
  email: z.string().trim().email("Enter a valid email address."),
  password: z.string().min(1, "Enter your password."),
});

type LoginInput = z.infer<typeof loginSchema>;

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const status = useSessionStore((s) => s.status);
  const setActiveOrg = useSessionStore((s) => s.setActiveOrg);
  const nextUrl = searchParams.get("next") ?? "/dashboard";

  const authenticated = useSessionStore((s) => s.status === "authenticated");

  useEffect(() => {
    if (authenticated) {
      router.replace(nextUrl.startsWith("/") ? nextUrl : "/dashboard");
    }
  }, [authenticated, nextUrl, router]);

  const form = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
    mode: "onTouched",
  });

  const mutation = useMutation({
    mutationFn: (values: LoginInput) => login(values.email, values.password),
    onSuccess: (response) => {
      setActiveOrg(response.organization.id);
      queryClient.clear();
      toast.success("Welcome back, " + response.user.fullName);
      router.replace(nextUrl.startsWith("/") ? nextUrl : "/dashboard");
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : "Could not sign in.");
    },
  });

  if (status === "bootstrapping" || authenticated) {
    return null;
  }

  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="items-center text-center">
        <SignalOpsMark className="mx-auto mb-2 h-10 w-10" />
        <CardTitle>Sign in to SignalOps</CardTitle>
        <CardDescription>Run incidents with clarity and control.</CardDescription>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
          noValidate
          className="grid gap-4"
        >
          <div className="grid gap-1.5">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              required
              aria-invalid={Boolean(form.formState.errors.email)}
              {...form.register("email")}
            />
            {form.formState.errors.email && (
              <p role="alert" className="text-xs text-destructive">
                {form.formState.errors.email.message}
              </p>
            )}
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              aria-invalid={Boolean(form.formState.errors.password)}
              {...form.register("password")}
            />
            {form.formState.errors.password && (
              <p role="alert" className="text-xs text-destructive">
                {form.formState.errors.password.message}
              </p>
            )}
          </div>
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
            Sign in
          </Button>
          <p className="text-center text-sm text-muted-foreground">
            No account?{" "}
            <Link href="/register" className="font-medium text-primary hover:underline">
              Create an organization
            </Link>
          </p>
        </form>
      </CardContent>
    </Card>
  );
}

export default function LoginPage() {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-background p-4">
      <Suspense fallback={null}>
        <LoginForm />
      </Suspense>
    </div>
  );
}
