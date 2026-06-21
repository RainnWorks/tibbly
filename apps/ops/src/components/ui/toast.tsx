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
 * Minimal toast primitive — context-driven `useToast()` returns a
 * `push` function. We render a fixed bottom-right stack. Each toast
 * auto-dismisses after `duration` ms (default 4s).
 *
 * Intentionally lo-fi — Radix/sonner can swap in later if the surface
 * needs richer behaviour (action slots, swipe-to-dismiss).
 */

export type ToastVariant = "info" | "success" | "danger";

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

  // Auto-dismiss loop. We schedule one timeout per toast that lacks a
  // pending one; on unmount we clear all pending timers.
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
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role="status"
            className={cn(
              "min-w-[260px] max-w-sm rounded-md border p-4 shadow-lg",
              "bg-[color:var(--color-osrs-panel)] text-amber-50",
              toast.variant === "success" &&
                "border-[color:var(--color-osrs-success)]",
              toast.variant === "danger" &&
                "border-[color:var(--color-osrs-danger)]",
              (toast.variant ?? "info") === "info" &&
                "border-[color:var(--color-osrs-panel-border)]",
            )}
          >
            <div className="font-osrs text-sm text-[color:var(--color-osrs-gold)]">
              {toast.title}
            </div>
            {toast.description ? (
              <div className="text-xs text-[color:var(--color-osrs-gold-soft)]/80 mt-1">
                {toast.description}
              </div>
            ) : null}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used inside <ToastProvider>");
  return ctx;
}
