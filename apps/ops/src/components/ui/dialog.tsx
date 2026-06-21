import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type HTMLAttributes,
  type PropsWithChildren,
  type ReactNode,
} from "react";
import { cn } from "@/lib/cn";

/**
 * Minimal accessible dialog primitive. We deliberately avoid pulling in
 * @radix-ui/react-dialog for the skeleton — the surface here is enough
 * for confirm/cancel modals on /pair and /accounts. We'll swap in a
 * full Radix dialog once real flows need focus traps + portaling.
 */

type DialogContextValue = {
  readonly open: boolean;
  readonly setOpen: (next: boolean) => void;
};

const DialogContext = createContext<DialogContextValue | null>(null);

function useDialogContext(component: string): DialogContextValue {
  const ctx = useContext(DialogContext);
  if (!ctx) {
    throw new Error(`${component} must be rendered inside <Dialog>`);
  }
  return ctx;
}

export type DialogProps = PropsWithChildren<{
  readonly open?: boolean;
  readonly defaultOpen?: boolean;
  readonly onOpenChange?: (open: boolean) => void;
}>;

export function Dialog({
  open: controlledOpen,
  defaultOpen = false,
  onOpenChange,
  children,
}: DialogProps): ReactNode {
  const [internalOpen, setInternalOpen] = useState(defaultOpen);
  const isControlled = controlledOpen !== undefined;
  const open = isControlled ? controlledOpen : internalOpen;

  const setOpen = useCallback(
    (next: boolean) => {
      if (!isControlled) setInternalOpen(next);
      onOpenChange?.(next);
    },
    [isControlled, onOpenChange],
  );

  const ctx = useMemo<DialogContextValue>(() => ({ open, setOpen }), [open, setOpen]);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, setOpen]);

  return <DialogContext.Provider value={ctx}>{children}</DialogContext.Provider>;
}

export function DialogTrigger({
  children,
  ...rest
}: HTMLAttributes<HTMLButtonElement> & PropsWithChildren): ReactNode {
  const { setOpen } = useDialogContext("DialogTrigger");
  return (
    <button type="button" onClick={() => setOpen(true)} {...rest}>
      {children}
    </button>
  );
}

export function DialogContent({
  className,
  children,
  ...rest
}: HTMLAttributes<HTMLDivElement>): ReactNode {
  const { open, setOpen } = useDialogContext("DialogContent");
  if (!open) return null;
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm"
      onClick={() => setOpen(false)}
      role="presentation"
    >
      <div
        role="dialog"
        aria-modal="true"
        className={cn(
          "max-w-md w-full mx-4 rounded-lg border p-6",
          "bg-[color:var(--color-osrs-panel)] border-[color:var(--color-osrs-panel-border)]",
          "shadow-2xl shadow-black/60",
          className,
        )}
        onClick={(event) => event.stopPropagation()}
        {...rest}
      >
        {children}
      </div>
    </div>
  );
}

export function DialogHeader({ className, ...rest }: HTMLAttributes<HTMLDivElement>): ReactNode {
  return <div className={cn("mb-4 flex flex-col gap-1", className)} {...rest} />;
}

export function DialogTitle({
  className,
  ...rest
}: HTMLAttributes<HTMLHeadingElement>): ReactNode {
  return (
    <h2
      className={cn(
        "font-osrs text-lg text-[color:var(--color-osrs-gold)]",
        className,
      )}
      {...rest}
    />
  );
}

export function DialogDescription({
  className,
  ...rest
}: HTMLAttributes<HTMLParagraphElement>): ReactNode {
  return (
    <p
      className={cn(
        "text-sm text-[color:var(--color-osrs-gold-soft)]/80",
        className,
      )}
      {...rest}
    />
  );
}
