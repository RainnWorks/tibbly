import { useNavigate } from "@tanstack/react-router";
import { useForm } from "react-hook-form";
import { AlertTriangle, ShieldCheck } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api";
import { login } from "@/lib/auth";

interface FormValues {
  email: string;
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function RouteLogin(): ReactNode {
  const navigate = useNavigate();
  const [topLevelError, setTopLevelError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    defaultValues: { email: "" },
  });

  const onSubmit = async (values: FormValues) => {
    setTopLevelError(null);
    try {
      await login(values.email);
      navigate({ to: "/" });
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setTopLevelError("not authorised");
        return;
      }
      setTopLevelError("login failed");
    }
  };

  return (
    <div className="min-h-screen grid place-items-center bg-[var(--color-ops-bg)] px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex items-center gap-2 text-[var(--color-ops-text-muted)]">
          <ShieldCheck
            size={16}
            strokeWidth={1.75}
            className="text-[var(--color-ops-accent)]"
            aria-hidden
          />
          <span className="text-sm tracking-tight">tibbly ops</span>
        </div>
        <h1 className="text-xl font-semibold text-[var(--color-ops-text)] tracking-tight">
          internal access only
        </h1>
        <p className="mt-2 text-sm text-[var(--color-ops-text-muted)]">
          this console is restricted. enter the email on the admin
          allow-list to continue.
        </p>

        <form
          className="mt-6 flex flex-col gap-3"
          onSubmit={handleSubmit(onSubmit)}
          noValidate
        >
          <label className="flex flex-col gap-1.5">
            <span className="text-xs text-[var(--color-ops-text-muted)]">
              operator email
            </span>
            <Input
              type="email"
              autoComplete="email"
              autoFocus
              aria-invalid={errors.email ? "true" : "false"}
              placeholder="you@example.com"
              {...register("email", {
                required: "email required",
                pattern: { value: EMAIL_RE, message: "enter a valid email" },
              })}
            />
            {errors.email ? (
              <span className="text-xs text-[var(--color-ops-danger)] font-mono">
                {errors.email.message}
              </span>
            ) : null}
          </label>

          {topLevelError ? (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-[4px] border border-[var(--color-ops-danger)]/40 bg-[var(--color-ops-danger)]/10 px-3 py-2 text-xs text-[var(--color-ops-danger)]"
            >
              <AlertTriangle size={14} strokeWidth={1.75} aria-hidden />
              <span className="font-mono">{topLevelError}</span>
            </div>
          ) : null}

          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "verifying" : "continue"}
          </Button>
        </form>

        <p className="mt-8 text-[11px] text-[var(--color-ops-text-faint)] font-mono">
          this is a stopgap. production path: google workspace oidc.
        </p>
      </div>
    </div>
  );
}
