package info.mester.network.partygames.game.healthshop

import info.mester.network.partygames.util.createBasicItem
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack

class HealthShopItem(
    val item: ItemStack,
    val price: Int,
    val slot: Int,
    val key: String,
    val category: String,
    val amount: Int = 1,
) {
    companion object {
        fun loadFromConfig(
            section: ConfigurationSection,
            key: String,
        ): HealthShopItem {
            val material = Material.matchMaterial(section.getString("id")!!)
            val amount = section.getInt("amount", 1)
            val lore =
                (
                    section.getStringList("lore") +
                        listOf(
                            "",
                            "<gray>Cost: <red>${String.format("%.1f", section.getInt("price") / 2.0)} ♥",
                        )
                ).toTypedArray()
            val item =
                createBasicItem(material ?: Material.BARRIER, section.getString("name") ?: "Unknown", amount, *lore)
            item.editMeta { meta ->
                meta.setEnchantmentGlintOverride(false)
                HealthShopUI.applyGenericItemMeta(meta)
            }
            // apply healing potion to item
            if (key == "splash_healing_i" || key == "splash_healing_ii") {
                HealthShopUI.setHealthPotion(item, key == "splash_healing_ii")
            }
            // apply regeneration potion to item
            if (key == "regen_potion") {
                HealthShopUI.setRegenPotion(item, false)
            }
            // apply speed potion to item
            if (key == "speed_potion") {
                HealthShopUI.setSpeedPotion(item, false)
            }
            // apply jump potion to item
            if (key == "jump_potion") {
                HealthShopUI.setJumpPotion(item, false)
            }

            return HealthShopItem(
                item,
                section.getInt("price"),
                section.getInt("slot"),
                key,
                section.getString("category") ?: "none",
                amount,
            )
        }
    }
}
