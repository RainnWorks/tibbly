import { useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
import { apiFetch } from "@/lib/api";

export interface AccountDTO {
  readonly id: string;
  readonly displayName: string;
  readonly accountType: string;
  readonly status: string;
  readonly lastVerifiedAt: string | null;
  readonly createdAt: string;
}

interface AccountListResponse {
  readonly accounts: ReadonlyArray<AccountDTO>;
}

export async function fetchAccounts(): Promise<AccountListResponse> {
  return apiFetch<AccountListResponse>("/v1/accounts", { authenticated: true });
}

export async function deleteAccount(id: string): Promise<void> {
  await apiFetch(`/v1/accounts/${encodeURIComponent(id)}`, {
    method: "DELETE",
    authenticated: true,
  });
}

export function RouteAccounts(): ReactNode {
  const { push } = useToast();
  const qc = useQueryClient();
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  const accountsQuery = useQuery({
    queryKey: ["accounts"],
    queryFn: fetchAccounts,
  });

  const accounts = accountsQuery.data?.accounts ?? [];
  const confirming = accounts.find((a) => a.id === confirmingId) ?? null;

  const deleteMutation = useMutation({
    mutationFn: deleteAccount,
    onSuccess: (_void, id) => {
      const removed = accounts.find((a) => a.id === id);
      push({
        title: "Account unlinked",
        description: removed
          ? `${removed.displayName} removed from this subscription.`
          : "Account removed.",
        variant: "info",
      });
      void qc.invalidateQueries({ queryKey: ["accounts"] });
      setConfirmingId(null);
    },
    onError: (err) => {
      push({
        title: "Couldn't unlink account",
        description: err instanceof Error ? err.message : "Try again shortly.",
        variant: "danger",
      });
    },
  });

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

      {accountsQuery.isLoading ? (
        <p className="text-[color:var(--color-osrs-gold-soft)]/70">Loading…</p>
      ) : accountsQuery.isError ? (
        <p role="alert" className="text-[color:var(--color-osrs-danger)]">
          Failed to load accounts:{" "}
          {accountsQuery.error instanceof Error
            ? accountsQuery.error.message
            : "unknown error"}
        </p>
      ) : accounts.length === 0 ? (
        <Card>
          <CardHeader>
            <CardTitle>No accounts yet</CardTitle>
            <CardDescription>
              Pair the RuneLite plugin and log in once to bind your first
              character.
            </CardDescription>
          </CardHeader>
        </Card>
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {accounts.map((account) => (
            <Card key={account.id}>
              <CardHeader>
                <CardTitle>{account.displayName}</CardTitle>
                <CardDescription>
                  {account.accountType} &middot; {account.status} &middot;{" "}
                  linked {new Date(account.createdAt).toLocaleDateString()}
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
      )}

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
            <Button
              variant="danger"
              onClick={() => {
                if (confirming) deleteMutation.mutate(confirming.id);
              }}
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? "Unlinking…" : "Unlink"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
