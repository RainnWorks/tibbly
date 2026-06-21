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

/**
 * Render a single route component inside an isolated TanStack Router
 * instance backed by a memory history. We re-create the router per
 * test to avoid cross-test state leakage.
 *
 * Path may be parameterised (e.g. `/users/$id`); the supplied
 * `initialPath` is what the memory history starts at.
 */
export async function renderRoute(
  routePath: string,
  initialPath: string,
  Component: () => ReactNode,
): Promise<RenderResult> {
  const rootRoute = createRootRoute({
    component: () => <Outlet />,
  });

  const childRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: routePath,
    component: Component,
  });

  const router = createRouter({
    routeTree: rootRoute.addChildren([childRoute]),
    history: createMemoryHistory({ initialEntries: [initialPath] }),
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

  await router.load();
  return result;
}
