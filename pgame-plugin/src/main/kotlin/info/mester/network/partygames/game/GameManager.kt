package info.mester.network.partygames.game

import info.mester.network.partygames.PartyGames
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import java.util.UUID

enum class GameType(
    val minigames: List<String>,
    val displayName: String,
) {
    HEALTH_SHOP(listOf("healthshop"), "Health Shop"),
    SPEED_BUILDERS(listOf("speedbuilders"), "Speed Builders"),
    GARDENING(listOf("gardening"), "Gardening"),
    FAMILY_NIGHT(
        listOf(
            "healthshop",
            "speedbuilders",
            "gardening",
            "damagedealer",
        ),
        "Family Night",
    ),
    DAMAGE_DEALER(
        listOf("damagedealer"),
        "Damage Dealer",
    ),
}

private val mm = MiniMessage.miniMessage()

class GameManager(
    plugin: PartyGames,
) {
    private val core = plugin.core
    private val gameRegistry = core.gameRegistry
    private val queues = mutableMapOf<UUID, Queue>()

    private fun createQueue(
        type: GameType,
        maxPlayers: Int = 8,
    ): Queue {
        val queue = Queue(type, maxPlayers, this)
        queues[queue.id] = queue
        return queue
    }

    private fun getQueueForPlayers(
        type: GameType,
        players: List<Player>,
    ): Queue {
        // either return the first queue that can still fit the players, or create a new queue
        val queue = queues.values.firstOrNull { it.type == type && it.maxPlayers - it.playerCount >= players.size }
        if (queue != null) {
            return queue
        }
        return createQueue(type)
    }

    fun removeQueue(id: UUID) {
        queues.remove(id)
    }

    fun joinQueue(
        type: GameType,
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
        val queue = getQueueForPlayers(type, players)
        queue.addPlayers(players)
    }

    fun getQueueOf(player: Player) = queues.values.firstOrNull { it.hasPlayer(player) }

    fun removePlayerFromQueue(player: Player) {
        getQueueOf(player)?.removePlayer(player)
    }

    fun startGame(queue: Queue) {
        queues.remove(queue.id)
        val players = queue.getPlayers()
        gameRegistry.startGame(players, queue.type.name)
    }
}
