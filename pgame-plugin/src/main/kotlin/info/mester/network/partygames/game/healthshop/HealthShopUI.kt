package info.mester.network.partygames.game.healthshop

import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.game.ShopFailedException
import info.mester.network.partygames.toRomanNumeral
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import java.util.UUID

class HealthShopUI(
    playerUUID: UUID,
    private val items: List<HealthShopItem>,
    private var money: Double,
) : InventoryHolder {
    private val inventory = Bukkit.createInventory(this, 5 * 9, Component.text("Health Shop"))
    private val purchasedItems: MutableList<HealthShopItem> = mutableListOf()
    private val player = Bukkit.getPlayer(playerUUID)!!

    companion object {
        private val maxArrows: MutableMap<UUID, Int> = mutableMapOf()

        fun maxArrows(uuid: UUID): Int = maxArrows[uuid] ?: 0

        fun setHealthPotion(
            item: ItemStack,
            strong: Boolean,
        ) {
            item.editMeta { meta ->
                val potionMeta = meta as PotionMeta
                potionMeta.basePotionType = if (strong) PotionType.STRONG_HEALING else PotionType.HEALING
                applyGenericItemMeta(meta)
            }
        }

        private fun setCustomPotion(
            item: ItemStack,
            potionEffect: PotionEffect,
            color: Color,
            potionName: String?,
        ) {
            item.editMeta { meta ->
                val potionMeta = meta as PotionMeta
                potionMeta.addCustomEffect(potionEffect, true)
                potionMeta.color = color
                val duration = potionEffect.duration / 20
                val minutes = duration / 60
                val seconds = String.format("%02d", duration % 60)
                if (potionName != null) {
                    val name =
                        MiniMessage
                            .miniMessage()
                            .deserialize(
                                "<!i><green>$potionName ${(potionEffect.amplifier + 1).toRomanNumeral()} <gray>(<yellow>$minutes<gray>:<yellow>$seconds<gray>)",
                            )
                    meta.displayName(name)
                }
                applyGenericItemMeta(meta)
            }
        }

        fun setRegenPotion(
            item: ItemStack,
            withName: Boolean = true,
        ) = setCustomPotion(
            item,
            PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 4, false),
            Color.fromRGB(205, 92, 171),
            if (withName) "Regeneration" else null,
        )

        fun setSpeedPotion(
            item: ItemStack,
            withName: Boolean = true,
        ) = setCustomPotion(
            item,
            PotionEffect(PotionEffectType.SPEED, 15 * 20, 1, false),
            Color.fromRGB(51, 235, 255),
            if (withName) "Speed" else null,
        )

        fun setJumpPotion(
            item: ItemStack,
            withName: Boolean = true,
        ) = setCustomPotion(
            item,
            PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 20, 3, false),
            Color.fromRGB(253, 255, 132),
            if (withName) "Jump Boost" else null,
        )

        fun applyGenericItemMeta(itemMeta: ItemMeta) {
            itemMeta.apply {
                isUnbreakable = true
                addItemFlags(ItemFlag.HIDE_UNBREAKABLE)
                addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
                addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
            }
        }
    }

    init {
        // init maxarrows to 0
        maxArrows[playerUUID] = 0
        for (item in items) {
            inventory.setItem(item.slot, item.item)
        }
    }

    override fun getInventory(): Inventory = inventory

    fun onInventoryClick(event: InventoryClickEvent) {
        val slot = event.slot
        val index = items.indexOfFirst { it.slot == slot }
        if (index == -1) {
            return
        }
        val shopItem = items[index]
        val item = inventory.getItem(slot)!!
        // toggle purchased state
        if (purchasedItems.contains(shopItem)) {
            removeItem(shopItem, item)
        } else {
            try {
                addItem(shopItem, item)
            } catch (e: ShopFailedException) {
                val message =
                    when (e.message) {
                        "no_healh" -> "You do not have enough hearts to purchase this item!"
                        "no_bow" -> "You must first buy a bow before purchasing this item!"
                        else -> "An error occurred while trying to purchase this item!"
                    }
                event.whoClicked.sendMessage(
                    Component.text(
                        message,
                        NamedTextColor.RED,
                    ),
                )
                player.playSound(
                    Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 1.0f, 1.0f),
                    Sound.Emitter.self(),
                )
            }
        }
        player.sendMessage(
            MiniMessage
                .miniMessage()
                .deserialize("<green>You have <red>${String.format("%.1f", player.health / 2.0)} ♥ <green>left!"),
        )
    }

    private fun removeItem(
        shopItem: HealthShopItem,
        inventoryItem: ItemStack,
    ) {
        purchasedItems.remove(shopItem)
        money += shopItem.price

        inventoryItem.editMeta { meta ->
            // remove the enchantment glint from the item in the ui
            meta.setEnchantmentGlintOverride(false)
            // remove underlined and bold from name
            val decorations =
                mapOf(
                    TextDecoration.UNDERLINED to TextDecoration.State.FALSE,
                    TextDecoration.BOLD to TextDecoration.State.FALSE,
                )
            meta.displayName(meta.displayName()!!.decorations(decorations))
        }
        // special case: if we remove a bow, remove all arrows
        if (shopItem.key == "bow") {
            val arrowItem = purchasedItems.firstOrNull { it.category == "arrow" }
            if (arrowItem != null) {
                removeItem(arrowItem, inventory.getItem(arrowItem.slot)!!)
            }
        }

        player.health = money
    }

    private fun addItem(
        shopItem: HealthShopItem,
        inventoryItem: ItemStack,
    ) {
        val sameCategory = purchasedItems.filter { it.category != "none" && it.category == shopItem.category }
        // calculate how much money we'd have if we removed all the items in the same category
        val moneyToAdd = sameCategory.sumOf { it.price }
        // check if we have enough money
        if ((money + moneyToAdd) <= shopItem.price) {
            throw ShopFailedException("no_health")
        }
        // check if we're trying to buy an arrow
        if (shopItem.category == "arrow") {
            // check if we have a bow
            if (!purchasedItems.any { it.key == "bow" }) {
                throw ShopFailedException("no_bow")
            }
        }
        purchasedItems.add(shopItem)
        money -= shopItem.price
        // play experience orb pickup sound
        player.playSound(
            Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 1.0f, 1.0f),
            Sound.Emitter.self(),
        )
        // remove all the items in the same category
        sameCategory.forEach { removeItem(it, inventory.getItem(it.slot)!!) }

        inventoryItem.editMeta { meta ->
            // add an enchantment glint to the item in the ui
            meta.setEnchantmentGlintOverride(true)
            // make name underlined and bold
            val decorations =
                mapOf(
                    TextDecoration.UNDERLINED to TextDecoration.State.TRUE,
                    TextDecoration.BOLD to TextDecoration.State.TRUE,
                )
            meta.displayName(meta.displayName()!!.decorations(decorations))
        }

        player.health = money
    }

    private fun addArmor(
        player: Player,
        armor: ArmorType,
    ) {
        // this ugly shit creates a list of armor items based on the armor type
        // order: helmet, chestplate, leggings, boots
        val armorItems =
            when (armor) {
                ArmorType.LEATHER ->
                    listOf(
                        ItemStack.of(Material.LEATHER_HELMET),
                        ItemStack.of(Material.LEATHER_CHESTPLATE),
                        ItemStack.of(Material.LEATHER_LEGGINGS),
                        ItemStack.of(Material.LEATHER_BOOTS),
                    )

                ArmorType.CHAINMAIL ->
                    listOf(
                        ItemStack.of(Material.CHAINMAIL_HELMET),
                        ItemStack.of(Material.CHAINMAIL_CHESTPLATE),
                        ItemStack.of(Material.CHAINMAIL_LEGGINGS),
                        ItemStack.of(Material.CHAINMAIL_BOOTS),
                    )

                ArmorType.IRON ->
                    listOf(
                        ItemStack.of(Material.IRON_HELMET),
                        ItemStack.of(Material.IRON_CHESTPLATE),
                        ItemStack.of(Material.IRON_LEGGINGS),
                        ItemStack.of(Material.CHAINMAIL_BOOTS),
                    )

                ArmorType.DIAMOND ->
                    listOf(
                        ItemStack.of(Material.DIAMOND_HELMET),
                        ItemStack.of(Material.DIAMOND_CHESTPLATE),
                        ItemStack.of(Material.IRON_LEGGINGS),
                        ItemStack.of(Material.IRON_BOOTS),
                    )

                ArmorType.NETHERITTE ->
                    listOf(
                        ItemStack.of(Material.NETHERITE_HELMET),
                        ItemStack.of(Material.NETHERITE_CHESTPLATE),
                        ItemStack.of(Material.DIAMOND_LEGGINGS),
                        ItemStack.of(Material.DIAMOND_BOOTS),
                    )
            }
        armorItems.forEach { armorItem ->
            armorItem.editMeta { meta ->
                applyGenericItemMeta(meta)
                if (purchasedItems.any { it.key == "protection_i" }) {
                    meta.addEnchant(Enchantment.PROTECTION, 1, true)
                }
                if (purchasedItems.any { it.key == "protection_ii" }) {
                    meta.addEnchant(Enchantment.PROTECTION, 2, true)
                }
                if (purchasedItems.any { it.key == "thorns" }) {
                    meta.addEnchant(Enchantment.THORNS, 2, true)
                }
            }
        }

        player.inventory.setItem(EquipmentSlot.HEAD, armorItems[0])
        player.inventory.setItem(EquipmentSlot.CHEST, armorItems[1])
        player.inventory.setItem(EquipmentSlot.LEGS, armorItems[2])
        player.inventory.setItem(EquipmentSlot.FEET, armorItems[3])
    }

    fun giveItems() {
        // process sword
        val addSword = { material: Material ->
            val sword = ItemStack.of(material)
            sword.editMeta { meta ->
                if (purchasedItems.any { it.key == "fire_aspect" }) {
                    meta.addEnchant(Enchantment.FIRE_ASPECT, 1, true)
                }
                purchasedItems.firstOrNull { it.key.startsWith("sharpness_") }.let { sharpnessItem ->
                    if (sharpnessItem != null) {
                        val sharpness = sharpnessItem.key.substringAfter("sharpness_").toInt()
                        meta.addEnchant(Enchantment.SHARPNESS, sharpness, true)
                    }
                }
                applyGenericItemMeta(meta)
            }
            player.inventory.addItem(sword)
        }
        kotlin
            .runCatching {
                purchasedItems.first { it.category == "sword" }.item.type
            }.onSuccess { material ->
                addSword(material)
            }.onFailure {
                addSword(Material.WOODEN_SWORD)
            }
        // process knockback stick
        if (purchasedItems.any { it.key == "knockback_stick" }) {
            val stick = ItemStack.of(Material.STICK)
            stick.editMeta { meta ->
                applyGenericItemMeta(meta)
                meta.addEnchant(Enchantment.KNOCKBACK, 2, true)
            }
            player.inventory.addItem(stick)
        }
        // process shield
        if (purchasedItems.any { it.key == "shield" }) {
            val shield = ItemStack.of(Material.SHIELD)
            shield.editMeta { meta ->
                applyGenericItemMeta(meta)
            }
            player.inventory.setItem(EquipmentSlot.OFF_HAND, shield)
        }
        // process golden apples
        purchasedItems.filter { it.category == "gap" }.forEach { item ->
            val apple = ItemStack.of(Material.GOLDEN_APPLE, item.amount)
            if (item.key == "golden_apple_inf") {
                apple.editMeta { meta ->
                    // add a fake enchantment to make it look like an enchanted golden apple
                    // the enchantment is also used to determine if the item is an infinite golden apple
                    meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true)
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                }
            }
            player.inventory.addItem(apple)
        }
        // process regeneration potion
        purchasedItems.firstOrNull { it.key == "regen_potion" }?.let { shopItem ->
            val potion = ItemStack.of(Material.POTION)
            setRegenPotion(potion)
            for (i in 1..shopItem.amount) {
                player.inventory.addItem(potion)
            }
        }
        // process speed potion
        purchasedItems.firstOrNull { it.key == "speed_potion" }?.let { shopItem ->
            val potion = ItemStack.of(Material.POTION)
            setSpeedPotion(potion)
            for (i in 1..shopItem.amount) {
                player.inventory.addItem(potion)
            }
        }
        // process jump potion
        purchasedItems.firstOrNull { it.key == "jump_potion" }?.let { shopItem ->
            val potion = ItemStack.of(Material.POTION)
            setJumpPotion(potion)
            for (i in 1..shopItem.amount) {
                player.inventory.addItem(potion)
            }
        }
        // process healing potions
        for (purchasedPotion in purchasedItems.filter { it.key == "splash_healing_i" || it.key == "splash_healing_ii" }) {
            val potion = ItemStack.of(Material.SPLASH_POTION, purchasedPotion.amount)
            setHealthPotion(potion, purchasedPotion.key == "splash_healing_ii")
            player.inventory.addItem(potion)
        }
        // process armor
        kotlin
            .runCatching {
                purchasedItems.first { it.category == "armor" }
            }.onSuccess { shopItem ->
                when (shopItem.key) {
                    "chainmail_armor" -> addArmor(player, ArmorType.CHAINMAIL)
                    "iron_armor" -> addArmor(player, ArmorType.IRON)
                    "diamond_armor" -> addArmor(player, ArmorType.DIAMOND)
                    "netherite_armor" -> addArmor(player, ArmorType.NETHERITTE)
                }
            }.onFailure {
                addArmor(player, ArmorType.LEATHER)
            }
        // process bow
        if (purchasedItems.any { it.key == "bow" }) {
            val bow = ItemStack.of(Material.BOW)
            bow.editMeta { meta ->
                applyGenericItemMeta(meta)
                if (purchasedItems.any { it.key == "flame" }) {
                    meta.addEnchant(Enchantment.FLAME, 1, true)
                }
                if (purchasedItems.any { it.key == "power_i" }) {
                    meta.addEnchant(Enchantment.POWER, 1, true)
                }
                if (purchasedItems.any { it.key == "power_ii" }) {
                    meta.addEnchant(Enchantment.POWER, 2, true)
                }
                if (purchasedItems.any { it.key == "punch_i" }) {
                    meta.addEnchant(Enchantment.PUNCH, 1, true)
                }
                if (purchasedItems.any { it.key == "punch_ii" }) {
                    meta.addEnchant(Enchantment.PUNCH, 2, true)
                }
            }
            player.inventory.addItem(bow)
            // an arrow is included with the bow
            maxArrows[player.uniqueId] = 1
        }
        // process arrows
        kotlin
            .runCatching {
                purchasedItems.first { it.category == "arrow" }
            }.onSuccess { shopItem ->
                maxArrows[player.uniqueId] = maxArrows[player.uniqueId]!! + shopItem.amount
            }
        // process tracker
        if (purchasedItems.any { it.key == "tracker" }) {
            val tracker = ItemStack.of(Material.COMPASS)
            tracker.editMeta { meta ->
                meta.setEnchantmentGlintOverride(false)
            }
            player.inventory.addItem(tracker)
        }
        // process steal perk
        if (purchasedItems.any { it.key == "steal_perk" }) {
            player.persistentDataContainer.set(
                NamespacedKey(PartyGames.plugin, "steal_perk"),
                PersistentDataType.BOOLEAN,
                true,
            )
        }
        // process heal perk
        if (purchasedItems.any { it.key == "heal_perk" }) {
            player.persistentDataContainer.set(
                NamespacedKey(PartyGames.plugin, "heal_perk"),
                PersistentDataType.BOOLEAN,
                true,
            )
        }
        // process double jump
        if (purchasedItems.any { it.key == "double_jump" }) {
            player.persistentDataContainer.set(
                NamespacedKey(PartyGames.plugin, "double_jump"),
                PersistentDataType.BOOLEAN,
                true,
            )
        }
        // process flint and steel
        if (purchasedItems.any { it.key == "flint_and_steel" }) {
            player.inventory.addItem(ItemStack.of(Material.FLINT_AND_STEEL, 1))
        }
        // process oak planks
        if (purchasedItems.any { it.key == "oak_planks" }) {
            player.inventory.addItem(ItemStack.of(Material.OAK_PLANKS, 64))
        }
    }
}
