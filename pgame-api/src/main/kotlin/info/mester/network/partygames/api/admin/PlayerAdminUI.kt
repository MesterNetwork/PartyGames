package info.mester.network.partygames.api.admin

import info.mester.network.partygames.api.Game
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

class PlayerAdminUI(
    private val game: Game,
    private val managedPlayer: Player,
) : InventoryHolder {
    private val inventory = Bukkit.createInventory(this, 9, Component.text("Admin UI"))

    init {
        val openVoiceItem = ItemStack.of(Material.NOTE_BLOCK)
        openVoiceItem.editMeta { meta ->
            meta.displayName(Component.text("Open Voice Chat").decoration(TextDecoration.ITALIC, false))
            meta.lore(
                listOf(
                    Component
                        .text("Moves the selected player into the Discord stage")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                ),
            )
        }
        val playerDataItem = ItemStack.of(Material.PAPER)
        playerDataItem.editMeta { meta ->
            val playerData = game.playerData(managedPlayer)
            if (playerData == null) {
                meta.displayName(Component.text("No player data").decoration(TextDecoration.ITALIC, false))
                return@editMeta
            }

            meta.displayName(Component.text("Player Data").decoration(TextDecoration.ITALIC, false))
            meta.lore(
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
        event.whoClicked.sendMessage(Component.text("TODO"))
    }
}
