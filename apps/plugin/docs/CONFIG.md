# Plugin configuration reference

Every player-facing setting on the OSRS LLM Helper / Tibbly plugin, in the
order they appear in the RuneLite config panel.

The source of truth is `apps/plugin/src/main/kotlin/co/rowm/osrsllm/OsrsLlmHelperConfig.kt`.
If a setting is added or renamed, update both that file AND this page in the
same PR.

## Top-level settings

| Setting | Default | Description |
|---|---|---|
| **I have read and accept data sharing** | off | Master consent gate. No data leaves the plugin until this is on AND a chat mode that uses the network is selected. Changes apply on next plugin restart. |
| **Enable cloud chat** | off | Toggle the legacy Tibbly cloud WSS link. Retained for backwards compatibility; the new `Chat mode` dropdown is the source of truth. |
| **Backend URL** | `wss://api.tibbly.io/plugin` | Tibbly backend endpoint. Must be `wss://` — plaintext is refused at startup. |

## Chat mode (advanced)

Sits under a collapsed "Chat mode (advanced)" section so a casual player
isn't faced with provider knobs they don't need. Open it to choose where
your chat messages go.

### Chat mode dropdown

| Choice | Where messages go | Who pays |
|---|---|---|
| **Tibbly cloud (managed)** *(default)* | Tibbly backend over WSS; backend talks to the LLM provider on your behalf | Tibbly subscription |
| **Direct: Anthropic (BYO key)** | Plugin → `api.anthropic.com` directly | Your Anthropic bill |
| **Direct: OpenAI (BYO key)** | Plugin → `api.openai.com` directly | Your OpenAI bill |
| **Direct: OpenRouter (BYO key)** | Plugin → `openrouter.ai` directly | Your OpenRouter bill |
| **Tools only (no chat)** | Chat is disabled. Tool panels and overlays stay live. | Free — no provider needed |

When a BYO mode is selected the plugin **never** routes the request
through Tibbly. We don't see your messages, your key, or your model
choice. See `DATA_DISCLOSURE.md` §D-quater for the exact wire shape and
the host allow-list. The runner that implements this path is
`apps/plugin/src/main/kotlin/co/rowm/osrsllm/cloud/DirectChatRunner.kt`.

### BYO API key

- Paste the provider key from your Anthropic / OpenAI / OpenRouter account.
- Stored locally inside the RuneLite config file with `secret = true`
  (rendered as a masked password field by RuneLite).
- **Never logged.** **Never echoed in chat replies.** **Never sent to
  Tibbly.** A Gradle guard (`:checkNoKeyLeak`) fails the plugin build
  on any literal that looks like a leak.
- Leave blank if you're using Tibbly cloud or Tools only mode.

#### How to get a key

- **Anthropic** — `https://console.anthropic.com/settings/keys`
- **OpenAI** — `https://platform.openai.com/api-keys`
- **OpenRouter** — `https://openrouter.ai/keys`

#### Safer key handling

- Generate a key dedicated to this plugin so you can rotate or revoke
  it without affecting your other workflows.
- Most providers let you cap monthly spend on a per-key basis. Set a
  cap that matches the budget you're comfortable losing if your machine
  is compromised.
- Don't share screenshots of the RuneLite config panel — even though
  the field is masked, your in-game state may be sensitive.

### BYO model

Optional. Names the model id the BYO runner sends to the provider:

- Anthropic: e.g. `claude-sonnet-4-5`, `claude-opus-4-7`
- OpenAI: e.g. `gpt-4o-mini`, `gpt-4.1`
- OpenRouter: `anthropic/claude-sonnet-4-5`, `openai/gpt-4o-mini`, etc.

Leave blank to use the plugin's per-provider default (chosen to be
cheap-but-capable).

### Share anonymous BYO usage telemetry

Off by default. When on, the plugin posts a tiny ping
(`{ provider, mode, latencyMs, pluginVersion }`) to Tibbly each chat
turn so we can publish an uptime dashboard for the BYO path. The ping
**never** includes your messages, your API key, your model id, or any
game state. See `DATA_DISCLOSURE.md` §D-tris-4.

## Tibbly Companion

Renders Tibbly as a small animated sprite that walks alongside your
character inside the RuneLite client. The companion is an overlay only.
It cannot click, walk, attack, or otherwise touch the game state. It
just exists, watches the same world you watch, and occasionally has
something to say.

The companion is gated by **both** consent (top-level) AND the
`Show Tibbly companion` toggle. With either off the renderer is never
instantiated.

### Show Tibbly companion

Default: on. When on, the sprite renders at a configurable follow
distance behind your character. When off, the companion disappears
immediately and all subscriptions tear down. No data is recorded.

### Companion form

Pick which Tibbly form walks beside you. Four choices at launch:

| Choice | Feel |
|---|---|
| **Veteran** *(default)* | A small hooded humanoid in a deliberately non-OSRS art style. Reads as "this is mine" rather than "this is a Jagex asset". |
| **Fox** | A small fox-like creature in OSRS-pet silhouette, for players who want the companion to vibe with their pet collection. |
| **Wisp** | A floating moth-wisp with a face. Soft, glowing, expressive. |
| **Golem** | A small wooden golem. Tank-shaped, sturdy, deliberate. |

Each starter has its own commissioned animation atlas. Until commission
lands the plugin uses a flat-coloured 32 by 32 placeholder so you can
still see where the companion is and the state machine still works.

### Companion name

Optional. Used by the backend personality engine as your companion's
nickname. Leave blank to be prompted on first launch. Renaming later is
fine and is handled by the dashboard / Tibbly account panel.

### Personality archetype

Voice style. Each starter has a sensible default but any archetype can
go with any visual.

| Archetype | Feel |
|---|---|
| **Dry wiki veteran** *(Veteran default)* | Sparse, accurate, occasionally biting. Reads the wiki at you. |
| **Soft, confused friend** *(Wisp default)* | Sweet, a little lost, cheers you on when you do well. |
| **Sardonic veteran** *(Fox default)* | Been there, killed that. Says less than they know. |
| **Earnest helper** *(Golem default)* | Genuinely wants to help. Always polite. Mostly upbeat. |

### Speech verbosity

Slider from 1 (mostly silent) to 5 (chatty). Default 3. Even at the
chattiest setting the companion respects hard cooldown limits: at most
one proactive line every 30 seconds, at most 8 per rolling hour, with
a decaying probability the longer you play. Dead air is sacred.

### Proactive lines

On by default. When off, Tibbly never speaks unless you click on the
sprite or address her in chat. Useful for streamers, content creators,
and anyone who wants the companion as visual presence only.

## Developer mode (advanced)

Off by default. Only useful when developing the plugin locally — turns
on the legacy MCP-over-HTTP server so a local `claude -p` can connect.
Hub-installed plugins should leave this off.

| Setting | Default | Notes |
|---|---|---|
| **Developer mode (advanced)** | off | Required for the local MCP server to start. |
| **Local MCP server (dev)** | off | Starts the local MCP server on next plugin restart. |
| **Local MCP host (dev)** | `127.0.0.1` | Loopback by default. |
| **Local MCP port (dev)** | `51823` | Port range 1024-65535. |

## Three-mode behaviour table

| Consent | cloudChatEnabled | chatMode | Result |
|---|---|---|---|
| off | * | * | No network egress. Plugin is fully local. |
| on | off | Cloud | Tibbly backend NOT connected (cloud chat still requires the legacy flag). |
| on | on | Cloud | Tibbly backend connected; chat goes via the cloud runner. |
| on | * | ByoAnthropic / ByoOpenAi / ByoOpenRouter | Tibbly backend NOT connected. Chat goes via `DirectChatRunner` straight to the provider's API. |
| on | * | ToolsOnly | Chat panel disabled; tool overlays + panels still live. |

If both `cloudChatEnabled = on` AND `chatMode != Cloud` (e.g.
`chatMode = ByoOpenAi`), the plugin logs a clear warning and routes via
the BYO path. We do not silently revert to cloud — the player's choice
of mode wins.
