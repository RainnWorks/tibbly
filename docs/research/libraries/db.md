# Database / ORM — recommendation

## Decision

**Use Drizzle ORM** with `drizzle-orm/pglite` for local dev and the Postgres
driver for prod. Zero codegen step, SQL-shaped types, works natively on Bun.

| Library | Verdict | Why |
|---|---|---|
| **Drizzle** | ✅ PICK | First-class PGLite + Postgres + Bun support, no codegen, lightweight, Zod schema integration via drizzle-zod |
| Prisma | ❌ Skip | Heavy CLI/codegen, separate query engine binary, awkward on Bun, overkill for the few tables we need tonight |
| Kysely | ⚠️ Fine alt | Pure type-safe query builder. Excellent if you want SQL-first. We prefer Drizzle because schema-as-code makes migrations trivial |

## Packages

- `drizzle-orm` — ORM core.
- `drizzle-kit` — migrations CLI.
- `@electric-sql/pglite` — WASM Postgres for dev / tests.
- `postgres` (or `pg`) — prod driver.

Source: https://orm.drizzle.team/docs/connect-pglite (official)

## Install (dev with PGLite)

```bash
bun add drizzle-orm @electric-sql/pglite
bun add -D drizzle-kit @types/bun
```

## Install (prod with Postgres)

```bash
bun add drizzle-orm postgres
```

## Hello-world: PGLite (dev)

```ts
// apps/backend/src/db/index.ts
import { drizzle } from 'drizzle-orm/pglite';

export const db = drizzle('./.data/dev.pgdata');
```

## Hello-world: Postgres (prod)

```ts
import { drizzle } from 'drizzle-orm/postgres-js';
import postgres from 'postgres';

const client = postgres(process.env.DATABASE_URL!);
export const db = drizzle(client);
```

## Schema-as-code example

```ts
// apps/backend/src/db/schema.ts
import { pgTable, text, integer, timestamp } from 'drizzle-orm/pg-core';

export const users = pgTable('users', {
  id: text('id').primaryKey(),                 // nanoid
  stripeCustomerId: text('stripe_customer_id'),
  credits: integer('credits').notNull().default(0),
  createdAt: timestamp('created_at').notNull().defaultNow(),
});

export const devices = pgTable('devices', {
  id: text('id').primaryKey(),                 // nanoid
  userId: text('user_id').notNull().references(() => users.id),
  deviceKeyHash: text('device_key_hash').notNull(),
  playerName: text('player_name'),
  createdAt: timestamp('created_at').notNull().defaultNow(),
});
```

## Migrations

```bash
bunx drizzle-kit generate
bunx drizzle-kit migrate
```

`drizzle.config.ts`:

```ts
import { defineConfig } from 'drizzle-kit';

export default defineConfig({
  schema: './src/db/schema.ts',
  out: './drizzle',
  dialect: 'postgresql',
  dbCredentials: { url: process.env.DATABASE_URL! },
});
```

## When NOT to use

- **Truly relational, deeply nested queries with `with: {}`.** Drizzle's relational
  query API exists but is less polished than Prisma's. We don't need it tonight.
- **Schema introspection of an existing huge DB.** Drizzle has `drizzle-kit pull`
  but Prisma is smoother. N/A — we own the schema.

## Maintenance signals

- Drizzle — actively maintained, weekly releases. https://orm.drizzle.team
- PGLite — ElectricSQL ships frequent releases. ~2.6 MB gzipped WASM.
  https://github.com/electric-sql/pglite

## Companion

- `drizzle-zod` — generate Zod schemas from Drizzle tables — share at the
  boundary with the frontend via `packages/shared-types`.
