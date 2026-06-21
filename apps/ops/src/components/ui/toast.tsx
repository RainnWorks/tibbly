import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
  type ReactNode,
} from "react";
import { cn } from "@/lib/cn";

/**
 * Minimal toast primitive. State-change confirmation only, per
 * MOTION_INTENSITY=2.
 */
export type ToastVariant = "info" | "success" | "danger" | "warn";

export type Toast = {
  readonly id: string;
  readonly title: string;
  readonly description?: string;
  readonly variant?: ToastVariant;
  readonly duration?: number;
};

type ToastContextValue = {
  readonly push: (toast: Omit<Toast, "id">) => string;
  readonly dismiss: (id: string) => void;
  readonly toasts: readonly Toast[];
};

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: PropsWithChildren): ReactNode {
  const [toasts, setToasts] = useState<readonly Toast[]>([]);

  const dismiss = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const push = useCallback<ToastContextValue["push"]>((toast) => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const next: Toast = { id, duration: 4000, variant: "info", ...toast };
    setToasts((prev) => [...prev, next]);
    return id;
  }, []);

  useEffect(() => {
    const timers = toasts.map((toast) =>
      window.setTimeout(() => dismiss(toast.id), toast.duration ?? 4000),
    );
    return () => {
      timers.forEach((t) => window.clearTimeout(t));
    };
  }, [toasts, dismiss]);

  const ctx = useMemo<ToastContextValue>(
    () => ({ push, dismiss, toasts }),
    [push, dismiss, toasts],
  );

  return (
    <ToastContext.Provider value={ctx}>
      {children}
      <div
        aria-live="polite"
        aria-atomic="true"
        className="fixed bottom-4 right-4 z-50 flex flex-col gap-2"
      >
        {toasts.map((toast) => {
          const variant = toast.variant ?? "info";
          return (
            <div
              key={toast.id}
              role="status"
              className={cn(
                "min-w-[260px] max-w-sm rounded-md border p-3",
                "bg-[var(--color-ops-surface-2)]",
                variant === "success" && "border-[var(--color-ops-accent)]",
                variant === "danger" && "border-[var(--color-ops-danger)]",
                variant === "warn" && "border-[var(--color-ops-warn)]",
                variant === "info" && "border-[var(--color-ops-border)]",
              )}
            >
              <div className="text-sm font-semibold text-[var(--color-ops-text)]">
                {toast.title}
              </div>
              {toast.description ? (
                <div className="text-xs text-[var(--color-ops-text-muted)] mt-1">
                  {toast.description}
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used inside <ToastProvider>");
  return ctx;
}
