# Scope guard — what NOT to do

Append-only. Each entry: "tempted to X, rejected because Y".
Re-read at the start of every loop. If I'm about to do something on this list,
I stop.

## Hard non-goals (from the user)

- ❌ **No in-game automation / bot behavior.** We help the player think and
  see; we never play for them. No mouse/keyboard synthesis.
- ❌ **No real-money trading helpers.** No auto-execute price flipping.
- ❌ **No client modifications outside the plugin sandbox.** We're a normal
  RuneLite plugin.

## Tempting things that are NOT must-ships tonight

- ❌ Building a mobile companion app. → Defer. Browser-only for now.
- ❌ Localization / i18n. → English only.
- ❌ Multi-language LLM model selection UI. → Default model per tier; tweak via
  the dashboard later.
- ❌ Self-hosted model option. → OpenRouter only.
- ❌ Voice input/output. → Text only.
- ❌ A full blog/CMS. → Plain markdown if we want a `/blog`.
- ❌ Stripe Connect / marketplace / multi-tenant. → Single seller.
- ❌ A Discord bot integration. → Defer.
- ❌ Refactoring the existing Kotlin plugin's internals for elegance.
  → Only touch the plugin to swap chat transport + add token gating.
- ❌ Migrating away from any existing dependency for "cleanliness". → No.
- ❌ Building a custom OAuth provider. → If we need a login, use Stripe
  Customer Portal + magic links; nothing custom.
- ❌ Designing an admin panel for ourselves. → Defer.
- ❌ Anything that requires user input we can't get tonight. → Pick the
  lowest-regret default, log it in `OPEN_QUESTIONS.md`, move on.

## Anti-patterns I will not fall into

- ❌ Rebuilding the plugin's MCP surface from scratch. The 72 tools we have
  are the asset. The work is wrapping them, not rewriting them.
- ❌ Spending more than ~10% of the night on docs that nobody will read tomorrow.
- ❌ Spinning up infra (Fly, Vercel) tonight if local dev is enough to prove
  the system works. Deployment is a separate session.
- ❌ Introducing new languages or runtimes beyond Bun/TS/Kotlin.
- ❌ Letting any sub-agent expand its own scope. Their In-Scope list is law.
- ❌ Touching `.env` or anything that affects the OpenRouter key beyond
  reading it from `process.env`.

## When something on this list "actually does need doing"

- Add a Block to `OPEN_QUESTIONS.md` so the user sees it on return.
- Do not action it tonight unless every must-ship is green.
