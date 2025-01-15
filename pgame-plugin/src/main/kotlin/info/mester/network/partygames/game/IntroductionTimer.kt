package info.mester.network.partygames.game

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.scheduler.BukkitTask
import java.util.function.Consumer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val INTRODUCTION_TIME = 8

class IntroductionTimer(
    private val game: Game,
) : Consumer<BukkitTask> {
    private var rotation = 0.0
    private var lastTime = System.currentTimeMillis()
    private var remainingTime = INTRODUCTION_TIME * 20

    private fun generateProgressBar(): String {
        val percentage = (1 - (remainingTime.toDouble() / (INTRODUCTION_TIME * 20))) * 100
        val filledSquares = ((percentage + 5) / 10).toInt().coerceIn(0, 10)

        return buildString {
            if (filledSquares > 0) {
                append("<green>")
                append("■".repeat(filledSquares))
                append("</green>")
            }
            if (filledSquares < 10) {
                append("<gray>")
                append("■".repeat(10 - filledSquares))
                append("</gray>")
            }
        }
    }

    override fun accept(t: BukkitTask) {
        val minigame = game.runningMinigame
        if (game.state != GameState.PRE_GAME || minigame == null) {
            t.cancel()
            return
        }
        val actionBar = "<dark_gray>[${generateProgressBar()}]"
        val players = game.onlinePlayers
        Audience.audience(players).sendActionBar(MiniMessage.miniMessage().deserialize(actionBar))
        remainingTime--
        if (remainingTime <= 0) {
            t.cancel()
            game.begin()
            return
        }
        val deltaTime = System.currentTimeMillis() - lastTime
        lastTime = System.currentTimeMillis()
        // rotate so that a full revolution takes 20 seconds
        rotation += deltaTime * 360.0 / 20000.0
        if (rotation > 360.0) {
            rotation = 0.0
        }
        for (player in players) {
            // we want to "spread" the players out along the circle, which we can do by
            // manipulating the degree before calculating the hit position
            // basically, offset the degree by the player's index in the list
            val offset = players.indexOf(player) * 360.0 / players.size
            val playerRotation = (rotation + offset) % 360.0
            // shoot a line from startpos with the angle and store the hit position
            val radians = Math.toRadians(playerRotation)
            val hitX = 15.0 * cos(radians)
            val hitZ = 15.0 * sin(radians)
            // to construct the final location for all players, take the x and z coordinates and set y to startPos.y + 15
            val finalPos =
                minigame.startPos.apply {
                    val finalX = x + hitX
                    val finalY = y + 15.0
                    val finalZ = z + hitZ
                    // calculate the yaw and pitch from the final coordinates to the startpos
                    val dx = x - finalX
                    val dy = y - (finalY + player.eyeHeight)
                    val dz = z - finalZ
                    val distanceXZ = sqrt(dx * dx + dz * dz)
                    yaw = Math.toDegrees(atan2(dz, dx)).toFloat() - 90
                    pitch = -Math.toDegrees(atan2(dy, distanceXZ)).toFloat()

                    x = finalX
                    z = finalZ
                    y = finalY
                }

            player.teleportAsync(finalPos)
        }
    }
}
