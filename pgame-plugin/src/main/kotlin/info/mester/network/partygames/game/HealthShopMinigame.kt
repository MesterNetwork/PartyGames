package info.mester.network.partygames.game

import com.destroystokyo.paper.ParticleBuilder
import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.game.healthshop.HealthShopItem
import info.mester.network.partygames.game.healthshop.HealthShopUI
import info.mester.network.partygames.game.healthshop.SupplyChestTimer
import info.mester.network.partygames.mm
import info.mester.network.partygames.util.WeightedItem
import info.mester.network.partygames.util.selectWeightedRandom
import info.mester.network.partygames.util.spreadPlayers
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.block.BlockFace
import org.bukkit.block.Chest
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.logging.Level
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

enum class HealthShopMinigameState {
    STARTING,
    SHOP,
    FIGHT,
}

class ShopFailedException(
    message: String,
) : Exception(message)

class HealthShopMinigame(
    game: Game,
) : Minigame(game, "healthshop") {
    companion object {
        private val shopItems: MutableList<HealthShopItem> = mutableListOf()
        private val startLocations: MutableMap<Int, Array<Vector>> = mutableMapOf()
        private val supplyDrops: MutableList<WeightedItem<String>> = mutableListOf()
        private var startingHealth: Double = 80.0
        private val plugin = PartyGames.plugin

        init {
            reload()
        }

        fun reload() {
            val config = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "health-shop.yml"))
            // load shop items by obtaining the config and reading every key inside "items" of "health-shop.yml"
            plugin.logger.info("Loading shop items...")
            config.getConfigurationSection("items")?.getKeys(false)?.forEach { key ->
                try {
                    val shopItem = HealthShopItem.loadFromConfig(config.getConfigurationSection("items.$key")!!, key)
                    shopItems.add(shopItem)
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to load shop item $key")
                    plugin.logger.log(Level.WARNING, e.message, e)
                }
            }
            // load spawn locations
            plugin.logger.info("Loading spawn locations...")
            val spawnLocationConfig = config.getConfigurationSection("spawn-locations")!!
            spawnLocationConfig.getKeys(false).forEach { key ->
                try {
                    // try to convert the key to an integer
                    val id = key.toIntOrNull() ?: return@forEach
                    // now load all the spawn locations
                    val locationList = spawnLocationConfig.getList(key) ?: return@forEach
                    val locations =
                        locationList.mapNotNull { entry ->
                            if (entry is Map<*, *>) {
                                val x = entry["x"] as? Double ?: return@mapNotNull null
                                val y = entry["y"] as? Double ?: return@mapNotNull null
                                val z = entry["z"] as? Double ?: return@mapNotNull null
                                Vector(x, y, z)
                            } else {
                                null
                            }
                        }
                    startLocations[id] = locations.toTypedArray()
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to load spawn location $key")
                    plugin.logger.log(Level.WARNING, e.message, e)
                }
            }
            supplyDrops.clear()
            config.getList("supply-drops")?.forEach { entry ->
                if (entry is Map<*, *>) {
                    val key = entry["key"] as? String ?: return@forEach
                    val weight = entry["weight"] as? Int ?: return@forEach
                    supplyDrops.add(WeightedItem(key, weight))
                }
            }
            startingHealth = config.getDouble("health", 80.0)
        }
    }

    private val arrowRegenerating = mutableListOf<UUID>()
    private var state = HealthShopMinigameState.STARTING
    private var fightStartedTime = -1L
    private var readyPlayers = 0

    /*
     * A map that links player UUIDs to their last damage source's UUID and a Long representing the time they were damaged
     */
    private val lastDamageTimes = mutableMapOf<UUID, Pair<UUID, Long>>()
    private val lastDoubleJump = mutableMapOf<UUID, Long>()

    /**
     * Every shop UI associated with a player
     */
    private val shopUIs: Map<UUID, HealthShopUI> =
        onlinePlayers.map { it.uniqueId }.associateWith { HealthShopUI(it, shopItems, startingHealth) }

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
            player.inventory.addItem(ItemStack.of(Material.ARROW, 1))
            needsArrows = !player.inventory.contains(Material.ARROW, HealthShopUI.maxArrows(player.uniqueId))
            // if the player still needs more arrows, start the timer again
            if (needsArrows) {
                Bukkit.getScheduler().runTaskLater(plugin, Runnable { regenerateArrow(player) }, 1)
            } else {
                arrowRegenerating.remove(player.uniqueId)
            }
            return false
        }
        return true
    }

    private fun setMaxHealth(
        player: Player,
        health: Double,
    ) {
        val attribute = player.getAttribute(Attribute.MAX_HEALTH)!!
        attribute.baseValue = health
    }

    private fun sendReadyStatus() {
        val message =
            mm.deserialize("<yellow>$readyPlayers<dark_gray>/<green>${game.onlinePlayers.size} <gray>players are ready!")
        audience.sendMessage(message)
    }

    private fun regenerateArrow(player: Player) {
        val startTime = System.currentTimeMillis()
        Bukkit
            .getScheduler()
            .runTaskTimer(plugin, { t -> if (!regenerateArrowTimer(player, startTime)) t.cancel() }, 0, 1)
    }

    private fun giveSurvivePoints(
        player: Player,
        didSurvive: Boolean,
    ) {
        // for every 20th second the player has survived, give them a point
        val survivedTime = System.currentTimeMillis() - fightStartedTime
        val survivedSeconds = survivedTime / 1000
        val survivedPoints = floor((survivedSeconds / 20).toDouble()).toInt() * (if (didSurvive) 2 else 1)
        if (survivedPoints > 0) {
            game.addScore(player, survivedPoints, "Survived $survivedSeconds seconds")
        }
    }

    override fun start() {
        super.start()
        startPos.world.time = 13000
        // set up the world border
        val worldBorder = startPos.world.worldBorder
        worldBorder.size = 121.0
        worldBorder.center = startPos
        worldBorder.warningDistance = 2
        worldBorder.damageBuffer = 0.0
        worldBorder.damageAmount = 1.5
        // send the players to the predefined spawn locations
        val spawnLocations =
            if (startLocations.contains(worldIndex)) startLocations[worldIndex]!!.toList().shuffled() else emptyList()
        val playersToSpawn = onlinePlayers.take(spawnLocations.size).shuffled()
        val playersToSpread =
            if (onlinePlayers.size > spawnLocations.size) onlinePlayers.drop(spawnLocations.size) else emptyList()
        // teleport playersToSpawn to the spawn locations
        for ((i, player) in playersToSpawn.withIndex()) {
            val location = spawnLocations[i].toLocation(startPos.world)
            player.teleport(location)
        }
        // spread playersToSpread around the start pos
        spreadPlayers(playersToSpread, startPos, 60)
        // reset players
        state = HealthShopMinigameState.SHOP
        for (player in game.onlinePlayers) {
            // open the shop UI for all players
            player.openInventory(shopUIs[player.uniqueId]!!.inventory)
            // prevent players from glitching out when spawned on a slab or otherwise non-full block
            player.allowFlight = true
            player.isFlying = true
            // update their max health
            setMaxHealth(player, startingHealth)
            player.health = startingHealth
            player.sendHealthUpdate()
            // reset perks
            player.persistentDataContainer.set(
                NamespacedKey(plugin, "steal_perk"),
                PersistentDataType.BOOLEAN,
                false,
            )
            player.persistentDataContainer.set(
                NamespacedKey(plugin, "heal_perk"),
                PersistentDataType.BOOLEAN,
                false,
            )
            player.persistentDataContainer.set(
                NamespacedKey(plugin, "double_jump"),
                PersistentDataType.BOOLEAN,
                false,
            )
        }
        // start a 30-second countdown for the shop state
        startCountdown(30000) {
            startFight()
        }
    }

    private fun startFight() {
        if (state == HealthShopMinigameState.FIGHT) {
            return
        }

        state = HealthShopMinigameState.FIGHT
        fightStartedTime = System.currentTimeMillis()

        for (player in game.onlinePlayers) {
            // close the shop UI
            player.closeInventory()
            // reset flight status
            player.allowFlight = false
            player.isFlying = false
            // cap the max health at the current health
            setMaxHealth(player, player.health)
            // reset saturation
            player.saturation = 0f
            player.sendHealthUpdate()
            // time to give the items! :)
            player.inventory.clear()
            shopUIs[player.uniqueId]!!.giveItems()
            // give the actual arrow items based on maxArrows
            val maxArrows = HealthShopUI.maxArrows(player.uniqueId)
            if (maxArrows > 0) {
                player.inventory.addItem(ItemStack.of(Material.ARROW, maxArrows))
            }
        }
        // start a 3-minute countdown for the fight
        startCountdown(3 * 60 * 1000) {
            end()
        }
        // start the supply chest timer
        Bukkit.getScheduler().runTaskTimer(plugin, SupplyChestTimer(this, 3 * 60 * 20), 0, 1)
        // shrink the world border to completely close in the last 30 seconds (5 minutes is the fight duration)
        startPos.world.worldBorder.setSize(5.0, TimeUnit.SECONDS, 3 * 60 - 30)
    }

    override fun finish() {
        for (player in game.onlinePlayers) {
            // give every alive player survive points
            if (player.gameMode == GameMode.SURVIVAL) {
                giveSurvivePoints(player, true)
            }
            // reset max health
            val attribute = player.getAttribute(Attribute.MAX_HEALTH)!!
            attribute.baseValue = attribute.defaultValue
        }
    }

    fun spawnSupplyChest() {
        audience.sendMessage(Component.text("A new supply chest is incoming!", NamedTextColor.GREEN))
        val worldBorder = startPos.world.worldBorder
        // generate a random location within the world border (minus 2 blocks to avoid spawning on the border)
        val maxSize = (worldBorder.size.toInt() - 2) / 2
        var x: Double
        var z: Double
        while (true) {
            x = worldBorder.center.x + Random.nextInt(-maxSize, maxSize)
            z = worldBorder.center.z + Random.nextInt(-maxSize, maxSize)
            // check if the location is not facing the void
            if (startPos.world.getHighestBlockAt(x.toInt(), z.toInt()).type != Material.AIR) {
                break
            }
        }
        val spawnLocation = Vector(x, startPos.y + 50, z).toLocation(startPos.world)
        val fallingBlock =
            startPos.world.spawn(spawnLocation, FallingBlock::class.java) { entity ->
                entity.blockData = Material.CHEST.createBlockData()
                entity.dropItem = false
                entity.cancelDrop = true
                entity.shouldAutoExpire(false)
            }
        // start a timer that spawns particles and checks if the block has fallen
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            { t ->
                if (!running) {
                    t.cancel()
                    return@runTaskTimer
                }
                if (fallingBlock.isValid) {
                    // spawn particles
                    ParticleBuilder(Particle.LAVA)
                        .location(fallingBlock.location.clone().add(0.0, 1.0, 0.0))
                        .count(15)
                        .offset(0.0, 0.0, 0.0)
                        .allPlayers()
                        .spawn()
                    // play the falling sounds
                    val sound = Sound.sound(Key.key("entity.blaze.shoot"), Sound.Source.MASTER, 1.5f, 0.5f)
                    audience.playSound(sound, fallingBlock.location.x, fallingBlock.location.y, fallingBlock.location.z)
                } else {
                    // the block has fallen, spawn the chest
                    val block = fallingBlock.world.getBlockAt(fallingBlock.location)
                    block.type = Material.CHEST
                    val blockState = block.state as Chest
                    blockState.persistentDataContainer.set(
                        NamespacedKey(plugin, "supply_chest"),
                        PersistentDataType.BOOLEAN,
                        true,
                    )
                    blockState.open()
                    blockState.update(true)
                    // play an explosion sound
                    val sound = Sound.sound(Key.key("entity.generic.explode"), Sound.Source.MASTER, 1.7f, 1.0f)
                    audience.playSound(sound, fallingBlock.location.x, fallingBlock.location.y, fallingBlock.location.z)
                    // spawn explosion particles
                    ParticleBuilder(Particle.EXPLOSION)
                        .location(fallingBlock.location)
                        .count(1)
                        .offset(0.0, 0.0, 0.0)
                        .allPlayers()
                        .spawn()
                    t.cancel()
                }
            },
            0,
            1,
        )
    }

    fun handleEntityShootBow(event: EntityShootBowEvent) {
        if (state != HealthShopMinigameState.FIGHT) {
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

    override fun handleDisconnect(
        player: Player,
        didLeave: Boolean,
    ) {
        // reset max health
        val attribute = player.getAttribute(Attribute.MAX_HEALTH)!!
        attribute.baseValue = attribute.defaultValue
        // check if there are only 1 or less alive players
        if (game.onlinePlayers.filter { it.gameMode == GameMode.SURVIVAL }.size <= 1) {
            end()
        }
    }

    /**
     * Ran when a player closes the shop UI
     */
    override fun handleInventoryClose(event: InventoryCloseEvent) {
        if (state != HealthShopMinigameState.SHOP) {
            return
        }
        // check if the inventory is the shop inventory
        val inventory = event.inventory
        if (inventory.holder !is HealthShopUI) {
            return
        }
        // mark the player as ready
        readyPlayers += 1
        if (readyPlayers == game.onlinePlayers.size) {
            // start the fight
            startFight()
            return
        }
        sendReadyStatus()
        event.player.sendMessage(Component.text("Left click to reopen the shop.", NamedTextColor.AQUA))
    }

    override fun handleEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (state != HealthShopMinigameState.FIGHT) {
            return
        }
        val damager = event.damager
        val damagee = event.entity
        if (damagee !is Player || damager !is Player) {
            return
        }

        if (damagee.isBlocking) {
            // nuke the shield
            val shield = damagee.inventory.itemInOffHand.clone()
            damagee.inventory.setItemInOffHand(ItemStack.of(Material.AIR))
            // after 1.5 seconds, give the shield back
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable {
                    if (!running) return@Runnable
                    damagee.inventory.setItemInOffHand(shield)
                },
                30,
            )
        }

        lastDamageTimes[damagee.uniqueId] = Pair(damager.uniqueId, System.currentTimeMillis())
        // show the damager the damagee's name and health in actionbar
        damager.sendActionBar(
            MiniMessage.miniMessage().deserialize(
                "<green><bold>${damagee.name}<reset> <dark_gray>-<reset> <red>${
                    String.format(
                        max(0.0, floor(damagee.health - event.finalDamage) / 2).toString(),
                        "%.1f",
                    )
                } ♥",
            ),
        )
    }

    override fun handlePlayerDeath(event: PlayerDeathEvent) {
        event.isCancelled = true

        event.deathMessage()?.let {
            // TODO: implement awesome custom death messages
            audience.sendMessage(it)
        }
        // put the player in spectator
        val killedPlayer = event.entity
        killedPlayer.gameMode = GameMode.SPECTATOR
        giveSurvivePoints(event.entity, false)
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
            game.addScore(assistPlayer, 10, "Assisted ${event.entity.name}")
        }
        // handle player kill
        if (killerPlayer is Player) {
            game.addScore(killerPlayer, 40, "Killed ${event.entity.name}")
            // check if the player has the steal perk
            if (killerPlayer.persistentDataContainer.get(
                    NamespacedKey(plugin, "steal_perk"),
                    PersistentDataType.BOOLEAN,
                ) == true
            ) {
                // copy the inventory of the killed player
                for (i in 0..40) {
                    killedPlayer.inventory.getItem(i)?.let { item ->
                        killerPlayer.inventory.setItem(i, item)
                    }
                }
            }
            // check if the player has the heal perk
            if (killerPlayer.persistentDataContainer.get(
                    NamespacedKey(plugin, "heal_perk"),
                    PersistentDataType.BOOLEAN,
                ) == true
            ) {
                killerPlayer.health = killerPlayer.getAttribute(Attribute.MAX_HEALTH)!!.value
                killerPlayer.sendHealthUpdate()
            }
        }
        // check if we only have one alive player left
        if (game.onlinePlayers.filter { it.gameMode == GameMode.SURVIVAL }.size <= 1) {
            end()
        }
    }

    fun handleEntityRegainHealth(event: EntityRegainHealthEvent) {
        // don't let players during the shop state regain health
        if (state == HealthShopMinigameState.SHOP) {
            event.isCancelled = true
            return
        }
    }

    fun handlePlayerItemConsume(event: PlayerItemConsumeEvent) {
        if (state != HealthShopMinigameState.FIGHT) {
            return
        }
        val item = event.item
        // check if the item has a special golden_apple_inf PDC
        if (item.itemMeta.hasEnchant(Enchantment.LUCK_OF_THE_SEA)) {
            // add a cooldown to the enchanted golden apple (10 seconds)
            event.player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, 10 * 20)
        }
        if (item.type == Material.POTION) {
            event.replacement = ItemStack.of(Material.AIR)
        }
    }

    override fun handlePlayerInteract(event: PlayerInteractEvent) {
        if (state == HealthShopMinigameState.SHOP) {
            event.setUseInteractedBlock(Event.Result.DENY)
            event.setUseItemInHand(Event.Result.DENY)
            // remove the player from the ready list
            readyPlayers -= 1
            readyPlayers = readyPlayers.coerceAtLeast(0)
            sendReadyStatus()
            // open the shop UI
            event.player.openInventory(shopUIs[event.player.uniqueId]!!.inventory)
        } else if (state == HealthShopMinigameState.FIGHT) {
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
                        .filter {
                            it.gameMode == GameMode.SURVIVAL && !PartyGames.plugin.isAdmin(it) && it.uniqueId != player.uniqueId
                        }.minByOrNull { it.location.distance(player.location) }
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
    }

    override fun handlePlayerMove(event: PlayerMoveEvent) {
        if (state == HealthShopMinigameState.SHOP) {
            // don't let the players move away, but let them look around
            event.to.x = event.from.x
            event.to.y = event.from.y
            event.to.z = event.from.z
            return
        }
        // check for double jump perk
        val player = event.player
        if (player.persistentDataContainer.get(
                NamespacedKey(PartyGames.plugin, "double_jump"),
                PersistentDataType.BOOLEAN,
            ) == true &&
            player.gameMode != GameMode.CREATIVE
        ) {
            // check if the player is on the ground and allow
            if (player.location.block
                    .getRelative(BlockFace.DOWN)
                    .type != Material.AIR
            ) {
                player.allowFlight = true
            }
        }
        // check if player is below 0 (kill instantly)
        if (player.location.y < 0) {
            player.teleport(startPos)
            @Suppress("UnstableApiUsage")
            player.damage(9999.0, DamageSource.builder(DamageType.OUT_OF_WORLD).build())
        }
    }

    override fun handleBlockPlace(event: BlockPlaceEvent) {
        if (event.block.type == Material.OAK_PLANKS) {
            // start a nice animation that eventually breaks the block
            Bukkit.getScheduler().runTaskTimer(
                plugin,
                object : Consumer<BukkitTask> {
                    private var remainingTime = 6 * 20

                    override fun accept(t: BukkitTask) {
                        if (!running) {
                            t.cancel()
                            return
                        }
                        remainingTime -= 1
                        if (remainingTime <= 0) {
                            event.block.type = Material.AIR
                            t.cancel()
                            return
                        } else {
                            // calculate the progress
                            val progress = 1 - (remainingTime.toFloat() / (6 * 20))
                            game.onlinePlayers.forEach { it.sendBlockDamage(event.block.location, progress) }
                        }
                    }
                },
                0,
                1,
            )
        }
    }

    override fun handlePrePlayerAttack(event: PrePlayerAttackEntityEvent) {
        if (state == HealthShopMinigameState.FIGHT) {
            event.isCancelled = false
        }
    }

    override fun handleInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player
        val inventory = event.inventory
        val holder = inventory.holder
        if (holder is Chest) {
            if (holder.persistentDataContainer.get(
                    NamespacedKey(plugin, "supply_chest"),
                    PersistentDataType.BOOLEAN,
                ) != true
            ) {
                return
            }
            event.isCancelled = true
            holder.block.type = Material.AIR
            // get a random supply drop
            val supplyDrop = supplyDrops.selectWeightedRandom()
            if (supplyDrop.startsWith("golden_apple_")) {
                val amount = supplyDrop.substringAfter("golden_apple_").toIntOrNull() ?: return
                player.inventory.addItem(ItemStack.of(Material.GOLDEN_APPLE, amount))
            }
            if (supplyDrop == "jump_potion") {
                val potion = ItemStack.of(Material.POTION)
                HealthShopUI.setJumpPotion(potion, true)
                player.inventory.addItem(potion)
            }
            if (supplyDrop == "regen_potion") {
                val potion = ItemStack.of(Material.POTION)
                HealthShopUI.setRegenPotion(potion, true)
                player.inventory.addItem(potion)
            }
        }
    }

    override fun handlePlayerToggleFlight(event: PlayerToggleFlightEvent) {
        val player = event.player
        if (player.gameMode == GameMode.CREATIVE) {
            return
        }
        val perk =
            player.persistentDataContainer.get(
                NamespacedKey(PartyGames.plugin, "double_jump"),
                PersistentDataType.BOOLEAN,
            )
        if (perk != true) {
            return
        }
        // check for last double jump
        val lastDoubleJumpTime = lastDoubleJump[player.uniqueId]
        if (lastDoubleJumpTime != null && System.currentTimeMillis() - lastDoubleJumpTime < 3000) {
            event.isCancelled = true
            return
        }
        lastDoubleJump[player.uniqueId] = System.currentTimeMillis()
        event.isCancelled = true
        player.allowFlight = false
        player.isFlying = false
        // create a small explosion particle
        ParticleBuilder(Particle.EXPLOSION)
            .location(player.location)
            .count(3)
            .offset(0.0, 0.0, 0.0)
            .source(player)
            .allPlayers()
            .spawn()
        // apply small velocity to the player in the direction they're looking in
        player.velocity =
            player.location.direction
                .normalize()
                .multiply(0.8)
                .add(Vector(0.0, 0.4, 0.0))
        val sound = Sound.sound(Key.key("entity.blaze.shoot"), Sound.Source.MASTER, 1.0f, 0.5f)
        player.playSound(sound, Sound.Emitter.self())
    }

    override val name: Component
        get() = Component.text("Health Shop", NamedTextColor.AQUA)
    override val description: Component
        get() =
            Component.text(
                "Buy items and weapons to fight in a free for all battleground.\nWatch out, the items cost not money, but your own health!",
                NamedTextColor.AQUA,
            )
}
