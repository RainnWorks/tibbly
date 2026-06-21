import { useParams } from "@tanstack/react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import {
  AlertTriangle,
  Ban,
  CheckCircle2,
  CircleDollarSign,
  Coins,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { CopyId } from "@/components/ui/copy-id";
import { RelativeTime } from "@/components/ui/relative-time";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import { apiFetch, ApiError } from "@/lib/api";
import { formatTokens, formatStripeMinor } from "@/lib/format";

interface UserDetail {
  ok: true;
  user: {
    id: string;
    email: string | null;
    stripeCustomerId: string | null;
    status: string;
    createdAt: string;
    updatedAt: string;
    deletedAt: string | null;
  };
  devices: ReadonlyArray<{
    id: string;
    displayName: string | null;
    playerName: string | null;
    lastSeenAt: string | null;
    createdAt: string;
  }>;
  osrsAccounts: ReadonlyArray<{
    id: string;
    displayName: string;
    accountType: string;
    status: string;
    lastVerifiedAt: string | null;
  }>;
  subscriptions: ReadonlyArray<{
    id: string;
    stripeSubscriptionId: string;
    tier: string;
    status: string;
    monthlyQuotaTokens: number;
    currentPeriodStart: string;
    currentPeriodEnd: string;
  }>;
  activeSubscription: {
    id: string;
    tier: string;
    status: string;
  } | null;
  balance: { balanceTokens: number };
  recentChats: ReadonlyArray<{
    id: string;
    title: string | null;
    createdAt: string;
    deletedAt: string | null;
  }>;
  recentToolCalls: ReadonlyArray<{
    toolName: string;
    status: string;
    durationMs: number | null;
    createdAt: string;
  }>;
  invoices: ReadonlyArray<{
    id: string;
    number: string | null;
    amount_paid: number;
    amount_due: number;
    currency: string;
    status: string | null;
    created: number;
    hosted_invoice_url: string | null;
  }>;
}

export function RouteUserDetail(): ReactNode {
  const { id } = useParams({ strict: false }) as { id: string };
  const queryClient = useQueryClient();
  const toast = useToast();

  const detail = useQuery({
    queryKey: ["admin.users.detail", id],
    queryFn: () => apiFetch<UserDetail>(`/admin/users/${id}`),
  });

  const [banOpen, setBanOpen] = useState(false);
  const [creditOpen, setCreditOpen] = useState(false);
  const [refundOpen, setRefundOpen] = useState(false);

  const banMutation = useMutation({
    mutationFn: (reason: string) =>
      apiFetch(`/admin/users/${id}/ban`, {
        method: "POST",
        body: JSON.stringify({ reason }),
      }),
    onSuccess: async () => {
      toast.push({ title: "user banned", variant: "danger" });
      setBanOpen(false);
      await queryClient.invalidateQueries({ queryKey: ["admin.users.detail", id] });
    },
    onError: () => {
      toast.push({ title: "ban failed", variant: "danger" });
    },
  });

  const unbanMutation = useMutation({
    mutationFn: () =>
      apiFetch(`/admin/users/${id}/unban`, {
        method: "POST",
        body: "{}",
      }),
    onSuccess: async () => {
      toast.push({ title: "user unbanned", variant: "success" });
      await queryClient.invalidateQueries({ queryKey: ["admin.users.detail", id] });
    },
  });

  const creditMutation = useMutation({
    mutationFn: (input: { tokens: number; reason: string }) =>
      apiFetch(`/admin/users/${id}/credit`, {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: async () => {
      toast.push({ title: "credit granted", variant: "success" });
      setCreditOpen(false);
      await queryClient.invalidateQueries({ queryKey: ["admin.users.detail", id] });
    },
    onError: () => {
      toast.push({ title: "credit failed", variant: "danger" });
    },
  });

  const refundMutation = useMutation({
    mutationFn: (input: {
      chargeId: string;
      amountUsdCents?: number;
      reason: string;
    }) =>
      apiFetch<{
        ok: true;
        refund: { id: string; isDevStub: boolean };
      }>(`/admin/users/${id}/refund`, {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: (res) => {
      toast.push({
        title: res.refund.isDevStub
          ? "dev stub refund (no money moved)"
          : "refund issued",
        variant: res.refund.isDevStub ? "warn" : "success",
      });
      setRefundOpen(false);
    },
    onError: (err) => {
      if (err instanceof ApiError && typeof err.body === "object" && err.body !== null) {
        toast.push({
          title: "refund failed",
          description: JSON.stringify(err.body),
          variant: "danger",
        });
      } else {
        toast.push({ title: "refund failed", variant: "danger" });
      }
    },
  });

  if (detail.isLoading) {
    return (
      <div className="text-xs text-[var(--color-ops-text-faint)] font-mono">
        loading user...
      </div>
    );
  }
  if (detail.error || !detail.data) {
    return (
      <div className="text-xs text-[var(--color-ops-danger)] font-mono">
        could not load user {id}
      </div>
    );
  }
  const d = detail.data;

  return (
    <div className="grid grid-cols-1 xl:grid-cols-[1fr_1fr_1fr] gap-4">
      {/* Column 1: identity */}
      <div className="flex flex-col gap-4">
        <Card>
          <CardHeader>
            <CardTitle>identity</CardTitle>
            <Badge
              tone={
                d.user.deletedAt
                  ? "warn"
                  : d.user.status === "banned"
                    ? "danger"
                    : "ok"
              }
            >
              {d.user.deletedAt ? "deleted" : d.user.status}
            </Badge>
          </CardHeader>
          <CardContent className="flex flex-col gap-3 text-sm">
            <Field label="user id">
              <CopyId value={d.user.id} label="user id" />
            </Field>
            <Field label="email">
              <span className="font-mono text-[var(--color-ops-text)]">
                {d.user.email ?? "none"}
              </span>
            </Field>
            <Field label="stripe customer">
              {d.user.stripeCustomerId ? (
                <CopyId
                  value={d.user.stripeCustomerId}
                  label="stripe customer id"
                />
              ) : (
                <span className="font-mono text-[var(--color-ops-text-faint)]">
                  none
                </span>
              )}
            </Field>
            <Field label="created">
              <RelativeTime value={d.user.createdAt} />
            </Field>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>devices ({d.devices.length})</CardTitle>
          </CardHeader>
          <CardContent className="divide-y divide-[var(--color-ops-border)]">
            {d.devices.length === 0 ? (
              <div className="text-xs text-[var(--color-ops-text-faint)] font-mono">
                no devices paired.
              </div>
            ) : (
              d.devices.map((dev) => (
                <div key={dev.id} className="py-2 first:pt-0 flex flex-col gap-1">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-mono text-[var(--color-ops-text)]">
                      {dev.playerName ?? dev.displayName ?? "unnamed device"}
                    </span>
                    <CopyId value={dev.id} label="device id" truncate={10} />
                  </div>
                  <div className="text-[11px] text-[var(--color-ops-text-faint)] font-mono">
                    last seen <RelativeTime value={dev.lastSeenAt} />
                  </div>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>osrs accounts ({d.osrsAccounts.length})</CardTitle>
          </CardHeader>
          <CardContent className="divide-y divide-[var(--color-ops-border)]">
            {d.osrsAccounts.length === 0 ? (
              <div className="text-xs text-[var(--color-ops-text-faint)] font-mono">
                none linked.
              </div>
            ) : (
              d.osrsAccounts.map((acc) => (
                <div
                  key={acc.id}
                  className="py-2 first:pt-0 flex items-center justify-between text-sm"
                >
                  <span className="font-mono text-[var(--color-ops-text)]">
                    {acc.displayName}
                  </span>
                  <span className="flex items-center gap-2">
                    <Badge tone="neutral">{acc.accountType}</Badge>
                    <Badge
                      tone={
                        acc.status === "verified"
                          ? "ok"
                          : acc.status === "revoked"
                            ? "danger"
                            : "warn"
                      }
                    >
                      {acc.status}
                    </Badge>
                  </span>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>

      {/* Column 2: subscription + balance + actions */}
      <div className="flex flex-col gap-4">
        <Card>
          <CardHeader>
            <CardTitle>subscription</CardTitle>
            {d.activeSubscription ? (
              <Badge tone="ok">{d.activeSubscription.tier}</Badge>
            ) : (
              <Badge tone="neutral">free</Badge>
            )}
          </CardHeader>
          <CardContent className="flex flex-col gap-3 text-sm">
            {d.subscriptions.length === 0 ? (
              <div className="text-xs text-[var(--color-ops-text-faint)] font-mono">
                no subscription history.
              </div>
            ) : (
              d.subscriptions.map((s) => (
                <div
                  key={s.id}
                  className="flex flex-col gap-1 border-b border-[var(--color-ops-border)] pb-3 last:border-0 last:pb-0"
                >
                  <div className="flex items-center justify-between">
                    <Badge
                      tone={
                        s.status === "active" || s.status === "trialing"
                          ? "ok"
                          : s.status === "past_due"
                            ? "warn"
                            : "neutral"
                      }
                    >
                      {s.status}
                    </Badge>
                    <CopyId
                      value={s.stripeSubscriptionId}
                      label="stripe sub id"
                      truncate={14}
                    />
                  </div>
                  <Field label="tier">
                    <span className="font-mono text-[var(--color-ops-text)]">
                      {s.tier}
                    </span>
                  </Field>
                  <Field label="renews">
                    <RelativeTime value={s.currentPeriodEnd} />
                  </Field>
                  <Field label="quota">
                    <span className="font-mono text-[var(--color-ops-text)]">
                      {formatTokens(s.monthlyQuotaTokens)} tokens
                    </span>
                  </Field>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>token balance (raw)</CardTitle>
            <Badge tone="warn">ops only</Badge>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <div className="font-mono text-3xl text-[var(--color-ops-text)] tabular-nums">
              {formatTokens(d.balance.balanceTokens)}
            </div>
            <div className="text-[11px] text-[var(--color-ops-text-faint)] font-mono">
              users never see this number. D-8.
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>actions</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {d.user.status === "banned" ? (
              <Button
                variant="secondary"
                onClick={() => unbanMutation.mutate()}
                disabled={unbanMutation.isPending}
              >
                <CheckCircle2 size={14} strokeWidth={1.75} /> unban user
              </Button>
            ) : (
              <Button variant="danger" onClick={() => setBanOpen(true)}>
                <Ban size={14} strokeWidth={1.75} /> ban user
              </Button>
            )}
            <Button variant="secondary" onClick={() => setCreditOpen(true)}>
              <Coins size={14} strokeWidth={1.75} /> grant tokens
            </Button>
            <Button
              variant="secondary"
              onClick={() => setRefundOpen(true)}
              disabled={!d.user.stripeCustomerId}
            >
              <CircleDollarSign size={14} strokeWidth={1.75} /> refund charge
            </Button>
            <a
              href={`/api/v1/me/export?userId=${encodeURIComponent(d.user.id)}`}
              className="text-xs text-[var(--color-ops-text-muted)] hover:text-[var(--color-ops-text)] font-mono"
            >
              gdpr export
            </a>
          </CardContent>
        </Card>
      </div>

      {/* Column 3: activity */}
      <div className="flex flex-col gap-4">
        <Card>
          <CardHeader>
            <CardTitle>recent chats</CardTitle>
            <span className="text-[11px] text-[var(--color-ops-text-faint)] font-mono">
              {d.recentChats.length}
            </span>
          </CardHeader>
          <CardContent className="divide-y divide-[var(--color-ops-border)]">
            {d.recentChats.length === 0 ? (
              <div className="text-xs text-[var(--color-ops-text-faint)] font-mono">
                no chats yet.
              </div>
            ) : (
              d.recentChats.map((c) => (
                <div
                  key={c.id}
                  className="py-2 first:pt-0 flex items-center justify-between text-sm"
                >
                  <span className="text-[var(--color-ops-text)] truncate max-w-[180px]">
                    {c.title ?? <span className="font-mono text-[var(--color-ops-text-faint)]">untitled</span>}
                  </span>
                  <RelativeTime value={c.createdAt} />
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>recent tool calls</CardTitle>
          </CardHeader>
          <CardContent className="divide-y divide-[var(--color-ops-border)]">
            {d.recentToolCalls.length === 0 ? (
              <div className="text-xs text-[var(--color-ops-text-faint)] font-mono">
                no tool calls yet.
              </div>
            ) : (
              d.recentToolCalls.map((t, i) => (
                <div
                  key={`${t.toolName}-${i}`}
                  className="py-2 first:pt-0 flex items-center justify-between text-xs font-mono"
                >
                  <span className="text-[var(--color-ops-text)]">{t.toolName}</span>
                  <span className="flex items-center gap-2">
                    <Badge
                      tone={
                        t.status === "ok"
                          ? "ok"
                          : t.status === "error" || t.status === "timeout"
                            ? "danger"
                            : "neutral"
                      }
                    >
                      {t.status}
                    </Badge>
                    <span className="text-[var(--color-ops-text-faint)]">
                      {t.durationMs ? `${t.durationMs}ms` : ""}
                    </span>
                  </span>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>last 10 invoices</CardTitle>
          </CardHeader>
          <CardContent className="divide-y divide-[var(--color-ops-border)]">
            {d.invoices.length === 0 ? (
              <div className="text-xs text-[var(--color-ops-text-faint)] font-mono">
                {d.user.stripeCustomerId
                  ? "no invoices on record."
                  : "no stripe customer linked."}
              </div>
            ) : (
              d.invoices.map((inv) => (
                <div key={inv.id} className="py-2 first:pt-0 text-xs font-mono">
                  <div className="flex items-center justify-between">
                    <span className="text-[var(--color-ops-text)]">
                      {inv.number ?? inv.id}
                    </span>
                    <span className="text-[var(--color-ops-text)]">
                      {formatStripeMinor(inv.amount_paid, inv.currency)}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-[var(--color-ops-text-faint)]">
                    <Badge
                      tone={inv.status === "paid" ? "ok" : "neutral"}
                    >
                      {inv.status ?? "unknown"}
                    </Badge>
                    <RelativeTime value={new Date(inv.created * 1000)} />
                  </div>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>

      <BanDialog
        open={banOpen}
        onOpenChange={setBanOpen}
        onConfirm={(reason) => banMutation.mutate(reason)}
        pending={banMutation.isPending}
      />
      <CreditDialog
        open={creditOpen}
        onOpenChange={setCreditOpen}
        onConfirm={(input) => creditMutation.mutate(input)}
        pending={creditMutation.isPending}
      />
      <RefundDialog
        open={refundOpen}
        onOpenChange={setRefundOpen}
        onConfirm={(input) => refundMutation.mutate(input)}
        pending={refundMutation.isPending}
      />
    </div>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}): ReactNode {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-[11px] uppercase tracking-wide text-[var(--color-ops-text-muted)]">
        {label}
      </span>
      <span>{children}</span>
    </div>
  );
}

function BanDialog({
  open,
  onOpenChange,
  onConfirm,
  pending,
}: {
  open: boolean;
  onOpenChange: (next: boolean) => void;
  onConfirm: (reason: string) => void;
  pending: boolean;
}): ReactNode {
  const [reason, setReason] = useState("");
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>ban user</DialogTitle>
          <DialogDescription>
            this ends any active session and locks the account out.
          </DialogDescription>
        </DialogHeader>
        <Input
          autoFocus
          placeholder="reason (visible in audit log)"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
        <div className="mt-4 flex items-center justify-end gap-2">
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            cancel
          </Button>
          <Button
            variant="danger"
            disabled={pending || reason.trim().length === 0}
            onClick={() => onConfirm(reason.trim())}
          >
            <AlertTriangle size={14} strokeWidth={1.75} /> ban
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function CreditDialog({
  open,
  onOpenChange,
  onConfirm,
  pending,
}: {
  open: boolean;
  onOpenChange: (next: boolean) => void;
  onConfirm: (input: { tokens: number; reason: string }) => void;
  pending: boolean;
}): ReactNode {
  const [tokens, setTokens] = useState("");
  const [reason, setReason] = useState("");
  const n = Number.parseInt(tokens, 10);
  const valid = Number.isFinite(n) && n > 0 && reason.trim().length > 0;
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>grant tokens</DialogTitle>
          <DialogDescription>
            added to the user's raw balance. users do not see this number.
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-2">
          <Input
            autoFocus
            placeholder="amount of tokens"
            inputMode="numeric"
            value={tokens}
            onChange={(e) => setTokens(e.target.value)}
          />
          <Input
            placeholder="reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </div>
        <div className="mt-4 flex items-center justify-end gap-2">
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            cancel
          </Button>
          <Button
            variant="primary"
            disabled={!valid || pending}
            onClick={() => onConfirm({ tokens: n, reason: reason.trim() })}
          >
            grant
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function RefundDialog({
  open,
  onOpenChange,
  onConfirm,
  pending,
}: {
  open: boolean;
  onOpenChange: (next: boolean) => void;
  onConfirm: (input: {
    chargeId: string;
    amountUsdCents?: number;
    reason: string;
  }) => void;
  pending: boolean;
}): ReactNode {
  const [chargeId, setChargeId] = useState("");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const cents = amount ? Number.parseInt(amount, 10) : undefined;
  const valid =
    chargeId.trim().length > 0 &&
    reason.trim().length > 0 &&
    (amount === "" || (Number.isFinite(cents) && (cents ?? 0) > 0));
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>refund charge</DialogTitle>
          <DialogDescription>
            paste the stripe charge id. leave amount blank to refund in
            full. dev environments return a labelled dev_stub.
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-2">
          <Input
            autoFocus
            placeholder="ch_..."
            value={chargeId}
            onChange={(e) => setChargeId(e.target.value)}
          />
          <Input
            placeholder="amount in usd cents (optional)"
            inputMode="numeric"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
          <Input
            placeholder="reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </div>
        <div className="mt-4 flex items-center justify-end gap-2">
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            cancel
          </Button>
          <Button
            variant="primary"
            disabled={!valid || pending}
            onClick={() =>
              onConfirm({
                chargeId: chargeId.trim(),
                ...(cents !== undefined ? { amountUsdCents: cents } : {}),
                reason: reason.trim(),
              })
            }
          >
            issue refund
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
