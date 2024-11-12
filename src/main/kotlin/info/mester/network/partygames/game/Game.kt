package info.mester.network.partygames.game

import com.infernalsuite.aswm.api.AdvancedSlimePaperAPI
import com.infernalsuite.aswm.api.world.SlimeWorld
import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.shorten
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.logging.Level
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.reflect.KClass

data class PlayerData(
    var score: Int,
)

enum class GameState {
    /**
     * The game is in the loading state, where the players fly around the starting position and the game is explained
     */
    PRE_GAME,

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
     * The game has just started, ready to load the first minigame
     */
    STARTING,
}

private class IntroductionTimer(
    private val game: Game,
) : Consumer<BukkitTask> {
    private val players = game.getPlayers()
    private var rotation = 0.0
    private var lastTime = System.currentTimeMillis()
    private var remainingTime = 10 * 20 // the game will auto-start in 10 seconds

    private fun generateProgressBar(): String {
        val percentage = (1 - (remainingTime.toDouble() / 200)) * 100
        val filledSquares = ((percentage + 4) / 10).toInt().coerceIn(0, 10)

        return buildString {
            if (filledSquares > 0) {
                append("<green>")
                append("■".repeat(filledSquares))
                append("</green>")
            }
            if (filledSquares < 10) {
                append("<gray>")
                append("■".repeat(10 - filledSquares))
                append("<gray>")
            }
        }
    }

    override fun accept(t: BukkitTask) {
        val minigame = game.runningMinigame
        if (game.state != GameState.PRE_GAME || minigame == null) {
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
        val actionBar = "<green>Starting in <yellow>${
            String.format(
                "%.2f",
                remainingTime / 20.0,
            )
        }</yellow> seconds</green> <dark_gray>[${generateProgressBar()}]"
        Audience.audience(players).sendActionBar(MiniMessage.miniMessage().deserialize(actionBar))
        remainingTime--
        if (remainingTime <= 0) {
            game.begin()
        }
    }
}

class Game(
    private val plugin: PartyGames,
    minigames: List<KClass<out Minigame>>,
    players: List<Player>,
) {
    private val slimeAPI = AdvancedSlimePaperAPI.instance()

    /**
     * The unique ID of the game
     */
    val id: UUID = UUID.randomUUID()

    /**
     * The name of the world used by the game
     */
    val worldName = "game-${id.shorten()}"

    /**
     * Map of UUIDs to player data
     */
    private val playerDatas = mutableMapOf<Player, PlayerData>()

    /**
     * Shuffled list of constructed Minigame classes, which will be used in the game
     */
    private var readyMinigames = mutableListOf<Minigame>()

    /**
     * Currently running Minigame, or null if no Minigame is running
     */
    private var _runningMinigame: Minigame? = null
    val runningMinigame get() = _runningMinigame

    /**
     * Current state of the game
     */
    private var _state = GameState.STARTING
    val state get() = _state

    /**
     * The boss bar to be used for the remaining time
     */
    val remainingBossBar =
        BossBar.bossBar(
            Component.text("Remaining time", NamedTextColor.GREEN),
            1.0F,
            BossBar.Color
                .GREEN,
            BossBar.Overlay.PROGRESS,
        )
    private val audience = Audience.audience(players)

    init {
        // add all players to the game
        players.forEach { addPlayer(it) }
        // start the game
        audience.sendMessage(Component.text("Setting up the game...", NamedTextColor.GREEN))
        readyMinigames = minigames.shuffled().map { it.constructors.first().call(this) }.toMutableList()
        try {
            loadNextMinigame()
        } catch (err: IllegalStateException) {
            // uh-oh!
            plugin.logger.log(Level.SEVERE, "An error occurred while setting up the game!", err)
            audience.sendMessage(Component.text("An error occurred while setting up the game!", NamedTextColor.RED))
            end() // terminate the game
        }
    }

    /**
     * Function to get a player's data
     *
     * @param playerUUID the UUID of the player to get
     * @return the player's data, or null if the player is not in the game
     */
    fun playerData(playerUUID: UUID): PlayerData? = playerData(Bukkit.getPlayer(playerUUID)!!)

    fun playerData(player: Player): PlayerData? = playerDatas[player]

    /**
     * Function to add a player to the game
     *
     * @param player the player to add
     */
    private fun addPlayer(player: Player) {
        if (playerDatas.containsKey(player)) {
            return
        }
        playerDatas[player] = PlayerData(0)
    }

    /**
     * Function to remove a player from the game
     *
     * @param player the player to remove
     */
    fun removePlayer(player: Player) {
        playerDatas.remove(player)
    }

    fun getPlayers() = playerDatas.keys.toList()

    fun hasPlayer(player: Player) = playerDatas.contains(player)

    private fun loadNextMinigame() {
        if (readyMinigames.isEmpty()) {
            // throw an exception
            throw IllegalStateException("No minigames are ready!")
        }

        if (_state != GameState.STARTING && _state != GameState.POST_GAME) {
            // throw an exception
            throw IllegalStateException("The game is not in the starting or post game state!")
        }
        // load the next minigame
        _state = GameState.PRE_GAME
        _runningMinigame = readyMinigames.removeAt(0)
        unloadWorld()
        // start an async task to load the world
        Bukkit.getAsyncScheduler().runDelayed(plugin, {
            // clone the minigame's world into the game's world
            val minigameWorld = slimeAPI.getLoadedWorld(_runningMinigame!!.worldName)
            val gameWorld = minigameWorld.clone(worldName)
            // now switch to sync mode
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable {
                    startIntroduction(gameWorld)
                },
            )
        }, 1, TimeUnit.SECONDS)
    }

    private fun startIntroduction(gameWorld: SlimeWorld) {
        slimeAPI.loadWorld(gameWorld, true)
        val minigame = _runningMinigame as Minigame

        audience.sendMessage(
            Component
                .text("Welcome to ", NamedTextColor.GREEN)
                .append(minigame.name)
                .append(Component.text("\n\n").append(minigame.description)),
        )
        for (player in getPlayers()) {
            player.teleport(minigame.startPos)
        }
        // start a timer that rotates the players around the start pos
        Bukkit.getScheduler().runTaskTimer(plugin, IntroductionTimer(this), 0, 1)
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
        getPlayers().forEach { player ->
            player.gameMode = GameMode.SPECTATOR
        }
        // wait for 5 seconds and load the new minigame
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                _runningMinigame = null
                if (readyMinigames.isEmpty()) {
                    end()
                } else {
                    loadNextMinigame()
                }
            },
            5 * 20,
        )
    }

    private fun sendToLimbo() {
        Audience.audience(playerDatas.keys).hideBossBar(this.remainingBossBar)
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        val hideNametags = scoreboard.getTeam("hide-nametag")
        val limboWorld = Bukkit.getWorld("limbo")!!
        for (player in playerDatas.keys) {
            player.gameMode = GameMode.SURVIVAL
            // remove all status effects
            for (effect in player.activePotionEffects) {
                player.removePotionEffect(effect.type)
            }
            // clear inventory
            player.inventory.clear()
            // extinguish fire
            player.fireTicks = 0
            // show name again
            hideNametags?.removePlayer(player)
            // heal to full and set food level to 20
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 20.0
            player.health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            player.foodLevel = 20
            player.saturation = 5f
            // reset exp
            player.exp = 0f
            player.level = 0
            // teleport to limo
            player.teleportAsync(limboWorld.spawnLocation)
            player.allowFlight = false
        }
    }

    private fun unloadWorld() {
        sendToLimbo()
        Bukkit.unloadWorld(worldName, false)
    }

    /**
     * Gracefully shut down the game (used when the game has to be ended without announcing the winners)
     */
    fun terminate() {
        _state = GameState.STOPPED

        unloadWorld()
        // this could be the case if we forcefully end the tournament with the command
        if (_runningMinigame != null) {
            _runningMinigame!!.terminate()
            _runningMinigame = null
        }
        // send everyone to the lobby world
        val lobbyWorld = Bukkit.getWorld("world")!!
        getPlayers().forEach { it.teleport(lobbyWorld.spawnLocation) }
    }

    /**
     * End the game and announce the winners
     */
    private fun end() {
        Bukkit.broadcast(Component.text("The game has ended!", NamedTextColor.GREEN))
        // create a sorted list of player data based on their score
        val sortedPlayerData =
            playerDatas.keys
                .associateWith { playerData(it.uniqueId)!!.score }
                .toList()
                .sortedByDescending { it.second }
        // display the top 3 players
        for ((player, score) in sortedPlayerData.take(3)) {
            Bukkit.broadcast(Component.text("${player.name} has scored $score points!", NamedTextColor.GOLD))
        }
        // TODO: add a nice place where people can see the winners as player NPCs, then teleport everyone back in about 10 seconds
        terminate()
    }
}
