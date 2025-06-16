package info.mester.network.partygames.api

import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent
import io.papermc.paper.event.entity.EntityMoveEvent
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.function.Consumer
import kotlin.random.Random

abstract class Minigame(
    protected val game: Game,
    minigameName: String,
) {
    private var _running = false
    private var countdownUUID = UUID.randomUUID()
    protected val plugin = PartyGamesCore.getInstance()
    protected val audience get() = Audience.audience(game.onlinePlayers + game.world.players.filter { plugin.isAdmin(it) })
    protected val onlinePlayers get() = game.onlinePlayers
    val running get() = _running
    val startPos: Location
        get() {
            val pos = field.clone()
            pos.world = Bukkit.getWorld(game.worldName)!!
            return pos
        }
    val rootWorldName: String
    val worldIndex: Int
    val originalPlugin: JavaPlugin

    init {
        val core = PartyGamesCore.getInstance()
        val minigameConfig = core.gameRegistry.getMinigame(minigameName)!!
        originalPlugin = minigameConfig.plugin
        worldIndex = Random.nextInt(0, minigameConfig.worlds.size)
        rootWorldName = minigameConfig.worlds[worldIndex].name
        startPos = minigameConfig.worlds[worldIndex].startPos.toLocation(Bukkit.getWorld(rootWorldName)!!)
    }

    /**
     * Function to start the minigame
     */
    open fun start() {
        _running = true

        game.onlinePlayers.forEach { player ->
            player.teleport(startPos)
            Game.resetPlayer(player)
        }
    }

    /**
     * Executed when the minigame is loaded and we already have a world ready
     *
     * Can be used to set up the world (unlike in the constructor, where a world is not yet ready)
     */
    open fun onLoad() {}

    /**
     * A function to finish the minigame (roll back any changes, handle scores, etc.)
     *
     * This will always run, regardless if the minigame was gracefully ended or not
     */
    open fun finish() {}

    /**
     * Function to stop the minigame
     */
    private fun end(nextGame: Boolean) {
        _running = false

        stopCountdown()
        audience.hideBossBar(game.remainingBossBar)
        finish()

        if (nextGame) {
            game.endMinigame()
        }
    }

    /**
     * Function to end the minigame and start the next one
     */
    fun end() {
        end(true)
    }

    /**
     * Function to terminate the minigame without the underlying game ending logic
     */
    fun terminate() {
        end(false)
    }

    private fun updateRemainingTime(
        startTime: Int,
        duration: Int,
    ): Boolean {
        val bar = game.remainingBossBar
        val remainingTimeTick = startTime + duration - Bukkit.getCurrentTick()
        val remainingTime = remainingTimeTick * 0.05
        if (remainingTime < 0) {
            audience.hideBossBar(bar)
            return false
        }
        val timeSeconds = remainingTime.toInt()
        val minutes = timeSeconds / 60
        val seconds = timeSeconds % 60
        val name =
            "<green>Time remaining: <red>$minutes<gray>:</gray>${seconds.toString().padStart(2, '0')}"

        bar.name(MiniMessage.miniMessage().deserialize(name))
        bar.progress(remainingTimeTick.toFloat() / duration.toFloat())

        return true
    }

    fun startCountdown(
        duration: Int,
        showBar: Boolean,
        onEnd: Runnable,
    ) {
        if (showBar) {
            audience.showBossBar(game.remainingBossBar)
        } else {
            audience.hideBossBar(game.remainingBossBar)
        }
        countdownUUID = UUID.randomUUID()
        val startTime = Bukkit.getCurrentTick()
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            object : Consumer<BukkitTask> {
                private val uuid = countdownUUID

                override fun accept(t: BukkitTask) {
                    if (!running || this.uuid != countdownUUID) {
                        t.cancel()
                        return
                    }
                    if (!updateRemainingTime(startTime, duration)) {
                        t.cancel()
                        onEnd.run()
                    }
                }
            },
            0,
            1,
        )
    }

    fun startCountdown(
        duration: Int,
        onEnd: Runnable,
    ) {
        startCountdown(duration, true, onEnd)
    }

    fun stopCountdown() {
        // by creating a new UUID, the currently running countdown will be cancelled
        countdownUUID = UUID.randomUUID()
    }

    // functions for handling events
    // entity events
    open fun handleEntityMove(event: EntityMoveEvent) {}

    open fun handleEntityChangeBlock(event: EntityChangeBlockEvent) {}

    open fun handleEntityCombust(event: EntityCombustEvent) {}

    open fun handleEntityDismount(event: EntityDismountEvent) {}

    open fun handleEntityDamageByEntity(event: EntityDamageByEntityEvent) {}

    open fun handleEntityRegainHealth(event: EntityRegainHealthEvent) {}

    open fun handleEntityShootBow(event: EntityShootBowEvent) {}

    open fun handleCreatureSpawn(
        event: CreatureSpawnEvent,
        player: Player,
    ) {
    }

    // block events
    open fun handleBlockPhysics(event: BlockPhysicsEvent) {}

    open fun handleBlockBreak(event: BlockBreakEvent) {}

    open fun handleBlockPlace(event: BlockPlaceEvent) {}

    open fun handleBlockBreakProgressUpdate(event: BlockBreakProgressUpdateEvent) {}

    // player events
    open fun handlePlayerMove(event: PlayerMoveEvent) {}

    open fun handlePlayerInteract(event: PlayerInteractEvent) {}

    open fun handlePlayerDeath(event: PlayerDeathEvent) {}

    open fun handlePrePlayerAttack(event: PrePlayerAttackEntityEvent) {}

    open fun handlePlayerDropItem(event: PlayerDropItemEvent) {}

    open fun handlePlayerToggleFlight(event: PlayerToggleFlightEvent) {}

    open fun handlePlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {}

    open fun handlePlayerChat(event: AsyncChatEvent) {}

    open fun handlePlayerItemConsume(event: PlayerItemConsumeEvent) {}

    // inventory events
    open fun handleInventoryClose(event: InventoryCloseEvent) {}

    open fun handleInventoryOpen(event: InventoryOpenEvent) {}

    open fun handleInventoryClick(
        event: InventoryClickEvent,
        clickedInventory: Inventory,
    ) {
    }
    // game events

    /**
     * Triggers when a player disconnects from the game.
     *
     * @param player The player who disconnected.
     * @param didLeave Indicates if the player left the game (true) or temporarily disconnected (false).
     */
    open fun handleDisconnect(
        player: Player,
        didLeave: Boolean,
    ) {
    }

    open fun handleRejoin(player: Player) {
    }

    abstract val name: Component
    abstract val description: Component
}
