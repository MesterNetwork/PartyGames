package info.mester.network.partygames.game.healthshop

import info.mester.network.partygames.mm
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.inventory.ItemStack

data class HealthShopKit(
    val items: List<HealthShopItem>,
    val index: Int,
) {
    fun getDisplayItem(): ItemStack =
        items.maxByOrNull { it.price }?.item?.clone()?.apply {
            editMeta { meta ->
                val name = if (index == 8) "<!i><gray>Last used kit" else "<!i><gray>Saved kit #${index + 1}"
                meta.displayName(mm.deserialize(name))

                val lore = mutableListOf<Component>()
                val items = items.sortedByDescending { it.price }
                for (item in items) {
                    lore.add(
                        mm.deserialize(
                            "<!i><name> <gray>- <red>${
                                String.format(
                                    "%.1f",
                                    item.price / 2.0,
                                )
                            } ♥",
                            Placeholder.component("name", (item.item.itemMeta.displayName() ?: Component.empty())),
                        ),
                    )
                }
                lore.add(Component.empty())
                lore.add(mm.deserialize("<!i><gray>Click to select this kit!"))
                if (index != 8) {
                    lore.add(mm.deserialize("<!i><gray>Right click to delete this kit!"))
                }
                meta.lore(lore)
            }
        } ?: ItemStack.empty()
}
