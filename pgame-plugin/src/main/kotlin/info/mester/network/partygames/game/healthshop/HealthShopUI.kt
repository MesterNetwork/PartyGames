package info.mester.network.partygames.game.healthshop

import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.api.createBasicItem
import info.mester.network.partygames.game.HealthShopMinigame
import info.mester.network.partygames.game.ShopFailedException
import info.mester.network.partygames.mm
import info.mester.network.partygames.toRomanNumeral
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.UseCooldown
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.translation.GlobalTranslator
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
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import java.util.Locale
import java.util.UUID

class HealthShopUI(
    private val playerUUID: UUID,
    private var money: Double,
    private var category: HealthShopItemCategory = HealthShopItemCategory.COMBAT,
) : InventoryHolder {
    private val inventory = Bukkit.createInventory(this, 5 * 9, Component.text("Health Shop"))
    private val purchasedItems: MutableList<HealthShopItem> = mutableListOf()
    private val databaseManager = PartyGames.plugin.databaseManager
    private val kits = databaseManager.getHealthShopKits(playerUUID).toMutableList()

    /**
     * Get the player
     */
    private val player get() = Bukkit.getPlayer(playerUUID)!!
    val playerData = HealthShopPlayerData()

    companion object {
        private val shopItems get() = HealthShopMinigame.getShopItems()
        private val INF_GAP_COOLDOWN_KEY = NamespacedKey(PartyGames.plugin, "inf_gap_cooldown")

        private fun setCustomPotion(
            item: ItemStack,
            potionEffects: List<PotionEffect>,
            color: Color,
            potionName: String?,
            showExtraData: Boolean = true,
        ) {
            item.editMeta(PotionMeta::class.java) { meta ->
                applyGenericItemMeta(meta)

                potionEffects.forEach { potionEffect ->
                    meta.addCustomEffect(potionEffect, true)
                }
                meta.color = color

                val duration = potionEffects.minBy { it.duration }.duration / 20
                val minutes = duration / 60
                val seconds = String.format("%02d", duration % 60)

                if (potionName != null) {
                    val name =
                        MiniMessage
                            .miniMessage()
                            .deserialize(
                                buildString {
                                    append("<!i><green>$potionName ")
                                    if (showExtraData) {
                                        append(
                                            "<yellow>${(potionEffects[0].amplifier + 1).toRomanNumeral()} <gray>(<yellow>$minutes<gray>:<yellow>$seconds<gray>)",
                                        )
                                    }
                                },
                            )
                    meta.displayName(name)
                }

                // if we have a composite potion, display all the effects
                if (potionEffects.size > 1) {
                    val lore = mutableListOf<Component>()
                    for (effect in potionEffects) {
                        val name =
                            GlobalTranslator.render(
                                Component.translatable(
                                    effect.type.translationKey(),
                                    Style
                                        .style(
                                            NamedTextColor.BLUE,
                                        ).decoration(TextDecoration.ITALIC, false),
                                ),
                                Locale.US,
                            )
                        val duration = effect.duration / 20
                        val minutes = duration / 60
                        val seconds = String.format("%02d", duration % 60)

                        val durationData =
                            mm.deserialize(
                                " <yellow>${(effect.amplifier + 1).toRomanNumeral()} <gray>(<yellow>$minutes<gray>:<yellow>$seconds<gray>)",
                            )
                        lore.add(name.append(durationData))
                    }
                    val currentLore = meta.lore() ?: mutableListOf()
                    if (currentLore.isNotEmpty()) {
                        lore.add(Component.empty())
                    }
                    lore.addAll(currentLore)
                    meta.lore(lore)
                }
            }
        }

        private fun setCustomPotion(
            item: ItemStack,
            potionEffect: PotionEffect,
            color: Color,
            potionName: String?,
        ) = setCustomPotion(
            item,
            listOf(potionEffect),
            color,
            potionName,
        )

        fun setHealthPotion(
            item: ItemStack,
            strong: Boolean,
        ) {
            item.editMeta(PotionMeta::class.java) { meta ->
                meta.basePotionType = if (strong) PotionType.STRONG_HEALING else PotionType.HEALING
                applyGenericItemMeta(meta)
            }
        }

        fun setRegen2Potion(item: ItemStack) {
            item.editMeta(PotionMeta::class.java) { meta ->
                applyGenericItemMeta(meta)
                meta.basePotionType = PotionType.STRONG_REGENERATION
            }
        }

        fun setTurtleMasterPotion(
            item: ItemStack,
            long: Boolean,
            strong: Boolean,
            withName: Boolean = true,
        ) {
            val name =
                when {
                    !withName -> null
                    !long && !strong -> "Turtle Master"
                    long -> "Long Turtle Master"
                    else -> "Strong Turtle Master"
                }
            return setCustomPotion(
                item,
                listOf(
                    PotionEffect(PotionEffectType.SLOWNESS, 20 * if (long) 40 else 20, if (strong) 5 else 3, false),
                    PotionEffect(PotionEffectType.RESISTANCE, 20 * if (long) 40 else 20, if (strong) 3 else 2, false),
                ),
                PotionEffectType.RESISTANCE.color,
                name,
            )
        }

        fun setLevitationPotion(
            item: ItemStack,
            level: Int,
            withName: Boolean = true,
        ) = setCustomPotion(
            item,
            PotionEffect(PotionEffectType.LEVITATION, 5 * 20, level, false),
            PotionEffectType.LEVITATION.color,
            if (withName) "Levitation" else null,
        )

        fun setBlindnessPotion(
            item: ItemStack,
            withName: Boolean = true,
        ) = setCustomPotion(
            item,
            PotionEffect(PotionEffectType.BLINDNESS, 10 * 20, 0, false),
            PotionEffectType.BLINDNESS.color,
            if (withName) "Blindness" else null,
        )

        fun setPoisonPotion(
            item: ItemStack,
            level: Int,
            withName: Boolean = true,
        ) = setCustomPotion(
            item,
            PotionEffect(PotionEffectType.POISON, 20 * 20, level, false),
            PotionEffectType.POISON.color,
            if (withName) "Poison" else null,
        )

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
            PotionEffect(PotionEffectType.SPEED, 20 * 20, 1, false),
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
        renderInventory()
    }

    override fun getInventory(): Inventory = inventory

    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked
        val slot = event.slot

        // check if we clicked on a page selector
        if (slot in 27..35) {
            when (slot) {
                29 -> category = HealthShopItemCategory.COMBAT
                30 -> category = HealthShopItemCategory.UTILITY
                32 -> category = HealthShopItemCategory.POTION
                33 -> category = HealthShopItemCategory.MISCELLANEOUS
            }
            renderInventory()
            return
        }

        // check if we clicked on a kit item
        if (slot >= 36) {
            val kitIndex = slot - 36
            val kit = kits.firstOrNull { it.index == kitIndex }

            if (!player.hasPermission("partygames.healthshop.kit.$kitIndex")) {
                player.sendMessage(
                    mm.deserialize("<red>You do not have permission to use this kit!"),
                )
                player.playSound(
                    Sound.sound(Key.key("entity.villager.no"), Sound.Source.MASTER, 1.0f, 1.0f),
                    Sound.Emitter.self(),
                )
                return
            }

            if (kit == null &&
                purchasedItems.isNotEmpty() &&
                // don't save last used kit (index 8)
                kitIndex != 8
            ) {
                // save the kit
                val kit = HealthShopKit(purchasedItems.toList(), kitIndex)
                kits.add(kit)
                databaseManager.saveHealthShopKit(playerUUID, kit)
                renderKits()
            } else if (kit != null) {
                if (event.click.isRightClick && kitIndex != 8) {
                    // delete the kit
                    kits.remove(kit)
                    databaseManager.deleteHealthShopKit(playerUUID, kitIndex)
                    renderKits()
                    return
                }
                // load the kit
                val currentItems = purchasedItems.toList()
                for (item in currentItems) {
                    removeItem(item)
                }
                for (item in kit.items) {
                    addItem(item)
                }
            }
        }

        val shopItem =
            shopItems.firstOrNull { it.slot == slot && it.category == category }
                ?: return // if the item is not found, do nothing

        // toggle purchased state
        if (purchasedItems.contains(shopItem)) {
            removeItem(shopItem)
        } else {
            try {
                addItem(shopItem)
            } catch (e: ShopFailedException) {
                val message =
                    when (e.message) {
                        "no_healh" -> "You do not have enough hearts to purchase this item!"
                        "no_bow" -> "You must first buy a bow before purchasing this item!"
                        else -> "An error occurred while trying to purchase this item!"
                    }
                player.sendMessage(
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

    private fun renderInventory() {
        inventory.clear()
        for (item in shopItems.filter { it.category == category }) {
            inventory.setItem(
                item.slot,
                item.item.clone().apply {
                    editMeta { meta ->
                        if (purchasedItems.contains(item)) {
                            meta.setEnchantmentGlintOverride(true)
                            val decorations =
                                mapOf(
                                    TextDecoration.UNDERLINED to TextDecoration.State.TRUE,
                                    TextDecoration.BOLD to TextDecoration.State.TRUE,
                                )
                            meta.displayName((meta.displayName() ?: meta.itemName()).decorations(decorations))
                        }
                    }
                },
            )
        }

        // set up page selector
        repeat(9) { i ->
            val inventoryIndex = 27 + i
            val item =
                createBasicItem(Material.GRAY_STAINED_GLASS_PANE, "").apply {
                    editMeta { meta ->
                        meta.isHideTooltip = true
                    }
                }
            inventory.setItem(inventoryIndex, item)
        }
        for (category in HealthShopItemCategory.entries) {
            val inventoryIndex =
                when (category) {
                    HealthShopItemCategory.COMBAT -> 29
                    HealthShopItemCategory.UTILITY -> 30
                    HealthShopItemCategory.POTION -> 32
                    HealthShopItemCategory.MISCELLANEOUS -> 33
                }
            val item =
                createBasicItem(
                    category.displayItem,
                    "<green>${category.name.lowercase().replaceFirstChar { it.uppercase() }}",
                )
            item.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES)
            inventory.setItem(inventoryIndex, item)
        }

        renderKits()
    }

    private fun renderKits() {
        repeat(9) { i ->
            val inventoryIndex = 36 + i
            if (i != 8 && !player.hasPermission("partygames.healthshop.kit.$i")) {
                val noPerms =
                    createBasicItem(
                        Material.BARRIER,
                        "<red>Locked kit",
                        1,
                        "<gray>You do not have permission to use this kit!",
                    )
                inventory.setItem(inventoryIndex, noPerms)
                return@repeat
            }

            val kit = kits.firstOrNull { it.index == i }
            if (kit == null) {
                val emptyKit =
                    createBasicItem(
                        Material.PAPER,
                        "<red>Empty Kit",
                        1,
                        if (i == 8) "<gray>Your last used kit will show up here" else "<gray>Click to save your current items as a kit!",
                    ).apply {
                        addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
                    }
                inventory.setItem(inventoryIndex, emptyKit)
            } else {
                inventory.setItem(inventoryIndex, kit.getDisplayItem())
            }
        }
    }

    private fun removeItem(shopItem: HealthShopItem) {
        if (!purchasedItems.remove(shopItem)) {
            return
        }
        money += shopItem.price

        renderInventory()

        // special case: if we remove a bow, remove all arrows
        if (shopItem.key == "bow") {
            val arrowItem = purchasedItems.firstOrNull { it.group == "arrow" }
            if (arrowItem != null) {
                removeItem(arrowItem)
            }
        }

        player.health = money
    }

    private fun addItem(shopItem: HealthShopItem) {
        val sameCategory = purchasedItems.filter { it.group != "none" && it.group == shopItem.group }
        // calculate how much money we'd have if we removed all the items in the same category
        val moneyToAdd = sameCategory.sumOf { it.price }
        // check if we have enough money
        if ((money + moneyToAdd) <= shopItem.price) {
            throw ShopFailedException("no_health")
        }
        // check if we're trying to buy an arrow
        if (shopItem.group == "arrow") {
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
        sameCategory.forEach { removeItem(it) }

        renderInventory()

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
                if (purchasedItems.any { it.key == "protection_iii" }) {
                    meta.addEnchant(Enchantment.PROTECTION, 3, true)
                }
                if (purchasedItems.any { it.key == "protection_iv" }) {
                    meta.addEnchant(Enchantment.PROTECTION, 4, true)
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
                        val sharpness = sharpnessItem.amount
                        meta.addEnchant(Enchantment.SHARPNESS, sharpness, true)
                    }
                }
                applyGenericItemMeta(meta)
            }
            player.inventory.addItem(sword)
        }
        kotlin
            .runCatching {
                purchasedItems.first { it.group == "sword" }.item.type
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
        // process armor
        kotlin
            .runCatching {
                purchasedItems.first { it.group == "armor" }
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
        }
        // process arrows
        kotlin
            .runCatching {
                purchasedItems.first { it.group == "arrow" }
            }.onSuccess { shopItem ->
                // 1 free arrow is included with the bow (which you need to buy an arrow)
                playerData.maxArrows = shopItem.amount + 1
            }

        // process golden apples
        purchasedItems.filter { it.group == "gap" }.forEach { item ->
            val apple = ItemStack.of(Material.GOLDEN_APPLE, item.amount)
            @Suppress("UnstableApiUsage")
            if (item.key == "golden_apple_inf") {
                // use the cooldown component for infinite golden apples
                val cooldown = UseCooldown.useCooldown(10f).cooldownGroup(INF_GAP_COOLDOWN_KEY)
                apple.setData(DataComponentTypes.USE_COOLDOWN, cooldown)
            }
            player.inventory.addItem(apple)
        }
        if (purchasedItems.any { it.key == "enchanted_golden_apple" }) {
            val apple = ItemStack.of(Material.ENCHANTED_GOLDEN_APPLE, 1)
            player.inventory.addItem(apple)
        }
        // process flint and steel
        if (purchasedItems.any { it.key == "flint_and_steel" }) {
            player.inventory.addItem(ItemStack.of(Material.FLINT_AND_STEEL, 1))
        }
        // process oak planks
        val oakPlanks = purchasedItems.firstOrNull { it.key == "oak_planks" }
        if (oakPlanks != null) {
            player.inventory.addItem(ItemStack.of(Material.OAK_PLANKS, oakPlanks.amount))
        }
        // process fishing rod
        if (purchasedItems.any { it.key == "fishing_rod" }) {
            val fishingRod = ItemStack.of(Material.FISHING_ROD)
            fishingRod.editMeta { meta ->
                applyGenericItemMeta(meta)
            }
            player.inventory.addItem(fishingRod)
        }
        // process fireballs
        val fireballItem = purchasedItems.firstOrNull { it.group == "fireball" }
        if (fireballItem != null) {
            val fireball = ItemStack.of(Material.FIRE_CHARGE, fireballItem.amount)
            fireball.editMeta { meta ->
                applyGenericItemMeta(meta)
            }
            player.inventory.addItem(fireball)
        }
        // process tnt
        val tntItem = purchasedItems.firstOrNull { it.group == "tnt" }
        if (tntItem != null) {
            val tnt = ItemStack.of(Material.TNT, tntItem.amount)
            tnt.editMeta { meta ->
                applyGenericItemMeta(meta)
            }
            player.inventory.addItem(tnt)
        }
        // process totem of undying
        if (purchasedItems.any { it.key == "totem_of_undying" }) {
            val totem = ItemStack.of(Material.TOTEM_OF_UNDYING)
            totem.editMeta { meta ->
                applyGenericItemMeta(meta)
            }
            player.inventory.setItem(EquipmentSlot.OFF_HAND, totem)
        }
        // process ender pearls
        val enderPearlItem = purchasedItems.firstOrNull { it.group == "ender_pearl" }
        if (enderPearlItem != null) {
            val enderPearl = ItemStack.of(Material.ENDER_PEARL, enderPearlItem.amount)
            enderPearl.editMeta { meta ->
                applyGenericItemMeta(meta)
            }
            player.inventory.addItem(enderPearl)
        }
        // process snow balls
        if (purchasedItems.any { it.key == "snowball" }) {
            val snowBall = ItemStack.of(Material.SNOWBALL, 16)
            snowBall.editMeta { meta ->
                applyGenericItemMeta(meta)
            }
            player.inventory.addItem(snowBall)
        }
        // process water bucket
        if (purchasedItems.any { it.key == "water_bucket" }) {
            val waterBucket = ItemStack.of(Material.WATER_BUCKET, 1)
            waterBucket.editMeta { meta ->
                applyGenericItemMeta(meta)
            }
            player.inventory.addItem(waterBucket)
        }

        // process healing potions
        for (purchasedPotion in purchasedItems.filter { it.key == "splash_healing" || it.key == "splash_healing_ii" }) {
            val potion = ItemStack.of(Material.SPLASH_POTION, purchasedPotion.amount)
            setHealthPotion(potion, purchasedPotion.key == "splash_healing")
            player.inventory.addItem(potion)
        }
        // process regeneration potions
        purchasedItems.firstOrNull { it.group == "regen_ii" }?.let { shopItem ->
            val potion = ItemStack.of(Material.POTION)
            setRegen2Potion(potion)
            repeat(shopItem.amount) {
                player.inventory.addItem(potion)
            }
        }
        purchasedItems.firstOrNull { it.key == "regen_v" }?.let { shopItem ->
            val potion = ItemStack.of(Material.POTION)
            setRegenPotion(potion)
            repeat(shopItem.amount) {
                player.inventory.addItem(potion)
            }
        }
        // process speed potion
        purchasedItems.firstOrNull { it.group == "speed_ii" }?.let { shopItem ->
            val potion = ItemStack.of(Material.POTION)
            setSpeedPotion(potion)
            repeat(shopItem.amount) {
                player.inventory.addItem(potion)
            }
        }
        // process jump potion
        purchasedItems.firstOrNull { it.group == "jump_boost" }?.let { shopItem ->
            val potion = ItemStack.of(Material.POTION)
            setJumpPotion(potion)
            repeat(shopItem.amount) {
                player.inventory.addItem(potion)
            }
        }
        // process turtle master potion
        purchasedItems.firstOrNull { it.group == "turtle_master" }?.let { shopItem ->
            val potion = ItemStack.of(Material.POTION)
            val long = shopItem.key.endsWith("_long")
            val strong = shopItem.key.endsWith("_strong")
            setTurtleMasterPotion(potion, long, strong)
            repeat(shopItem.amount) {
                player.inventory.addItem(potion)
            }
        }
        // process poison potion
        purchasedItems.firstOrNull { it.group == "poison" }?.let { shopItem ->
            val potion = ItemStack.of(Material.SPLASH_POTION)
            setPoisonPotion(potion, if (shopItem.key == "poison_ii") 1 else 0)
            repeat(shopItem.amount) {
                player.inventory.addItem(potion)
            }
        }
        // process blindness potion
        purchasedItems.firstOrNull { it.group == "poison" }?.let { shopItem ->
            val potion = ItemStack.of(Material.SPLASH_POTION)
            setBlindnessPotion(potion)
            repeat(shopItem.amount) {
                player.inventory.addItem(potion)
            }
        }
        // process levitation potion
        purchasedItems.firstOrNull { it.group == "levitation" }?.let { shopItem ->
            val potion = ItemStack.of(Material.SPLASH_POTION)
            setLevitationPotion(potion, if (shopItem.key == "levitation_ii") 1 else 0)
            repeat(shopItem.amount) {
                player.inventory.addItem(potion)
            }
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
            playerData.stealPerk = true
        }
        // process heal perk
        if (purchasedItems.any { it.key == "heal_perk" }) {
            playerData.healPerk = true
        }
        // process double jump
        if (purchasedItems.any { it.key == "double_jump" }) {
            playerData.doubleJump = true
        }
        // process feather fall
        if (purchasedItems.any { it.key == "feather_fall" }) {
            playerData.featherFall = true
        }

        // save this kit (index 8 is the last used kit)
        if (purchasedItems.isEmpty()) {
            return
        }
        val kit = HealthShopKit(purchasedItems.toList(), 8)
        databaseManager.saveHealthShopKit(playerUUID, kit)
    }
}
