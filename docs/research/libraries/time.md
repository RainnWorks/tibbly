# Time / date library — recommendation

## Decision

**Use `date-fns`** — modular, tree-shakable, immutable functions, TypeScript
native. It's the NORTH_STAR default and there's no good reason to deviate.

| Library | Verdict | Why |
|---|---|---|
| **date-fns** | ✅ PICK | 200+ functions, modular imports (only what you use ships), 36.6k★ |
| dayjs | ⚠️ Fine alt | Smaller (~2 kB) but mutable-feeling API, more plugins required to match date-fns features. 48.7k★ |
| moment | ❌ Skip | Legacy, in maintenance mode, large bundle |

date-fns wins on tree-shaking — if you only import `format` and `addDays` you
get just those. dayjs is smaller out of the box but pulls plugin overhead the
moment you need anything non-trivial.

## Packages

- `date-fns` — v4.4.0 (2026-05-29), 36.6k★.
  - https://date-fns.org
  - https://github.com/date-fns/date-fns

## Install

```bash
bun add date-fns
```

## Hello-world

Source: https://date-fns.org/docs/Getting-Started

```ts
import { format, addDays, formatDistanceToNow, parseISO } from 'date-fns';

format(new Date(), 'yyyy-MM-dd');                // '2026-06-21'
addDays(new Date(), 7);                          // a week from now
formatDistanceToNow(parseISO('2026-06-20T12:00:00Z'));
// → 'about 1 day' (relative time strings — handy for "last seen")
```

## Recommended usage in this repo

- **Backend timestamps** — keep DB columns as Postgres `timestamptz`, pass
  through as JS `Date` (Drizzle handles this).
- **Dashboard display** — render via `format()` and `formatDistanceToNow()`.
  Always render in user's local TZ.
- **Billing math** — use `differenceInDays`, `startOfMonth`, `endOfMonth` for
  prorate / period calculations.
- **Token usage charts** — bucket timestamps with `startOfDay`, `startOfHour`.

## Timezones

`date-fns` keeps dates in the JS `Date` (always UTC under the hood). For
explicit-timezone work (Stripe period math) install `date-fns-tz`:

```bash
bun add date-fns-tz
```

```ts
import { formatInTimeZone } from 'date-fns-tz';
formatInTimeZone(new Date(), 'Europe/London', 'yyyy-MM-dd HH:mm zzz');
```

## When NOT to use

- **Inside the Kotlin plugin.** That's `java.time` — not our problem.
- **For high-volume server logs.** `Date.now()` is fine; reach for date-fns
  only when you need formatting or arithmetic.

## Maintenance signals

- date-fns — v4.4.0 (2026-05-29). https://github.com/date-fns/date-fns
- 36.6k★, active, MIT.
