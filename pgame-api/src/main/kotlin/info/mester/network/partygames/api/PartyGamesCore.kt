package info.mester.network.partygames.api

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

fun createBasicItem(
    material: Material,
    name: String,
    count: Int = 1,
    vararg lore: String,
): ItemStack {
    val item = ItemStack.of(material, count)
    item.editMeta { meta ->
        meta.displayName(MiniMessage.miniMessage().deserialize("<!i>$name"))
        meta.lore(lore.map { MiniMessage.miniMessage().deserialize("<!i>$it") })
    }
    return item
}

fun UUID.shorten() = this.toString().replace("-", "")

class PartyGamesCore : JavaPlugin() {
    companion object {
        private var instance: PartyGamesCore? = null

        fun getInstance(): PartyGamesCore {
            if (instance == null) {
                throw IllegalStateException("PartyGamesCore has not been initialized!")
            }
            return instance!!
        }
    }

    lateinit var gameRegistry: GameRegistry

    private fun updateVisibilityOfPlayer(
        playerToChange: Player,
        visible: Boolean,
    ) {
        // change the player's visibility for everyone who isn't an admin
        for (player in Bukkit
            .getOnlinePlayers()
            .filter { it.uniqueId != playerToChange.uniqueId && !isAdmin(it) }) {
            if (visible) {
                player.hidePlayer(this, playerToChange)
            } else {
                player.showPlayer(this, playerToChange)
            }
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

    /**
     * Function to check if an entity (usually a player) is an admin
     *
     * @param entity the entity to check
     * @return true if the entity is an admin, false otherwise
     */
    fun isAdmin(entity: Entity): Boolean = isAdmin(entity.uniqueId)

    override fun onEnable() {
        instance = this
        gameRegistry = GameRegistry(this)
        Bukkit.getPluginManager().registerEvents(PartyGamesListener(this), this)
    }

    override fun onDisable() {
        gameRegistry.shutdown()
    }
}
