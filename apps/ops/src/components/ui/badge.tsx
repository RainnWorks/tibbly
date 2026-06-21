import { forwardRef, type HTMLAttributes } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/cn";

const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-medium uppercase tracking-wide",
  {
    variants: {
      tone: {
        neutral:
          "bg-[var(--color-ops-surface-2)] text-[var(--color-ops-text-muted)] border-[var(--color-ops-border)]",
        ok: "bg-[var(--color-ops-accent)]/15 text-[var(--color-ops-accent)] border-[var(--color-ops-accent)]/40",
        warn:
          "bg-[var(--color-ops-warn)]/15 text-[var(--color-ops-warn)] border-[var(--color-ops-warn)]/40",
        danger:
          "bg-[var(--color-ops-danger)]/15 text-[var(--color-ops-danger)] border-[var(--color-ops-danger)]/40",
      },
    },
    defaultVariants: {
      tone: "neutral",
    },
  },
);

export type BadgeProps = HTMLAttributes<HTMLSpanElement> &
  VariantProps<typeof badgeVariants>;

export const Badge = forwardRef<HTMLSpanElement, BadgeProps>(
  ({ className, tone, ...rest }, ref) => (
    <span
      ref={ref}
      className={cn(badgeVariants({ tone }), className)}
      {...rest}
    />
  ),
);
Badge.displayName = "Badge";
