import { useEffect, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useNavigate, useSearch } from "@tanstack/react-router";
import { z } from "zod";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { apiFetch, ApiError } from "@/lib/api";

const pairSchema = z.object({
  pairingCode: z
    .string()
    .trim()
    .regex(/^[2-9ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$/i, "6 characters, letters or numbers"),
});
type PairValues = z.infer<typeof pairSchema>;

interface ClaimResponse {
  readonly userId: string;
  readonly deviceId: string;
  readonly userCreated: boolean;
}

/**
 * POST /v1/pairing/claim. Exported so tests can spy on it / swap it.
 */
export async function claimPairingCode(code: string): Promise<ClaimResponse> {
  return apiFetch<ClaimResponse>("/v1/pairing/claim", {
    method: "POST",
    body: JSON.stringify({ code: code.toUpperCase() }),
  });
}

interface PairSearch {
  code?: string;
}

export function RoutePair(): ReactNode {
  const { push } = useToast();
  const navigate = useNavigate();
  // Stripe checkout success redirects back here with ?code=ABC123 so the
  // user doesn't have to re-type the code.
  const search = useSearch({ strict: false }) as PairSearch;

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    reset,
    setValue,
  } = useForm<PairValues>({
    resolver: zodResolver(pairSchema),
    defaultValues: { pairingCode: "" },
  });

  useEffect(() => {
    if (search.code && /^[A-Za-z0-9]{6}$/.test(search.code)) {
      setValue("pairingCode", search.code.toUpperCase());
    }
  }, [search.code, setValue]);

  const mutation = useMutation({
    mutationFn: claimPairingCode,
    onSuccess: () => {
      push({
        title: "Plugin paired",
        description: "Device bound to your account.",
        variant: "success",
      });
      reset();
      void navigate({ to: "/usage" });
    },
    onError: (err: unknown) => {
      const message =
        err instanceof ApiError && err.status === 410
          ? "Code already used or expired — generate a new one in RuneLite."
          : err instanceof ApiError && err.status === 404
            ? "Unknown code — check the in-game banner for the latest one."
            : err instanceof Error
              ? err.message
              : "Failed to pair plugin.";
      push({
        title: "Pairing failed",
        description: message,
        variant: "danger",
      });
    },
  });

  function onSubmit(values: PairValues): void {
    mutation.mutate(values.pairingCode.toUpperCase());
  }

  const busy = isSubmitting || mutation.isPending;

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <h1 className="font-osrs text-3xl text-[color:var(--color-osrs-gold)]">
          Pair plugin
        </h1>
        <p className="text-[color:var(--color-osrs-gold-soft)]">
          Enter the six-character code shown in RuneLite to bind this device to
          your account.
        </p>
      </header>

      <Card className="max-w-lg">
        <CardHeader>
          <CardTitle>One-time pairing code</CardTitle>
          <CardDescription>
            The code rotates every 10 minutes. Generate a new one in RuneLite if
            it's expired.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="flex flex-col gap-3"
            onSubmit={handleSubmit(onSubmit)}
            noValidate
          >
            <label
              htmlFor="pairingCode"
              className="text-sm text-[color:var(--color-osrs-gold-soft)]"
            >
              Pairing code
            </label>
            <Input
              id="pairingCode"
              autoComplete="one-time-code"
              placeholder="ABC123"
              maxLength={6}
              aria-invalid={errors.pairingCode ? "true" : "false"}
              {...register("pairingCode")}
            />
            {errors.pairingCode ? (
              <span role="alert" className="text-xs text-[color:var(--color-osrs-danger)]">
                {errors.pairingCode.message}
              </span>
            ) : null}
            <Button type="submit" disabled={busy}>
              {busy ? "Verifying…" : "Pair plugin"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
