import { forwardRef, type ButtonHTMLAttributes } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/cn";

/**
 * Ops button. Compact, square-edged on inputs, accent on primary.
 * The accent is mint-green so the primary action reads as "system
 * alive" rather than warm marketing yellow.
 */
const buttonVariants = cva(
  [
    "inline-flex items-center justify-center gap-2 whitespace-nowrap",
    "border text-sm font-medium",
    "transition-colors duration-100",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-ops-accent)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--color-ops-bg)]",
    "disabled:opacity-50 disabled:pointer-events-none",
  ].join(" "),
  {
    variants: {
      variant: {
        primary:
          "bg-[var(--color-ops-accent)] text-[var(--color-ops-accent-ink)] border-[var(--color-ops-accent)] hover:brightness-95",
        secondary:
          "bg-[var(--color-ops-surface-2)] text-[var(--color-ops-text)] border-[var(--color-ops-border)] hover:border-[var(--color-ops-border-strong)]",
        ghost:
          "bg-transparent text-[var(--color-ops-text-muted)] border-transparent hover:text-[var(--color-ops-text)] hover:bg-[var(--color-ops-surface)]",
        danger:
          "bg-[var(--color-ops-danger)] text-[var(--color-ops-bg)] border-[var(--color-ops-danger)] hover:brightness-95",
        warn:
          "bg-[var(--color-ops-warn)] text-[var(--color-ops-accent-ink)] border-[var(--color-ops-warn)] hover:brightness-95",
      },
      size: {
        sm: "h-7 px-2.5 rounded-[4px] text-xs",
        md: "h-9 px-3.5 rounded-[4px]",
        lg: "h-11 px-5 rounded-[4px] text-base",
      },
    },
    defaultVariants: {
      variant: "primary",
      size: "md",
    },
  },
);

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> &
  VariantProps<typeof buttonVariants>;

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, type = "button", ...rest }, ref) => (
    <button
      ref={ref}
      type={type}
      className={cn(buttonVariants({ variant, size }), className)}
      {...rest}
    />
  ),
);
Button.displayName = "Button";
