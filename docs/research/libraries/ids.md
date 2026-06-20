# IDs — recommendation

## Decision

- **`nanoid`** for the vast majority of IDs (user IDs, device IDs, request
  IDs, pairing codes derived alphabet, anything URL-safe and short).
- **`uuid` v7** when we need time-ordered IDs (chat message IDs, log line IDs,
  event IDs) — v7 sorts lexicographically by creation time, which helps
  Postgres index locality.

| Library | Use for | Why |
|---|---|---|
| **nanoid** | User/device/session IDs, request IDs, opaque tokens | 21-char default, URL-safe, 118 bytes minified, secure |
| **uuid v7** | Chat/event/log IDs that benefit from time-ordering | Sorts by creation time → better index locality in Postgres |

## Packages

- `nanoid` — v5.1.15 (2026-06-20), 26.8k★. https://github.com/ai/nanoid
- `uuid` — v14.0.1 (2026-06-20), 15.3k★. https://github.com/uuidjs/uuid

## Install

```bash
bun add nanoid uuid
```

## Hello-world: nanoid

Source: https://github.com/ai/nanoid

```ts
import { nanoid, customAlphabet } from 'nanoid';

// default: 21-char URL-safe
const userId = nanoid();              // 'V1StGXR8_Z5jdHi6B-myT'

// custom: shorter, no ambiguous chars — perfect for pairing codes
const pairingCode = customAlphabet('23456789ABCDEFGHJKLMNPQRSTUVWXYZ', 6);
const code = pairingCode();           // 'K7P4R2'
```

## Hello-world: uuid v7

Source: https://github.com/uuidjs/uuid

```ts
import { v7 as uuidv7 } from 'uuid';

const messageId = uuidv7();           // '01941f29-7c00-75f4-a310-744d2167fc5b'
```

## Conventions

- All IDs are TEXT in Postgres, not UUID column type, so we can mix.
- Always prefix IDs in code for grep-ability:
  - `user_${nanoid()}`
  - `dev_${nanoid()}`
  - `msg_${uuidv7()}` if you want the time-ordering AND a prefix.
- Pairing codes use the `customAlphabet` 6-char form above. 32^6 ≈ 1B values,
  but only ever ~hundreds active simultaneously, so collision risk is fine.

## When NOT to use

- **Don't use crypto.randomUUID for IDs we want sorted by time.** It's v4 →
  random → terrible index locality.
- **Don't use nanoid for chat/event IDs.** Use uuid v7 so Postgres B-tree
  indexes stay local in time.

## Maintenance signals

- nanoid — v5.1.15 (2026-06-20). Zero dependencies.
- uuid — v14.0.1 (2026-06-20). RFC9562-compliant, tree-shakable.
