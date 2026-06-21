import { Link, Outlet } from "@tanstack/react-router";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

const NAV: ReadonlyArray<{ to: string; label: string }> = [
  { to: "/", label: "Overview" },
  { to: "/pair", label: "Pair plugin" },
  { to: "/usage", label: "Usage" },
  { to: "/accounts", label: "Accounts" },
  { to: "/billing", label: "Billing" },
];

export function AppShell(): ReactNode {
  return (
    <div className="flex flex-col min-h-screen">
      <header className="border-b border-[color:var(--color-osrs-panel-border)] bg-[color:var(--color-osrs-panel)]/70 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <Link
            to="/"
            className="font-osrs text-xl text-[color:var(--color-osrs-gold)] tracking-wider"
          >
            OSRS LLM Helper
          </Link>
          <nav className="flex items-center gap-1">
            {NAV.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className={cn(
                  "px-3 py-1.5 rounded-md text-sm font-medium transition-colors",
                  "text-[color:var(--color-osrs-gold-soft)] hover:bg-[color:var(--color-osrs-bg)]",
                )}
                activeProps={{
                  className: "bg-[color:var(--color-osrs-bg)] text-[color:var(--color-osrs-gold)]",
                }}
                activeOptions={{ exact: item.to === "/" }}
              >
                {item.label}
              </Link>
            ))}
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-8">
        <Outlet />
      </main>

      <footer className="border-t border-[color:var(--color-osrs-panel-border)] bg-[color:var(--color-osrs-panel)]/60">
        <div className="mx-auto max-w-6xl px-6 py-4 text-xs text-[color:var(--color-osrs-gold-soft)]/60">
          OSRS LLM Helper dashboard skeleton — real backend wiring lands in RAI-27.
        </div>
      </footer>
    </div>
  );
}
