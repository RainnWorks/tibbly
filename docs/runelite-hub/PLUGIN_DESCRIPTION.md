# Plugin Hub description text

Source-of-truth for the text that goes in:

1. `runelite-plugin.properties` → `description=`
2. The hub PR description on `runelite/plugin-hub`
3. The plugin's "About" tab inside the panel
4. The marketing site's plugin page

Keep all four in sync -- when one changes, the others change. Reviewers compare.

---

## Hook (≤500 characters, for the hub listing)

> **OSRS LLM Helper** -- talk to an AI co-pilot that actually knows what's in your inventory. Ask "what should I do next for Hard Combat Achievements" or "what gear should I bank for Vorkath" and get answers grounded in your real character: stats, equipment, bank, quest log, slayer task, and location. Cloud chat is opt-in; the plugin works in local tools-only mode by default. Powered by Anthropic Claude via OpenRouter.

**Character count:** 489.

---

## Full description (2–3 paragraphs, for the hub PR + about tab)

### Paragraph 1 -- what it does

OSRS LLM Helper turns a large-language-model chat into an in-game co-pilot that
actually understands your character. The plugin reads the same game state every
RuneLite plugin reads -- your stats, equipment, inventory, bank, quest log,
diary progress, combat achievements, slayer task, location, and a few more --
and exposes that state as a tool surface that an LLM running on our backend
can call when answering your questions. Instead of "what should I do at 73
Slayer", you get "you have a black mask in your bank, your assignment is
Greater Demons, and you're 14 quest points from Curse of Arrav -- here's the
route that beats both this week".

### Paragraph 2 -- how it talks to our backend

When you enable cloud chat, the plugin opens a single outbound WebSocket
connection to `wss://api.osrsllm.app` (a server we operate). The backend
drives an LLM (currently Anthropic's Claude family, via OpenRouter) and sends
*tool calls* back down that connection -- for example "fetch the user's bank
filtered to combat gear" -- which the plugin runs locally and replies to. The
plugin never sends your bank, inventory, equipment, or chat history
unprompted; data flows only in response to a tool call the LLM made because
your question required it. A full breakdown of every field that can leave the
client lives at <https://osrsllm.app/privacy> and in the plugin's About tab.
Cloud chat is **off by default**. The plugin works in local tools-only mode
without any network egress until you toggle it on.

### Paragraph 3 -- what it does NOT do

The plugin never simulates mouse or keyboard input, never invokes menu
actions on your behalf, never auto-clicks, and never types in the chatbox.
It is overlay-only: tiles, NPCs, and ground items can be highlighted at the
LLM's suggestion, and advice is rendered in a side panel. You always perform
the actions yourself. This is a help-the-player plugin, not a play-the-player
plugin -- consistent with Jagex's third-party client guidelines and RuneLite's
plugin-hub policy.

---

## In-panel "About" tab (shorter -- fits in a 300×400 panel)

```
OSRS LLM Helper
v0.1.0  ·  MIT  ·  github.com/RainnWorks/tibbly-plugin

What this is
A chat with an LLM that can read your character state via tool calls.
Helps with goal-planning, gear advice, slayer task strategy, achievements.

What leaves your client (only when cloud chat is enabled)
• your username and IP address (every connection)
• your character data (stats, gear, inventory, bank, quests, etc.)
  but only when the LLM's tool call requires it for your question
• your chat input text

What never leaves
• OSRS passwords (we never see them)
• actions -- we render advice, you click

Cloud chat is OFF by default. Toggle it in the config below.
Full data list: osrsllm.app/privacy
Report bugs: github.com/RainnWorks/tibbly-plugin/issues
```

---

## Manifest `warning=` line (final)

```
warning=This plugin submits your IP address, in-game username, chat input, and -- when relevant to your question -- your stats, equipment, inventory, bank, location, quest progress, diary progress, and combat achievement state to a server (osrsllm.app) not controlled or verified by the RuneLite developers, which forwards your prompts to LLM providers via OpenRouter. Cloud chat is opt-in and off by default; the plugin works in local tools-only mode without any network egress.
```

(Long, intentional -- mirrors WikiSync's blunt template; transparency beats brevity.)

---

## Tags (for the hub)

`chat`, `helper`, `assistant`, `ai`, `external`, `pvm`, `quest`, `slayer`

Justification: matches existing precedents:
- `external` -- Wise Old Man, WikiSync, Group Ironmen Tracker all use it.
- `chat` -- many panel-driven plugins use this.
- `helper`, `pvm`, `quest`, `slayer` -- discoverable by the players this targets.

Avoid: `boss`, `combat-helper`, `pvp` -- these tags attract scrutiny since
several rejected categories cluster around them.
