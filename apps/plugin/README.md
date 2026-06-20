# osrs-llm-helper

A RuneLite plugin that embeds an [MCP](https://modelcontextprotocol.io) server
inside the client, exposing live game state as tools an LLM agent can call.

V1 is a **read-only tool suite** — point any MCP-aware agent (e.g. `claude -p`)
at the plugin and it can answer questions like "what quests am I in the middle
of?" or "what's missing from my Dragon Slayer kit?" using your actual game data.

## Tools exposed (V1)

| Tool | Description |
| --- | --- |
| `get_inventory` | 28-slot inventory contents |
| `get_bank` | Last-seen bank contents (refreshes while the bank is open) |
| `get_equipment` | Worn gear by slot |
| `get_stats` | Skill levels (real + boosted) and experience |
| `get_player_state` | Name, combat, world, location, HP, prayer, run, spec |
| `get_quests` | Quest progress; `state` arg: `ALL` \| `IN_PROGRESS` \| `NOT_STARTED` \| `FINISHED` |

All responses are JSON wrapped in `{ "meta": { snapshotAt, loggedIn }, "data": ... }`.

## Build

Requires JDK 11+ and Gradle. First time:

```sh
gradle wrapper
./gradlew shadowJar
```

The shaded plugin jar lands in `build/libs/osrs-llm-helper-0.1.0.jar`. Sideload
it into RuneLite via developer mode or by dropping it into the external plugin
directory.

To run RuneLite with the plugin loaded in dev mode:

```sh
./gradlew runRuneLite
```

## Hot reload during development

The `runRuneLite` task already opens a JDWP debug port on `5005` and (when running
on JetBrains Runtime) enables enhanced class redefinition.

Two-terminal workflow:

```sh
# terminal 1 — RuneLite with debug agent
./gradlew runRuneLite

# terminal 2 — recompile on every file save
./gradlew -t classes
```

### VSCode

Recommended extensions:
- **Extension Pack for Java** (`vscjava.vscode-java-pack`) — provides the
  debugger that attaches over JDWP.
- **Kotlin** (`fwcd.kotlin` or `mathiasfrohlich.Kotlin`) — editor support for
  `.kt` files. Debugging Kotlin works through the Java debugger; the source
  language doesn't matter to JDWP.

`.vscode/launch.json` is already checked in with an "Attach to RuneLite (5005)"
config, and `java.debug.settings.hotCodeReplace` is set to `auto` so saved
changes get pushed into the running JVM whenever `./gradlew -t classes` has
written fresh class bytes.

Flow:
1. Start RuneLite: `./gradlew runRuneLite`.
2. Start continuous compile: `./gradlew -t classes`.
3. In VSCode: `Run and Debug` panel → "Attach to RuneLite (5005)" → green play
   button.
4. Edit code, save. HotSwap fires automatically.
5. Toggle the plugin off/on in the RuneLite sidebar to re-run `startUp()` with
   the new code (only needed when the change affects `startUp()` / `shutDown()`
   or you've added a new tool/event handler).

### IntelliJ

1. `Run` → `Attach to Process…` → pick the RuneLite JVM on `:5005`.
2. After editing Kotlin, hit **Build → Reload Changed Classes** (or `⌃⇧F9` on macOS).
3. Toggle the plugin off/on in the RuneLite sidebar to re-run `startUp()` with the new code.

### Reload limitations

- **Stock OpenJDK** (what SDKMAN's `tem` distro gives you): only method-body
  changes HotSwap. Adding a new MCP tool, new field, or new class still requires
  a full restart.
- **JetBrains Runtime**: full add/remove of methods, fields, and classes. To upgrade:

```sh
sdk install java 21.0.10-jbr
sdk default java 21.0.10-jbr
```

The `runRuneLite` task already has the `-XX:+AllowEnhancedClassRedefinition` flag
guarded by `-XX:+IgnoreUnrecognizedVMOptions`, so it's a no-op on Temurin and
activates automatically on JBR.

## Connect an MCP agent

The plugin serves Streamable HTTP at `http://<host>:<port>/mcp` (default
`127.0.0.1:51823`). Configure the Claude CLI:

```sh
claude mcp add osrs --transport http http://127.0.0.1:51823/mcp
```

Then talk to it from your terminal while you play:

```sh
claude -p "Which quests am I currently in the middle of, and what's the next step likely to be?"
claude -p "Audit my inventory — anything I should bank before going to the Wilderness?"
claude -p "Compare my stats against what I'd need for Dragon Slayer 2."
```

## Configuration (in-RuneLite)

Settings panel: `OSRS LLM Helper`
- **Enable MCP server** — toggle on/off without unloading the plugin.
- **Bind host** — defaults to `127.0.0.1`. Don't expose to your LAN unless you
  know what you're doing; the server is unauthenticated.
- **Port** — defaults to 51823.

## Architecture

```
RuneLite client thread        MCP / Ktor coroutines
─────────────────────         ─────────────────────
@Subscribe handlers      ──▶  AtomicReference<GameSnapshot>  ──▶  tool handlers
(inventory, bank,             (lock-free; tools read              (read snapshot,
 equipment, stats,             a consistent immutable               return JSON)
 game tick → player,           view from any thread)
 game tick → quests)
```

- `GameStateStore` snapshots state on RuneLite events. It never touches the
  client object off the client thread.
- `McpServerService` owns an embedded Ktor CIO server (chosen over Netty to
  avoid version conflicts with RuneLite's bundled Netty) running the MCP
  Streamable HTTP transport.
- Tools just read the latest snapshot — no client-thread hop, no blocking.

## Roadmap

- **V2** — Bank-tab writes: a `set_bank_tab(name, items)` tool that creates or
  updates a [bank tag tab](https://github.com/runelite/runelite/wiki/Bank-Tags)
  by writing to the same config keys the Bank Tags plugin uses.
- **V2** — In-RuneLite chat panel that spawns `claude -p` for you so you don't
  need a terminal open while playing.
- **V3** — Wiki lookup + GE price tools.
- **V3** — World-map / NPC / object proximity tools.

## Caveats

- **Sideload only for now.** RuneLite's Plugin Hub has a curated third-party
  dependency allowlist; the MCP Kotlin SDK + Ktor likely aren't on it yet. Use
  `--developer-mode` or drop the shadow jar into the external plugin folder.
- **Bank data lags.** OSRS only sends bank contents to the client while the
  bank is open. The `bank.lastSeenAt` field tells the agent how stale the
  snapshot is.
- **Unauthenticated.** Anything that can hit `127.0.0.1:51823` can read your
  game state. Don't bind to a public interface.
