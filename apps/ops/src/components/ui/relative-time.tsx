import type { ReactNode } from "react";

/**
 * Relative-time + absolute hover. Both forms are present because the
 * operator needs the relative form for scanning and the absolute form
 * for incident triage / Stripe-side cross-reference.
 */
export interface RelativeTimeProps {
  readonly value: string | Date | null | undefined;
  readonly className?: string;
}

const MS = {
  s: 1000,
  m: 60 * 1000,
  h: 60 * 60 * 1000,
  d: 24 * 60 * 60 * 1000,
};

function relative(now: number, then: number): string {
  const diff = now - then;
  if (diff < 0) return "in the future";
  if (diff < MS.m) return `${Math.max(1, Math.floor(diff / MS.s))}s ago`;
  if (diff < MS.h) return `${Math.floor(diff / MS.m)}m ago`;
  if (diff < MS.d) return `${Math.floor(diff / MS.h)}h ago`;
  return `${Math.floor(diff / MS.d)}d ago`;
}

function isoLike(d: Date): string {
  const y = d.getUTCFullYear();
  const mo = String(d.getUTCMonth() + 1).padStart(2, "0");
  const da = String(d.getUTCDate()).padStart(2, "0");
  const hh = String(d.getUTCHours()).padStart(2, "0");
  const mi = String(d.getUTCMinutes()).padStart(2, "0");
  return `${y}-${mo}-${da} ${hh}:${mi} UTC`;
}

export function RelativeTime({ value, className }: RelativeTimeProps): ReactNode {
  if (value === null || value === undefined) {
    return <span className={className}>never</span>;
  }
  const d = typeof value === "string" ? new Date(value) : value;
  if (Number.isNaN(d.getTime())) {
    return <span className={className}>invalid</span>;
  }
  const abs = isoLike(d);
  const rel = relative(Date.now(), d.getTime());
  return (
    <span
      className={className ?? "font-mono text-xs text-[var(--color-ops-text-muted)]"}
      title={abs}
    >
      {rel}
    </span>
  );
}
