/**
 * Ops console login → user search → ban, exercised through Claude in
 * Chrome. Companion to marketing-checkout.ts.
 *
 * Run:
 *   bun run e2e/browser/ops-login-and-debug.ts
 *
 * The orchestrator seeds one paying user up-front so the Users list has
 * a row to click and ban. The admin email is the orchestrator's deterministic
 * value (env.ADMIN_EMAIL) and the JWT secret is per-run, so logging in
 * works against the fresh in-process backend without touching production.
 */
import { bootOrchestrator } from "../orchestrator/boot";
import { makeDeviceKey } from "../harness/fake-plugin";
import { pairUser } from "../orchestrator/seed";

const STEPS = [
  {
    title: "Open the ops login page",
    action: "navigate to {opsUrl}/login",
    expect: [
      "Login form visible",
      "Header reads 'internal access only'",
      "No console errors",
    ],
  },
  {
    title: "Submit the admin email",
    action:
      "type '{adminEmail}' into the email input, click the 'continue' button",
    expect: [
      "POST {backendUrl}/admin/login returns 200 with body { ok: true, email }",
      "Set-Cookie response header carries ops_session=...",
      "Router redirects to /",
    ],
  },
  {
    title: "Navigate to Users",
    action: "click the 'Users' nav item or navigate to {opsUrl}/users",
    expect: [
      "GET {backendUrl}/admin/users returns 200 with at least one row",
      "Seed user '{seedEmail}' visible in the table",
    ],
  },
  {
    title: "Search by email",
    action: "type '{seedEmail}' in the search input",
    expect: ["Filtered list has exactly one row"],
  },
  {
    title: "Click the seed user row",
    action: "left_click the row to navigate into the user detail view",
    expect: [
      "GET {backendUrl}/admin/users/{seedUserId} returns 200",
      "Detail view shows the user's email, tier, status: active",
    ],
  },
  {
    title: "Open the Ban modal and submit",
    action: "click 'Ban', type 'abuse e2e' as the reason, confirm",
    expect: [
      "POST {backendUrl}/admin/users/{seedUserId}/ban returns 200",
      "Status pill flips to 'banned'",
    ],
  },
  {
    title: "Verify dial-settings 6/2/8 in the AppShell design",
    action: "read the page source / styles for the AppShell tokens",
    expect: [
      "The dial design from AppShell.tsx top-of-file is rendered: density 6, contrast 2, weight 8 (per docs/agents/AGENT_LOG.md)",
    ],
  },
] as const;

async function main(): Promise<void> {
  const ctx = await bootOrchestrator({ withOps: true });

  // Seed one user the operator can click on.
  const deviceKey = makeDeviceKey();
  const seed = await pairUser(ctx, {
    deviceKey,
    email: "e2e-seed@example.test",
    tier: "pro",
    creditTokens: 100_000,
  });

  process.on("SIGINT", () => {
    void ctx.cleanup().then(() => process.exit(0));
  });

  /* eslint-disable no-console */
  console.log("");
  console.log("=== ops-login-and-debug runbook ===");
  console.log(`backendUrl = ${ctx.backendUrl}`);
  console.log(`opsUrl     = ${ctx.opsUrl}`);
  console.log(`adminEmail = ${ctx.env.ADMIN_EMAIL}`);
  console.log(`seedUserId = ${seed.userId}`);
  console.log(`seedEmail  = ${seed.email}`);
  console.log("");
  STEPS.forEach((step, i) => {
    const replace = (s: string): string =>
      s
        .replace("{opsUrl}", ctx.opsUrl ?? "(no ops)")
        .replace("{backendUrl}", ctx.backendUrl)
        .replace("{adminEmail}", ctx.env.ADMIN_EMAIL)
        .replace("{seedUserId}", seed.userId)
        .replace("{seedEmail}", seed.email ?? "");
    console.log(`[${i + 1}] ${step.title}`);
    console.log(`    action: ${replace(step.action)}`);
    for (const e of step.expect) {
      console.log(`    expect: ${replace(e)}`);
    }
    console.log("");
  });
  console.log("Orchestrator is up. Drive Chrome from the agent. Ctrl-C to stop.");
  /* eslint-enable no-console */
}

if (import.meta.main) {
  void main();
}
