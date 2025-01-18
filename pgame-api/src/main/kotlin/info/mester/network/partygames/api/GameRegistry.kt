package info.mester.network.partygames.api

import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.reflect.KClass

data class MinigameWorld(
    val name: String,
    val startPos: Vector,
)

data class RegisteredMinigame(
    val plugin: JavaPlugin,
    val minigame: KClass<out Minigame>,
    val name: String,
    val worlds: List<MinigameWorld>,
)

data class MinigameBundle(
    val plugin: JavaPlugin,
    val minigames: List<String>,
    val name: String,
    val displayName: String,
)

class GameRegistry(
    private val core: PartyGamesCore,
) {
    private val minigames = mutableListOf<RegisteredMinigame>()
    private val bundles = mutableListOf<MinigameBundle>()
    private val games = mutableMapOf<UUID, Game>()

    fun registerMinigame(
        plugin: JavaPlugin,
        className: String,
        name: String,
        worlds: List<MinigameWorld>,
        registerAs: String? = null,
    ) {
        if (worlds.isEmpty()) {
            throw IllegalArgumentException("Worlds cannot be empty!")
        }
        val clazz = plugin.javaClass.classLoader.loadClass(className)
        if (!Minigame::class.java.isAssignableFrom(clazz)) {
            throw IllegalArgumentException("Class $className is not a subclass of Minigame!")
        }
        val minigameClazz = clazz.asSubclass(Minigame::class.java)
        val kClass = minigameClazz.kotlin
        val registeredMinigame = RegisteredMinigame(plugin, kClass, name.uppercase(), worlds)
        minigames.add(registeredMinigame)
        if (registerAs != null) {
            bundles.add(MinigameBundle(plugin, listOf(name), name.uppercase(), registerAs))
        }
    }

    fun registerBundle(
        plugin: JavaPlugin,
        minigames: List<String>,
        name: String,
        displayName: String,
    ) {
        bundles.add(MinigameBundle(plugin, minigames, name.uppercase(), displayName))
    }

    fun getMinigame(name: String): RegisteredMinigame? = minigames.firstOrNull { it.name == name.uppercase() }

    private fun getBundle(name: String): MinigameBundle? = bundles.firstOrNull { it.name == name }

    fun getBundles(): List<MinigameBundle> = bundles

    fun startGame(
        players: List<Player>,
        bundleName: String,
    ) {
        val bundle =
            getBundle(bundleName.uppercase()) ?: throw IllegalArgumentException("Bundle $bundleName not found!")
        val game = Game(core, bundle, players)
        games[game.id] = game
    }

    fun getGameOf(player: Player) = games.values.firstOrNull { it.hasPlayer(player) }

    fun getGameByWorld(world: World) = games.values.firstOrNull { it.worldName == world.name }

    fun shutdown() {
        games.values.forEach { it.terminate() }
    }

    fun getGames(): Array<Game> = games.values.toTypedArray()

    fun removeGame(game: Game) {
        games.remove(game.id)
    }
}
