package info.mester.network.partygames.game.gardening

import info.mester.network.partygames.game.Game
import info.mester.network.partygames.roundTo
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Vector
import java.util.UUID

abstract class Plant(
    location: Location,
    private val game: Game,
) {
    protected var level = 0
    private var progress = 0.0
    private val contributors = mutableMapOf<UUID, Double>()
    protected val location: Location
        get() = field.clone()
    private val infoDisplay: TextDisplay
    private var active = true

    init {
        this.location = location

        infoDisplay =
            location.world.spawn(location.clone().add(0.5, 1.0, 0.5), TextDisplay::class.java) { entity ->
                entity.viewRange = 0.1f
                entity.billboard = Display.Billboard.CENTER
                entity.alignment = TextDisplay.TextAlignment.CENTER
                entity.text(Component.text("$progress%", getProgressionColor()))
            }
    }

    /**
     * Function that returns the color of the progression bar based on the progress.
     * It interpolates between (255, 85, 85) and (85, 255, 85).
     */
    private fun getProgressionColor(): TextColor {
        // interpolate between (255, 85, 85) and (85, 255, 85) based on the progress
        val clampedProgress = progress.coerceIn(0.0, 100.0) / 100.0
        val startColor = Triple(255, 85, 85) // RGB for (255, 85, 85)
        val endColor = Triple(85, 255, 85) // RGB for (85, 255, 85)
        val red = (startColor.first + (endColor.first - startColor.first) * clampedProgress).toInt()
        val green = (startColor.second + (endColor.second - startColor.second) * clampedProgress).toInt()
        val blue = (startColor.third + (endColor.third - startColor.third) * clampedProgress).toInt()
        return TextColor.color(red, green, blue)
    }

    private fun giveScore() {
        val totalScore = getTotalScore()
        // distribute points to contributors, based on their contribution (max = 100)
        for ((uuid, contribution) in contributors) {
            val score = ((contribution / 100.0) * totalScore).toInt()
            if (score == 0) {
                continue
            }
            // give points to player
            game.playerData(uuid)?.let { playerData ->
                playerData.score += score
                val player = Bukkit.getPlayer(uuid) ?: return@let
                player.sendMessage(
                    Component.text(
                        "You received $score points for watering $contribution% of this plant!",
                        if (score > 0) NamedTextColor.GREEN else NamedTextColor.RED,
                    ),
                )
            }
        }
        contributors.clear()
    }

    protected fun deactivate() {
        active = false
        infoDisplay.remove()
    }

    /**
     * Utility function to place a block at a relative position to the plant's location.
     * @param relativePos the relative position to the plant's location
     * @param block the block to place
     */
    protected fun placeBlock(
        relativePos: Vector,
        block: Material,
    ): Block {
        val pos = location.clone().add(relativePos)
        pos.block.type = block
        return pos.block
    }

    protected fun moveInfoDisplay(relativePos: Vector) {
        val pos = location.clone().add(relativePos)
        infoDisplay.teleport(pos)
    }

    fun water(
        player: Player,
        amount: Double,
    ) {
        if (!active) {
            return
        }
        val scaledAmount = amount * getProgressScale()
        if (contributors.containsKey(player.uniqueId)) {
            contributors[player.uniqueId] = contributors[player.uniqueId]!! + scaledAmount
        } else {
            contributors[player.uniqueId] = scaledAmount
        }
        contributors[player.uniqueId] = contributors[player.uniqueId]!!.coerceAtMost(100.0).roundTo(2)
        progress += scaledAmount
        // round progress to 2 decimal places
        progress = progress.roundTo(2)
        if (progress >= 100.0) {
            giveScore()
            level++
            progress = 0.0
            spawn()
        }
        if (active) {
            infoDisplay.text(Component.text("$progress%", getProgressionColor()))
        }
    }

    fun killWeed(player: Player): Boolean {
        val score = getWeedKillScore()
        if (score == 0) {
            // punish the player with -10 points for killing a non-weed
            game.playerData(player)?.let { playerData ->
                playerData.score -= 10
                player.sendMessage(
                    Component.text(
                        "You received -10 points for killing a non-weed!",
                        NamedTextColor.RED,
                    ),
                )
            }
            return false
        }
        // give points to player
        game.playerData(player)?.let { playerData ->
            playerData.score += score
            player.sendMessage(Component.text("You received $score points for killing a weed!", NamedTextColor.GREEN))
        }
        deactivate()
        location.block.type = Material.AIR
        return true
    }

    fun isActive(): Boolean = active

    protected open fun getProgressScale(): Double = 1.0

    /**
     * Abstract method that spawns the plant at the location.
     * It may be used in combination with [level] to spawn different types of plants based on the level.
     */
    abstract fun spawn()

    /**
     * Abstract method that returns the total score that can be awarded for a 100% completion of the plant.
     * It may be used in combination with [level] to award different scores based on the level.
     */
    abstract fun getTotalScore(): Int

    /**
     * Method that returns the score that can be awarded for killing a weed.
     * 0 if the plant is not a weed.
     */
    open fun getWeedKillScore(): Int = 0
}
