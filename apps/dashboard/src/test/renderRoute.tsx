import type { ReactNode } from "react";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderResult } from "@testing-library/react";
import { ToastProvider } from "@/components/ui/toast";

type SupportedPath = "/" | "/pair" | "/usage" | "/accounts" | "/billing";

/**
 * Render a single route component inside an isolated TanStack Router
 * instance backed by a memory history. We re-create the router per
 * test to avoid cross-test state leakage. The shell is a bare
 * <Outlet /> — AppShell behaviour is exercised by its own integration
 * test, not by every route render.
 */
export async function renderRoute(
  path: SupportedPath,
  Component: () => ReactNode,
): Promise<RenderResult> {
  const rootRoute = createRootRoute({
    component: () => <Outlet />,
  });

  const childRoute = createRoute({
    getParentRoute: () => rootRoute,
    path,
    component: Component,
  });

  const router = createRouter({
    routeTree: rootRoute.addChildren([childRoute]),
    history: createMemoryHistory({ initialEntries: [path] }),
  });

  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  const result = render(
    <QueryClientProvider client={qc}>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </QueryClientProvider>,
  );

  // TanStack Router resolves the initial match asynchronously.
  await router.load();
  return result;
}
