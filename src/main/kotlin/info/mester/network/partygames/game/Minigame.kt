package info.mester.network.partygames.game

import info.mester.network.partygames.PartyGames
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
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.function.Consumer
import kotlin.random.Random

abstract class Minigame(
    protected val game: Game,
    startPosPath: String,
) {
    private var _running = false
    private var countdownUUID = UUID.randomUUID()
    protected val plugin = PartyGames.plugin
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

    init {
        val startPosConfig = plugin.config.getConfigurationSection("locations.minigames.$startPosPath")!!
        val worlds = startPosConfig.getStringList("worlds")
        // choose a random world from the list
        worldIndex = Random.nextInt(0, worlds.size)
        rootWorldName = worlds[worldIndex]
        val x = startPosConfig.getDouble("x", 0.0)
        val y = startPosConfig.getDouble("y", 0.0)
        val z = startPosConfig.getDouble("z", 0.0)
        startPos = Location(Bukkit.getWorld(rootWorldName)!!, x, y, z)
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
     * A function to finish the minigame (roll back any changes, handle scores, etc.)
     * This will always run, regardless if the minigame was gracefully ended or not
     */
    open fun finish() {}

    /**
     * Function to stop the minigame (score calculation happens in [finish]
     */
    private fun end(nextGame: Boolean) {
        _running = false

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
        startTime: Long,
        duration: Long,
    ): Boolean {
        val bar = game.remainingBossBar
        val remainingTime = startTime + duration - System.currentTimeMillis()
        if (remainingTime < 0) {
            audience.hideBossBar(bar)
            return false
        }
        val time = remainingTime / 1000
        val minutes = time / 60
        val seconds = time % 60
        val name =
            "<green>Time remaining: <red>$minutes<gray>:</gray>${seconds.toString().padStart(2, '0')}"

        bar.name(MiniMessage.miniMessage().deserialize(name))
        bar.progress(remainingTime.toFloat() / duration.toFloat())

        return true
    }

    fun startCountdown(
        duration: Long,
        showBar: Boolean,
        onEnd: () -> Unit,
    ) {
        if (showBar) {
            audience.showBossBar(game.remainingBossBar)
        } else {
            audience.hideBossBar(game.remainingBossBar)
        }
        countdownUUID = UUID.randomUUID()
        val startTime = System.currentTimeMillis()
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
                        onEnd()
                    }
                }
            },
            0,
            1,
        )
    }

    fun startCountdown(
        duration: Long,
        onEnd: () -> Unit,
    ) {
        startCountdown(duration, true, onEnd)
    }

    fun stopCountdown() {
        // by creating a new UUID, the currently running countdown will be cancelled
        countdownUUID = UUID.randomUUID()
    }

    // functions for handling events
    open fun handleEntityMove(event: EntityMoveEvent) {}

    open fun handlePlayerInteract(event: PlayerInteractEvent) {}

    open fun handlePlayerMove(event: PlayerMoveEvent) {}

    open fun handleBlockPhysics(event: BlockPhysicsEvent) {}

    open fun handleEntityCombust(event: EntityCombustEvent) {}

    open fun handlePlayerDeath(event: PlayerDeathEvent) {}

    open fun handleBlockBreak(event: BlockBreakEvent) {}

    open fun handleBlockPlace(event: BlockPlaceEvent) {}

    open fun handlePrePlayerAttack(event: PrePlayerAttackEntityEvent) {}

    open fun handleInventoryClose(event: InventoryCloseEvent) {}

    open fun handlePlayerDropItem(event: PlayerDropItemEvent) {}

    open fun handleEntityChangeBlock(event: EntityChangeBlockEvent) {}

    open fun handleInventoryOpen(event: InventoryOpenEvent) {}

    open fun handlePlayerToggleFlight(event: PlayerToggleFlightEvent) {}

    open fun handlePlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {}

    open fun handleEntityDismount(event: EntityDismountEvent) {}

    open fun handleDisconnect(
        player: Player,
        didLeave: Boolean,
    ) {
    }

    open fun handleRejoin(player: Player) {}

    open fun handlePlayerChat(event: AsyncChatEvent) {}

    open fun handleEntityDamageByEntity(event: EntityDamageByEntityEvent) {}

    abstract val name: Component
    abstract val description: Component
}
