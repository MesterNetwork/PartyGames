package info.mester.network.partygames

import info.mester.network.partygames.api.MinigameWorld
import info.mester.network.partygames.api.PartyGamesCore
import info.mester.network.partygames.game.GameManager
import info.mester.network.partygames.level.LevelManager
import info.mester.network.partygames.level.LevelPlaceholder
import info.mester.network.partygames.sidebar.SidebarManager
import net.kyori.adventure.text.minimessage.MiniMessage
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.World
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
    lateinit var core: PartyGamesCore
    lateinit var gameManager: GameManager
    lateinit var scoreboardLibrary: ScoreboardLibrary
    lateinit var playingPlaceholder: PlayingPlaceholder
    lateinit var databaseManager: DatabaseManager
    lateinit var levelManager: LevelManager
    lateinit var spawnLocation: Location
    lateinit var sidebarManager: SidebarManager

    companion object {
        private var _plugin: PartyGames? = null
        val plugin get() = _plugin!!

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

    fun showPlayerLevel(player: Player) {
        // if the player is in the lobby world, set their xp to their level
        val levelData = levelManager.levelDataOf(player.uniqueId)
        player.level = levelData.level
        player.exp = levelData.xp / levelData.xpToNextLevel.toFloat()
    }

    fun reload() {
        reloadConfig()
        spawnLocation = config.getLocation("spawn-location")!!
        registerMinigames()
    }

    override fun onEnable() {
        _plugin = this
        core = PartyGamesCore.getInstance()
        saveResource("config.yml", false)
        saveResource("health-shop.yml", false)
        saveResource("speed-builders.yml", false)
        saveResource("sniffer-hunt.yml", false)
        reload()
        // register low-level APIs
        try {
            scoreboardLibrary = ScoreboardLibrary.loadScoreboardLibrary(this)
        } catch (e: NoPacketAdapterAvailableException) {
            logger.warning("Failed to load ScoreboardLibrary, fallbacking to no-op")
            scoreboardLibrary = NoopScoreboardLibrary()
        }
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

    private fun registerMinigames() {
        core.gameRegistry.unregisterPlugin(this)
        val minigames = config.getConfigurationSection("minigames")!!
        for (minigameName in minigames.getKeys(false)) {
            val minigameConfig = minigames.getConfigurationSection(minigameName)!!
            val worldsList = minigameConfig.getList("worlds")!!
            val worlds =
                worldsList.mapNotNull { entry ->
                    if (entry is Map<*, *>) {
                        val world = entry["world"] as String
                        val x = entry["x"] as Double
                        val y = entry["y"] as Double
                        val z = entry["z"] as Double
                        MinigameWorld(world, org.bukkit.util.Vector(x, y, z))
                    } else {
                        null
                    }
                }
            val className = minigameConfig.getString("class")!!
            val displayName = minigameConfig.getString("display-name")
            core.gameRegistry.registerMinigame(
                this,
                className,
                minigameName,
                worlds,
                displayName,
            )
        }
        // register bundles for each minigame, then family night
        core.gameRegistry.registerBundle(
            this,
            listOf("healthshop", "speedbuilders", "gardening", "damagedealer"),
            "familynight",
            "Family Night",
        )
    }

    override fun onDisable() {
        // Plugin shutdown logic
        levelManager.stop()
    }
}
