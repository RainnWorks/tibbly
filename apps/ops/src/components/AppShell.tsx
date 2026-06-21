import {
  Link,
  Outlet,
  useNavigate,
  useRouterState,
} from "@tanstack/react-router";
import { useEffect, useState, type ReactNode } from "react";
import {
  Activity,
  BarChart3,
  Gauge,
  LogOut,
  Search,
  Users,
} from "lucide-react";
import { fetchSession, getOperatorEmail, logout } from "@/lib/auth";
import { cn } from "@/lib/cn";

const NAV: ReadonlyArray<{
  to: string;
  label: string;
  icon: typeof Gauge;
  shortcut: string;
}> = [
  { to: "/", label: "Dashboard", icon: Gauge, shortcut: "g h" },
  { to: "/users", label: "Users", icon: Users, shortcut: "g u" },
  { to: "/analytics", label: "Analytics", icon: BarChart3, shortcut: "g a" },
  { to: "/openrouter", label: "OpenRouter", icon: Activity, shortcut: "g o" },
];

export function AppShell(): ReactNode {
  const navigate = useNavigate();
  const state = useRouterState({ select: (s) => s.location.pathname });
  const [checking, setChecking] = useState(true);
  const [email, setEmail] = useState<string | null>(getOperatorEmail());

  // First-load auth gate. Bypass on /login so the login screen can render.
  useEffect(() => {
    let cancelled = false;
    async function check() {
      if (state === "/login") {
        if (!cancelled) setChecking(false);
        return;
      }
      try {
        const found = await fetchSession();
        if (cancelled) return;
        if (!found) {
          navigate({ to: "/login" });
          return;
        }
        setEmail(found);
      } catch {
        if (!cancelled) navigate({ to: "/login" });
      } finally {
        if (!cancelled) setChecking(false);
      }
    }
    check();
    return () => {
      cancelled = true;
    };
    // We intentionally only re-run on path changes that cross the
    // login/non-login boundary; the navigate is stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  // g-prefix navigation: `g h`, `g u`, `g a`, `g o`. `/` focuses search.
  useEffect(() => {
    let gMode = false;
    let gTimer: number | undefined;
    const onKey = (event: KeyboardEvent) => {
      if (event.metaKey || event.ctrlKey || event.altKey) return;
      const target = event.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === "INPUT" ||
          target.tagName === "TEXTAREA" ||
          target.isContentEditable)
      ) {
        return;
      }
      if (event.key === "/") {
        event.preventDefault();
        const el = document.querySelector<HTMLInputElement>("[data-ops-search]");
        el?.focus();
        return;
      }
      if (event.key === "g") {
        gMode = true;
        if (gTimer) window.clearTimeout(gTimer);
        gTimer = window.setTimeout(() => {
          gMode = false;
        }, 800);
        return;
      }
      if (gMode) {
        gMode = false;
        if (event.key === "h") navigate({ to: "/" });
        if (event.key === "u") navigate({ to: "/users" });
        if (event.key === "a") navigate({ to: "/analytics" });
        if (event.key === "o") navigate({ to: "/openrouter" });
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [navigate]);

  if (state === "/login") {
    return (
      <div className="min-h-screen">
        <Outlet />
      </div>
    );
  }

  if (checking) {
    return (
      <div className="min-h-screen flex items-center justify-center text-[var(--color-ops-text-faint)] font-mono text-sm">
        verifying session
      </div>
    );
  }

  return (
    <div className="grid min-h-screen grid-cols-[200px_1fr] bg-[var(--color-ops-bg)]">
      <aside className="border-r border-[var(--color-ops-border)] bg-[var(--color-ops-surface)] flex flex-col">
        <div className="px-4 py-4 border-b border-[var(--color-ops-border)]">
          <div className="text-sm font-semibold text-[var(--color-ops-text)] tracking-tight">
            tibbly ops
          </div>
          <div className="text-[11px] text-[var(--color-ops-text-faint)] font-mono mt-1">
            internal
          </div>
        </div>
        <nav className="flex-1 px-2 py-3 flex flex-col gap-0.5">
          {NAV.map((item) => {
            const Icon = item.icon;
            return (
              <Link
                key={item.to}
                to={item.to}
                className={cn(
                  "flex items-center justify-between gap-2 rounded-[4px] px-2.5 py-1.5",
                  "text-sm text-[var(--color-ops-text-muted)] hover:text-[var(--color-ops-text)] hover:bg-[var(--color-ops-surface-2)]",
                )}
                activeProps={{
                  className:
                    "bg-[var(--color-ops-surface-2)] text-[var(--color-ops-text)]",
                }}
                activeOptions={{ exact: item.to === "/" }}
              >
                <span className="flex items-center gap-2">
                  <Icon size={14} strokeWidth={1.75} />
                  {item.label}
                </span>
                <span className="font-mono text-[10px] text-[var(--color-ops-text-faint)]">
                  {item.shortcut}
                </span>
              </Link>
            );
          })}
        </nav>
        <div className="px-3 py-3 border-t border-[var(--color-ops-border)] text-[11px] text-[var(--color-ops-text-faint)] font-mono">
          <div>
            <span className="opacity-70">op</span>{" "}
            <span className="text-[var(--color-ops-text-muted)]">{email ?? "?"}</span>
          </div>
          <button
            type="button"
            onClick={async () => {
              await logout();
              setEmail(null);
              navigate({ to: "/login" });
            }}
            className="mt-2 inline-flex items-center gap-1.5 text-[var(--color-ops-text-muted)] hover:text-[var(--color-ops-text)]"
          >
            <LogOut size={12} strokeWidth={1.75} /> sign out
          </button>
        </div>
      </aside>

      <main className="flex flex-col min-w-0">
        <header className="sticky top-0 z-10 flex h-12 items-center gap-3 border-b border-[var(--color-ops-border)] bg-[var(--color-ops-bg)]/95 backdrop-blur px-4">
          <div className="text-xs text-[var(--color-ops-text-muted)] font-mono">
            {state}
          </div>
          <div className="ml-auto flex items-center gap-2 text-[11px] text-[var(--color-ops-text-faint)] font-mono">
            <Search size={12} strokeWidth={1.75} />
            <span>press / to search</span>
          </div>
        </header>
        <div className="flex-1 min-w-0 p-5">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
