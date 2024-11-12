package info.mester.network.partygames.game

import info.mester.network.partygames.PartyGames
import io.papermc.paper.event.entity.EntityMoveEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent

abstract class Minigame(
    protected val game: Game,
    startPosPath: String,
) {
    protected val plugin = PartyGames.plugin
    protected val audience = Audience.audience(game.getPlayers())
    private var _running = false
    val running: Boolean
        get() = _running
    val startPos: Location
        get() {
            val pos = field.clone()
            pos.world = Bukkit.getWorld(game.worldName)!!
            return pos
        }
    val worldName: String

    init {
        val pos = plugin.config.getLocation(startPosPath)!!
        worldName = pos.world.name
        startPos = pos
    }

    /**
     * Function to start the minigame
     */
    open fun start() {
        _running = true

        game.getPlayers().forEach { player ->
            player.teleport(startPos)
            player.isFlying = false
            player.gameMode = GameMode.SURVIVAL
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

        bar.name(
            Component.text("Time remaining: ", NamedTextColor.GREEN).append(
                Component
                    .text(minutes.toString(), NamedTextColor.RED)
                    .append(Component.text(":", NamedTextColor.GRAY))
                    .append(Component.text(seconds.toString(), NamedTextColor.RED)),
            ),
        )
        bar.progress(remainingTime.toFloat() / duration.toFloat())

        return true
    }

    fun startCountdown(
        duration: Long,
        showBar: Boolean,
        onEnd: () -> Unit,
    ) {
        if (showBar) audience.showBossBar(game.remainingBossBar)
        val startTime = System.currentTimeMillis()
        plugin.server.scheduler.runTaskTimer(
            plugin,
            { t ->
                if (!running) {
                    t.cancel()
                    return@runTaskTimer
                }
                if (!updateRemainingTime(startTime, duration)) {
                    t.cancel()
                    onEnd()
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

    abstract val name: Component
    abstract val description: Component
}
