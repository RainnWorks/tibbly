# Data Retention Policy

> **STATUS: DRAFT — NEEDS LAWYER REVIEW BEFORE PUBLICATION.**
> Author: agent `r-legal` for RAI-34, 2026-06-21.

Precise companion to PRIVACY.md §8. Also the **operational spec** that
backend retention jobs (cron + DB purge) must enforce.

**Last updated:** 2026-06-21.

---

## Principles

1. **Minimise.** Hold only what we need, for as long as we need.
2. **Tier by sensitivity.** Chat content is more sensitive than aggregate counts.
3. **Be explicit.** Every datum has a named retention class.
4. **Be reversible until you can't be.** Soft-delete with grace period,
   then hard delete.
5. **Carve out for accounting + legal hold.** Never silently retain
   beyond what's documented.

## Retention classes

| Class             | TTL                       | What it covers                                                                   |
|-------------------|---------------------------|----------------------------------------------------------------------------------|
| `chat-default`    | 30 days                   | Chat input + LLM output + tool call traces, per-turn token counts                |
| `chat-extended`   | 90 days (opt-in)          | Same as `chat-default` but for users who turned on extended history              |
| `state-snapshot`  | Same as parent chat       | Game state snapshots attached to a chat turn                                     |
| `session-log`     | 30 days                   | WebSocket connection logs (open/close/error), per-session IP                     |
| `metric-agg`      | 13 months                 | Aggregated, anonymised usage counters (turns per day, tokens per model)          |
| `account`         | Until user deletion + 30d grace | Email, device key hash, player bindings, plan, prefs                       |
| `consent`         | Account life + 6 years    | Consent grants (proof) — even after account deletion, for limitation periods     |
| `billing`         | 6 years UK / 7 years US   | Invoices, Stripe customer object, payment intents                                |
| `error-trace`     | 90 days                   | Sentry events, request scrubbed payloads                                         |
| `security-log`    | 1 year                    | Admin actions, suspicious auth attempts                                          |
| `support-ticket`  | 2 years from last touch   | Email threads with support                                                       |
| `marketing-list`  | Until unsubscribe + 12 months | Email subscriptions for product updates                                      |

## Per-store enforcement

| Store                                | Class(es)                                 | Enforcement mechanism                                  |
|--------------------------------------|-------------------------------------------|--------------------------------------------------------|
| `chats` table (Postgres)             | chat-default, chat-extended, state-snapshot | Nightly cron: DELETE WHERE created_at < now() - ttl    |
| `sessions` table                     | session-log                               | Nightly cron: DELETE WHERE created_at < now() - 30d    |
| `metrics_agg` table                  | metric-agg                                | Monthly cron: DELETE WHERE period < now() - 13mo       |
| `users` table                        | account                                   | On delete API: soft delete + 30d grace + hard delete    |
| `consent_grants` table               | consent                                   | Retained after user deletion (anonymised: keep hashed user_id, drop email) |
| `invoices` (mirrored from Stripe)    | billing                                   | Retained per accounting policy; reviewed annually      |
| Sentry                               | error-trace                               | Project setting: 90-day retention                      |
| `admin_audit_log` table              | security-log                              | Annual archive + purge                                 |
| Email provider (Resend etc.) outbox  | support-ticket                            | Auto-archive after 2 years                             |
| Mailing list                         | marketing-list                            | Auto-purge unsubscribed addresses after 12 months      |

## Deletion mechanics

### Soft delete vs hard delete

- **Soft delete** = row flagged `deleted_at = now()` and excluded from
  all live queries. Users can re-activate within the 30-day grace.
- **Hard delete** = `DELETE FROM ...` at TTL expiry. No recovery.

Backups are encrypted; a hard-deleted row rotates out of incremental
backups within **35 days** (max backup lineage).

### Stripe-side billing carve-out

When a user deletes their account:

1. Our `users` row is hard-deleted (after grace).
2. The Stripe `customer` object is **detached from the user** (email
   nulled out, name nulled out, metadata `osrs_user_id` removed).
3. The Stripe customer object itself remains in Stripe for the accounting
   period (6y UK / 7y US).
4. After that period, we delete the Stripe customer via Stripe API.

This is the **minimum necessary** retention to satisfy HMRC VAT rules
(SI 1995/2518) and IRS Pub. 583.

### Legal hold override

If we are served with a preservation order or are subject to active
litigation, we may pause retention purges on the affected records.
Users affected by a hold will be notified unless the hold prohibits notification.

## How retention TTLs are surfaced to the user

- **Dashboard → Account → Privacy** shows: "Your oldest stored chat is from
  <date>. Default retention is 30 days; you can extend to 90."
- The Export tool returns retention metadata for each record.
- Privacy Policy §8 publishes the headline numbers.

## Audit + review

- Retention crons emit a metric on each run: rows deleted per class.
- Annually (calendar Q1) we review whether the TTLs are still appropriate.
- We will not silently *extend* a TTL on existing data without a re-consent.

## Open questions for the lawyer

1. Is the 13-month `metric-agg` retention defensible as "anonymised" or
   do we need to argue it on legitimate interest?
2. The 6-year UK billing retention assumes we are UK-domiciled. If we
   re-incorporate, this changes.
3. The "consent record retained 6 years after account deletion" is based
   on standard UK limitation periods. Confirm.

---

## Sources

- GDPR Art. 5(1)(e) — storage limitation principle.
- GDPR Art. 17 — right to erasure.
- HMRC VAT record-keeping: SI 1995/2518 reg. 31 — 6-year minimum.
- IRS Publication 583 — 3-to-7 year retention guidance.
- UK Limitation Act 1980 — 6-year limitation period for contract.

**NEEDS LAWYER REVIEW.** Especially: the precise retention numbers and
the "Stripe carve-out" mechanics.
