# Output channels — talking back to the player

Every way the plugin can present information to the player. Each channel lists
the minimal Java/Kotlin signature, the thread it must be invoked on, and a
short example. Inject-by-Guice services are marked `@Singleton` so a single
`@Inject` field works from your plugin.

Thread shorthand:
- **client thread** = RuneLite's game thread. Use `ClientThread.invoke(Runnable)`
  or be inside an `@Subscribe` for a game event.
- **EDT** = Swing Event Dispatch Thread. Use `SwingUtilities.invokeLater(...)`.
- **any** = thread-safe / queued internally.

---

## In-game chat

`ChatMessageManager` queues messages that drain onto the next client tick.
The queue is a `ConcurrentLinkedQueue`, so `queue(...)` is callable from **any
thread**; the actual `Client.addChatMessage` happens later on the client
thread.

- `ChatMessageManager.queue(QueuedMessage msg)` — appends a chat line.
  **Thread: any.**
- `QueuedMessage.builder()` fields:
  - `type` (required, `ChatMessageType`) — e.g. `GAMEMESSAGE`, `CONSOLE`,
    `ITEM_EXAMINE`, `FRIENDSCHATNOTIFICATION`, `CLAN_MESSAGE`, `BROADCAST`.
  - `value` — raw text (used if no `runeLiteFormattedMessage`).
  - `name` — sender name in the prefix bracket (e.g. `"OSRSHelper"`).
  - `sender` — channel name (clan/friends). Usually null.
  - `runeLiteFormattedMessage` — pre-coloured string from `ChatMessageBuilder`.
  - `timestamp` — unix seconds; 0 = let client stamp it.
- `ChatMessageBuilder` — fluent builder emitting `<colNORMAL>` / `<colHIGHLIGHT>`
  tags that resolve against the user's chat-colour config:
  - `.append(ChatColorType)` — switch slot (`NORMAL` or `HIGHLIGHT`).
  - `.append(String)` — text (jagex-escaped).
  - `.append(Color, String)` — explicit colour `<col=hex>...</col>`.
  - `.img(int spriteId)` — inline mod-icon sprite.
  - `.build()`.
- `ChatColorType` — only `NORMAL` and `HIGHLIGHT`. Prefer these over hard-coded
  colours so the user's chat-colour overrides apply.
- Tutorial-island guard: messages are silently dropped while the player is
  on Tutorial Island regions.

Example — info line in the chatbox:
```java
String formatted = new ChatMessageBuilder()
    .append(ChatColorType.NORMAL).append("LLM says: ")
    .append(ChatColorType.HIGHLIGHT).append(answer)
    .build();
chatMessageManager.queue(QueuedMessage.builder()
    .type(ChatMessageType.GAMEMESSAGE)
    .name("OSRSHelper")
    .runeLiteFormattedMessage(formatted)
    .build());
```

Lower-level alternative: `Client.addChatMessage(type, name, message, sender)`
— must be called on the **client thread**.

---

## OS-level notification

`Notifier` — combined desktop toast + optional chime + chat echo + screen
flash, all gated by the user's RuneLite notification settings.

- `Notifier.notify(String message)` — fires a default-config notification.
  **Thread: any.**
- `Notifier.notify(String message, TrayIcon.MessageType type)` —
  `INFO`/`WARNING`/`ERROR` (Linux maps WARNING/ERROR to `notify-send -u critical`).
- `Notifier.notify(Notification notification, String message)` — supply a
  `net.runelite.client.config.Notification` for per-event overrides.
  Convention: expose a `@ConfigItem Notification` on your config so users
  can tune it.

One call may produce, each toggleable via `Notification`:
- Tray balloon (Windows), `notify-send` (Linux), `osascript`/`terminal-notifier`
  (macOS).
- `Toolkit.beep()` or a custom `.wav` via `AudioPlayer` from
  `~/.runelite/notification.wav` or `~/.runelite/notifications/<file>`.
- `GAMEMESSAGE` chat echo (highlight-coloured).
- Canvas flash for 2 s or until user interaction (`Notifier.processFlash`).
- Taskbar flash / window focus via `ClientUI`.
- A `NotificationFired` event on the event bus.

Example:
```java
notifier.notify("Inventory full");
notifier.notify("Boss spawning", TrayIcon.MessageType.WARNING);
```

---

## Screen overlays (canvas drawing)

The general way to draw on the game viewport. Overlays render every frame on
the **client thread** via `OverlayRenderer`. Inside `render(Graphics2D)` you
may read game state directly.

### `Overlay` (abstract)
- `Dimension render(Graphics2D g)` — implement. Return drawn size or null.
- `setPosition(OverlayPosition)` — anchor: `TOP_LEFT`, `TOP_CENTER`,
  `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`, `ABOVE_CHATBOX_RIGHT`,
  `CANVAS_TOP_RIGHT`, `DYNAMIC` (draws itself, e.g. world-space), `TOOLTIP`,
  `DETACHED` (deprecated).
- `setLayer(OverlayLayer)` — z-order:
  - `ABOVE_SCENE` — above 3D scene, below HUD/widgets.
  - `UNDER_WIDGETS` — default; below interfaces, above overheads.
  - `ABOVE_WIDGETS` — above interfaces, below right-click menu.
  - `ALWAYS_ON_TOP` — above everything.
  - `MANUAL` — only draws via `drawAfterInterface()`/`drawAfterLayer()`.
- `setPriority(float)` — sort within a layer. Constants `PRIORITY_LOW=0`,
  `PRIORITY_DEFAULT=0.25`, `PRIORITY_MED=0.5`, `PRIORITY_HIGH=0.75`,
  `PRIORITY_HIGHEST=1`. For DYNAMIC/DETACHED higher = drawn later (on top);
  for anchored positions higher = closer to anchor.
- `drawAfterInterface(int)` / `drawAfterLayer(int)` — hook to draw right
  after a specific interface/widget paints (use with `MANUAL` layer).
- `setResizable(boolean)`, `setMinimumSize(int)` — user-resizable overlays.
- `addMenuEntry(MenuAction, option, target [, Consumer<MenuEntry>])` —
  right-click menu items on the overlay itself.

### `OverlayManager` (`@Singleton`)
- `add(Overlay)` — register. Synchronised, **thread: any**.
- `remove(Overlay)`, `removeIf(Predicate)`, `clear()`.
- `saveOverlay(o)` / `resetOverlay(o)` — persist or wipe user's preferred
  position/size/location.

### `OverlayPanel` (extends `Overlay`)
Preferred base for boxed status overlays. Owns a `PanelComponent`; add
children to it and the panel auto-renders, sizes, and clears between frames.
- `panelComponent.getChildren().add(LayoutableRenderableEntity)` — inside
  `render(...)`. `clearChildren` defaults to true, so add per-frame.
- `setPreferredColor(Color)` — background tint.
- `setDynamicFont(true)` — auto-resizes font when user resizes the panel.

### Components (`net.runelite.client.ui.overlay.components`)
All implement `LayoutableRenderableEntity` (`render`, `setPreferredLocation`,
`setPreferredSize`, `getBounds`).
- `TitleComponent.builder().text(...).color(...).build()` — centred title.
- `LineComponent.builder().left(..).right(..).leftColor(..).rightColor(..).build()`
  — two-column row, auto-wraps long text.
- `TextComponent` — single positioned string.
- `ImageComponent(BufferedImage)` — sprite/icon.
- `ProgressBarComponent` — horizontal bar with min/max/value.
- `ProgressPieComponent` — circular timer.
- `SplitComponent` — pair of components, horizontal or vertical.
- `BackgroundComponent` — coloured rectangle.
- `InfoBoxComponent` — image-and-text tile used by the InfoBox renderer.
- `PanelComponent` — vertical/horizontal stack, optional wrap.

Example — top-right status panel:
```java
public class HelperOverlay extends OverlayPanel {
    @Inject HelperOverlay(HelperPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_RIGHT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
    }
    @Override public Dimension render(Graphics2D g) {
        panelComponent.getChildren().add(TitleComponent.builder().text("OSRS Helper").build());
        panelComponent.getChildren().add(LineComponent.builder().left("State:").right(state).build());
        return super.render(g);
    }
}
// plugin: overlayManager.add(overlay) in startUp, .remove in shutDown.
```

### `WidgetItemOverlay` / `WidgetOverlay`
Specialised overlay base classes for per-inventory-item drawing and
re-positioning of native game widgets. Use these when you need to highlight
specific inventory slots without drawing the whole HUD yourself.

---

## InfoBox (corner badge)

Square image-plus-text badges that stack along an edge — buff timers,
counters, etc.

- `InfoBox` (abstract):
  - ctor: `InfoBox(BufferedImage image, Plugin plugin)`.
  - `abstract String getText()` — short text over the icon (often `""`).
  - `abstract Color getTextColor()`.
  - `boolean render()` — return false to hide this frame.
  - `boolean cull()` — return true to have `InfoBoxManager.cull()`
    auto-remove it.
  - `setTooltip(String)`, `setPriority(InfoBoxPriority)`, `setImage(...)`.
- Built-in subclasses:
  - `Counter(image, plugin, int count)` — renders `count` as text.
  - `Timer(long period, ChronoUnit unit, image, plugin)` — counts down `m:ss`,
    text turns red at ≤10 % remaining, auto-culls on expiry.
  - `LoopTimer` — repeating timer.
- `InfoBoxManager` (`@Singleton`):
  - `addInfoBox(InfoBox)` — **thread: any**.
  - `removeInfoBox(InfoBox)`, `removeIf(Predicate)`, `cull()`,
    `getInfoBoxes()`.

The manager auto-rescales images to `RuneLiteConfig.infoBoxSize`, groups
boxes into `InfoBoxOverlay` layers (user can detach/flip/delete groups via
right-click) and injects the corresponding menu entries.

Example — a 30 s countdown:
```java
BufferedImage icon = itemManager.getImage(ItemID.STAMINA_POTION);
Timer t = new Timer(30, ChronoUnit.SECONDS, icon, this);
t.setTooltip("Stamina");
infoBoxManager.addInfoBox(t); // auto-removed on expiry via cull()
```

---

## Tooltip (cursor-anchored hint)

Drawn by `TooltipOverlay` next to the mouse for the current frame only.
Re-add each tick (from another overlay's `render`, or from `onClientTick` /
`MenuEntryAdded`).

- `Tooltip`:
  - `Tooltip(String text)` — supports `</br>` line breaks and `<col=...>`.
  - `Tooltip(LayoutableRenderableEntity component)` — any layoutable.
- `TooltipManager` (`@Singleton`): `add(Tooltip)`, `addFront(Tooltip)`,
  `clear()`. **Thread: client thread** — the list is not synchronised and is
  expected to be touched from `render` paths.

Example — tooltip when hovering an NPC:
```java
@Subscribe public void onClientTick(ClientTick e) {
    MenuEntry[] menu = client.getMenuEntries();
    if (menu.length == 0) return;
    if (menu[menu.length - 1].getType() == MenuAction.NPC_SECOND_OPTION) {
        tooltipManager.add(new Tooltip("LLM: ask about this NPC"));
    }
}
```

---

## World map

Pins drawn on the in-game world-map interface by `WorldMapOverlay`.

- `WorldMapPoint` (`@SuperBuilder`):
  - `worldPoint` (`WorldPoint`) — tile to anchor at.
  - `image` (`BufferedImage`) — sprite to draw.
  - `imagePoint` (`Point`) — anchor offset inside image (null = centre).
  - `tooltip` — hover text on the map.
  - `name` — right-click menu label when `jumpOnClick` is set.
  - `target` — where the map jumps if clicked (defaults to `worldPoint`).
  - `jumpOnClick` — enable click-to-pan.
  - `snapToEdge` — marker rides the visible map edge when off-screen;
    override `onEdgeSnap()` / `onEdgeUnsnap()` to react.
- `WorldMapPointManager` (`@Singleton`, `CopyOnWriteArrayList`,
  **thread: any**): `add`, `remove`, `removeIf`.

Example — a custom marker:
```java
WorldMapPoint pin = WorldMapPoint.builder()
    .worldPoint(new WorldPoint(3200, 3200, 0)).image(icon)
    .tooltip("Suggested fishing spot").jumpOnClick(true).name("Goto").build();
worldMapPointManager.add(pin);
```

---

## World-space hint arrow

Built-in to the game client: an arrow that hovers over an entity or tile.
Only one can be active at a time per client. **Thread: client thread.**

- `Client.setHintArrow(WorldPoint)` / `setHintArrow(LocalPoint)` /
  `setHintArrow(NPC)` / `setHintArrow(Player)`.
- `Client.clearHintArrow()`.
- `Client.hasHintArrow()`, `Client.getHintArrowType()`,
  `Client.getHintArrowPoint()` for inspection.

Example:
```java
clientThread.invoke(() -> client.setHintArrow(targetNpc));
```

---

## Sidebar plugin panel

Sidebar tab + Swing panel. **All UI construction & mutation must happen on
the EDT.**

- `PluginPanel` (abstract `JPanel`):
  - `PANEL_WIDTH=225`, `SCROLLBAR_WIDTH=17`. Default (wrap) gives you a
    scroll pane, dark background, `DynamicGridLayout(0,1,0,3)`.
  - From `Activatable`: `onActivate()` / `onDeactivate()` fire when the user
    opens/closes the tab.
- `NavigationButton` (`@Value @Builder`):
  - `icon` (~24 px `BufferedImage`).
  - `tooltip`, `priority` (lower = higher in the sidebar).
  - `panel` (`PluginPanel`) — clicking expands it; null = pure button.
  - `onClick` (`Runnable`) — extra side-effect on click.
  - `popup` (`Map<String, Runnable>`) — right-click menu items.
- `ClientToolbar` (`@Singleton`):
  - `addNavigation(NavigationButton)` / `removeNavigation(...)` — wraps
    `SwingUtilities.invokeLater`, so **thread: any**.
  - `openPanel(NavigationButton)` — programmatic expand; **EDT only**.

Example:
```java
navButton = NavigationButton.builder()
    .tooltip("OSRS Helper").icon(icon).priority(5).panel(panel).build();
clientToolbar.addNavigation(navButton);   // in startUp
clientToolbar.removeNavigation(navButton); // in shutDown
```

---

## Audio

Two distinct paths.

### Out-of-game audio — `AudioPlayer` (`@Singleton`)
Plays arbitrary audio (`.wav` plus anything `javax.sound.sampled` accepts)
through the JVM mixer. Independent of game volume sliders. **Thread: any**;
internally uses `Clip` + a self-closing line listener.
- `play(File, float gainDB)`.
- `play(Class<?> resourceOwner, String resourcePath, float gainDB)`.
- `play(InputStream, float gainDB)`.
- Gain is decibels (`0` = unchanged); for linear `0..1` use
  `20 * log10(volume)`.
- Throws `IOException`, `UnsupportedAudioFileException`,
  `LineUnavailableException`.

```java
try { audioPlayer.play(getClass(), "/ding.wav", -6f); } // half volume
catch (Exception e) { log.warn("ding failed", e); }
```

### In-game SFX — `Client.playSoundEffect`
Uses the OSRS audio engine; obeys the game SFX volume. **Thread: client
thread.** IDs in `net.runelite.api.SoundEffectID`.
- `playSoundEffect(int id)` — at player position.
- `playSoundEffect(int id, int volume)` — `SoundEffectVolume.LOW`/`MEDIUM`/
  `HIGH`. Plays even if SFX is muted.
- `playSoundEffect(int id, int x, int y, int range[, int delay])` — spatial,
  attenuates with distance.

```java
clientThread.invoke(() -> client.playSoundEffect(SoundEffectID.GE_DECREMENT_PLOP));
```

---

## Cheatsheet

| Channel | Class | Thread | Persists for |
|---|---|---|---|
| Chat line | `ChatMessageManager.queue` | any | until scrolled off |
| Desktop toast | `Notifier.notify` | any | OS default |
| Canvas overlay | `OverlayManager.add` + `Overlay.render` | render = client thread | until removed |
| Infobox | `InfoBoxManager.addInfoBox` | any | until removed / culled |
| Tooltip | `TooltipManager.add` | client thread | one frame |
| World-map pin | `WorldMapPointManager.add` | any | until removed |
| Hint arrow | `Client.setHintArrow` | client thread | until cleared / new arrow |
| Sidebar panel | `ClientToolbar.addNavigation` + `PluginPanel` | EDT for UI | plugin lifetime |
| Out-of-game sound | `AudioPlayer.play` | any | length of clip |
| In-game sound | `Client.playSoundEffect` | client thread | length of clip |

---

## Wiring summary

```java
public class OsrsHelperPlugin extends Plugin {
    @Inject ChatMessageManager chatMessageManager;
    @Inject Notifier notifier;
    @Inject OverlayManager overlayManager;
    @Inject InfoBoxManager infoBoxManager;
    @Inject TooltipManager tooltipManager;
    @Inject WorldMapPointManager worldMapPointManager;
    @Inject ClientToolbar clientToolbar;
    @Inject AudioPlayer audioPlayer;
    @Inject Client client;
    @Inject ClientThread clientThread;
    @Inject HelperOverlay helperOverlay;
    private NavigationButton navButton;

    @Override protected void startUp() {
        overlayManager.add(helperOverlay);
        clientToolbar.addNavigation(navButton = NavigationButton.builder()
            .tooltip("OSRS Helper").icon(icon).panel(panel).build());
    }
    @Override protected void shutDown() {
        overlayManager.remove(helperOverlay);
        clientToolbar.removeNavigation(navButton);
        infoBoxManager.removeIf(b -> b.getPlugin() == this);
        clientThread.invoke(client::clearHintArrow);
    }
}
```
