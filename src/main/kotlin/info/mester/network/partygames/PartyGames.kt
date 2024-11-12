package info.mester.network.partygames

import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.ViaAPI
import info.mester.network.partygames.admin.updateVisibilityOfPlayer
import info.mester.network.partygames.game.GameManager
import okhttp3.OkHttpClient
import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.pow
import kotlin.math.roundToInt

fun UUID.shorten() = this.toString().replace("-", "")

fun Double.roundTo(places: Int): Double {
    require(places >= 0) { "Decimal places must be non-negative." }
    val factor = 10.0.pow(places.toDouble())
    return (this * factor).roundToInt() / factor
}

class PartyGames : JavaPlugin() {
    lateinit var gameManager: GameManager
    lateinit var viaAPI: ViaAPI<*>

    companion object {
        val plugin = PartyGames()
        val httpClient = OkHttpClient()
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

    /**
     * Function to check if an entity (usually a player) is an admin
     *
     * @param entity the entity to check
     * @return true if the entity is an admin, false otherwise
     */
    fun isAdmin(entity: Entity): Boolean = isAdmin(entity.uniqueId)

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
        viaAPI = Via.getAPI()
        gameManager = GameManager(this)
        server.pluginManager.registerEvents(PartyListener(this), this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
        gameManager.shutdown()
    }
}
