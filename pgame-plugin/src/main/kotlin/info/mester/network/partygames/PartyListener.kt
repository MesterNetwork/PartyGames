package info.mester.network.partygames

import info.mester.network.partygames.api.GameState
import info.mester.network.partygames.api.events.GameStartedEvent
import info.mester.network.partygames.api.events.GameTerminatedEvent
import info.mester.network.partygames.game.HealthShopMinigame
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ArrowBodyCountChangeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldLoadEvent

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
        for (player in event.players) {
            sidebarManager.openGameSidebar(player)
        }
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
}
