package co.rowm.osrsllm.companion

import net.runelite.api.ChatMessageType
import net.runelite.api.Client
import net.runelite.api.coords.WorldPoint
import net.runelite.api.events.ChatMessage
import net.runelite.api.events.GameTick
import net.runelite.api.events.HitsplatApplied
import net.runelite.api.events.PlayerSpawned
import net.runelite.api.events.WidgetLoaded
import net.runelite.client.eventbus.Subscribe
import org.slf4j.LoggerFactory

/**
 * Translates RuneLite events into [CompanionEvent]s and drives the
 * companion's per-tick path follower.
 *
 * Single-responsibility shim that exists so the state machine, the
 * pathfinder, and the orchestrator never have to know about
 * [net.runelite.api.events.*] types. The gradle gates that scan the
 * `companion/` package allow RuneLite imports only in this adapter,
 * the renderer, and the resource atlas - everywhere else stays pure.
 *
 * The adapter is GATED on consent + companionEnabled. When either is
 * off, it noop's; the consent gate is the one checked by
 * `:checkCompanionConsentGated`.
 */
class CompanionEventAdapter(
    private val client: Client,
    private val stateMachine: CompanionStateMachine,
    private val orchestrator: CompanionDialogueOrchestrator,
    private val pathFollower: CompanionPathFollower,
    private val runtimeState: CompanionRuntimeState,
    private val consentAcceptedSupplier: () -> Boolean,
    private val companionEnabledSupplier: () -> Boolean,
    private val followOffset: Int = CompanionFollowOffset.DEFAULT_FOLLOW_TILES,
    private val walkable: WalkableTest = WalkableTest { _, _, _ -> true },
) {

    private val log = LoggerFactory.getLogger(CompanionEventAdapter::class.java)
    @Volatile private var lastPlayerTile: CompanionTile? = null
    @Volatile private var lastPlayerFacing: Direction = Direction.SOUTH

    @Subscribe
    fun onGameTick(@Suppress("UNUSED_PARAMETER") tick: GameTick) {
        if (!isLive()) {
            runtimeState.hide()
            return
        }
        val player = client.localPlayer ?: return
        val tile = WorldPoint.fromLocal(client, player.localLocation) ?: return
        val playerTile = CompanionTile(tile.x, tile.y, tile.plane)
        val previous = lastPlayerTile
        if (previous != null && previous != playerTile) {
            val dx = playerTile.x - previous.x
            val dy = playerTile.y - previous.y
            lastPlayerFacing = Direction.fromDelta(dx, dy)
            stateMachine.on(
                CompanionEvent.PlayerMoved(
                    direction = lastPlayerFacing,
                    fromTile = previous,
                    toTile = playerTile,
                ),
            )
        } else if (previous != null) {
            stateMachine.on(CompanionEvent.PlayerStopped)
        }
        lastPlayerTile = playerTile
        // Recompute the follow target.
        val follow = CompanionFollowOffset.followTile(
            playerTile = playerTile,
            facing = lastPlayerFacing,
            walkable = walkable,
            followTiles = followOffset,
        )
        val now = System.currentTimeMillis()
        pathFollower.retarget(follow, now)
        // Drive timeouts (yawn, sit, etc).
        stateMachine.tick(now)
        // Publish the renderer snapshot.
        val snapshot = pathFollower.advance(now, stateMachine.current())
        runtimeState.update(snapshot)
    }

    @Subscribe
    fun onChatMessage(event: ChatMessage) {
        if (!isLive()) return
        when (event.type) {
            ChatMessageType.GAMEMESSAGE -> {
                val msg = event.message.orEmpty()
                if (LEVEL_UP_REGEX.containsMatchIn(msg)) {
                    stateMachine.on(CompanionEvent.LevelUp)
                    orchestrator.consider(
                        CompanionDialogueOrchestrator.ProactiveTrigger(
                            id = "level_up",
                            contextSnapshot = mapOf("message" to msg.take(200)),
                        ),
                    )
                } else if (PET_DROP_REGEX.containsMatchIn(msg)) {
                    stateMachine.on(CompanionEvent.PetDrop)
                    orchestrator.consider(
                        CompanionDialogueOrchestrator.ProactiveTrigger(
                            id = "pet_drop",
                            contextSnapshot = mapOf("message" to msg.take(200)),
                        ),
                    )
                } else if (COLLECTION_REGEX.containsMatchIn(msg)) {
                    stateMachine.on(CompanionEvent.CollectionLogNotification)
                    orchestrator.consider(
                        CompanionDialogueOrchestrator.ProactiveTrigger(
                            id = "collection_log",
                            contextSnapshot = mapOf("message" to msg.take(200)),
                        ),
                    )
                }
            }
            ChatMessageType.NPC_EXAMINE,
            ChatMessageType.OBJECT_EXAMINE,
            ChatMessageType.ITEM_EXAMINE,
                -> stateMachine.on(CompanionEvent.ExamineFired)
            else -> Unit
        }
    }

    @Subscribe
    fun onPlayerSpawned(event: PlayerSpawned) {
        if (!isLive()) return
        val player = client.localPlayer ?: return
        if (event.player != player) return
        // First sight of the local player - drop the companion onto the
        // follow tile so it doesn't fade in from off-screen.
        val tile = WorldPoint.fromLocal(client, player.localLocation) ?: return
        val playerTile = CompanionTile(tile.x, tile.y, tile.plane)
        val follow = CompanionFollowOffset.followTile(
            playerTile = playerTile,
            facing = lastPlayerFacing,
            walkable = walkable,
            followTiles = followOffset,
        )
        pathFollower.teleport(follow, lastPlayerFacing)
        lastPlayerTile = playerTile
    }

    @Subscribe
    fun onHitsplatApplied(event: HitsplatApplied) {
        if (!isLive()) return
        if (event.actor != client.localPlayer) return
        // Player took a hit - look-at the source if we can.
        stateMachine.on(CompanionEvent.ExamineFired)
    }

    @Subscribe
    fun onWidgetLoaded(event: WidgetLoaded) {
        if (!isLive()) return
        // Bank widget group id is stable across revisions. We refer to it
        // by literal rather than gameval to keep this adapter free of
        // gameval constant churn.
        when (event.groupId) {
            BANK_WIDGET_GROUP -> stateMachine.on(CompanionEvent.BankOpened)
            NPC_DIALOG_WIDGET_GROUP, PLAYER_DIALOG_WIDGET_GROUP -> stateMachine.on(CompanionEvent.NpcDialogOpened)
            LEVEL_UP_WIDGET_GROUP -> {
                stateMachine.on(CompanionEvent.LevelUp)
                orchestrator.consider(
                    CompanionDialogueOrchestrator.ProactiveTrigger("level_up_widget"),
                )
            }
            else -> Unit
        }
    }

    /** Whether the companion subsystem should run at all this tick. */
    private fun isLive(): Boolean = consentAcceptedSupplier() && companionEnabledSupplier()

    companion object {
        // Stable bank + dialog widget group ids - kept as literals because
        // the gameval constant names occasionally rename across RuneLite
        // versions. The ids themselves haven't moved in years.
        const val BANK_WIDGET_GROUP: Int = 12
        const val NPC_DIALOG_WIDGET_GROUP: Int = 231
        const val PLAYER_DIALOG_WIDGET_GROUP: Int = 217
        const val LEVEL_UP_WIDGET_GROUP: Int = 233

        private val LEVEL_UP_REGEX = Regex(
            "Congratulations, you('|)ve just advanced",
            RegexOption.IGNORE_CASE,
        )
        private val PET_DROP_REGEX = Regex(
            "You have a funny feeling like you're being followed",
            RegexOption.IGNORE_CASE,
        )
        private val COLLECTION_REGEX = Regex(
            "New item added to your collection log",
            RegexOption.IGNORE_CASE,
        )
    }
}
