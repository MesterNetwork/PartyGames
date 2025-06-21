package info.mester.network.partygames.game

import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.api.MinigameBundle
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.util.UUID

private val mm = MiniMessage.miniMessage()

class QueueManager(
    plugin: PartyGames,
) {
    private val core = plugin.core
    private val gameRegistry = core.gameRegistry
    private val queues = mutableMapOf<UUID, Queue>()

    private fun createQueue(
        bundle: MinigameBundle,
        maxPlayers: Int = 8,
    ): Queue {
        val queue = Queue(bundle, maxPlayers, this)
        queues[queue.id] = queue
        return queue
    }

    private fun getQueueForPlayers(
        bundle: MinigameBundle,
        players: List<Player>,
    ): Queue {
        // either return the first queue that can still fit the players, or create a new queue
        val queue = queues.values.firstOrNull { it.bundle == bundle && it.maxPlayers - it.playerCount >= players.size }
        if (queue != null) {
            return queue
        }
        return createQueue(bundle)
    }

    fun removeQueue(id: UUID) {
        queues.remove(id)
    }

    fun joinQueue(
        bundle: MinigameBundle,
        players: List<Player>,
    ) {
        // check if there is a player that is already in a game
        val playersInGame = players.filter { gameRegistry.getGameOf(it) != null }
        if (playersInGame.isNotEmpty()) {
            Audience.audience(playersInGame).sendMessage(
                mm.deserialize(
                    "<red>You are already in a game! You cannot join another game!",
                ),
            )
            return
        }
        // remove players from queues that already have them
        for (player in players) {
            removePlayerFromQueue(player)
        }
        val queue = getQueueForPlayers(bundle, players)
        queue.addPlayers(players)
    }

    fun getQueueOf(player: Player) = queues.values.firstOrNull { it.hasPlayer(player) }

    fun removePlayerFromQueue(player: Player) {
        getQueueOf(player)?.removePlayer(player)
    }

    fun startGame(queue: Queue) {
        queues.remove(queue.id)
        val players = queue.getPlayers()
        gameRegistry.startGame(players, queue.bundle.name)
    }
}
