# Tibbly ops console: design contract

This is the binding design contract for `apps/ops`. Every UI component in
the ops console measures itself against this doc. If a file violates one
of the rules below, the PR is not done. Source: taste-skill pre-flight
(see `.claude/skills/taste-skill/SKILL.md`) applied to the D-8 pivot
(see `docs/agents/DECISION_LOG.md`).

## 1. Design read

Tibbly internal ops cockpit: a single-operator hands-on console for
handling paying-customer issues at 2am. Reads like a calmly-lit airline
ops bridge. Never marketing-flashy, never decorative. Operator-first: the
operator types more than they click, lives in tables, copies ids, and
needs every number a glance away. Designed for one human (Tom) and a
small future ops team. No public framing, no marketing voice.

## 2. Dials

| Dial | Value | Justification |
|---|---|---|
| `DESIGN_VARIANCE` | 6 | Asymmetric layouts where they serve a hierarchy (one big revenue card carrying secondary stats; user detail page splits identity column from activity panes). Not chaos. Never three-equal-feature-card grids. |
| `MOTION_INTENSITY` | 2 | State-change confirmations only. Row focus, save toasts, ban dialog open/close. Zero decorative motion. No marquees, no parallax, no scroll-pinned content. Realtime feed appends without animation noise. |
| `VISUAL_DENSITY` | 8 | Cockpit. Tight padding, hairline dividers, every number in monospace, copy-pasteable user ids, timestamps in both relative and absolute. Tables are the primary surface; cards are the exception. |

## 3. Type system

**Pair:** Geist Sans for chrome, JetBrains Mono for data, ids, timestamps,
numbers, code paths.

- Headings + body chrome use `Geist`. Modern grotesque, neutral, reads as
  software rather than marketing. Not Inter (banned default).
- Every datum reaches for `JetBrains Mono`: user ids, Stripe ids, Stripe
  charge ids, token counts, monetary amounts, dates, durations, status
  enums. Operator copies and pastes constantly; monospace columns
  dramatically reduce scan friction in tables.
- Fonts ship self-hosted via `@fontsource-variable/geist` and
  `@fontsource-variable/jetbrains-mono` so the page does not depend on
  Google Fonts at runtime.

Default weights: 400 body, 500 emphasis, 600 headings, 600 numeric tabs.
Numeric figures everywhere use `font-variant-numeric: tabular-nums`.

## 4. Palette

A single accent on a calm dark base. Off-black, never pure black; off-white,
never pure white. The accent reads as "live system, ok" and doubles as
the focus ring. Two semantic colours (warn / danger) used sparingly.

| Token | Hex | Use |
|---|---|---|
| `--ops-bg` | `#0c0e12` | Page background. Off-black with a faint cool tilt. Not pure black. |
| `--ops-surface` | `#13161c` | Cards, table headers, top nav. |
| `--ops-surface-2` | `#1a1e26` | Elevated surfaces, modal background, focused row. |
| `--ops-border` | `#262b35` | Hairline dividers, table cell borders. |
| `--ops-border-strong` | `#3a4150` | Focused row outline, dialog border. |
| `--ops-text` | `#e6e8ec` | Primary text. Off-white. |
| `--ops-text-muted` | `#9aa0ac` | Secondary text, labels. |
| `--ops-text-faint` | `#5d6470` | Helper text, placeholder, timestamps. |
| `--ops-accent` | `#7cffb5` | Single accent. Mint-green. Used for: focus ring, healthy status, primary action background, sparkline curves. Reads as "system alive". |
| `--ops-accent-ink` | `#0c2418` | Text colour ON the accent (button label, badge text). |
| `--ops-warn` | `#f5b54a` | Warning state: past-due subscription, soft-deleted account. |
| `--ops-danger` | `#ff6b6b` | Banned, error, refund. Used minimally. |

The accent is the only chromatic colour reached for in chrome. Charts use
the accent as the primary series and a desaturated cool-grey as the
secondary, never a rainbow palette.

## 5. Icon source

`lucide-react` (already in `package.json`, project-level override of the
taste-skill default). One family for the entire app. Global stroke width
`1.75`. No hand-rolled SVG paths. If an icon is missing, compose from
existing primitives or add a different lucide glyph; never inline a
custom path.

## 6. Shape and motion locks

- Single corner-radius scale: `4px` for inputs, table cells, badges;
  `8px` for cards, dialog; pill (`9999px`) for status badges only.
- No card shadows on the page background; cards are distinguished by
  surface colour and a 1px border, not elevation.
- Modal uses an inner border highlight (`inset 0 1px 0 rgba(255,255,255,0.04)`)
  rather than a drop shadow.
- Reduced motion is the default behaviour; any transition above 120ms
  must be wrapped in `prefers-reduced-motion: no-preference`.

## 7. Copy register

- Plain functional voice. Operator-facing. No marketing copy, no
  emoji, no exclamation marks except in destructive confirmations.
- Numbers are always raw (not "23K", say "22,847") in tables, but the
  home dashboard tiles may use compact form for the big number with the
  raw form in the hover title.
- Time is shown in two forms: relative ("3m ago") then absolute
  ("2026-06-21 09:43 UTC") in a faint tooltip and as a copy target.
- Empty states are short: "No users match this filter." No flourish.
- Em-dashes are completely banned. See SKILL.md section 9.G.

## 8. Layout pattern

The shell is a left-hand vertical nav (collapsible) + a content area
with a sticky top bar containing the current route, a global search
focus (`/`), and the operator's avatar (initials only). The home
dashboard is asymmetric: one large revenue+cost panel taking the left
two thirds with sparklines, a vertical column of secondary stat tiles
on the right, and a realtime feed pinned to the bottom. The user
detail page is a 3-column grid: identity strip (col 1), subscription
and balance (col 2), activity timeline (col 3). Tables are full-width
with sticky headers.

## 9. Keyboard

The operator drives by keyboard.

| Key | Action |
|---|---|
| `/` | Focus search in any table. |
| `j` / `k` | Move focused row down / up in tables. |
| `Enter` | Open focused row. |
| `Esc` | Close modal, clear search focus. |
| `g h` | Go home. |
| `g u` | Go to users. |
| `g a` | Go to analytics. |
| `g o` | Go to OpenRouter. |

Shortcuts are surfaced in a faint `?` overlay reachable from the top bar.

## 10. Auth wall

Whole site is auth-walled. First load checks for an `ops_session` cookie.
If absent, redirect to `/login`. The login screen is one input, one
button, no decoration. On submit the backend checks the email against
`ADMIN_EMAILS`, mints a short-lived JWT (HMAC, `jose`), and sets it as
an `httpOnly`, `Secure`, `SameSite=Strict` cookie. Every subsequent
fetch attaches `x-admin-email` from the JWT payload so the existing
`requireAdmin` middleware in `apps/backend/src/api/admin/usage.ts`
keeps working unchanged.

This is the stopgap. The production path is Google Workspace OIDC and
should land before we onboard a second operator. The login email magic
link via Resend or similar is the natural next step; documented in the
PR body so it does not get lost.

## 11. What this design contract bans

- Em-dashes anywhere on the page (see SKILL.md 9.G).
- `Inter` as the default sans.
- `Fraunces` or any serif.
- Three-equal-feature-card layouts.
- Beige + brass + oxblood palette.
- Hand-rolled SVG icon paths.
- Div-based fake screenshots.
- Token-spend visibility in any user-facing surface (D-8 rule). Internal
  ops keeps the raw token math; user proxies are tier-aware sentence
  forms ("23/30 messages used today"). The ops console is internal and
  exempt from the hide rule.
- Marketing flourish copy. "Quietly trusted by", "From the field",
  decorative status dots, scroll cues, locale strips.
- Centered hero layouts. There is no hero on this app.

## 12. Mandatory pre-flight before any PR

Before declaring an `apps/ops` PR done:

1. `git diff | grep -P '[\x{2014}\x{2013}]'` must be empty (em-dash + en-dash ban).
2. Every component referenced from `apps/ops/src/routes/` must render
   without throwing in its sibling `*.test.tsx`.
3. `bun run typecheck` clean from `apps/ops` and `apps/backend`.
4. `bun run test` clean from both.
5. The taste-skill Section 14 checklist re-read and ticked.
