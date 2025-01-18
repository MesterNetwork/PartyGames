package info.mester.network.partygames

import info.mester.network.partygames.api.GameState
import info.mester.network.partygames.api.events.GameEndedEvent
import info.mester.network.partygames.api.events.GameStartedEvent
import info.mester.network.partygames.api.events.GameTerminatedEvent
import info.mester.network.partygames.api.events.PlayerRejoinedEvent
import info.mester.network.partygames.api.events.PlayerRemovedFromGameEvent
import info.mester.network.partygames.game.HealthShopMinigame
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ArrowBodyCountChangeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldLoadEvent
import kotlin.math.floor

class PartyListener(
    private val plugin: PartyGames,
) : Listener {
    private val gameManager = plugin.gameManager
    private val core = plugin.core
    private val sidebarManager = plugin.sidebarManager

    @EventHandler
    fun onAsyncChat(event: AsyncChatEvent) {
        if (core.isAdmin(event.player)) {
            return
        }
        // rewrite viewers so only players in the same world can see the message
        if (!event.player.hasPermission("partygames.globalchat")) {
            val viewers = event.viewers()
            viewers.clear()
            for (player in event.player.world.players) {
                viewers.add(player)
            }
        }
        val plainText = PlainTextComponentSerializer.plainText().serialize(event.message())
        val game = core.gameRegistry.getGameOf(event.player) ?: return
        // special code for saying "fire map"
        if (game.state == GameState.PRE_GAME &&
            game.runningMinigame is HealthShopMinigame &&
            game.runningMinigame?.worldIndex == 0 &&
            plainText == "fire map"
        ) {
            game.awardPhrase(event.player, plainText, 25, "FIRE MAP!!!!")
        }
        // special code for saying "gg"
        if (game.state == GameState.POST_GAME && !game.hasNextMinigame() && plainText.lowercase() == "gg") {
            game.awardPhrase(event.player, "gg", 15, "Good Game")
        }
        // special code for saying "i wanna lose"
        if (plainText.lowercase() == "i wanna lose") {
            game.awardPhrase(event.player, "minuspoints", -200, "You wanted it")
        }
        // special code for "givex" and "losex"
        if (plainText.lowercase().startsWith("give") && event.player.hasPermission("partygames.admin")) {
            val amount = plainText.substringAfter("give").toIntOrNull() ?: return
            game.addScore(event.player, amount, "admin command")
        }
        if (plainText.lowercase().startsWith("lose") && event.player.hasPermission("partygames.admin")) {
            val amount = plainText.substringAfter("lose").toIntOrNull() ?: return
            game.addScore(event.player, -amount, "admin command")
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        gameManager.getQueueOf(event.player)?.removePlayer(event.player)
        plugin.sidebarManager.unregisterPlayer(event.player)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.showPlayerLevel(event.player)
        plugin.sidebarManager.openLobbySidebar(event.player)
    }

    @EventHandler
    fun onArrowBodyCountChange(event: ArrowBodyCountChangeEvent) {
        // players should not have arrows stuck in their butts
        event.newAmount = 0
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (plugin.core.isAdmin(event.player)) {
            return
        }
        plugin.gameManager.getQueueOf(event.player)?.handlePlayerInteract(event)
    }

    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        val world = event.world
        PartyGames.initWorld(world)
    }

    @EventHandler
    fun onGameStarted(event: GameStartedEvent) {
        val game = event.game
        plugin.playingPlaceholder.addPlaying(game.bundle.name, event.players.size)
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                for (player in event.players) {
                    sidebarManager.openGameSidebar(player)
                }
            },
            1,
        )
    }

    @EventHandler
    fun onGameTerminated(event: GameTerminatedEvent) {
        val game = event.game
        plugin.playingPlaceholder.removePlaying(game.bundle.name, event.playerCount)
        for (player in event.game.onlinePlayers) {
            plugin.sidebarManager.openLobbySidebar(player)
            plugin.showPlayerLevel(player)
        }
        event.setSpawnLocation(plugin.spawnLocation)
    }

    @EventHandler
    fun onGameEnded(event: GameEndedEvent) {
        // increase everyone's xp based on the score
        for ((player, data) in event.topList) {
            val oldLevel = plugin.levelManager.levelDataOf(player.uniqueId)
            plugin.levelManager.addXp(player.uniqueId, data.score.coerceAtLeast(0))
            val newLevel = plugin.levelManager.levelDataOf(player.uniqueId)
            val onlinePlayer = Bukkit.getPlayer(player.uniqueId) ?: return
            val levelUpMessage =
                buildString {
                    val levelString = "<gray>Level: <yellow>${oldLevel.level}"
                    append(levelString)
                    val leveledUp = newLevel.level > oldLevel.level
                    if (leveledUp) {
                        append(" <dark_gray>-> <green>${newLevel.level} <green><bold>LEVEL UP!</bold>\n")
                    } else {
                        append("\n")
                    }
                    append("<gray>Progress: ")
                    append("<yellow>${newLevel.xp} <dark_gray>[")
                    val maxSquares = 15
                    // render the progress bar (we have progressLength squares available)
                    val progress = (newLevel.xp / newLevel.xpToNextLevel.toFloat())
                    val previousProgress = (oldLevel.xp / oldLevel.xpToNextLevel.toFloat())
                    val filledSquares = floor(progress * maxSquares).toInt()
                    var previousFilledSquares = if (leveledUp) 0 else floor(previousProgress * maxSquares).toInt()
                    // if there are no additional squares, that means we've only earned very little progress
                    // in that case, the last progress square should always be green to indicate that
                    var additionalSquares = filledSquares - previousFilledSquares
                    if (additionalSquares == 0) {
                        previousFilledSquares -= 1
                        additionalSquares = 1
                    }
                    for (i in 0 until previousFilledSquares) {
                        append("<yellow>■")
                    }
                    for (i in 0 until additionalSquares) {
                        append("<green>■")
                    }
                    for (i in 0 until maxSquares - filledSquares) {
                        append("<gray>■")
                    }
                    append("<dark_gray>] <green>${newLevel.xpToNextLevel}\n")
                    append("<dark_gray>${"-".repeat(30)}")
                }
            onlinePlayer.sendMessage(mm.deserialize(levelUpMessage))
        }
    }

    @EventHandler
    fun onPlayerRejoined(event: PlayerRejoinedEvent) {
        val player = event.player
        plugin.sidebarManager.openGameSidebar(player)
    }

    @EventHandler
    fun onPlayerRemovedFromGame(event: PlayerRemovedFromGameEvent) {
        val player = event.player
        if (player.isOnline) {
            plugin.sidebarManager.openLobbySidebar(player)
            player.teleport(plugin.spawnLocation)
        }
    }
}
