import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

/**
 * Small dense stat container for the dashboard home. Asymmetric layout
 * lives outside; the tile is a square-ish primitive.
 */
export interface StatTileProps {
  readonly label: string;
  readonly value: ReactNode;
  readonly hint?: ReactNode;
  readonly tone?: "neutral" | "ok" | "warn" | "danger";
  readonly className?: string;
  readonly footer?: ReactNode;
}

const toneText: Record<NonNullable<StatTileProps["tone"]>, string> = {
  neutral: "text-[var(--color-ops-text)]",
  ok: "text-[var(--color-ops-accent)]",
  warn: "text-[var(--color-ops-warn)]",
  danger: "text-[var(--color-ops-danger)]",
};

export function StatTile({
  label,
  value,
  hint,
  tone = "neutral",
  className,
  footer,
}: StatTileProps): ReactNode {
  return (
    <div
      className={cn(
        "rounded-lg border bg-[var(--color-ops-surface)]",
        "border-[var(--color-ops-border)] p-4 flex flex-col gap-2",
        className,
      )}
    >
      <div className="text-[11px] uppercase tracking-wide text-[var(--color-ops-text-muted)]">
        {label}
      </div>
      <div
        className={cn(
          "font-mono text-2xl font-semibold tabular-nums",
          toneText[tone],
        )}
      >
        {value}
      </div>
      {hint ? (
        <div className="text-xs text-[var(--color-ops-text-faint)] font-mono">
          {hint}
        </div>
      ) : null}
      {footer ? <div className="mt-1">{footer}</div> : null}
    </div>
  );
}
