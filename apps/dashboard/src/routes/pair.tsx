import { useState, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";

const pairSchema = z.object({
  pairingCode: z
    .string()
    .trim()
    .regex(/^[A-Z0-9]{6}$/i, "6 characters, letters or numbers"),
});
type PairValues = z.infer<typeof pairSchema>;

export function RoutePair(): ReactNode {
  const { push } = useToast();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pendingCode, setPendingCode] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    reset,
  } = useForm<PairValues>({
    resolver: zodResolver(pairSchema),
    defaultValues: { pairingCode: "" },
  });

  function onSubmit(values: PairValues): void {
    setPendingCode(values.pairingCode.toUpperCase());
    setConfirmOpen(true);
  }

  function confirmPair(): void {
    push({
      title: "Pairing confirmed",
      description: `Code ${pendingCode ?? ""} accepted (skeleton — backend wires in RAI-27)`,
      variant: "success",
    });
    setConfirmOpen(false);
    setPendingCode(null);
    reset();
  }

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
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Verifying…" : "Pair plugin"}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm pairing</DialogTitle>
            <DialogDescription>
              We'll bind this RuneLite device to your account using code{" "}
              <span className="font-osrs text-[color:var(--color-osrs-gold)]">
                {pendingCode}
              </span>
              .
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2 mt-4">
            <Button variant="secondary" onClick={() => setConfirmOpen(false)}>
              Cancel
            </Button>
            <Button onClick={confirmPair}>Confirm</Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
