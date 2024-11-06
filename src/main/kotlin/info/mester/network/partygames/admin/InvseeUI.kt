package info.mester.network.partygames.admin

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

class InvseeUI(
    player: Player,
) : InventoryHolder {
    private val inventory =
        Bukkit.createInventory(
            this,
            6 * 9,
            MiniMessage.miniMessage().deserialize("<gray>Inventory of <green>${player.name}"),
        )

    init {
        // 0-8: armor, mainhand, offhand
        inventory.setItem(0, player.inventory.boots ?: ItemStack(Material.BARRIER))
        inventory.setItem(1, player.inventory.leggings ?: ItemStack(Material.BARRIER))
        inventory.setItem(2, player.inventory.chestplate ?: ItemStack(Material.BARRIER))
        inventory.setItem(3, player.inventory.helmet ?: ItemStack(Material.BARRIER))
        inventory.setItem(4, player.inventory.itemInMainHand)
        inventory.setItem(5, player.inventory.itemInOffHand)
        // 9-17: gray glass panes
        val divider = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        divider.editMeta { meta ->
            meta.isHideTooltip = true
        }
        for (i in 9..17) {
            inventory.setItem(i, divider)
        }
        // go through the player's inventory from 0 to 35
        for (i in 0..35) {
            val slot =
                when (i) {
                    // hotbar goes on the bottom
                    in 0..8 -> i + 5 * 9
                    // the rest start from the 3rd row
                    in 9..35 -> 9 + i
                    else -> throw IllegalStateException("Invalid slot index: $i")
                }
            inventory.setItem(slot, player.inventory.getItem(i) ?: ItemStack(Material.BARRIER))
        }
    }

    override fun getInventory(): Inventory = inventory
}
