import {
  createRootRoute,
  createRoute,
  createRouter,
} from "@tanstack/react-router";
import { AppShell } from "@/components/AppShell";
import { RouteAnalytics } from "@/routes/analytics";
import { RouteDashboard } from "@/routes/dashboard";
import { RouteLogin } from "@/routes/login";
import { RouteOpenRouter } from "@/routes/openrouter";
import { RouteUsers } from "@/routes/users";
import { RouteUserDetail } from "@/routes/user-detail";

const rootRoute = createRootRoute({
  component: AppShell,
});

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/login",
  component: RouteLogin,
});

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: RouteDashboard,
});

const usersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/users",
  component: RouteUsers,
});

const userDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/users/$id",
  component: RouteUserDetail,
});

const analyticsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/analytics",
  component: RouteAnalytics,
});

const openrouterRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/openrouter",
  component: RouteOpenRouter,
});

const routeTree = rootRoute.addChildren([
  loginRoute,
  dashboardRoute,
  usersRoute,
  userDetailRoute,
  analyticsRoute,
  openrouterRoute,
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
