package info.mester.network.partygames.game

import com.infernalsuite.aswm.api.AdvancedSlimePaperAPI
import com.infernalsuite.aswm.api.world.SlimeWorld
import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.mm
import info.mester.network.partygames.shorten
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.UUID
import java.util.logging.Level

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

class Game(
    private val plugin: PartyGames,
    val type: GameType,
    players: List<Player>,
) {
    companion object {
        /**
         * General function to completely reset a player
         * @param player the player to reset
         */
        fun resetPlayer(player: Player) {
            player.gameMode = GameMode.SURVIVAL
            // remove all status effects
            for (effect in player.activePotionEffects) {
                player.removePotionEffect(effect.type)
            }
            // clear inventory
            player.inventory.clear()
            // extinguish fire
            player.fireTicks = 0
            // reset health and food level
            player.health = 20.0
            player.foodLevel = 20
            player.saturation = 0f
            player.sendHealthUpdate()
            // reset exp
            player.exp = 0f
            player.level = 0
            player.allowFlight = false
            player.isFlying = false
        }
    }

    private val slimeAPI = AdvancedSlimePaperAPI.instance()

    /**
     * The unique ID of the game
     */
    val id: UUID = UUID.randomUUID()

    /**
     * The index of the current minigame. Incremented every time a new minigame is loaded.
     */
    private var minigameIndex = -1

    /**
     * The name of the world used by the game
     */
    val worldName get() = "game-${id.shorten()}-$minigameIndex"
    val world get() = Bukkit.getWorld(worldName)!!

    /**
     * Map of player UUIDs to their player data
     */
    private val playerDatas = mutableMapOf<UUID, PlayerData>()

    /**
     * Shuffled list of constructed Minigame classes, which will be used in the game
     */
    private val readyMinigames: Array<Minigame>

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
    private val audience get() = Audience.audience(onlinePlayers)
    private val firemapAwared = mutableSetOf<UUID>()

    /**
     * Function to get a player's data
     *
     * @param playerUUID the UUID of the player to get
     * @return the player's data, or null if the player is not in the game
     */
    fun playerData(playerUUID: UUID) = playerDatas[playerUUID]

    fun playerData(player: Player) = playerData(player.uniqueId)

    /**
     * Get the top n players in the game
     * @param n the number of players to get
     * @return a list of pairs of the player and their data
     */
    fun topPlayers(n: Int): List<Pair<OfflinePlayer, PlayerData>> =
        playerDatas
            .toList()
            .sortedByDescending { it.second.score }
            .take(n)
            .map { Bukkit.getOfflinePlayer(it.first) to it.second }

    /**
     * Function to add a player to the game
     *
     * @param player the player to add
     */
    private fun addPlayer(player: Player) {
        if (playerDatas.containsKey(player.uniqueId)) {
            return
        }
        playerDatas[player.uniqueId] = PlayerData(0)
    }

    /**
     * Function to remove a player from the game
     * @param player the player to remove
     */
    fun removePlayer(player: Player) {
        playerDatas.remove(player.uniqueId)
        handleDisconnect(player, true)
        if (player.isOnline) {
            resetPlayer(player)
            player.teleport(plugin.spawnLocation)
        }
    }

    /**
     * Get all currently online players in the game
     */
    val onlinePlayers get() = playerDatas.keys.toList().mapNotNull { Bukkit.getPlayer(it) }

    fun hasPlayer(player: Player) = playerDatas.contains(player.uniqueId)

    init {
        // add all players to the game
        players.forEach { addPlayer(it) }
        // set up the game
        audience.sendMessage(Component.text("Starting the game...", NamedTextColor.GREEN))
        readyMinigames =
            type.minigames
                .shuffled()
                .map { it.constructors.first().call(this) }
                .toTypedArray()
        // update the playing placeholder
        plugin.playingPlaceholder.addPlaying(type.name, players.size)
        try {
            val success = nextMinigame()
            if (!success) {
                throw IllegalStateException("Couldn't load the first minigame!")
            }
            // wait a tick and set up the sidebar
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable {
                    for (player in players) {
                        plugin.sidebarManager.openGameSidebar(player)
                    }
                },
                1,
            )
        } catch (err: IllegalStateException) {
            // uh-oh!
            plugin.logger.log(Level.SEVERE, "An error occurred while setting up the game!", err)
            audience.sendMessage(Component.text("An error occurred while setting up the game!", NamedTextColor.RED))
            terminate()
        }
    }

    /**
     * Begin the async process of loading the next minigame
     */
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
        minigameIndex++
        _runningMinigame = readyMinigames[minigameIndex]
        // start an async task to load the world
        Bukkit.getAsyncScheduler().runNow(plugin) {
            // clone the minigame's world into the game's world
            val minigameWorld = slimeAPI.getLoadedWorld(_runningMinigame!!.rootWorldName)
            val gameWorld = minigameWorld.clone(worldName)
            // now switch to sync mode
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable {
                    startIntroduction(gameWorld)
                },
            )
        }
    }

    private fun startIntroduction(gameWorld: SlimeWorld) {
        // load the new world
        slimeAPI.loadWorld(gameWorld, true)
        val minigame = _runningMinigame as Minigame
        audience.sendMessage(
            Component
                .text("Welcome to ", NamedTextColor.GREEN)
                .append(minigame.name)
                .append(Component.text("\n\n").append(minigame.description)),
        )
        // reset the players and teleport them to the start pos
        resetPlayers()
        for (player in onlinePlayers) {
            player.gameMode = GameMode.SPECTATOR
            player.teleport(minigame.startPos)
        }
        // unload the previous world
        if (minigameIndex > 0) {
            minigameIndex--
            // teleport all admins to the new world too
            for (admin in world.players.filter { plugin.isAdmin(it) }) {
                admin.teleport(minigame.startPos)
            }
            unloadWorld(false)
            minigameIndex++
        }
        // start a timer that rotates the players around the start pos
        Bukkit.getScheduler().runTaskTimer(plugin, IntroductionTimer(this), 0, 1)
    }

    private fun nextMinigame(): Boolean {
        if (minigameIndex >= readyMinigames.size - 1 || (state != GameState.POST_GAME && state != GameState.STARTING)) {
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
        runningMinigame!!.start()
    }

    fun endMinigame() {
        _state = GameState.POST_GAME
        onlinePlayers.forEach { player ->
            player.gameMode = GameMode.SPECTATOR
            player.teleport(runningMinigame!!.startPos)
        }
        // wait for 5 seconds and load the new minigame
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                val success = nextMinigame()
                if (!success) {
                    _runningMinigame = null
                    end()
                }
            },
            5 * 20,
        )
    }

    private fun resetPlayers() {
        audience.hideBossBar(this.remainingBossBar)
        for (player in onlinePlayers) {
            resetPlayer(player)
        }
    }

    private fun unloadWorld(reset: Boolean = true) {
        if (reset) {
            resetPlayers()
        }
        val world = Bukkit.getWorld(worldName)
        if (world != null) {
            val success = Bukkit.unloadWorld(worldName, true)
            if (!success) {
                println("Failed to unload world!")
            }
        }
    }

    /**
     * Gracefully shut down the game (used when the game has to be ended without announcing the winners)
     */
    fun terminate() {
        _state = GameState.STOPPED
        plugin.playingPlaceholder.removePlaying(type.name, playerDatas.size)
        // this could be the case if we forcefully end the tournament with the command
        runningMinigame?.terminate()
        _runningMinigame = null
        // send everyone to the lobby world and unload the world
        world.players.forEach {
            it.teleport(plugin.spawnLocation)
        }
        unloadWorld()
        // final cleanup
        for (player in onlinePlayers) {
            resetPlayer(player)
            plugin.sidebarManager.openLobbySidebar(player)
            plugin.showPlayerLevel(player)
        }
        plugin.gameManager.removeGame(this)
    }

    /**
     * End the game and announce the winners
     */
    private fun end() {
        audience.sendMessage(Component.text("The game has ended!", NamedTextColor.GREEN))
        // create a sorted list of player data based on their score
        val sortedPlayerData = playerDatas.toList().sortedByDescending { it.second.score }
        // display the top 3 players
        for ((playerUUID, data) in sortedPlayerData.take(3)) {
            val player = Bukkit.getOfflinePlayer(playerUUID)
            audience.sendMessage(Component.text("${player.name} has scored ${data.score} points!", NamedTextColor.GOLD))
        }
        // increase everyone's xp based on the score
        for ((playerUUID, data) in sortedPlayerData) {
            plugin.levelManager.addXp(playerUUID, data.score)
        }
        // TODO: add a nice place where people can see the winners as player NPCs, then teleport everyone back in about 10 seconds
        terminate()
    }

    fun handleRejoin(player: Player) {
        resetPlayer(player)
        plugin.sidebarManager.openGameSidebar(player)
        player.gameMode = GameMode.SPECTATOR
        // teleport to current minigame
        if (runningMinigame != null) {
            player.teleport(runningMinigame!!.startPos)
        }
        audience.sendMessage(
            MiniMessage.miniMessage().deserialize("<green><bold><italic>${player.name} has rejoined the game!"),
        )
        runningMinigame?.handleRejoin(player)
    }

    fun handleDisconnect(
        player: Player,
        didLeave: Boolean,
    ) {
        player.hideBossBar(remainingBossBar)
        audience.sendMessage(
            mm.deserialize("<red><bold><italic>${player.name} has ${if (didLeave) "left" else "disconnected from"} the game!"),
        )
        runningMinigame?.handleDisconnect(player, didLeave)
    }

    fun addScore(
        player: Player,
        score: Int,
        reason: String,
    ) {
        if (score == 0) {
            return
        }
        playerData(player)?.let { playerData ->
            playerData.score += score
            val color = if (score >= 0) "green" else "red"
            val sign = if (score >= 0) "+" else ""
            player.sendMessage(mm.deserialize("<$color>$sign$score <gray>($reason)"))
            val sound =
                Sound.sound(
                    Key.key("entity.experience_orb.pickup"),
                    Sound.Source.MASTER,
                    1.0f,
                    if (score >= 0) 1.0f else 0.5f,
                )
            player.playSound(sound, Sound.Emitter.self())
        }
    }

    fun awardSayingFiremap(player: Player) {
        if (firemapAwared.contains(player.uniqueId)) {
            return
        }
        firemapAwared.add(player.uniqueId)
        addScore(player, 50, "Being awesome")
    }
}
