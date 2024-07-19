package info.mester.bedless.tournament.admin

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import info.mester.bedless.tournament.Tournament
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import okhttp3.Request
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

data class PackInfo(
    val packs: List<Pack>,
)

data class Pack(
    val id: String,
    val icon: String,
    @SerializedName("short_name") val shortName: String,
    @SerializedName("friendly_name") val friendlyName: String,
    val description: String,
    val downloads: Downloads,
)

data class Downloads(
    @SerializedName("1.8.9") val version189: String,
    @SerializedName("1.20.5") val version1205: String,
    val bedrock: String,
)

class PlayerAdminUI(
    managedPlayer: Entity,
) : InventoryHolder {
    private val inventory = Bukkit.createInventory(this, 9, Component.text("Admin UI"))

    init {
        Tournament.game.addPlayer(managedPlayer.uniqueId)

        val openVoiceItem = ItemStack.of(Material.NOTE_BLOCK)
        openVoiceItem.itemMeta =
            openVoiceItem.itemMeta.apply {
                displayName(Component.text("Open Voice Chat").decoration(TextDecoration.ITALIC, false))
                lore(
                    listOf(
                        Component
                            .text("Moves the selected player into the Discord stage")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    ),
                )
            }

        val playerDataItem = ItemStack.of(Material.PAPER)
        playerDataItem.itemMeta =
            playerDataItem.itemMeta.apply {
                val playerData = Tournament.game.playerData(managedPlayer.uniqueId)
                if (playerData == null) {
                    displayName(Component.text("No player data").decoration(TextDecoration.ITALIC, false))
                    return@apply
                }

                displayName(Component.text("Player Data").decoration(TextDecoration.ITALIC, false))
                lore(
                    listOf(
                        Component
                            .text("Score: ${playerData.score}")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    ),
                )
            }
        inventory.apply {
            setItem(0, openVoiceItem)
            setItem(1, playerDataItem)
        }
    }

    override fun getInventory(): Inventory = inventory

    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.rawSlot == 0) {
            // send a GET request to https://bedless.mester.info/api/packdata (expect JSON)
            val request =
                Request
                    .Builder()
                    .url("https://bedless.mester.info/api/packdata")
                    .build()

            Tournament.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    event.whoClicked.sendMessage(
                        Component.text("Failed to fetch pack data", NamedTextColor.RED),
                    )
                    return
                }

                val packData = response.body?.string()
                if (packData == null) {
                    event.whoClicked.sendMessage(
                        Component.text("Failed to fetch pack data", NamedTextColor.RED),
                    )
                    return
                }

                val gson = Gson()
                val packInfo = gson.fromJson(packData, PackInfo::class.java)

                event.whoClicked.sendMessage(
                    Component.text(
                        packInfo.packs.find { it.id == "60k" }?.friendlyName ?: "Pack not found",
                        NamedTextColor.RED,
                    ),
                )
            }
        }
    }
}
