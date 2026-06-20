# Testing — recommendation

## Decision

- **Backend:** `bun:test` (built into Bun). Jest-compatible API, zero deps.
- **Frontend:** `vitest` + `@testing-library/react`.
- **E2E:** Claude-in-Chrome MCP (already wired) for smoke flows; Playwright
  for any CI-driven E2E later.

| Layer | Pick | Why |
|---|---|---|
| Backend unit / integration | `bun:test` | Built-in, fast, Jest-compatible matchers, async/timeouts/retry/repeats supported |
| Frontend unit / component | `vitest` (+ RTL) | Vite-native, ESM-first, fast watch mode |
| E2E (overnight build) | Claude-in-Chrome | Already wired, no extra infra |
| E2E (future CI) | Playwright | Cross-browser, mature |

## Backend: bun:test

Source: https://bun.com/docs/test/writing

```bash
# nothing to install — built in
bun test
```

```ts
// apps/backend/src/billing/credits.test.ts
import { expect, test, describe } from 'bun:test';
import { decrementCredits } from './credits';

describe('decrementCredits', () => {
  test('blocks at zero', async () => {
    await expect(decrementCredits({ userId: 'u1', tokens: 9999 }))
      .rejects.toThrow(/credits/i);
  });

  test('deducts on success', async () => {
    const remaining = await decrementCredits({ userId: 'u2', tokens: 100 });
    expect(remaining).toBeGreaterThanOrEqual(0);
  });
});
```

Run a single file:

```bash
bun test src/billing/credits.test.ts
```

Supports `test.skip`, `test.only`, `test.each`, `expect.assertions(n)`,
snapshots, mock functions — all Jest-compatible.

## Frontend: vitest

Source: https://vitest.dev/guide/  — v4.1.7 latest.

```bash
bun add -D vitest @testing-library/react @testing-library/jest-dom jsdom
```

`vite.config.ts`:

```ts
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: { environment: 'jsdom', globals: true, setupFiles: ['./src/test/setup.ts'] },
});
```

```tsx
// apps/dashboard/src/components/PairForm.test.tsx
import { render, screen } from '@testing-library/react';
import { describe, test, expect } from 'vitest';
import { PairForm } from './PairForm';

describe('PairForm', () => {
  test('renders an input', () => {
    render(<PairForm />);
    expect(screen.getByPlaceholderText('ABC123')).toBeInTheDocument();
  });
});
```

## E2E: Claude-in-Chrome (tonight)

Already wired in the harness. Smoke tests live alongside the marketing /
dashboard apps as scripts that the operating agent runs on each loop. See
the parent agent's instructions for the actual invocation.

## E2E: Playwright (future)

Source: https://playwright.dev/docs/intro

```bash
bun create playwright@latest
```

Adopt later when we set up CI. Don't write Playwright tests tonight — the
Claude-in-Chrome smoke tests give us the coverage we need.

## When NOT to use each

- Don't use vitest for backend — bun:test is faster and zero-config in a Bun
  project.
- Don't use bun:test for React components — the JSDOM + RTL story is smoother
  in vitest.
- Don't write Playwright tests tonight — overhead for no gain pre-handback.

## Maintenance signals

- bun:test — part of Bun runtime, no version pin needed.
- vitest — v4.1.7 (2026). https://vitest.dev
- Playwright — v1.4x line. https://playwright.dev/docs/intro
