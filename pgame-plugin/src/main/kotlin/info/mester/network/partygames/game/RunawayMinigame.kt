package info.mester.network.partygames.game

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.scheduler.BukkitTask
import java.util.function.Consumer
import kotlin.math.floor

class RunawayMinigame(
    game: Game,
) : Minigame(game, "runaway") {
    override fun start() {
        super.start()
        // start a 30-second countdown for the minigame
        startCountdown(30000) {
            end()
        }
        // start a timer that is constantly updating every player's actionbar with the distance from the start position
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            object : Consumer<BukkitTask> {
                override fun accept(t: BukkitTask) {
                    if (!running) {
                        t.cancel()
                        return
                    }
                    val distances = sortedDistances

                    for (player in game.onlinePlayers) {
                        val distance = distances.find { it.first.uniqueId == player.uniqueId }!!.second
                        // show message in player's actionbar
                        player.sendActionBar(Component.text("Distance from start: $distance blocks"))
                    }
                }
            },
            0,
            2,
        )
    }

    val sortedDistances: List<Pair<Player, Double>>
        get() {
            // calculate the distance between all players and the start position as HashMap
            return game
                .onlinePlayers
                .associateWith { String.format("%.2f", startPos.distance(it.location)).toDouble() }
                // sort by descending distance
                .toList()
                .sortedByDescending { it.second }
        }

    override fun finish() {
        val distances = sortedDistances
        // scoring system: +1 point for every 10 blocks away, +5 points for being #1 in the list, +3 points for being #2 or #3 +1 points for being in the top 10
        for (player in game.onlinePlayers) {
            val distanceScore = floor(distances.find { it.first.uniqueId == player.uniqueId }!!.second / 10).toInt()
            // get the position of the player in the list
            val position = distances.indexOfFirst { it.first.uniqueId == player.uniqueId }
            val leaderboardScore =
                when (position) {
                    0 -> 5
                    1, 2 -> 3
                    in 3..9 -> 1
                    else -> 0
                }

            game.addScore(player, distanceScore, "Distance to start")
            if (leaderboardScore != 0) {
                game.addScore(player, leaderboardScore, "Leaderboard position")
            }
        }
    }

    override fun handlePlayerDeath(event: PlayerDeathEvent) {
        event.isCancelled = true
        super.handlePlayerDeath(event)
    }

    override val name: Component
        get() = Component.text("Runaway", NamedTextColor.AQUA)
    override val description: Component
        get() = Component.text("Run as far away as possible in 30 seconds!", NamedTextColor.AQUA)
}
