package info.mester.network.partygames

import info.mester.network.partygames.game.Game
import okhttp3.OkHttpClient
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.pow
import kotlin.math.roundToInt

fun Double.roundTo(places: Int): Double {
    require(places >= 0) { "Decimal places must be non-negative." }
    val factor = 10.0.pow(places.toDouble())
    return (this * factor).roundToInt() / factor
}

class PartyGames : JavaPlugin() {
    companion object {
        private val _plugin = PartyGames()
        private val _client = OkHttpClient()
        private var _game: Game = Game(_plugin)
        val plugin: PartyGames
            get() = _plugin
        val client: OkHttpClient
            get() = _client
        val game: Game
            get() = _game
    }

    override fun onEnable() {
        saveResource("config.yml", true)
        saveResource("health-shop.yml", true)
        saveResource("speedbuilders.zip", true)
        // extract speedbuilders.zip and save to speedbuilders folder
        val zipFile = File(dataFolder, "speedbuilders.zip")
        val outputDir = File(dataFolder, "speedbuilders")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var zipEntry: ZipEntry? = zis.nextEntry
            while (zipEntry != null) {
                val newFile = File(outputDir, zipEntry.name)
                // Create directories for nested entries
                if (zipEntry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zipEntry = zis.nextEntry
            }
            zis.closeEntry()
        }
        // Plugin startup logic
        server.pluginManager.registerEvents(PartyListener(this), this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
