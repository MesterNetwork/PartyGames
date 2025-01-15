package info.mester.network.partygames

import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.ViaAPI
import info.mester.network.partygames.admin.updateVisibilityOfPlayer
import info.mester.network.partygames.game.GameManager
import info.mester.network.partygames.level.LevelManager
import info.mester.network.partygames.level.LevelPlaceholder
import info.mester.network.partygames.sidebar.SidebarManager
import net.kyori.adventure.text.minimessage.MiniMessage
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary
import okhttp3.OkHttpClient
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToInt

val mm = MiniMessage.miniMessage()

fun UUID.shorten() = this.toString().replace("-", "")

fun Double.roundTo(places: Int): Double {
    require(places >= 0) { "Decimal places must be non-negative." }
    val factor = 10.0.pow(places.toDouble())
    return (this * factor).roundToInt() / factor
}

fun Int.toRomanNumeral(): String {
    val romanNumerals =
        listOf(
            1000 to "M",
            900 to "CM",
            500 to "D",
            400 to "CD",
            100 to "C",
            90 to "XC",
            50 to "L",
            40 to "XL",
            10 to "X",
            9 to "IX",
            5 to "V",
            4 to "IV",
            1 to "I",
        )
    var number = this
    val result = StringBuilder()

    for ((value, symbol) in romanNumerals) {
        while (number >= value) {
            result.append(symbol)
            number -= value
        }
    }

    return result.toString()
}

fun Int.pow(power: Int): Double = this.toDouble().pow(power)

class PartyGames : JavaPlugin() {
    lateinit var gameManager: GameManager
    lateinit var viaAPI: ViaAPI<*>
    lateinit var scoreboardLibrary: ScoreboardLibrary
    lateinit var playingPlaceholder: PlayingPlaceholder
    lateinit var databaseManager: DatabaseManager
    lateinit var levelManager: LevelManager
    lateinit var spawnLocation: Location
    lateinit var sidebarManager: SidebarManager

    companion object {
        val plugin = PartyGames()
        val httpClient = OkHttpClient()

        fun initWorld(world: World) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false)
            world.setGameRule(GameRule.DO_TILE_DROPS, false)
            world.setGameRule(GameRule.DO_FIRE_TICK, false)
            world.setGameRule(GameRule.DO_MOB_LOOT, false)
            world.setGameRule(GameRule.DO_INSOMNIA, false)
            world.setGameRule(GameRule.NATURAL_REGENERATION, true)
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0)
            world.time = 6000
        }
    }

    /**
     * List of UUIDs of players who are currently in admin mode
     * A user is considered an admin if they are in the admins list
     */
    private val admins = mutableListOf<UUID>()

    /**
     * Function to set a player's admin status
     *
     * @param player the player to manage
     * @param isAdmin true if the player should be an admin, false otherwise
     */
    fun setAdmin(
        player: Player,
        isAdmin: Boolean,
    ) {
        if (isAdmin) {
            // make sure the player can see the admins
            for (admin in admins) {
                player.showPlayer(
                    this,
                    Bukkit.getPlayer(admin)!!,
                )
            }
            admins.add(player.uniqueId)
        } else {
            // make sure the player can't see the admin
            for (admin in admins) {
                player.hidePlayer(
                    this,
                    Bukkit.getPlayer(admin)!!,
                )
            }
            admins.remove(player.uniqueId)
        }
        updateVisibilityOfPlayer(player, isAdmin)
    }

    private fun isAdmin(uuid: UUID): Boolean = admins.contains(uuid)

    fun showPlayerLevel(player: Player) {
        // if the player is in the lobby world, set their xp to their level
        val levelData = levelManager.levelDataOf(player.uniqueId)
        player.level = levelData.level
        player.exp = levelData.xp / levelData.xpToNextLevel.toFloat()
    }

    /**
     * Function to check if an entity (usually a player) is an admin
     *
     * @param entity the entity to check
     * @return true if the entity is an admin, false otherwise
     */
    fun isAdmin(entity: Entity): Boolean = isAdmin(entity.uniqueId)

    fun reload() {
        spawnLocation = config.getLocation("spawn-location")!!
    }

    override fun onEnable() {
        saveResource("config.yml", true)
        saveResource("health-shop.yml", true)
        saveResource("speed-builders.yml", true)
        saveResource("sniffer-hunt.yml", true)
        spawnLocation = config.getLocation("spawn-location")!!
        // register low-level APIs
        try {
            scoreboardLibrary = ScoreboardLibrary.loadScoreboardLibrary(this)
        } catch (e: NoPacketAdapterAvailableException) {
            logger.warning("Failed to load ScoreboardLibrary, fallbacking to no-op")
            scoreboardLibrary = NoopScoreboardLibrary()
        }
        viaAPI = Via.getAPI()
        // register managers
        databaseManager = DatabaseManager(File(dataFolder, "partygames.db"))
        levelManager = LevelManager(this)
        gameManager = GameManager(this)
        sidebarManager = SidebarManager(this)
        // register placeholders
        playingPlaceholder = PlayingPlaceholder()
        playingPlaceholder.register()
        LevelPlaceholder(levelManager).register()
        // set up event listeners
        server.pluginManager.registerEvents(PartyListener(this), this)
        // init all worlds
        for (world in Bukkit.getWorlds()) {
            initWorld(world)
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
        gameManager.shutdown()
        levelManager.stop()
    }
}
