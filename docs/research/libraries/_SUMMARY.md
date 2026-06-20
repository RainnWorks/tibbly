# Library cheat sheet

One-line recommendations. Click through to per-area docs for install commands,
hello-world examples, and "when NOT to use" notes.

## Stack at a glance

| Area | Pick | Notes |
|---|---|---|
| LLM orchestration | **`ai` (Vercel AI SDK v6) + `@openrouter/ai-sdk-provider`** | See [llm.md](./llm.md) |
| Backend framework | **Hono** on Bun (`bun add hono`) | See [backend.md](./backend.md) |
| DB / ORM | **Drizzle** + `@electric-sql/pglite` (dev) + `postgres` (prod) | See [db.md](./db.md) |
| Auth | **better-auth** for dashboard + **custom device-key + pairing-code** for plugin | See [auth.md](./auth.md) |
| Billing | **`stripe` v22 Node SDK** (drop `stripe-event-types` — inline now) | See [billing.md](./billing.md) |
| React stack | **Vite + React 18 + TanStack Query/Router + Tailwind + shadcn + RHF + Zod** | See [react.md](./react.md) |
| Charts | **recharts** | See [charts.md](./charts.md) |
| Realtime | **Bun.serve native WS** (high-volume) + **hono/bun WS** (HTTP-adjacent) | See [realtime.md](./realtime.md) |
| Testing | **bun:test** (backend) + **vitest** + **RTL** (frontend) + **Claude-in-Chrome** (E2E tonight) | See [testing.md](./testing.md) |
| Logging | **pino** + `pino-pretty` (dev transport) | See [logging.md](./logging.md) |
| IDs | **nanoid** (default) + **uuid v7** (time-ordered) | See [ids.md](./ids.md) |
| Time | **date-fns** (+ `date-fns-tz` for explicit TZ math) | See [time.md](./time.md) |

## Combined install — backend

```bash
cd apps/backend
bun add hono ai @openrouter/ai-sdk-provider zod \
        drizzle-orm @electric-sql/pglite postgres \
        better-auth stripe pino nanoid uuid date-fns
bun add -D drizzle-kit pino-pretty @types/bun
```

## Combined install — dashboard

```bash
cd apps/dashboard
bun create vite@latest . -- --template react-ts
bun add react react-dom \
        @tanstack/react-query @tanstack/react-router \
        react-hook-form zod @hookform/resolvers \
        recharts \
        clsx tailwind-merge class-variance-authority lucide-react \
        date-fns nanoid
bun add -D tailwindcss postcss autoprefixer vitest \
            @testing-library/react @testing-library/jest-dom jsdom
bunx tailwindcss init -p
bunx shadcn@latest init
```

## Combined install — marketing

Same as dashboard, plus presence client only — no auth, no charts.

## Hard "don'ts"

- ❌ Don't pick Prisma (codegen + query engine binary on Bun is painful).
- ❌ Don't write a custom auth library (use better-auth + the device-key flow).
- ❌ Don't add LangChain (Vercel AI SDK is enough; LangChain is overhead).
- ❌ Don't add `stripe-event-types` (event types inline in `stripe` v15+).
- ❌ Don't reach for Partykit tonight — Bun.serve's pub/sub covers it.
- ❌ Don't use `console.log` on the backend — pino with redaction is the rule.
- ❌ Don't use `crypto.randomUUID` for chat/event IDs — use uuid v7.
- ❌ Don't use moment / dayjs over date-fns unless there's a bundle-size emergency.

## Trust signals checked 2026-06-21

All versions confirmed against the official GitHub release pages or npm:

- ai SDK — v6 line. https://ai-sdk.dev
- @openrouter/ai-sdk-provider — v1.2.x. https://www.npmjs.com/package/@openrouter/ai-sdk-provider
- hono — v4.12.26 (2026-06-18). https://github.com/honojs/hono
- drizzle-orm — current. https://orm.drizzle.team
- better-auth — v1.6.20 (2026-06-20). https://github.com/better-auth/better-auth
- stripe — v22.2.2 (2026-06-18). https://github.com/stripe/stripe-node
- recharts — v3.8.1 (2026-03-25). https://github.com/recharts/recharts
- vitest — v4.1.7. https://vitest.dev
- pino — v10.3.1 (2026-02-09). https://github.com/pinojs/pino
- nanoid — v5.1.15 (2026-06-20). https://github.com/ai/nanoid
- uuid — v14.0.1 (2026-06-20). https://github.com/uuidjs/uuid
- date-fns — v4.4.0 (2026-05-29). https://github.com/date-fns/date-fns
- dayjs — v1.11.21 (2026-05-26). https://github.com/iamkun/dayjs
