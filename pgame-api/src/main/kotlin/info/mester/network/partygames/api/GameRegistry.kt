package info.mester.network.partygames.api

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.reflect.KClass

data class RegisteredMinigame(
    val plugin: JavaPlugin,
    val minigame: KClass<out Minigame>,
    val name: String,
    val showInJoin: Boolean,
    val startPos: Vector,
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
        minigame: KClass<out Minigame>,
        name: String,
        startPos: Vector,
        showInJoin: Boolean,
        register: String?,
    ) {
        val registeredMinigame = RegisteredMinigame(plugin, minigame, name, showInJoin, startPos)
        minigames.add(registeredMinigame)
        if (register != null) {
            bundles.add(MinigameBundle(plugin, listOf(name), name, register))
        }
    }

    fun registerBundle(
        plugin: JavaPlugin,
        minigames: List<String>,
        name: String,
        displayName: String,
    ) {
        bundles.add(MinigameBundle(plugin, minigames, name, displayName))
    }

    fun getStartPos(name: String): Vector? {
        val minigame = minigames.firstOrNull { it.name == name }
        return minigame?.startPos
    }

    fun getMinigame(name: String): RegisteredMinigame? = minigames.firstOrNull { it.name == name }

    fun getBundle(name: String): MinigameBundle? = bundles.firstOrNull { it.name == name }

    fun startGame(
        players: List<Player>,
        bundleName: String,
    ) {
        val bundle = getBundle(bundleName) ?: throw IllegalArgumentException("Bundle $bundleName not found!")
        val game = Game(core, bundle, players)
        games[game.id] = game
    }
}
