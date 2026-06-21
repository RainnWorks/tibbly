import { useState, type ReactNode } from "react";
import { Check, Copy } from "lucide-react";
import { cn } from "@/lib/cn";

/**
 * A copy-to-clipboard pill for ids. Monospace, single-line, click to
 * copy. Operator-friendly: every user id, charge id, subscription id in
 * the app gets wrapped in one of these.
 */
export interface CopyIdProps {
  readonly value: string;
  readonly label?: string;
  readonly truncate?: number;
  readonly className?: string;
}

export function CopyId({
  value,
  label,
  truncate,
  className,
}: CopyIdProps): ReactNode {
  const [copied, setCopied] = useState(false);
  const display =
    truncate && value.length > truncate
      ? `${value.slice(0, Math.max(1, truncate - 4))}...${value.slice(-4)}`
      : value;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1200);
    } catch {
      // Some browsers block clipboard without user-gesture in tests;
      // fail silently.
    }
  };

  return (
    <button
      type="button"
      onClick={handleCopy}
      title={label ? `${label}: ${value}` : value}
      aria-label={label ? `Copy ${label}` : `Copy ${value}`}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-[4px] border px-2 py-0.5",
        "font-mono text-[12px] text-[var(--color-ops-text)] bg-[var(--color-ops-surface-2)]",
        "border-[var(--color-ops-border)] hover:border-[var(--color-ops-border-strong)]",
        "transition-colors duration-100",
        className,
      )}
    >
      <span className="whitespace-nowrap">{display}</span>
      {copied ? (
        <Check size={12} className="text-[var(--color-ops-accent)]" aria-hidden />
      ) : (
        <Copy size={12} className="text-[var(--color-ops-text-faint)]" aria-hidden />
      )}
    </button>
  );
}
