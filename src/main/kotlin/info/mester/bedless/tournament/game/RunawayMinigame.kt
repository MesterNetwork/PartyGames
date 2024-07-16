package info.mester.bedless.tournament.game

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.function.Consumer
import kotlin.math.floor

class ShowDistanceTask(private val minigame: RunawayMinigame) : Consumer<BukkitTask> {
    override fun accept(t: BukkitTask) {
        if(!minigame.running) {
            t.cancel()
            return
        }

        val distances = minigame.sortedDistances

        for (player in minigame.game.players()) {
            val distance = distances.find { it.first.uniqueId == player.uniqueId }!!.second
            // show message in player's actionbar
            player.sendActionBar(Component.text("Distance from start: $distance blocks"))
        }
    }
}

class RunawayMinigame(private val _game: Game) : Minigame(_game) {
    init {
        _startPos = game.plugin.config.getLocation("locations.minigames.runaway")!!
    }
    override fun start() {
        super.start()

        val plugin = _game.plugin()

        // start a timer that ends the minigame after 20 seconds
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            end()
        }, 20 * 20)

        // start a timer that is constantly updating every player's actionbar with the distance from the start position
        val showDistanceTask = ShowDistanceTask(this)
        plugin.server.scheduler.runTaskTimer(plugin, showDistanceTask, 0, 2)
    }

    val sortedDistances: List<Pair<Player, Double>>
        get() {
            // calculate the distance between all players and the start position as HashMap
            return _game.players().associateWith { String.format("%.2f", startPos.distance(it.location)).toDouble() }
                // sort by descending distance
                .toList()
                .sortedByDescending { it.second }
        }

    override fun end() {
        val distances = sortedDistances

        // scoring system: +1 point for every 10 blocks away, +5 points for being #1 in the list, +3 points for being #2 or #3 +1 points for being in the top 10
        for (player in _game.players()) {
            val distanceScore = floor(distances.find { it.first.uniqueId == player.uniqueId }!!.second / 10).toInt()
            // get the position of the player in the list
            val position = distances.indexOfFirst { it.first.uniqueId == player.uniqueId }

            val score = distanceScore + when (position) {
                0 -> 5
                1,2 -> 3
                in 3..9 -> 1
                else -> 0
            }

            _game.playerData(player.uniqueId)!!.score += score
            player.sendMessage(Component.text("You scored $score points!", NamedTextColor.GREEN))
        }

        super.end()
    }

    override val name: Component
        get() = Component.text("Runaway", NamedTextColor.AQUA)
    override val description: Component
        get() = Component.text("Run as far away as possible in 20 seconds!", NamedTextColor.AQUA)
}