import { forwardRef, type ButtonHTMLAttributes } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/cn";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md border text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-[color:var(--color-osrs-bg)] focus-visible:ring-[color:var(--color-osrs-gold)] disabled:opacity-50 disabled:pointer-events-none",
  {
    variants: {
      variant: {
        primary:
          "bg-[color:var(--color-osrs-gold)] text-[color:var(--color-osrs-bg)] border-[color:var(--color-osrs-gold)] hover:brightness-110",
        secondary:
          "bg-[color:var(--color-osrs-panel)] text-[color:var(--color-osrs-gold-soft)] border-[color:var(--color-osrs-panel-border)] hover:border-[color:var(--color-osrs-gold-soft)]",
        ghost:
          "bg-transparent text-[color:var(--color-osrs-gold-soft)] border-transparent hover:bg-[color:var(--color-osrs-panel)]",
        danger:
          "bg-[color:var(--color-osrs-danger)] text-white border-[color:var(--color-osrs-danger)] hover:brightness-110",
      },
      size: {
        sm: "h-8 px-3",
        md: "h-10 px-4",
        lg: "h-12 px-6 text-base",
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
