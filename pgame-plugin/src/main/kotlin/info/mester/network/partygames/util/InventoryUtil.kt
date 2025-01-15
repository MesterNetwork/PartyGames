package info.mester.network.partygames.util

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

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
