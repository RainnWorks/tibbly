package co.rowm.osrsllm.widgets

import net.runelite.api.events.WidgetClosed
import net.runelite.api.events.WidgetLoaded
import net.runelite.client.eventbus.Subscribe
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks which widget groups are currently loaded ("open on screen") by subscribing
 * to RuneLite's WidgetLoaded / WidgetClosed events.
 *
 * Used by the harness preamble (so the agent knows the bank/GE/dialog is open) and
 * by the `read_interface` MCP tool (which walks the widget tree of a given group).
 *
 * Updates happen on the client thread (where the events fire). Reads via [open]
 * are thread-safe via the underlying ConcurrentHashMap.
 */
@Singleton
class WidgetTracker @Inject constructor() {

    // group id -> timestamp first observed open
    private val openGroups = ConcurrentHashMap<Int, Long>()

    @Subscribe
    fun onWidgetLoaded(event: WidgetLoaded) {
        openGroups.putIfAbsent(event.groupId, System.currentTimeMillis())
    }

    @Subscribe
    fun onWidgetClosed(event: WidgetClosed) {
        openGroups.remove(event.groupId)
    }

    fun clear() = openGroups.clear()

    /**
     * Currently-loaded widget groups. Returned with optional human names from the
     * curated [GROUP_NAMES] map; unknown groups still appear with just their id so
     * the agent can call read_interface on them.
     */
    fun open(): List<OpenInterface> = openGroups.keys
        .map { id -> OpenInterface(id = id, name = GROUP_NAMES[id]) }
        .sortedBy { it.id }

    data class OpenInterface(val id: Int, val name: String?)

    companion object {
        /**
         * Curated names for the most relevant widget groups. Anything not here
         * still gets reported by id so the agent can read_interface it.
         * Group IDs derived from RuneLite's InterfaceID / WidgetID constants.
         */
        private val GROUP_NAMES: Map<Int, String> = mapOf(
            12 to "Bank",
            15 to "Bank inventory",
            27 to "Bank pin",
            64 to "Dialog (player)",
            65 to "Bond",
            84 to "Equipment stats",
            85 to "Worn equipment inventory",
            116 to "Side panel",
            121 to "Keybindings",
            134 to "Settings",
            148 to "Resizable viewport bottom line",
            161 to "Resizable viewport",
            162 to "Chatbox",
            164 to "Chatbox (fixed)",
            182 to "Logout panel",
            187 to "Adventure log",
            193 to "Dialog (sprite)",
            213 to "Player design",
            216 to "Dialog (multi)",
            217 to "Dialog (player options)",
            219 to "Dialog (option)",
            229 to "Dialog (NPC)",
            231 to "NPC dialog",
            259 to "Achievement diary",
            270 to "Make-X",
            287 to "Lightbox",
            300 to "Shop",
            301 to "Shop inventory",
            334 to "Trade (player)",
            335 to "Trade confirm",
            336 to "Trade inventory",
            365 to "Music",
            370 to "POH options",
            387 to "Skills",
            399 to "Quest list",
            429 to "Friends list",
            458 to "POH furniture",
            465 to "Grand Exchange",
            467 to "GE inventory",
            475 to "Wilderness ditch warning",
            541 to "Clan chat",
            542 to "Rare drop table",
            553 to "Cooking interface",
            593 to "Combat options",
            595 to "World map",
            601 to "Music tab",
            629 to "Quest journal",
            660 to "Slayer rewards",
            706 to "Group ironman",
            711 to "Group ironman storage",
            715 to "Combat achievements",
            720 to "Collection log",
            906 to "Login click-to-play",
            // Older / minigame
            24 to "Barrows reward",
            548 to "Resizable viewport (classic)",
        )
    }
}
