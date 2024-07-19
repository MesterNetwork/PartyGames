package info.mester.bedless.tournament.game

import info.mester.bedless.tournament.Tournament
import info.mester.bedless.tournament.admin.updateVisibilityOfPlayer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class PlayerData(
    var score: Int,
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
    STARTING,
}

class Game(
    private val _plugin: Tournament,
) {
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
    private val minigames = listOf(HealthShopMinigame::class, RunawayMinigame::class, MathMinigame::class)

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
     * The boss bar to be used for the remaining time
     */
    private val _remainingBossBar =
        BossBar.bossBar(
            Component.text("Remaining time", NamedTextColor.GREEN),
            1.0F,
            BossBar.Color
                .GREEN,
            BossBar.Overlay.PROGRESS,
        )
    val remainingBossBar: BossBar
        get() = _remainingBossBar

    /**
     * Function to set a player's admin status
     *
     * @param player the player to manage
     * @param isAdmin true if the player should be an admin, false otherwise
     */
    fun setAdmin(
        player: Player,
        isAdmin: Boolean,
    ) {
        if (isAdmin) {
            // make sure the player can see the admins
            for (admin in admins) {
                player.showPlayer(Tournament.plugin, Bukkit.getPlayer(admin)!!)
            }
            admins.add(player.uniqueId)
        } else {
            // make sure the player can't see the admin
            for (admin in admins) {
                player.hidePlayer(Tournament.plugin, Bukkit.getPlayer(admin)!!)
            }
            admins.remove(player.uniqueId)
        }
        updateVisibilityOfPlayer(player, isAdmin)
    }

    /**
     * Function to check if a player is an admin
     *
     * @param player the UUID of the player to check
     * @return true if the player is an admin, false otherwise
     */
    fun isAdmin(player: UUID): Boolean = admins.contains(player)

    fun isAdmin(player: Player): Boolean = isAdmin(player.uniqueId)

    fun isAdmin(player: HumanEntity): Boolean = isAdmin(player.uniqueId)

    /**
     * Function to get a player's data
     *
     * @param player the UUID of the player to get
     * @return the player's data, or null if the player is not in the game
     */
    fun playerData(player: UUID): PlayerData? = players[player]

    /**
     * Function to add a player to the game
     *
     * @param player the player to add
     */
    fun addPlayer(player: UUID) {
        if (players.containsKey(player)) {
            return
        }
        players[player] = PlayerData(0)
    }

    /**
     * Function to remove a player from the game
     *
     * @param player the player to remove
     */
    fun removePlayer(player: UUID) {
        players.remove(player)
    }

    private fun reset() {
        // remove all player data
        players.clear()
    }

    fun players(): List<Player> = players.keys.map { _plugin.server.getPlayer(it)!! }

    fun start() {
        // reset current game state
        reset()

        _state = GameState.STARTING
        // add all online players who are not admins
        for (player in Bukkit.getOnlinePlayers().filter { !isAdmin(it) }) {
            addPlayer(player.uniqueId)
        }

        readyMinigames = minigames.shuffled().map { it.constructors.first().call() }.toMutableList()
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
            Component
                .text("Welcome to the ", NamedTextColor.GREEN)
                .append(_runningMinigame!!.name)
                .append(Component.text(" minigame!", NamedTextColor.GREEN))
                .append(Component.text("\n\n").append(_runningMinigame!!.description)),
        )

        _plugin.server.scheduler.runTaskTimer(
            _plugin,
            object : Consumer<BukkitTask> {
                private var rotation = 0.0
                private var lastTime = System.currentTimeMillis()

                override fun accept(t: BukkitTask) {
                    if (_state != GameState.LOADING) {
                        t.cancel()
                        return
                    }
                    val deltaTime = System.currentTimeMillis() - lastTime
                    lastTime = System.currentTimeMillis()
                    // rotate so that a full revolution takes 20 seconds
                    rotation += deltaTime * 360.0 / 20000.0
                    if (rotation > 360.0) {
                        rotation = 0.0
                    }
                    val playerList = players().map { it.uniqueId }
                    for (player in players()) {
                        // we want to "spread" the players out along the circle, which we can do by
                        // manipulating the degree before calculating the hit position
                        // basically, offset the degree by the player's index in the list
                        val offset = playerList.indexOf(player.uniqueId) * 360.0 / playerList.size
                        val playerRotation = (rotation + offset) % 360.0
                        // shoot a line from startpos with the angle and store the hit position
                        val radians = Math.toRadians(playerRotation)
                        val hitX = 15.0 * cos(radians)
                        val hitZ = 15.0 * sin(radians)
                        // to construct the final location for all players, take the x and z coordinates and set y to startPos.y + 15
                        val finalPos =
                            _runningMinigame!!.startPos.apply {
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

                        player.teleport(finalPos)
                    }
                }
            },
            0,
            1,
        )
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

        resetPlayers()

        _runningMinigame = null
        if (readyMinigames.isEmpty()) {
            end()
        }
    }

    private fun resetPlayers() {
        Audience.audience(Bukkit.getOnlinePlayers()).hideBossBar(remainingBossBar)
        for (player in players()) {
            // remove all status effects
            for (effect in player.activePotionEffects) {
                player.removePotionEffect(effect.type)
            }

            player.gameMode = GameMode.SURVIVAL
            // clear inventory
            player.inventory.clear()
            // heal to full and set food level to 20
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 20.0
            player.health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            player.foodLevel = 20
            player.saturation = 5f
            // teleport to lobby
            player.teleport(_plugin.config.getLocation("locations.waiting-lobby")!!)
        }
    }

    fun end() {
        _state = GameState.STOPPED

        resetPlayers()
        // this could be the case if we forcefully end the tournament with the command
        if (_runningMinigame != null) {
            _runningMinigame!!.terminate()
            _runningMinigame = null
        }

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
