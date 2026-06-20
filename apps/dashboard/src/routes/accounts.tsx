import { useState, type ReactNode } from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";

type LinkedAccount = {
  readonly id: string;
  readonly displayName: string;
  readonly accountType: "main" | "ironman" | "group ironman" | "ultimate ironman";
  readonly linkedAt: string;
};

// Placeholder data — RAI-27 swaps for real /accounts query.
const PLACEHOLDER_ACCOUNTS: ReadonlyArray<LinkedAccount> = [
  {
    id: "acct_demo_1",
    displayName: "Zezima",
    accountType: "main",
    linkedAt: "2026-06-21",
  },
  {
    id: "acct_demo_2",
    displayName: "B0aty",
    accountType: "ironman",
    linkedAt: "2026-06-20",
  },
];

export function RouteAccounts(): ReactNode {
  const { push } = useToast();
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  const confirming =
    PLACEHOLDER_ACCOUNTS.find((a) => a.id === confirmingId) ?? null;

  function onUnlink(): void {
    if (!confirming) return;
    push({
      title: "Account unlinked",
      description: `${confirming.displayName} removed (skeleton only — wires up in RAI-27)`,
      variant: "info",
    });
    setConfirmingId(null);
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <h1 className="font-osrs text-3xl text-[color:var(--color-osrs-gold)]">
          OSRS accounts
        </h1>
        <p className="text-[color:var(--color-osrs-gold-soft)]">
          Characters linked to this subscription. One paid seat covers all
          accounts on your device.
        </p>
      </header>

      <div className="grid gap-3 md:grid-cols-2">
        {PLACEHOLDER_ACCOUNTS.map((account) => (
          <Card key={account.id}>
            <CardHeader>
              <CardTitle>{account.displayName}</CardTitle>
              <CardDescription>
                {account.accountType} &middot; linked {account.linkedAt}
              </CardDescription>
            </CardHeader>
            <CardContent className="flex justify-end">
              <Button
                variant="ghost"
                onClick={() => setConfirmingId(account.id)}
                aria-label={`Unlink ${account.displayName}`}
              >
                Unlink
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

      <Dialog
        open={confirming !== null}
        onOpenChange={(open) => {
          if (!open) setConfirmingId(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Unlink account?</DialogTitle>
            <DialogDescription>
              {confirming
                ? `Are you sure you want to unlink ${confirming.displayName}? You can re-pair anytime from the plugin.`
                : ""}
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2 mt-4">
            <Button variant="secondary" onClick={() => setConfirmingId(null)}>
              Cancel
            </Button>
            <Button variant="danger" onClick={onUnlink}>
              Unlink
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
