import {
  createRootRoute,
  createRoute,
  createRouter,
} from "@tanstack/react-router";
import { AppShell } from "@/components/AppShell";
import { RouteRoot } from "@/routes/root";
import { RoutePair } from "@/routes/pair";
import { RouteUsage } from "@/routes/usage";
import { RouteAccounts } from "@/routes/accounts";
import { RouteBilling } from "@/routes/billing";

const rootRoute = createRootRoute({
  component: AppShell,
});

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: RouteRoot,
});

const pairRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/pair",
  component: RoutePair,
});

const usageRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/usage",
  component: RouteUsage,
});

const accountsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/accounts",
  component: RouteAccounts,
});

const billingRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/billing",
  component: RouteBilling,
});

const routeTree = rootRoute.addChildren([
  indexRoute,
  pairRoute,
  usageRoute,
  accountsRoute,
  billingRoute,
]);

export const router = createRouter({
  routeTree,
  defaultPreload: "intent",
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
