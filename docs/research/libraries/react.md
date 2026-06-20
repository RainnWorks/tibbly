# React stack — recommendation

## Decision

**Vite + React 18 + TanStack Query v5 + TanStack Router + Tailwind + shadcn/ui
primitives + react-hook-form + Zod resolver.**

That is the modern, well-trodden stack and matches NORTH_STAR's library list.
Choosing TanStack Router over React Router because it's type-safe end-to-end
and pairs natively with TanStack Query for loaders.

## Packages and install

```bash
# scaffold
bun create vite@latest apps/dashboard -- --template react-ts

# core stack
bun add react react-dom
bun add @tanstack/react-query @tanstack/react-router
bun add react-hook-form zod @hookform/resolvers
bun add clsx tailwind-merge class-variance-authority
bun add lucide-react

# tailwind + shadcn primitives
bun add -D tailwindcss postcss autoprefixer @types/react @types/react-dom
bunx tailwindcss init -p
bunx shadcn@latest init
```

Sources:
- https://vitejs.dev/guide/
- https://tanstack.com/query/latest
- https://tanstack.com/router/latest
- https://react-hook-form.com/get-started
- https://ui.shadcn.com/docs

## Hello-world: TanStack Query

```tsx
// apps/dashboard/src/main.tsx
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { createRoot } from 'react-dom/client';

const qc = new QueryClient();

function Usage() {
  const { data, isLoading } = useQuery({
    queryKey: ['usage'],
    queryFn: () => fetch('/api/usage').then((r) => r.json()),
  });
  if (isLoading) return <div>loading…</div>;
  return <div>credits remaining: {data.credits}</div>;
}

createRoot(document.getElementById('root')!).render(
  <QueryClientProvider client={qc}>
    <Usage />
  </QueryClientProvider>,
);
```

## Hello-world: react-hook-form + Zod resolver

Source: https://react-hook-form.com/get-started

```tsx
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const schema = z.object({
  pairingCode: z.string().length(6, '6 characters'),
});
type FormValues = z.infer<typeof schema>;

export function PairForm() {
  const { register, handleSubmit, formState: { errors, isSubmitting } } =
    useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    await fetch('/api/devices/pair', {
      method: 'POST',
      body: JSON.stringify(values),
    });
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <input {...register('pairingCode')} placeholder="ABC123" />
      {errors.pairingCode && <span>{errors.pairingCode.message}</span>}
      <button disabled={isSubmitting}>Pair plugin</button>
    </form>
  );
}
```

## Router choice

- **TanStack Router** for the dashboard (typed routes, search-param validation).
- **No router** on the marketing site if it's mostly static — use Vite's
  multi-page mode or a single SPA index with anchor links. Simpler.

## Styling

- **Tailwind** for utility classes.
- **shadcn/ui** components — copied into the repo (not a dep), so we can theme
  to OSRS aesthetic without fighting a library.
- Use `class-variance-authority` for component variants.

## When NOT to use

- **Server components / Next.js.** Out of scope. Vite SPA is enough.
- **Redux / Zustand.** TanStack Query handles server state; React's own
  `useState` / context handles UI state. No global store needed.
- **Form libraries other than react-hook-form** — we want one form library, RHF
  is canonical.

## Maintenance signals

- Vite — v6 line as of 2026.
- React — v18, v19 stable but not adopted yet.
- TanStack Query — v5 line current. https://tanstack.com/query/latest
- TanStack Router — v1 stable. https://tanstack.com/router/latest
- react-hook-form — v7 stable.
- shadcn/ui — actively curated.
