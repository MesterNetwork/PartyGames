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
    val minVersion: Int,
) {
    ARCADE(listOf(MathMinigame::class, RunawayMinigame::class), 47),
    HEALTH_SHOP(listOf(HealthShopMinigame::class), 735),
    SPEED_BUILDERS(listOf(SpeedBuildersMinigame::class), 47),
    GARDENING(listOf(GardeningMinigame::class), 477),
    FAMILY_NIGHT(
        listOf(
            MathMinigame::class,
            RunawayMinigame::class,
            HealthShopMinigame::class,
            SpeedBuildersMinigame::class,
            GardeningMinigame::class,
        ),
        735,
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
        // check for minimum version
        val inCompatiblePlayers = players.filter { plugin.viaAPI.getPlayerVersion(it.uniqueId) < type.minVersion }
        if (inCompatiblePlayers.isNotEmpty()) {
            val compatibleAudience = Audience.audience(players.filter { it !in inCompatiblePlayers })
            val incompatibleAudience = Audience.audience(inCompatiblePlayers)
            compatibleAudience.sendMessage(
                mm.deserialize(
                    "<red>You are trying to join a game that is not compatible with your Minecraft version! Please play using the latest version of Minecraft if you want to guarantee full compatibility.",
                ),
            )
            incompatibleAudience.sendMessage(
                mm.deserialize(
                    "<red>The following players are using an incompatible version of Minecraft and cannot join this game:",
                ),
            )
            incompatibleAudience.sendMessage(
                mm.deserialize(
                    inCompatiblePlayers
                        .map { "<yellow>${it.name}</yellow>" }
                        .joinToString { "<dark_gray>, " },
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

    private fun getQueueOf(player: Player) = queues.values.firstOrNull { it.hasPlayer(player) }

    private fun getGameOf(player: Player) = games.values.firstOrNull { it.hasPlayer(player) }

    fun getGameByWorld(world: World) = games.values.firstOrNull { it.worldName == world.name }

    fun shutdown() {
        games.values.forEach { it.terminate() }
    }

    fun removePlayerFromQueue(player: Player) {
        getQueueOf(player)?.removePlayer(player)
        getGameOf(player)?.removePlayer(player)
    }

    fun startGame(queue: Queue) {
        queues.remove(queue.id)
        val players = queue.getPlayers()
        val game = Game(plugin, queue.type.minigames, players)
        games[game.id] = game
    }
}
