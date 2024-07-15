package info.mester.bedless.tournament.game

import info.mester.bedless.tournament.Tournament
import io.papermc.paper.entity.LookAnchor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.reflect.KClass

data class PlayerData(
    var score: Int
)

enum class GameState {
    /**
     * The game is in the loading state, where the players fly around the starting position and the game is explained
     */
    LOADING,

    /**
     * A minigame is currently running
     */
    PLAYING,
    STOPPED
}

class Game(private val plugin: Tournament) {
    /**
     * List of UUIDs of players who are admins
     */
    private val admins = mutableListOf<UUID>()

    /**
     * Map of UUIDs to player data
     */
    private val players = mutableMapOf<UUID, PlayerData>()

    /**
     * List of Minigame classes that may be used in the game
     */
    private val minigames = listOf<KClass<out Minigame>>(RunawayMinigame::class)

    /**
     * Shuffled list of constructed Minigame classes, which will be used in the game
     */
    private var readyMinigames = mutableListOf<Minigame>()

    /**
     * Currently running Minigame, or null if no Minigame is running
     */
    private var runningMinigame: Minigame? = null

    /**
     * Current state of the game
     */
    private var state = GameState.STOPPED

    init {
        // set up a task that adds a score to every player every second
        plugin.run {
            server.scheduler.runTaskTimer(this, Runnable {
                for (player in players.values) {
                    player.score++
                }
            }, 0, 20)
        }
    }

    /**
     * Function to set a player's admin status
     *
     * @param player the UUID of the player to set
     * @param admin true if the player should be an admin, false otherwise
     */
    fun setAdmin(player: UUID, admin: Boolean) {
        if (admin) {
            admins.add(player)
        } else {
            admins.remove(player)
        }
    }

    /**
     * Function to check if a player is an admin
     *
     * @param player the UUID of the player to check
     * @return true if the player is an admin, false otherwise
     */
    fun isAdmin(player: UUID): Boolean {
        return admins.contains(player)
    }

    /**
     * Function to get a player's data
     *
     * @param player the UUID of the player to get
     * @return the player's data, or null if the player is not in the game
     */
    fun playerData(player: UUID): PlayerData? {
        return players[player]
    }

    /**
     * Function for adding a player to the game
     *
     * @param player the player to add
     */
    fun addPlayer(player: UUID) {
        if (players.containsKey(player)) {
            return
        }
        players[player] = PlayerData(0)
    }

    private fun reset() {
        // remove all player data
        players.clear()
    }

    fun players(): List<Player> {
        return players.keys.map { plugin.server.getPlayer(it)!! }
    }

    fun plugin(): Tournament {
        return plugin
    }

    fun state(): GameState {
        return state
    }

    fun start() {
        // reset current game state
        reset()

        // add all online players who are not admins
        for (player in plugin.server.onlinePlayers) {
            if (!isAdmin(player.uniqueId)) {
                addPlayer(player.uniqueId)
            }
        }

        readyMinigames.addAll(
            minigames.shuffled()
                .map { it.constructors.first().call(this, Location(plugin().server.worlds[0], 165.5, 135.0, 76.5)) })
        loadNextMinigame()
    }

    private fun loadNextMinigame() {
        if (readyMinigames.isEmpty()) {
            return
        }

        state = GameState.LOADING

        runningMinigame = readyMinigames.removeAt(0)

        // put every player in spectator mode
        for (player in players().toList()) {
            player.gameMode = GameMode.SPECTATOR
        }

        Bukkit.broadcast(
            Component.text("Welcome to the ").append(runningMinigame!!.name())
                .append(Component.text(" minigame!", NamedTextColor.GREEN))
                .append(Component.text("\n\n").append(runningMinigame!!.description()))
        )

        class RotatePeopleTask : Consumer<BukkitTask> {
            private var degrees = 0.0
            private var lastTime = System.currentTimeMillis()
            override fun accept(t: BukkitTask) {
                if (state != GameState.LOADING) {
                    t.cancel()
                    return
                }

                val deltaTime = System.currentTimeMillis() - lastTime
                lastTime = System.currentTimeMillis()
                // rotate so that 15 seconds is 360 degrees
                degrees += deltaTime * 360.0 / 15000.0
                if (degrees > 360.0) {
                    degrees = 0.0
                }

                // shoot a line from startpos with the angle and store the hit position
                val radians = Math.toRadians(degrees)
                val hitX = 15.0 * cos(radians)
                val hitZ = 15.0 * sin(radians)

                // to construct the final location for all players, take the x and z coordinates and set y to startPos.y + 15
                val finalPos = runningMinigame!!.startPos().apply {
                    x += hitX
                    z += hitZ
                    y += 15.0
                }

                for (player in players()) {
                    player.teleport(finalPos)
                    player.lookAt(runningMinigame!!.startPos(), LookAnchor.EYES)
                }
            }
        }

        plugin.server.scheduler.runTaskTimer(plugin, RotatePeopleTask(), 0, 1)
    }

    /**
     * Begins the current minigame
     */
    fun begin() {
        state = GameState.PLAYING

        // start the minigame
        runningMinigame!!.start()
    }
}