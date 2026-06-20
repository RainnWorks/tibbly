import { forwardRef, type InputHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

export type InputProps = InputHTMLAttributes<HTMLInputElement>;

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, type = "text", ...rest }, ref) => (
    <input
      ref={ref}
      type={type}
      className={cn(
        "flex h-10 w-full rounded-md border bg-[color:var(--color-osrs-panel)] px-3 py-2 text-sm",
        "border-[color:var(--color-osrs-panel-border)] text-amber-50 placeholder:text-[color:var(--color-osrs-gold-soft)]/60",
        "focus-visible:outline-none focus-visible:border-[color:var(--color-osrs-gold)] focus-visible:ring-1 focus-visible:ring-[color:var(--color-osrs-gold)]",
        "disabled:cursor-not-allowed disabled:opacity-50",
        className,
      )}
      {...rest}
    />
  ),
);
Input.displayName = "Input";
