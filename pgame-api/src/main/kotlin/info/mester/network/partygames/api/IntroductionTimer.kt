package info.mester.network.partygames.api

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin

private const val INTRODUCTION_TIME = 8

enum class IntroductionType {
    /**
     * The introduction will be a circle around the start position.
     * Players will be teleported to the circle and rotated around the start position.
     */
    CIRCLE,

    /**
     * The introduction will be at the start position.
     * The players will be locked to the start position and will only be able to look around.
     */
    STATIC,
}

class IntroductionTimer(
    private val game: Game,
) : Consumer<BukkitTask> {
    private var rotation = 0.0
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

        when (minigame.introductionType) {
            IntroductionType.CIRCLE -> circleIntroduction(minigame, players)
            IntroductionType.STATIC -> staticIntroduction(minigame, players)
        }
    }

    private fun circleIntroduction(
        minigame: Minigame,
        players: List<Player>,
    ) {
        // rotate so that a full revolution takes 20 seconds (400 ticks)
        rotation += 360 / 400.0
        if (rotation > 360.0) {
            rotation = rotation % 360.0
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
                    x += hitX
                    y += 15.0
                    z += hitZ
                    val direction = minigame.startPos.toVector().subtract(Vector(x, y, z))
                    setDirection(direction)
                }

            player.teleportAsync(finalPos)
        }
    }

    private fun staticIntroduction(
        minigame: Minigame,
        players: List<Player>,
    ) {
        for (player in players) {
            // teleport the player to the start position and lock their rotation
            val playerLocation = player.location
            if (playerLocation.world != minigame.startPos.world) {
                continue
            }
            if (playerLocation.distance(minigame.startPos) > 0.01) {
                player.teleportAsync(
                    minigame.startPos.apply {
                        yaw = player.location.yaw
                        pitch = player.location.pitch
                    },
                )
            }
        }
    }
}
