package info.mester.bedless.tournament.game

import info.mester.bedless.tournament.Tournament
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
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.io.File
import java.util.UUID
import kotlin.math.floor

enum class HealthShopMinigameState {
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

class NotEoughMoneyException(
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

            item.itemMeta =
                item.itemMeta.apply {
                    itemName(MiniMessage.miniMessage().deserialize(section.getString("name")!!))
                    lore(
                        (section.getList("lore") as List<*>).map {
                            MiniMessage.miniMessage().deserialize(it.toString()).decoration(
                                TextDecoration.ITALIC,
                                false,
                            )
                        },
                    )
                    setEnchantmentGlintOverride(false)
                }

            return HealthShopItem(
                item,
                section.getInt("price"),
                section.getInt("slot"),
                key,
                section.getString("category") ?: "default",
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
    private val inventory = Bukkit.createInventory(this, 27, Component.text("Health Shop"))
    private val purchasedItems: MutableList<HealthShopItem> = mutableListOf()
    private val player = Bukkit.getPlayer(playerUUID)!!

    init {
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
            } catch (e: NotEoughMoneyException) {
                event.whoClicked.sendMessage(
                    Component.text(
                        "You do not have enough hearts to purchase this item!",
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

        inventoryItem.itemMeta =
            inventoryItem.itemMeta.apply {
                // remove the enchantment glint from the item in the ui
                setEnchantmentGlintOverride(false)
                // remove underlined and bold from name
                val decorations =
                    mapOf(
                        TextDecoration.UNDERLINED to TextDecoration.State.FALSE,
                        TextDecoration.BOLD to TextDecoration.State.FALSE,
                    )
                itemName(itemName().decorations(decorations))
            }

        player.health = money
    }

    private fun addItem(
        shopItem: HealthShopItem,
        inventoryItem: ItemStack,
    ) {
        val sameCategory = purchasedItems.filter { it.category == shopItem.category }
        // calculate how much money we'd have if we removed all the items in the same category
        val moneyToAdd = sameCategory.sumOf { it.price }
        // check if we have enough money
        if ((money + moneyToAdd) <= shopItem.price) {
            throw NotEoughMoneyException("not enough hearts")
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

        inventoryItem.itemMeta =
            inventoryItem.itemMeta.apply {
                // add an enchantment glint to the item in the ui
                setEnchantmentGlintOverride(true)
                // make name underlined and bold
                val decorations =
                    mapOf(
                        TextDecoration.UNDERLINED to TextDecoration.State.TRUE,
                        TextDecoration.BOLD to TextDecoration.State.TRUE,
                    )
                itemName(itemName().decorations(decorations))
            }

        player.health = money
    }

    fun giveItems() {
        val applyGenericItemMeta = { itemMeta: ItemMeta ->
            itemMeta.apply {
                isUnbreakable = true
                addItemFlags(ItemFlag.HIDE_UNBREAKABLE)
            }
        }
        // process sword
        val addSword = { material: Material ->
            val sword = ItemStack.of(material)
            sword.itemMeta =
                sword.itemMeta.apply {
                    if (purchasedItems.any { it.key == "sharpness_i" }) {
                        addEnchant(Enchantment.SHARPNESS, 1, true)
                    }
                    if (purchasedItems.any { it.key == "sharpness_ii" }) {
                        addEnchant(Enchantment.SHARPNESS, 2, true)
                    }
                    applyGenericItemMeta(this)
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
            shield.itemMeta =
                shield.itemMeta.apply {
                    applyGenericItemMeta(this)
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
            apple.itemMeta =
                apple.itemMeta.apply {
                    addItemFlags(ItemFlag.HIDE_ENCHANTS)
                    persistentDataContainer.set(
                        NamespacedKey(Tournament.plugin, "golden_apple_inf"),
                        PersistentDataType.BOOLEAN,
                        true,
                    )
                }
            player.inventory.setItem(1, apple)
        }
        // process armor
        val addArmor = { armor: ArmorType ->
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
                armorItem.itemMeta =
                    armorItem.itemMeta.apply {
                        applyGenericItemMeta(this)
                        if (purchasedItems.any { it.key == "protection_i" }) {
                            addEnchant(Enchantment.PROTECTION, 1, true)
                        }
                        if (purchasedItems.any { it.key == "protection_ii" }) {
                            addEnchant(Enchantment.PROTECTION, 2, true)
                        }
                    }
            }
            player.inventory.setItem(EquipmentSlot.HEAD, armorItems[0])
            player.inventory.setItem(EquipmentSlot.CHEST, armorItems[1])
            player.inventory.setItem(EquipmentSlot.LEGS, armorItems[2])
            player.inventory.setItem(EquipmentSlot.FEET, armorItems[3])
        }
        kotlin
            .runCatching {
                purchasedItems.first { it.category == "armor" }
            }.onSuccess { shopItem ->
                when (shopItem.key) {
                    "chainmail_armor" -> addArmor(ArmorType.CHAINMAIL)
                    "iron_armor" -> addArmor(ArmorType.IRON)
                    "diamond_armor" -> addArmor(ArmorType.DIAMOND)
                    "netherite_armor" -> addArmor(ArmorType.NETHERITTE)
                }
            }.onFailure {
                addArmor(ArmorType.LEATHER)
            }
    }
}

class HealthShopMinigame : Minigame() {
    private var shopItems: MutableList<HealthShopItem> = mutableListOf()
    private var _state = HealthShopMinigameState.SHOP
    val state: HealthShopMinigameState
        get() = _state
    private var fightStartedTime = 0L
    private val startingHealth = 60.0

    // a map that links player UUIDs to their last damage source's UUID and a Long representing the time they were damaged
    private val lastDamageTimes: MutableMap<UUID, Pair<UUID, Long>> = mutableMapOf()

    /**
     * Every shop UI associated with a player
     */
    private val shopUIs: Map<UUID, HealthShopUI>

    init {
        startPos = game.plugin.config.getLocation("locations.minigames.health-shop")!!
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
        for (player in game.players()) {
            // open the shop UI for all players
            player.openInventory(shopUIs[player.uniqueId]!!.inventory)
            // update their max health
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = startingHealth
            player.health = startingHealth
        }
        // start a 1-minute countdown for the shop state
        startCountdown(60000) {
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
            shopUIs[player.uniqueId]!!.giveItems()
        }
        // custom spreadplayers implementation: spread the players around start pos
        // in a 50 block radius
        spreadPlayers(game.players(), startPos, 50)
        // start a 5-minute countdown for the fight
        startCountdown(300000) {
            end()
        }
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
    ) {
        val damagerUUID = damager.uniqueId
        val damageeUUID = damagee.uniqueId

        lastDamageTimes[damagerUUID] = Pair(damageeUUID, System.currentTimeMillis())
    }

    fun handlePlayerDeath(event: PlayerDeathEvent) {
        event.isCancelled = true

        event.deathMessage()?.let {
            Bukkit.broadcast(it)
        }
        // put the player in spectator
        val player = event.entity
        player.gameMode = GameMode.SPECTATOR
        // give survive points
        giveSurvivePoints(event.entity)
        // check if the player died from a player damage
        @Suppress("UnstableApiUsage")
        val causingEntity = event.damageSource.causingEntity
        // handle assist
        lastDamageTimes[event.entity.uniqueId]?.let { (lastDamagerUUID, lastDamageTime) ->
            // check if that player was the last damager
            if (causingEntity != null && lastDamagerUUID == causingEntity.uniqueId) {
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
        if (causingEntity is Player) {
            game.playerData(causingEntity.uniqueId)!!.score += 5
            causingEntity.sendMessage(
                Component.text(
                    "You have gained 5 points for killing ${event.entity.name}!",
                    NamedTextColor.GREEN,
                ),
            )
            causingEntity.playSound(
                Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 1.0f, 1.0f),
                Sound.Emitter.self(),
            )
        }
        // check if we only have one alive player left
        if (game.players().filter { it.gameMode == GameMode.SURVIVAL }.size <= 1) {
            end()
        }
    }

    fun handlePlayerConsumeItem(event: PlayerItemConsumeEvent) {
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
                "You have a minute to buy various weapons, armors and upgrades... with your own health!\nMake the decision between having the strongest sword or the best defense.\nAfter the time runs out, you'll use your purchased items to fight in a free for all battleground.",
                NamedTextColor.AQUA,
            )
}
