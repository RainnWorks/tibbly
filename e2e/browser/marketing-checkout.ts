/**
 * Marketing landing → Stripe checkout, exercised through Claude in Chrome.
 *
 * How to run (operator: a Claude Code session with claude-in-chrome MCP
 * enabled):
 *
 *   1. `bun run e2e/browser/marketing-checkout.ts` — this boots the
 *      orchestrator with `withMarketing: true` and prints the marketing
 *      URL and the human-readable steps below.
 *   2. From the agent loop, drive Chrome via the MCP tools, performing
 *      each step exactly. The expected outcomes are described inline.
 *   3. When the agent reports green or red, kill the orchestrator with
 *      Ctrl-C — `await ctx.cleanup()` runs in the SIGINT handler.
 *
 * Why we don't drive the browser from inside this script: the Chrome
 * MCP only exists in the agent's tool surface; this file is a runbook
 * the agent reads.
 */
import { bootOrchestrator } from "../orchestrator/boot";

const STEPS = [
  {
    title: "Open the marketing landing",
    action: "navigate to {marketingUrl}",
    expect: [
      "200 in network requests log",
      "Hero section visible with the Tibbly headline",
      "No em-dashes anywhere in the rendered text (per taste-skill)",
      "No console errors",
    ],
  },
  {
    title: "Scroll to PricingTiers",
    action: "scroll down until the 'Choose Hobbyist' CTA is on screen",
    expect: [
      "Three tier cards visible: Hobbyist, Pro, Iron",
      "Each CTA reads exactly 'Choose Hobbyist' / 'Choose Pro' / 'Choose Iron'",
    ],
  },
  {
    title: "Click the Hobbyist CTA",
    action: "left_click the 'Choose Hobbyist' button",
    expect: [
      "A fetch to {backendUrl}/v1/billing/checkout/hobbyist appears in the network log",
      "Response body has shape { url: 'https://checkout.stripe.test/dev_stub?...', tier: 'hobbyist' }",
      "Browser attempts to navigate to checkout.stripe.test (will fail to load, that is fine — the assertion is the URL)",
    ],
  },
  {
    title: "Assert the dev-stub URL never reached real Stripe",
    action:
      "verify the recorded request URL begins with 'https://checkout.stripe.test/dev_stub' and that no request was made to api.stripe.com",
    expect: ["Zero requests to api.stripe.com or checkout.stripe.com"],
  },
  {
    title: "Capture a screenshot for the loop log",
    action: "computer screenshot, save_to_disk true",
    expect: ["Marketing page hero + pricing visible in the screenshot"],
  },
] as const;

async function main(): Promise<void> {
  const ctx = await bootOrchestrator({ withMarketing: true });
  process.on("SIGINT", () => {
    void ctx.cleanup().then(() => process.exit(0));
  });

  // eslint-disable-next-line no-console
  console.log("");
  // eslint-disable-next-line no-console
  console.log("=== marketing-checkout runbook ===");
  // eslint-disable-next-line no-console
  console.log(`backendUrl   = ${ctx.backendUrl}`);
  // eslint-disable-next-line no-console
  console.log(`marketingUrl = ${ctx.marketingUrl}`);
  // eslint-disable-next-line no-console
  console.log("");
  STEPS.forEach((step, i) => {
    // eslint-disable-next-line no-console
    console.log(`[${i + 1}] ${step.title}`);
    // eslint-disable-next-line no-console
    console.log(
      `    action: ${step.action
        .replace("{marketingUrl}", ctx.marketingUrl ?? "(no marketing)")
        .replace("{backendUrl}", ctx.backendUrl)}`,
    );
    for (const e of step.expect) {
      // eslint-disable-next-line no-console
      console.log(`    expect: ${e}`);
    }
    // eslint-disable-next-line no-console
    console.log("");
  });
  // eslint-disable-next-line no-console
  console.log("Orchestrator is up. Drive Chrome from the agent. Ctrl-C to stop.");
}

if (import.meta.main) {
  void main();
}
