package info.mester.network.partygames.game

import info.mester.network.partygames.PartyGames
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.reflect.KClass

enum class GameType(
    val minigames: List<KClass<out Minigame>>,
    val displayName: String,
) {
    HEALTH_SHOP(listOf(HealthShopMinigame::class), "Health Shop"),
    SPEED_BUILDERS(listOf(SpeedBuildersMinigame::class), "Speed Builders"),
    GARDENING(listOf(GardeningMinigame::class), "Gardening"),
    FAMILY_NIGHT(
        listOf(
            HealthShopMinigame::class,
            SpeedBuildersMinigame::class,
            GardeningMinigame::class,
            DamageDealer::class,
        ),
        "Family Night",
    ),
    SNIFFER_HUNT(
        listOf(
            SnifferHuntMinigame::class,
        ),
        "Sniffer Hunt",
    ),
    DAMAGE_DEALER(
        listOf(
            DamageDealer::class,
        ),
        "Damage Dealer",
    ),
}

private val mm = MiniMessage.miniMessage()

class GameManager(
    private val plugin: PartyGames,
) {
    private val queues = mutableMapOf<UUID, Queue>()
    private val games = mutableMapOf<UUID, Game>()

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
        val playersInGame = players.filter { getGameOf(it) != null }
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

    fun getGameOf(player: Player) = games.values.firstOrNull { it.hasPlayer(player) }

    fun getGameByWorld(world: World) = games.values.firstOrNull { it.worldName == world.name }

    fun shutdown() {
        games.values.forEach { it.terminate() }
    }

    fun removePlayerFromQueue(player: Player) {
        getQueueOf(player)?.removePlayer(player)
    }

    fun startGame(queue: Queue) {
        queues.remove(queue.id)
        val players = queue.getPlayers()
        val game = Game(plugin, queue.type, players)
        games[game.id] = game
    }

    fun getGames(): Array<Game> = games.values.toTypedArray()

    fun removeGame(game: Game) {
        games.remove(game.id)
    }
}
