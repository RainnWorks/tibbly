# osrs-llm-helper

Paid SaaS that turns any LLM into a live, in-game OSRS co-pilot.
A RuneLite plugin streams live game state to our Bun backend, which drives
the chat loop over OpenRouter and bills via Stripe.

## Monorepo

This is a Bun-workspace monorepo. All authoritative docs live in
[`docs/INDEX.md`](docs/INDEX.md) — start there.

```
apps/
  plugin/      # RuneLite plugin (Kotlin + Gradle) — the in-game tool surface
  backend/     # Bun + Hono service — chat orchestration, billing, auth
  dashboard/   # React + Vite + Tailwind — paying-user portal
  marketing/   # React + Vite + Tailwind — landing/pricing/demo/FAQ
packages/
  shared-types/   # TS types shared across backend + frontends + plugin protocol
  osrs-assets/    # OSRS sprite/asset catalog
  tooling/        # Shared ESLint, Prettier, tsconfig presets
infra/
  docker/      # Local-dev compose + Dockerfiles
docs/          # Operating contract, architecture, research, agent briefs
```

## Quick start

```sh
# Install all JS/TS workspace deps
bun install

# Typecheck every workspace
bun run typecheck

# Build the RuneLite plugin (Kotlin)
cd apps/plugin && ./gradlew shadowJar
```

Copy `.env.example` to `.env` and fill in:

- `DATABASE_URL` — PGLite (dev) or Postgres (prod).
- `OPENROUTER_API_KEY` — from <https://openrouter.ai/keys>.
- `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` — Stripe billing.
- `PORT` — backend HTTP/WS port (default 3000).

## Where to read next

- [`docs/INDEX.md`](docs/INDEX.md) — full doc index.
- [`docs/agents/NORTH_STAR.md`](docs/agents/NORTH_STAR.md) — what we're building and why.
- [`docs/architecture/MONOREPO.md`](docs/architecture/MONOREPO.md) — this layout in detail.
- [`apps/plugin/README.md`](apps/plugin/README.md) — original RuneLite plugin docs.
