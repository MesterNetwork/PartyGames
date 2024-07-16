package info.mester.bedless.tournament.game

import info.mester.bedless.tournament.Tournament
import io.papermc.paper.entity.LookAnchor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
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

    /**
     * A minigame has just ended, but the tournament is still running, ready to load the next minigame
     */
    POST_GAME,

    /**
     * The game is over
     */
    STOPPED,

    /**
     * The game has just started, ready to load the next minigame
     */
    STARTING
}

class Game(private val _plugin: Tournament) {
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
    private val minigames = listOf<KClass<out Minigame>>(RunawayMinigame::class, MathMinigame::class)

    /**
     * Shuffled list of constructed Minigame classes, which will be used in the game
     */
    private var readyMinigames = mutableListOf<Minigame>()

    /**
     * Currently running Minigame, or null if no Minigame is running
     */
    private var _runningMinigame: Minigame? = null
    val runningMinigame: Minigame?
        get() = _runningMinigame

    /**
     * Current state of the game
     */
    private var _state = GameState.STOPPED
    val state: GameState
        get() = _state

    val plugin: Tournament
        get() = _plugin

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
        return players.keys.map { _plugin.server.getPlayer(it)!! }
    }

    fun plugin(): Tournament {
        return _plugin
    }

    fun start() {
        // reset current game state
        reset()

        _state = GameState.STARTING

        // add all online players who are not admins
        for (player in _plugin.server.onlinePlayers) {
            if (!isAdmin(player.uniqueId)) {
                addPlayer(player.uniqueId)
            }
        }

        readyMinigames.addAll(
            minigames.shuffled()
                .map { it.constructors.first().call(this) })
        loadNextMinigame()
    }

    private fun loadNextMinigame() {
        if (readyMinigames.isEmpty()) {
            // throw an exception
            throw IllegalStateException("No minigames are ready!")
        }

        if (_state != GameState.STARTING && _state != GameState.POST_GAME) {
            // throw an exception
            throw IllegalStateException("The game is not in the starting or post game state!")
        }

        _state = GameState.LOADING

        // load the next minigame
        _runningMinigame = readyMinigames.removeAt(0)

        // put every player in spectator mode
        for (player in players().toList()) {
            player.gameMode = GameMode.SPECTATOR
        }

        Bukkit.broadcast(
            Component.text("Welcome to the ", NamedTextColor.GREEN).append(_runningMinigame!!.name)
                .append(Component.text(" minigame!", NamedTextColor.GREEN))
                .append(Component.text("\n\n").append(_runningMinigame!!.description))
        )

        _plugin.server.scheduler.runTaskTimer(_plugin, object : Consumer<BukkitTask> {
            private var degrees = 0.0
            private var lastTime = System.currentTimeMillis()

            override fun accept(t: BukkitTask) {
                if (_state != GameState.LOADING) {
                    t.cancel()
                    return
                }

                val deltaTime = System.currentTimeMillis() - lastTime
                lastTime = System.currentTimeMillis()
                // rotate so that a full revolution takes 20 seconds
                degrees += deltaTime * 360.0 / 20000.0
                if (degrees > 360.0) {
                    degrees = 0.0
                }

                // shoot a line from startpos with the angle and store the hit position
                val radians = Math.toRadians(degrees)
                val hitX = 15.0 * cos(radians)
                val hitZ = 15.0 * sin(radians)

                // to construct the final location for all players, take the x and z coordinates and set y to startPos.y + 15
                val finalPos = _runningMinigame!!.startPos.apply {
                    x += hitX
                    z += hitZ
                    y += 15.0
                }

                for (player in players()) {
                    player.teleport(finalPos)
                    @Suppress("UnstableApiUsage")
                    player.lookAt(_runningMinigame!!.startPos, LookAnchor.EYES)
                }
            }
        }, 0, 1)
    }

    fun nextMinigame(): Boolean {
        if (readyMinigames.isEmpty() || (_state != GameState.POST_GAME && _state != GameState.STARTING)) {
            return false
        }

        loadNextMinigame()
        return true
    }

    /**
     * Begins the current minigame
     */
    fun begin() {
        _state = GameState.PLAYING

        // start the minigame
        _runningMinigame!!.start()
    }

    fun endMinigame() {
        _state = GameState.POST_GAME

        val lobby = _plugin.config.getLocation("locations.waiting-lobby")!!
        for (player in players()) {
            // remove all status effects
            for (effect in player.activePotionEffects) {
                player.removePotionEffect(effect.type)
            }

            // clear inventory
            player.inventory.clear()

            // heal to full and set food level to 20
            player.health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            player.foodLevel = 20

            // teleport to lobby
            player.teleport(lobby)
        }

        if (readyMinigames.isEmpty()) {
            end()
        }
    }

    private fun end() {
        _state = GameState.STOPPED

        Bukkit.broadcast(Component.text("The tournament has ended!", NamedTextColor.GREEN))

        // create a sorted list of player data based on their score
        val sortedPlayerData =
            players().associateWith { playerData(it.uniqueId)!!.score }.toList().sortedByDescending { it.second }

        // display the top 3 players
        for ((player, score) in sortedPlayerData.take(3)) {
            Bukkit.broadcast(Component.text("${player.name} has scored $score points!", NamedTextColor.GOLD))
        }
    }
}