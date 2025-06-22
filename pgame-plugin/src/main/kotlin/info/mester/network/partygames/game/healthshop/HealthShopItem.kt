package info.mester.network.partygames.game.healthshop

import info.mester.network.partygames.api.createBasicItem
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

enum class HealthShopItemCategory(
    val displayItem: Material,
) {
    /**
     * Combat items, such as weapons and armor.
     */
    COMBAT(Material.DIAMOND_SWORD),

    /**
     * Utility items, such as potions and food.
     */
    UTILITY(Material.GOLDEN_APPLE),

    /**
     * Potions.
     */
    POTION(Material.POTION),

    /**
     * Miscellaneous items that do not fit into other categories.
     */
    MISCELLANEOUS(Material.COMPASS),
}

class HealthShopItem(
    val item: ItemStack,
    val price: Int,
    val slot: Int,
    val key: String,
    val group: String,
    val amount: Int = 1,
    val category: HealthShopItemCategory = HealthShopItemCategory.MISCELLANEOUS,
) {
    companion object {
        @Suppress("UnstableApiUsage")
        fun loadFromConfig(
            section: ConfigurationSection,
            key: String,
        ): HealthShopItem {
            val material = Material.matchMaterial(section.getString("id")!!) ?: Material.BARRIER
            val amount = section.getInt("amount", 1)
            val group = section.getString("group") ?: "none"
            val lore =
                (
                    section.getStringList("lore") +
                        listOf(
                            "",
                            "<gray>Cost: <red>${String.format("%.1f", section.getInt("price") / 2.0)} ♥",
                        )
                ).toTypedArray()
            val item =
                createBasicItem(
                    material,
                    section.getString("name") ?: "Unknown",
                    amount,
                    *lore,
                ).apply {
                    addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
                    val defaultMaxStack = material.maxStackSize
                    if (defaultMaxStack < amount) {
                        setData(DataComponentTypes.MAX_STACK_SIZE, amount)
                        this.amount = amount
                    }
                }
            item.editMeta { meta ->
                meta.setEnchantmentGlintOverride(false)
                HealthShopUI.applyGenericItemMeta(meta)
            }
            // apply healing potion to item
            if (group == "splash_healing" || group == "splash_healing_ii") {
                HealthShopUI.setHealthPotion(item, key == "splash_healing_ii")
            }
            // apply regeneration potion to item
            if (group == "regen_ii") {
                HealthShopUI.setRegen2Potion(item)
            }
            if (key == "regen_v") {
                HealthShopUI.setRegenPotion(item, false)
            }
            // apply speed potion to item
            if (group == "speed_ii") {
                HealthShopUI.setSpeedPotion(item, false)
            }
            // apply jump potion to item
            if (group == "jump_boost") {
                HealthShopUI.setJumpPotion(item, false)
            }
            // apply turtle master
            if (group == "turtle_master") {
                val long = key.endsWith("_long")
                val strong = key.endsWith("_strong")
                HealthShopUI.setTurtleMasterPotion(item, long, strong)
            }

            return HealthShopItem(
                item,
                section.getInt("price"),
                section.getInt("slot"),
                key,
                group,
                amount,
                category =
                    HealthShopItemCategory.entries.firstOrNull {
                        it.name == section.getString("category")?.uppercase()
                    } ?: HealthShopItemCategory.MISCELLANEOUS,
            )
        }
    }
}
