"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { SignalOpsMark } from "@/components/app/session-provider";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { register } from "@/lib/api/auth";
import { useSessionStore } from "@/stores/session-store";

const registerSchema = z.object({
  fullName: z.string().trim().min(1, "Enter your name.").max(120),
  email: z.string().trim().email("Enter a valid email address."),
  password: z
    .string()
    .regex(
      /^(?=.*\p{L})(?=.*\d).{10,72}$/u,
      "Use 10-72 characters with at least one letter and one digit."
    ),
  organizationName: z.string().trim().min(2, "At least 2 characters.").max(80),
});

type RegisterInput = z.infer<typeof registerSchema>;

export default function RegisterPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const status = useSessionStore((s) => s.status);
  const authenticated = status === "authenticated";

  useEffect(() => {
    if (authenticated) router.replace("/dashboard");
  }, [authenticated, router]);

  const form = useForm<RegisterInput>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: "", email: "", password: "", organizationName: "" },
    mode: "onTouched",
  });

  const mutation = useMutation({
    mutationFn: (values: RegisterInput) => register(values),
    onSuccess: (response) => {
      queryClient.clear();
      toast.success("Organization created", { description: response.organization.name });
      router.replace("/dashboard");
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : "Could not register.");
    },
  });

  if (status === "bootstrapping" || authenticated) return null;

  return (
    <div className="flex min-h-dvh items-center justify-center bg-background p-4">
      <Card className="w-full max-w-sm">
        <CardHeader className="items-center text-center">
          <SignalOpsMark className="mx-auto mb-2 h-10 w-10" />
          <CardTitle>Create your organization</CardTitle>
          <CardDescription>You will become its Owner.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
            noValidate
            className="grid gap-4"
          >
            <div className="grid gap-1.5">
              <Label htmlFor="fullName">Full name</Label>
              <Input id="fullName" autoComplete="name" required {...form.register("fullName")} />
              {form.formState.errors.fullName && (
                <p role="alert" className="text-xs text-destructive">
                  {form.formState.errors.fullName.message}
                </p>
              )}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" autoComplete="email" required {...form.register("email")} />
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
                autoComplete="new-password"
                required
                aria-describedby="password-hint"
                {...form.register("password")}
              />
              <p id="password-hint" className="text-xs text-muted-foreground">
                10-72 characters, at least one letter and one digit.
              </p>
              {form.formState.errors.password && (
                <p role="alert" className="text-xs text-destructive">
                  {form.formState.errors.password.message}
                </p>
              )}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="organizationName">Organization name</Label>
              <Input id="organizationName" required {...form.register("organizationName")} />
              {form.formState.errors.organizationName && (
                <p role="alert" className="text-xs text-destructive">
                  {form.formState.errors.organizationName.message}
                </p>
              )}
            </div>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />}
              Create organization
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              Already have an account?{" "}
              <Link href="/login" className="font-medium text-primary hover:underline">
                Sign in
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
