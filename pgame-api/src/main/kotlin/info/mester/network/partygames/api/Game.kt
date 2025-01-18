package info.mester.network.partygames.api

import com.infernalsuite.aswm.api.AdvancedSlimePaperAPI
import com.infernalsuite.aswm.api.world.SlimeWorld
import info.mester.network.partygames.api.events.GameEndedEvent
import info.mester.network.partygames.api.events.GameStartedEvent
import info.mester.network.partygames.api.events.GameTerminatedEvent
import info.mester.network.partygames.api.events.PlayerRejoinedEvent
import info.mester.network.partygames.api.events.PlayerRemovedFromGameEvent
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

private val mm = MiniMessage.miniMessage()

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
    private val core: PartyGamesCore,
    val bundle: MinigameBundle,
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

    /**
     * Map of phrases that have been awarded to players
     * Used for things like giving xp for saying "gg" after the game ends
     */
    private val awardedPhrases = mutableMapOf<String, MutableSet<UUID>>()

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

    fun topPlayers() = topPlayers(playerDatas.size)

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
        }
        val event = PlayerRemovedFromGameEvent(this, player)
        event.callEvent()
        if (playerDatas.isEmpty()) {
            end()
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
            bundle.minigames
                .shuffled()
                .map {
                    core.gameRegistry
                        .getMinigame(it)!!
                        .minigame.constructors
                        .first()
                        .call(this)
                }.toTypedArray()
        try {
            val success = nextMinigame()
            if (!success) {
                throw IllegalStateException("Couldn't load the first minigame!")
            }
            val event = GameStartedEvent(this, players)
            event.callEvent()
        } catch (err: IllegalStateException) {
            // uh-oh!
            core.logger.log(Level.SEVERE, "An error occurred while setting up the game!", err)
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
        Bukkit.getAsyncScheduler().runNow(core) {
            // clone the minigame's world into the game's world
            val minigameWorld = slimeAPI.getLoadedWorld(_runningMinigame!!.rootWorldName)
            val gameWorld = minigameWorld.clone(worldName)
            // now switch to sync mode
            Bukkit.getScheduler().runTask(
                core,
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
            for (admin in world.players.filter { core.isAdmin(it) }) {
                admin.teleport(minigame.startPos)
            }
            unloadWorld(false)
            minigameIndex++
        }
        // start a timer that rotates the players around the start pos
        Bukkit.getScheduler().runTaskTimer(core, IntroductionTimer(this), 0, 1)
    }

    fun hasNextMinigame(): Boolean = minigameIndex < readyMinigames.size - 1

    private fun nextMinigame(): Boolean {
        if (!hasNextMinigame() || (state != GameState.POST_GAME && state != GameState.STARTING)) {
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
            core,
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
        // this could be the case if we forcefully end the tournament with the command
        runningMinigame?.terminate()
        _runningMinigame = null
        val event = GameTerminatedEvent(this, playerDatas.size)
        event.callEvent()
        // send everyone to the lobby world and unload the world
        world.players.forEach {
            it.teleport(event.getSpawnLocation())
        }
        unloadWorld()
        // final cleanup
        for (player in onlinePlayers) {
            resetPlayer(player)
        }
        core.gameRegistry.removeGame(this)
    }

    /**
     * End the game and announce the winners
     */
    private fun end() {
        audience.sendMessage(Component.text("The game has ended!", NamedTextColor.GREEN))
        // create a sorted list of player data based on their score
        val topList = topPlayers()
        // display the top 3 players
        val messageLength = 30
        val topListMessage =
            buildString {
                append("<dark_gray>${"-".repeat(messageLength)}\n")
                append("<yellow><bold>Top players:</bold>\n")

                for (i in topList.indices) {
                    val topPlayer = topList.getOrNull(i)
                    val color =
                        if (topPlayer != null) {
                            when (i) {
                                0 -> "<#Ffd700>"
                                1 -> "<#C0C0C0>"
                                2 -> "<#CD7F32>"
                                else -> "<gray>"
                            }
                        } else {
                            "<gray>"
                        }
                    append(
                        "${color}${i + 1}. ${topPlayer?.first?.name ?: "<gray>Nobody"} <gray>- <green>${topPlayer?.second?.score ?: 0}\n",
                    )
                }

                append("<dark_gray>${"-".repeat(messageLength)}")
            }
        audience.sendMessage(mm.deserialize(topListMessage))
        val event = GameEndedEvent(this, topList)
        event.callEvent()
        // TODO: add a nice place where people can see the winners as player NPCs, then teleport everyone back in about 10 seconds
        terminate()
    }

    fun handleRejoin(player: Player) {
        resetPlayer(player)
        val event = PlayerRejoinedEvent(this, player)
        if (!event.callEvent()) {
            return
        }
        player.gameMode = GameMode.SPECTATOR
        audience.sendMessage(
            MiniMessage.miniMessage().deserialize("<green><bold><italic>${player.name} has rejoined the game!"),
        )
        // teleport to current minigame
        if (runningMinigame != null) {
            player.teleport(runningMinigame!!.startPos)
            runningMinigame!!.handleRejoin(player)
        }
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

    fun awardPhrase(
        player: Player,
        phrase: String,
        score: Int,
        reason: String,
    ) {
        if (!awardedPhrases.containsKey(phrase)) {
            awardedPhrases[phrase] = mutableSetOf()
        }
        if (awardedPhrases[phrase]!!.contains(player.uniqueId)) {
            return
        }
        awardedPhrases[phrase]!!.add(player.uniqueId)
        addScore(player, score, reason)
    }
}
