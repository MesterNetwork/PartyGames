package info.mester.bedless.tournament.game

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.function.Consumer

class ShowDistanceTask(private val minigame: RunawayMinigame) : Consumer<BukkitTask> {
    override fun accept(t: BukkitTask) {
        if(!minigame.running()) {
            t.cancel()
            return
        }

        val distances = minigame.getSortedDistances()

        for (player in minigame.game().players()) {
            val distance = distances.find { it.first.uniqueId == player.uniqueId }!!.second
            // show message in player's actionbar
            player.sendActionBar(Component.text("Distance from start: $distance blocks"))
        }
    }
}

class RunawayMinigame(private val game: Game, private val startPos: Location) : Minigame(game, startPos) {
    override fun start() {
        super.start()

        val plugin = game.plugin()

        // start a timer that ends the minigame after 20 seconds
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            end()
        }, 20 * 20)

        // start a timer that is constantly updating every player's actionbar with the distance from the start position
        val showDistanceTask = ShowDistanceTask(this)
        plugin.server.scheduler.runTaskTimer(plugin, showDistanceTask, 0, 2)
    }

    fun getSortedDistances(): List<Pair<Player, Double>> {
        // calculate the distance between all players and the start position as HashMap
        return game.players().associateWith { String.format("%.2f", startPos.distance(it.location)).toDouble() }
            // sort by descending distance
            .toList()
            .sortedByDescending { it.second }
    }

    override fun end() {
        super.end()

        val distances = getSortedDistances()

        Bukkit.broadcast(Component.text("The distances are: ${distances.joinToString(", ")}, winner is: ${distances[0].first.name}"))
    }

    override fun name(): Component {
        return Component.text("Runaway")
    }

    override fun description(): Component {
        return Component.text("Run as far away as possible in 20 seconds!", NamedTextColor.AQUA)
    }
}