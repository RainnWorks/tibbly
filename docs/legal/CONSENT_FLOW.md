# Consent Flow — Plugin First-Launch

> **STATUS: DRAFT — NEEDS LAWYER REVIEW BEFORE PUBLICATION.**
> Author: agent `r-legal` for RAI-34, 2026-06-21.

This document specifies the **UX and copy** for the first-launch consent
flow inside the RuneLite plugin. It is the legal anchor that makes the
Privacy Policy and Terms enforceable.

---

## 1. When the flow fires

- **First time** the user opens the OSRS LLM Helper sidebar after
  installing the plugin, before any chat or tool call leaves the client.
- **Again** if the privacy policy or terms change materially.
- **Again** if the user clicks "Reset consent" in plugin settings.

Until consent is granted, the plugin:
- still loads;
- can show local-only features (the tool catalogue, settings, the
  "what is this" page);
- but **cannot open a WebSocket to our backend**, **cannot send any
  game state**, **cannot start a chat**.

## 2. The screen — visual spec

```
+--------------------------------------------------------+
| [OSRS LLM Helper logo]                                 |
|                                                        |
| Before we hand the mic over                            |
|                                                        |
| OSRS LLM Helper is a chat companion that watches your  |
| game state and sends it (plus your typed question) to  |
| an AI model on OpenRouter, which replies through us.   |
|                                                        |
| Here is exactly what leaves your computer when you     |
| chat:                                                  |
|                                                        |
|  - Your typed message.                                 |
|  - A snapshot of the relevant game state — by default: |
|    inventory, equipped gear, current location, active  |
|    quest steps, slayer task. Bank and full quest log   |
|    are off by default; turn them on per chat.          |
|  - A device key we generated locally to identify your  |
|    install.                                            |
|  - Your OSRS player name, so we know which character   |
|    is chatting.                                        |
|                                                        |
| Where it goes:                                         |
|                                                        |
|  Your machine -> our backend (TLS) -> OpenRouter ->    |
|  the LLM provider (e.g. Anthropic / OpenAI / Google).  |
|                                                        |
| What we DO NOT collect:                                |
|                                                        |
|  - Your Jagex password.                                |
|  - Your bank PIN.                                      |
|  - Other players' data.                                |
|  - Mouse, keyboard, screen or microphone.              |
|                                                        |
| Retention: chat history kept 30 days by default        |
| (90 if you opt in). Full details in Privacy Policy.    |
|                                                        |
| Important: large language models can be wrong. Do not  |
| stake hardcore lives, untradeables, or PvP fights on   |
| advice without sanity-checking it.                     |
|                                                        |
| [Read Privacy Policy] [Read Terms of Service]          |
|                                                        |
| [x] I have read and accept the Privacy Policy and      |
|     Terms of Service.                                  |
|                                                        |
| [x] I understand my chat input and a snapshot of my    |
|     game state will be sent to OpenRouter and the      |
|     model provider it routes to.                       |
|                                                        |
| [ ] (Optional) Send anonymous usage analytics so I can |
|     help improve the tool. You can change this any     |
|     time in Settings.                                  |
|                                                        |
|                          [Decline]  [Accept & Continue]|
+--------------------------------------------------------+
```

Notes on the design:

- **Two mandatory checkboxes**, both unticked by default — Accept
  button is disabled until both are ticked. This is the GDPR
  "freely given, specific, informed, unambiguous" bar (Art. 4(11) + 7).
- **The analytics checkbox is genuinely optional**, unticked by default.
  The Accept button does not depend on it.
- **Both policy links open in the user's external browser**, not in a
  webview.
- **The "Decline" button is visually equal weight** to Accept (same
  size, same prominence). No dark patterns.
- Copy in the box is **under 200 words**.

## 3. What Decline does

- Closes the consent screen.
- Plugin stays installed but enters **"local mode only"**:
  - Sidebar shows: "Cloud chat disabled. You declined the data-sharing
    consent. The plugin can still show local-only views. To enable chat,
    open Settings -> Reset consent."
  - The MCP tool surface is **still locally callable** by other RuneLite
    plugins or by a locally-running `claude -p` (v1 behaviour).
  - No WebSocket opens. No telemetry, no analytics.

This preserves v1's privacy-allergic-user path as a supported mode.

## 4. What Accept does

1. Persists `consent.granted = true`, `consent.version = "<sha>"`,
   `consent.timestampUtc`, `consent.policyVersionPrivacy`,
   `consent.policyVersionTerms`, and (separately) `consent.analytics`.
2. Generates the **device key** if not already present.
3. Opens the WebSocket handshake. The first frame includes the consent
   record so the backend can refuse the connection if consent is
   missing or stale.
4. Surfaces the chat input.

## 5. Reaffirmation triggers

The user is re-prompted when:

- The Privacy Policy version (`policyVersionPrivacy`) advances.
- The Terms of Service version (`policyVersionTerms`) advances.
- More than 13 months have passed since last grant (defensive — keeps
  consent "fresh" per EDPB guidance).
- The user manually clicks **Reset consent** in plugin settings.
- The backend tells the client `consentRequired: true` in a session
  open response.

## 6. Per-feature granular toggles (in Settings)

| Toggle                         | Default | Stored at      |
|--------------------------------|---------|----------------|
| Inventory in snapshot          | on      | client + server|
| Equipment in snapshot          | on      | client + server|
| Current tile + region          | on      | client + server|
| Active quest steps             | on      | client + server|
| Slayer task                    | on      | client + server|
| Bank contents                  | off     | client + server|
| Full quest log                 | off     | client + server|
| Group iron man teammates' state| off     | client + server|
| Friends / clan chat lines      | off     | client + server|
| Extended chat history (90d)    | off     | server         |
| Anonymous usage analytics      | off     | client + server|

Defaults reflect "minimum needed to be useful, off for everything
sensitive". Toggles propagate to the backend so even a compromised
client can't override the server-side guard.

## 7. Audit + proof

For every consent grant we store:

- user id (post-account-creation; pre-account-creation, the device key);
- timestamp (UTC, ISO-8601);
- IP at time of grant (retained 30d then hashed);
- exact policy version hashes (privacy + terms);
- a snapshot of the on-screen text the user saw (rendered HTML hash);
- which checkboxes were ticked.

We expose this record to the user via the dashboard ("My consent history").

## 8. Children + age confirmation

The screen contains a passive note: "By continuing you confirm you meet
the minimum age in your region (13 US / 16 EU)."

We do not add a separate age-gate checkbox unless legal review tells us
to — overuse of click-through age gates trains users to ignore them
(EDPB has commented on this).

## 9. Accessibility

- Tab-order through the screen reaches both buttons.
- Screen reader labels on every control.
- 4.5:1 contrast minimum on all text.
- Renders correctly at the smallest RuneLite sidebar width.

## 10. Implementation owners

- **Plugin (Kotlin):** screen + persistence + WebSocket refusal-until-consent
  gate. Lives in `apps/plugin/`.
- **Backend (Bun):** consent record store + version comparison + the
  "consentRequired" response. Lives in `apps/backend/src/api/consent.ts`
  (to be created in RAI-14).
- **Dashboard (React):** "My consent history" + the Reset flow trigger.
  Lives in `apps/dashboard/`.

## 11. Open questions for the lawyer

1. Do we need a separate **Acceptable Use Policy** linked from the consent
   screen, or is folding it into ToS §6 enough?
2. Do we need explicit consent for **profiling for billing purposes**
   (Art. 22)? Token metering is automated; is that "solely automated
   decision-making producing legal effects"?
3. For the **cooling-off carve-out (Reg. 37 UK CCR)**, the consent wording
   for "I want delivery to begin immediately and I waive my right to
   cancel" needs to live in the checkout flow, not here. But we should
   cross-reference it.
4. Should the analytics opt-in be a **separate modal after first meaningful
   use** rather than on the consent screen?

---

## Sources + structure references

- GDPR Art. 4(11), Art. 7, Recital 32 — basis for consent quality.
- ePrivacy Directive Art. 5(3) — basis for analytics opt-in default.
- EDPB Guidelines 05/2020 on consent — basis for granularity + freshness.
- ICO Children's Code (Age-Appropriate Design Code).
- Plausible cookie-less analytics model.

**NEEDS LAWYER REVIEW.** Especially: §6's granular toggle list, §8's
age-gate approach, and §11 open questions.
