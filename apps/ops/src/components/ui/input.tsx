import { forwardRef, type InputHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

export type InputProps = InputHTMLAttributes<HTMLInputElement>;

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, type = "text", ...rest }, ref) => (
    <input
      ref={ref}
      type={type}
      className={cn(
        "flex h-9 w-full rounded-[4px] border px-3 py-2 text-sm",
        "bg-[var(--color-ops-surface-2)] text-[var(--color-ops-text)]",
        "border-[var(--color-ops-border)]",
        "placeholder:text-[var(--color-ops-text-faint)]",
        "focus-visible:outline-none focus-visible:border-[var(--color-ops-accent)] focus-visible:ring-1 focus-visible:ring-[var(--color-ops-accent)]",
        "disabled:cursor-not-allowed disabled:opacity-50",
        className,
      )}
      {...rest}
    />
  ),
);
Input.displayName = "Input";
