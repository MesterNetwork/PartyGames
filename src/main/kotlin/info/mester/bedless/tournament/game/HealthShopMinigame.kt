package info.mester.bedless.tournament.game

import info.mester.bedless.tournament.Tournament
import info.mester.bedless.tournament.admin.SkinType
import info.mester.bedless.tournament.admin.changePlayerSkin
import info.mester.bedless.tournament.admin.spreadPlayers
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import org.bukkit.scoreboard.Team
import java.io.File
import java.util.UUID
import kotlin.math.floor
import kotlin.math.max

enum class HealthShopMinigameState {
    STARTING,
    SHOP,
    FIGHT,
}

private enum class ArmorType {
    LEATHER,
    CHAINMAIL,
    IRON,
    DIAMOND,
    NETHERITTE,
}

class ShopFailedException(
    message: String,
) : Exception(message)

class HealthShopItem(
    private val _item: ItemStack,
    private val _price: Int,
    private val _slot: Int,
    private val _key: String,
    private val _category: String,
    private val _amount: Int = 1,
) {
    val item: ItemStack
        get() = _item
    val price: Int
        get() = _price
    val slot: Int
        get() = _slot
    val key: String
        get() = _key
    val category: String
        get() = _category
    val amount: Int
        get() = _amount

    companion object {
        fun loadFromConfig(
            section: ConfigurationSection,
            key: String,
        ): HealthShopItem {
            val material = Material.matchMaterial(section.getString("id")!!)
            val amount = section.getInt("amount", 1)
            val item = ItemStack.of(material ?: Material.BARRIER, amount)

            item.editMeta { meta ->
                meta.itemName(MiniMessage.miniMessage().deserialize(section.getString("name")!!))
                meta.lore(
                    (section.getList("lore") as List<*>).map {
                        MiniMessage.miniMessage().deserialize(it.toString()).decoration(
                            TextDecoration.ITALIC,
                            false,
                        )
                    } +
                        listOf(
                            Component.empty(),
                            Component
                                .text("Cost: ", NamedTextColor.GRAY)
                                .append(
                                    Component.text(
                                        "${
                                            String.format(
                                                "%.1f",
                                                section.getInt("price") / 2.0,
                                            )
                                        } ♥",
                                        NamedTextColor.RED,
                                    ),
                                ),
                        ),
                )
                meta.setEnchantmentGlintOverride(false)
                HealthShopUI.applyGenericItemMeta(meta)
            }
            // apply healing potion to item
            if (key == "splash_healing_i" || key == "splash_healing_ii") {
                HealthShopUI.setHealthPotion(item, key == "splash_healing_ii")
            }
            // apply regeneration potion to item
            if (key == "regen_potion") {
                HealthShopUI.setRegenPotion(item)
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
            }
        }

        fun setRegenPotion(item: ItemStack) {
            item.editMeta { meta ->
                val potionMeta = meta as PotionMeta
                potionMeta.basePotionType = PotionType.STRONG_REGENERATION
            }
        }

        fun applyGenericItemMeta(itemMeta: ItemMeta) {
            itemMeta.apply {
                isUnbreakable = true
                addItemFlags(ItemFlag.HIDE_UNBREAKABLE)
                addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)
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
                return
            }
        }
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
            meta.itemName(meta.itemName().decorations(decorations))
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
            throw ShopFailedException("not enough hearts")
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
            meta.itemName(meta.itemName().decorations(decorations))
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
                        ItemStack.of(Material.IRON_BOOTS),
                    )

                ArmorType.DIAMOND ->
                    listOf(
                        ItemStack.of(Material.DIAMOND_HELMET),
                        ItemStack.of(Material.DIAMOND_CHESTPLATE),
                        ItemStack.of(Material.DIAMOND_LEGGINGS),
                        ItemStack.of(Material.DIAMOND_BOOTS),
                    )

                ArmorType.NETHERITTE ->
                    listOf(
                        ItemStack.of(Material.NETHERITE_HELMET),
                        ItemStack.of(Material.NETHERITE_CHESTPLATE),
                        ItemStack.of(Material.NETHERITE_LEGGINGS),
                        ItemStack.of(Material.NETHERITE_BOOTS),
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
                if (purchasedItems.any { it.key == "sharpness_i" }) {
                    meta.addEnchant(Enchantment.SHARPNESS, 1, true)
                }
                if (purchasedItems.any { it.key == "sharpness_iii" }) {
                    meta.addEnchant(Enchantment.SHARPNESS, 3, true)
                }
                if (purchasedItems.any { it.key == "sharpness_v" }) {
                    meta.addEnchant(Enchantment.SHARPNESS, 5, true)
                }
                applyGenericItemMeta(meta)
            }
            player.inventory.setItem(0, sword)
        }
        kotlin
            .runCatching {
                purchasedItems.first { it.category == "sword" }.item.type
            }.onSuccess { material ->
                addSword(material)
            }.onFailure {
                addSword(Material.WOODEN_SWORD)
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
        purchasedItems.filter { it.category == "gap" && it.key != "golden_apple_inf" }.forEach { item ->
            val apple = ItemStack.of(Material.GOLDEN_APPLE, item.amount)
            player.inventory.setItem(1, apple)
        }
        // process infinite golden apple
        if (purchasedItems.any { it.key == "golden_apple_inf" }) {
            val apple = ItemStack.of(Material.ENCHANTED_GOLDEN_APPLE)
            apple.editMeta { meta ->
                meta.persistentDataContainer.set(
                    NamespacedKey(Tournament.plugin, "golden_apple_inf"),
                    PersistentDataType.BOOLEAN,
                    true,
                )
            }
            player.inventory.setItem(1, apple)
        }
        // process regeneration potion
        if (purchasedItems.any { it.key == "regen_potion" }) {
            val potion = ItemStack.of(Material.POTION)
            setRegenPotion(potion)
            player.inventory.addItem(potion)
            player.inventory.addItem(potion)
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
                NamespacedKey(Tournament.plugin, "steal_perk"),
                PersistentDataType.BOOLEAN,
                true,
            )
        }
        // process heal perk
        if (purchasedItems.any { it.key == "heal_perk" }) {
            player.persistentDataContainer.set(
                NamespacedKey(Tournament.plugin, "heal_perk"),
                PersistentDataType.BOOLEAN,
                true,
            )
        }
    }
}

class HealthShopMinigame : Minigame() {
    private var shopItems: MutableList<HealthShopItem> = mutableListOf()
    private var _state = HealthShopMinigameState.STARTING
    val state: HealthShopMinigameState
        get() = _state
    private var fightStartedTime = 0L
    private val startingHealth = 60.0
    private val arrowRegenerating = mutableListOf<UUID>()

    // a map that links player UUIDs to their last damage source's UUID and a Long representing the time they were damaged
    private val lastDamageTimes: MutableMap<UUID, Pair<UUID, Long>> = mutableMapOf()

    /**
     * Every shop UI associated with a player
     */
    private val shopUIs: Map<UUID, HealthShopUI>

    private fun regenerateArrowTimer(
        player: Player,
        startTime: Long,
    ): Boolean {
        if (!running) {
            return false
        }
        // check if the player is still alive
        if (!player.isOnline || player.gameMode == GameMode.SPECTATOR) {
            return false
        }
        // calculate the time remaining (3-second delay)
        val timeRemaining = 3 * 1000 - (System.currentTimeMillis() - startTime)
        val secondsRemaining = (timeRemaining / 1000).toInt()
        val totalProgress = timeRemaining / 3000f
        // update the player's experience level and experience bar
        player.level = max(0, secondsRemaining)
        player.exp = max(0f, totalProgress)
        // check if the countdown is over
        if (timeRemaining <= 0) {
            // count the arrows in the player's inventory
            var needsArrows = !player.inventory.contains(Material.ARROW, HealthShopUI.maxArrows(player.uniqueId))
            if (!needsArrows) {
                return false
            }
            // give the player an arrow
            player.inventory.addItem(ItemStack(Material.ARROW, 1))
            needsArrows = !player.inventory.contains(Material.ARROW, HealthShopUI.maxArrows(player.uniqueId))
            // if the player still needs more arrows, start the timer again
            if (needsArrows) {
                Bukkit.getScheduler().runTaskLater(game.plugin, Runnable { regenerateArrow(player) }, 1)
            } else {
                arrowRegenerating.remove(player.uniqueId)
            }
            return false
        }
        return true
    }

    private fun regenerateArrow(player: Player) {
        val startTime = System.currentTimeMillis()
        Bukkit
            .getScheduler()
            .runTaskTimer(game.plugin, { t -> if (!regenerateArrowTimer(player, startTime)) t.cancel() }, 0, 1)
    }

    private fun giveSurvivePoints(player: Player) {
        // for every 15th second the player has survived, give them a point
        val survivedTime = System.currentTimeMillis() - fightStartedTime
        val survivedSeconds = survivedTime / 1000
        val survivedPoints = floor((survivedSeconds / 15).toDouble()).toInt()
        if (survivedPoints > 0) {
            game.playerData(player.uniqueId)!!.score += survivedPoints
            player.sendMessage(
                Component.text(
                    "You have gained $survivedPoints points for surviving for $survivedSeconds seconds!",
                    NamedTextColor.GREEN,
                ),
            )
        }
    }

    fun handleEntityShootBow(event: EntityShootBowEvent) {
        if (_state != HealthShopMinigameState.FIGHT) {
            return
        }
        if (event.entity.type != EntityType.PLAYER) {
            return
        }
        val player = event.entity as Player
        if (arrowRegenerating.contains(player.uniqueId)) {
            return
        }
        // start regenerating arrows
        arrowRegenerating.add(player.uniqueId)
        regenerateArrow(player)
    }

    /**
     * Ran when a player closes the shop UI
     */
    fun handlePlayerCloseUI(uuid: UUID) {
        // wait a tick since this always comes from InventoryCloseEvent
        game.plugin.server.scheduler.runTaskLater(
            game.plugin,
            Runnable {
                if (state != HealthShopMinigameState.SHOP) {
                    return@Runnable
                }
                // check if player is online
                val player = Bukkit.getPlayer(uuid) ?: return@Runnable
                if (!player.isOnline) {
                    return@Runnable
                }
                // open the shop UI
                player.openInventory(shopUIs[uuid]!!.inventory)
            },
            1,
        )
    }

    fun handlePlayerHit(
        damager: Player,
        damagee: Player,
        damage: Double,
    ) {
        if (_state != HealthShopMinigameState.FIGHT) {
            return
        }
        val damagerUUID = damager.uniqueId
        val damageeUUID = damagee.uniqueId

        if (damagee.isBlocking) {
            // nuke the shield
            val shield = damagee.inventory.itemInOffHand.clone()
            damagee.inventory.setItemInOffHand(ItemStack(Material.AIR))
            // after 1.5 seconds, give the shield back
            Bukkit.getScheduler().runTaskLater(
                game.plugin,
                Runnable {
                    if (!running) return@Runnable
                    damagee.inventory.setItemInOffHand(shield)
                },
                (1.5 * 20).toLong(),
            )
        }

        lastDamageTimes[damagerUUID] = Pair(damageeUUID, System.currentTimeMillis())
        // show the damager the damagee's name and health in actionbar
        damager.sendActionBar(
            MiniMessage.miniMessage().deserialize(
                "<green><bold>${damagee.name}<reset> <dark_gray>-<reset> <red>${
                    String.format(
                        max(0.0, floor(damagee.health - damage) / 2).toString(),
                        "%.1f",
                    )
                } ♥",
            ),
        )
    }

    fun handlePlayerDeath(event: PlayerDeathEvent) {
        event.isCancelled = true

        event.deathMessage()?.let {
            Bukkit.broadcast(it)
        }
        // put the player in spectator
        val killedPlayer = event.entity
        killedPlayer.gameMode = GameMode.SPECTATOR
        // give survive points
        giveSurvivePoints(event.entity)
        // check if the player died from a player damage
        @Suppress("UnstableApiUsage")
        val killerPlayer = event.damageSource.causingEntity
        // handle assist
        lastDamageTimes[event.entity.uniqueId]?.let { (lastDamagerUUID, lastDamageTime) ->
            // check if that player was the last damager
            if (killerPlayer != null && lastDamagerUUID == killerPlayer.uniqueId) {
                return@let
            }
            // check if 30 seconds have passed since the last damage
            if (System.currentTimeMillis() - lastDamageTime > 30000) {
                return@let
            }
            val assistPlayer = Bukkit.getPlayer(lastDamagerUUID) ?: return@let
            game.playerData(assistPlayer.uniqueId)!!.score += 3
            assistPlayer.sendMessage(
                Component.text(
                    "You have gained 3 points for assisting ${event.entity.name}!",
                    NamedTextColor.GREEN,
                ),
            )
            assistPlayer.playSound(
                Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 1.0f, 1.0f),
                Sound.Emitter.self(),
            )
        }
        // handle player kill
        if (killerPlayer is Player) {
            game.playerData(killerPlayer.uniqueId)!!.score += 5
            killerPlayer.sendMessage(
                Component.text(
                    "You have gained 5 points for killing ${event.entity.name}!",
                    NamedTextColor.GREEN,
                ),
            )
            killerPlayer.playSound(
                Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 1.0f, 1.0f),
                Sound.Emitter.self(),
            )
            // check if the player has the steal perk
            if (killerPlayer.persistentDataContainer.get(
                    NamespacedKey(Tournament.plugin, "steal_perk"),
                    PersistentDataType.BOOLEAN,
                ) == true
            ) {
                // copy the inventory and health of the killed player
                killerPlayer.inventory.clear()
                for (i in 0..40) {
                    killedPlayer.inventory.getItem(i)?.let { item ->
                        killerPlayer.inventory.setItem(i, item)
                    }
                }
                killerPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH)!!.baseValue =
                    killedPlayer
                        .getAttribute(
                            Attribute.GENERIC_MAX_HEALTH,
                        )!!
                        .baseValue
                killerPlayer.health = killerPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH)!!.baseValue
            }
            // check if the player has the heal perk
            if (killerPlayer.persistentDataContainer.get(
                    NamespacedKey(Tournament.plugin, "heal_perk"),
                    PersistentDataType.BOOLEAN,
                ) == true
            ) {
                killerPlayer.health = killerPlayer.getAttribute(Attribute.GENERIC_MAX_HEALTH)!!.baseValue
            }
        }
        // check if we only have one alive player left
        if (game.players().filter { it.gameMode == GameMode.SURVIVAL }.size <= 1) {
            end()
        }
    }

    fun handleEntityRegainHealth(event: EntityRegainHealthEvent) {
        if (event.entity.type != EntityType.PLAYER) {
            return
        }
        // don't let players during the shop state regain health via saturation
        if (state == HealthShopMinigameState.SHOP &&
            event.regainReason == EntityRegainHealthEvent.RegainReason.SATIATED
        ) {
            event.isCancelled = true
            return
        }
    }

    fun handlePlayerItemConsume(event: PlayerItemConsumeEvent) {
        if (_state != HealthShopMinigameState.FIGHT) {
            return
        }
        val item = event.item
        // check if the item has a special golden_apple_inf PDC
        if (item.itemMeta.persistentDataContainer.get(
                NamespacedKey(Tournament.plugin, "golden_apple_inf"),
                PersistentDataType.BOOLEAN,
            ) == true
        ) {
            event.isCancelled = true
            // since we cancelled the event, we need to manually give the effects
            // add absoprtion I for 2 minutes and regeneration II for 5 seconds
            event.player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 2 * 60 * 20, 0))
            event.player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 1))
            // add a cooldown to the enchanted golden apple
            event.player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, 20 * 20)
        }
        if (item.type == Material.POTION) {
            event.replacement = ItemStack(Material.AIR)
        }
    }

    fun handlePlayerInteract(event: PlayerInteractEvent) {
        if (_state != HealthShopMinigameState.FIGHT) {
            return
        }
        // check if item is the tracker
        val item = event.item
        if (item?.type == Material.COMPASS) {
            val player = event.player
            if (player.hasCooldown(Material.COMPASS)) {
                return
            }
            event.setUseInteractedBlock(Event.Result.DENY)
            event.setUseItemInHand(Event.Result.DENY)
            // get the nearest player
            val nearestPlayer =
                Bukkit
                    .getOnlinePlayers()
                    .filter { it.gameMode == GameMode.SURVIVAL && !Tournament.game.isAdmin(it) && it.uniqueId != player.uniqueId }
                    .minByOrNull { it.location.distance(player.location) }
            if (nearestPlayer == null) {
                player.sendMessage(
                    Component.text(
                        "Nobody to track! (this is definitely a bug lol)",
                        NamedTextColor.RED,
                    ),
                )
                return
            }
            player.setCooldown(Material.COMPASS, 5 * 20)
            nearestPlayer.sendMessage(Component.text("You have been tracked!", NamedTextColor.GREEN))
            // set the compass' direction to the nearest player's location
            item.editMeta { meta ->
                val compassMeta = meta as CompassMeta
                compassMeta.lodestone = nearestPlayer.location
                compassMeta.isLodestoneTracked = false
            }
        }
    }

    init {
        _startPos = game.plugin.config.getLocation("locations.minigames.health-shop")!!
        // load shop items by obtaining the config and reading every key inside "items" of "health-shop.yml"
        val config = YamlConfiguration.loadConfiguration(File(game.plugin.dataFolder, "health-shop.yml"))
        config.getConfigurationSection("items")?.getKeys(false)?.forEach { key ->
            game.plugin.logger.info("Loading shop item $key")
            val shopItem = HealthShopItem.loadFromConfig(config.getConfigurationSection("items.$key")!!, key)
            shopItems.add(shopItem)
        }
        shopUIs = game.players().map { it.uniqueId }.associateWith { HealthShopUI(it, shopItems, startingHealth) }
    }

    override fun start() {
        _state = HealthShopMinigameState.SHOP
        for (player in game.players()) {
            // open the shop UI for all players
            player.openInventory(shopUIs[player.uniqueId]!!.inventory)
            // update their max health
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = startingHealth
            player.health = startingHealth
            // reset perks
            player.persistentDataContainer.set(
                NamespacedKey(Tournament.plugin, "steal_perk"),
                PersistentDataType.BOOLEAN,
                false,
            )
            player.persistentDataContainer.set(
                NamespacedKey(Tournament.plugin, "heal_perk"),
                PersistentDataType.BOOLEAN,
                false,
            )
        }
        // start a 45-second countdown for the shop state
        startCountdown(45000) {
            startFight()
        }

        super.start()
    }

    private fun startFight() {
        _state = HealthShopMinigameState.FIGHT

        fightStartedTime = System.currentTimeMillis()

        for (player in game.players()) {
            // close the shop UI
            player.closeInventory()
            // cap the max health at the current health
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = player.health
            // time to give the items! :)
            player.inventory.clear()
            shopUIs[player.uniqueId]!!.giveItems()
            // give the actual arrow items based on maxArrows
            val maxArrows = HealthShopUI.maxArrows(player.uniqueId)
            if (maxArrows > 0) {
                player.inventory.setItem(8, ItemStack(Material.ARROW, maxArrows))
            }
            // hide their nametag
            val board = Bukkit.getScoreboardManager().mainScoreboard
            val team = board.getTeam("hide-nametag") ?: board.registerNewTeam("hide-nametag")
            // admins should still see nametags
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM)
            team.addEntity(player)
            // hide the skins (this is ugly)
            changePlayerSkin(player, SkinType.STEVE)
        }
        // custom spreadplayers implementation: spread the players around start pos
        // in a 100 block radius
        // TODO: set radius to 100
        spreadPlayers(game.players(), startPos, 50)
        // start a 5-minute countdown for the fight
        startCountdown(300000) {
            end()
        }
    }

    override fun end(nextGame: Boolean) {
        // start an async task to reset everyone's skin
        Bukkit.getAsyncScheduler().runNow(
            Tournament.plugin,
        ) {
            for (player in game.players()) {
                changePlayerSkin(player, SkinType.OWN)
            }
        }
        super.end(nextGame)
    }

    override fun end() {
        // give every alive player survive points
        for (player in game.players().filter { it.gameMode == GameMode.SURVIVAL }) {
            giveSurvivePoints(player)
        }
        super.end()
    }

    override val name: Component
        get() = Component.text("Health shop", NamedTextColor.AQUA)
    override val description: Component
        get() =
            Component.text(
                "You have 45 seconds to buy various weapons, armors and upgrades... with your own health!\nMake the decision between having the strongest sword or the best defense.\nAfter the time runs out, you'll use your purchased items to fight in a free for all battleground.",
                NamedTextColor.AQUA,
            )
}
