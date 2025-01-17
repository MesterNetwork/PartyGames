package info.mester.network.partygames.game

import com.destroystokyo.paper.ParticleBuilder
import info.mester.network.partygames.UUIDDataType
import info.mester.network.partygames.api.Game
import info.mester.network.partygames.api.GameState
import info.mester.network.partygames.api.Minigame
import info.mester.network.partygames.game.gardening.Cactus
import info.mester.network.partygames.game.gardening.GardenTap
import info.mester.network.partygames.game.gardening.Lilac
import info.mester.network.partygames.game.gardening.OakTree
import info.mester.network.partygames.game.gardening.Peony
import info.mester.network.partygames.game.gardening.Plant
import info.mester.network.partygames.game.gardening.RainbowFlower
import info.mester.network.partygames.game.gardening.Rose
import info.mester.network.partygames.game.gardening.Sunflower
import info.mester.network.partygames.game.gardening.Weed
import info.mester.network.partygames.game.gardening.ZombieWeed
import info.mester.network.partygames.util.WeightedItem
import info.mester.network.partygames.util.getHighestBlockBelow
import info.mester.network.partygames.util.getPointsAlongSegment
import info.mester.network.partygames.util.getTouchedBlocks
import info.mester.network.partygames.util.selectWeightedRandom
import io.papermc.paper.event.entity.EntityMoveEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Rabbit
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import java.util.UUID

private const val MAP_RADIUS = 80
private const val MAX_PLANTS = 500

private enum class ObjectType {
    RAINBOW_FLOWER,
    OAK_TREE,
    SUNFLOWER,
    CACTUS,
    WEED,
    ZOMBIE_WEED,
    ROSE,
    PEONY,
    LILAC,
}

class GardeningMinigame(
    game: Game,
) : Minigame(game, "gardening") {
    private val grassBlocks = mutableListOf<Location>()
    private val plants = mutableMapOf<Location, Plant>()
    private val hoses = mutableMapOf<UUID, MutableList<Rabbit>>()
    private val previousHoseDistances = mutableMapOf<UUID, Double>()
    private val taps = mutableMapOf<Location, GardenTap>()

    /**
     * The power of the hose determines how far it shoots.
     * A higher power means a longer range, but less water droplets.
     */
    private val hosePowers = mutableMapOf<UUID, Int>()

    private fun shootWater(player: Player) {
        val tap = getTap(player) ?: return
        val hasWater = tap.takeWater()
        if (!hasWater) {
            return
        }
        // set the player's xp progress to the tap's water level
        player.exp = tap.getFullness().toFloat()
        val power = hosePowers[player.uniqueId]!!
        // power goes from 0 to 10, so we divide by 10 to get a value between 0 and 1
        val normalPower = power / 10.0
        val direction =
            player.location.direction
                .normalize()
                .multiply(Vector(normalPower * 1.5 + 0.25, normalPower * 0.35 + 0.35, normalPower * 1.5 + 0.25))
        // launch an invisible small armor stand in the direction
        player.world.spawn(player.location.add(0.0, player.eyeHeight, 0.0), ArmorStand::class.java) { entity ->
            // apply special effects
            entity.isInvisible = true
            entity.isCollidable = false
            entity.isSilent = true
            entity.isSmall = true
            entity.isInvulnerable = true
            entity.getAttribute(Attribute.SCALE)?.baseValue = 0.06125
            // set velocity
            entity.velocity = direction
            // save player's UUID in pbc
            entity.persistentDataContainer.set(NamespacedKey(plugin, "player"), UUIDDataType(), player.uniqueId)
            // save the power of the hose in pbc
            entity.persistentDataContainer.set(
                NamespacedKey(plugin, "hosePower"),
                PersistentDataType.INTEGER,
                hosePowers[player.uniqueId]!!,
            )
            // hide the armor stand from every online player
            Bukkit.getOnlinePlayers().forEach { player ->
                player.hideEntity(plugin, entity)
            }
        }
    }

    private fun waterLand(
        location: Location,
        player: Player,
        droplets: Int,
    ) {
        val waterAmount = droplets / 6.0
        plants[location]?.water(player, waterAmount)
        // instead of just watering the location, locate all 8 (orthogonal + diagonal) neighbors of the location and water them
        val neighbors =
            listOf(
                location.clone().add(-1.0, 0.0, 0.0),
                location.clone().add(1.0, 0.0, 0.0),
                location.clone().add(0.0, 0.0, -1.0),
                location.clone().add(0.0, 0.0, 1.0),
                location.clone().add(1.0, 0.0, -1.0),
                location.clone().add(1.0, 0.0, 1.0),
                location.clone().add(-1.0, 0.0, -1.0),
                location.clone().add(-1.0, 0.0, 1.0),
            )

        for (neighbor in neighbors) {
            // only provide 40% of the water to the neighbor plant
            plants[neighbor]?.water(player, waterAmount * 0.4)
        }
    }

    private fun fetchAllGrassBlocks() {
        val world = startPos.world
        for (x in startPos.blockX - MAP_RADIUS..startPos.blockX + MAP_RADIUS) {
            for (z in startPos.blockZ - MAP_RADIUS..startPos.blockZ + MAP_RADIUS) {
                val block = world.getHighestBlockAt(x, z)
                if (block.type == Material.GRASS_BLOCK) {
                    grassBlocks.add(block.location)
                }
            }
        }
    }

    private fun getFreeGrassBlock() =
        grassBlocks
            .map { it.clone().add(0.0, 1.0, 0.0) }
            .filter { startPos.world.getBlockAt(it).type == Material.AIR || startPos.world.getBlockAt(it).type == Material.SHORT_GRASS }
            .randomOrNull()

    private fun spawnWater(
        location: Location,
        player: Player,
        power: Int,
    ) {
        // start a timer that triggers the water land
        // calculate the delay based on the 0.3 blocks per tick speed of the particle
        val highestBlockLoc = getHighestBlockBelow(location, 50.0).clone().add(0.0, 1.0, 0.0)
        // removing 1 from the y coordinate to make sure the water lands on the block's top surface
        val delay = ((location.y - highestBlockLoc.y - 1) / 0.3).coerceAtLeast(0.0).toLong()
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                if (!running || !player.isOnline) {
                    return@Runnable
                }
                waterLand(highestBlockLoc, player, 11 - power)
            },
            delay,
        )
    }

    private fun resetHose(player: Player) {
        // reset all hose locations and kill all rabbits with the player's uuid
        hoses[player.uniqueId]?.forEach { rabbit ->
            rabbit.remove()
        }
        hoses[player.uniqueId] = mutableListOf()
        player.exp = 0.0f
    }

    private fun spawnHose(
        player: Player,
        location: Location,
    ) {
        val playerHoses = hoses[player.uniqueId] ?: return
        // spawn an invisible rabbit to leash
        location.world.spawn(location, Rabbit::class.java) { entity ->
            entity.isInvisible = true
            entity.isInvulnerable = true
            entity.isSilent = true
            entity.setGravity(false)
            entity.setAI(false)
            entity.getAttribute(Attribute.SCALE)?.baseValue = 0.06125
            playerHoses.add(entity)
        }
        // now we need to reset the leash holder of all the hoses
        resetHoseLeashHolder(player)
    }

    private fun resetHoseLeashHolder(player: Player) {
        val playerHoses = hoses[player.uniqueId] ?: return
        for ((i, hose) in playerHoses.withIndex()) {
            // if this is the last hose, the player is the leash holder
            // otherwise, the next rabbit is the leash holder
            val leashHolder = if (i == playerHoses.size - 1) player else playerHoses[i + 1]
            hose.setLeashHolder(leashHolder)
        }
    }

    private fun getTotalHoseDistance(player: Player): Double {
        val hoses = hoses[player.uniqueId] ?: return 0.0
        // to calculate the total hose distance, we need to add up the distances of all the hoses incrementally,
        // so it's hose0 + hose1, hose1 + hose2, and finally hoseN + player
        // first construct an array of all the hose locations, including the player's location at the end
        val hoseLocations = hoses.map { it.location }.toMutableList()
        hoseLocations.add(player.location)
        // now calculate the total hose distance
        var totalHoseDistance = 0.0
        for (i in 1 until hoseLocations.size) {
            totalHoseDistance += hoseLocations[i].distance(hoseLocations[i - 1])
        }
        return totalHoseDistance
    }

    private fun hasHose(player: Player): Boolean = hoses[player.uniqueId]?.isNotEmpty() == true

    private fun getTap(player: Player): GardenTap? {
        if (!hasHose(player)) {
            return null
        }
        val playerHoses = hoses[player.uniqueId]!!
        // we're using this super ugly location.block.location to make sure
        // the location aligns with the xyz coordinates (which is how the taps are stored)
        return taps[
            playerHoses
                .first()
                .location.block.location,
        ]
    }

    private fun getPlayersFromTap(tap: GardenTap): List<Player> = game.onlinePlayers.filter { getTap(it) == tap }

    override fun start() {
        super.start()
        fetchAllGrassBlocks()
        val worldBorder = startPos.world.worldBorder
        worldBorder.center = startPos
        worldBorder.size = 2 * MAP_RADIUS + 1.0
        worldBorder.warningDistance = 0
        // start a timer that triggers the hose shooting
        Bukkit.getScheduler().runTaskTimer(plugin, { t ->
            if (!running) {
                t.cancel()
                return@runTaskTimer
            }
            for (player in game.onlinePlayers.filter { it.isSneaking && hoses[it.uniqueId]?.isNotEmpty() == true }) {
                shootWater(player)
            }
        }, 0, 1)
        // spawn 150 garden taps with a timer to prevent lag
        var tapCount = 0
        Bukkit.getScheduler().runTaskTimer(plugin, { t ->
            if (!running) {
                t.cancel()
                return@runTaskTimer
            }
            val location = getFreeGrassBlock()
            if (location == null) {
                t.cancel()
                return@runTaskTimer
            }
            taps[location] = GardenTap(location)
            tapCount++
            if (tapCount >= 150) {
                t.cancel()
            }
        }, 0, 1)
        // start a timer that spawns an object every 5 ticks
        Bukkit.getScheduler().runTaskTimer(plugin, { t ->
            if (!running) {
                t.cancel()
                return@runTaskTimer
            }
            val activePlants = plants.filter { it.value.isActive() }
            if (activePlants.size >= MAX_PLANTS) {
                return@runTaskTimer
            }
            val location = getFreeGrassBlock()
            if (location == null) {
                if (activePlants.isEmpty()) {
                    // no free block found and all plants are inactive, time to end the game
                    end()
                }
                return@runTaskTimer
            }
            // decide what to spawn
            val spawnedObject =
                listOf(
                    WeightedItem(ObjectType.WEED, 25),
                    WeightedItem(ObjectType.ZOMBIE_WEED, 2),
                    WeightedItem(ObjectType.RAINBOW_FLOWER, 5),
                    WeightedItem(ObjectType.OAK_TREE, 15),
                    WeightedItem(ObjectType.CACTUS, 35),
                    WeightedItem(ObjectType.SUNFLOWER, 80),
                    WeightedItem(ObjectType.ROSE, 80),
                    WeightedItem(ObjectType.PEONY, 80),
                    WeightedItem(ObjectType.LILAC, 80),
                ).selectWeightedRandom()
            val plant =
                when (spawnedObject) {
                    ObjectType.RAINBOW_FLOWER -> RainbowFlower(location, game)
                    ObjectType.OAK_TREE -> OakTree(location, game)
                    ObjectType.SUNFLOWER -> Sunflower(location, game)
                    ObjectType.CACTUS -> Cactus(location, game)
                    ObjectType.WEED -> Weed(location, game)
                    ObjectType.ZOMBIE_WEED -> ZombieWeed(location, game)
                    ObjectType.ROSE -> Rose(location, game)
                    ObjectType.PEONY -> Peony(location, game)
                    ObjectType.LILAC -> Lilac(location, game)
                }
            plant.spawn()
            plants[location] = plant
        }, 0, 5)
        // start a timer that increases the water level of the taps every 5 ticks
        Bukkit.getScheduler().runTaskTimer(plugin, { t ->
            if (!running) {
                t.cancel()
                return@runTaskTimer
            }
            for (tap in taps.values) {
                tap.addWater()
                for (player in getPlayersFromTap(tap)) {
                    player.exp = tap.getFullness().toFloat()
                }
            }
        }, 0, 5)
        // start a 2,5-minute countdown for the game
        startCountdown((2.5 * 60 * 1000).toLong()) {
            end()
        }
        // setup players
        for (player in game.onlinePlayers) {
            setupPlayer(player)
        }
    }

    private fun setupPlayer(player: Player) {
        // add a lead to the player's inventory
        val hose = ItemStack.of(Material.LEAD)
        hose.editMeta { meta ->
            meta.itemName(Component.text("Hose", NamedTextColor.GOLD))
            meta.lore(
                listOf(
                    Component.text("Right click on a garden tap to attach a hose to it."),
                    Component.text("Right click with the hose to increase its power."),
                    Component.text("Left click with the hose to decrease its power."),
                    Component.text("Sneak to shoot water from your hose."),
                    Component.text("Drop to detach the hose, whereever you are."),
                ).map { it.decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY) },
            )
        }
        val weedKiller = ItemStack.of(Material.SHEARS)
        weedKiller.editMeta { meta ->
            meta.itemName(Component.text("Weed Killer", NamedTextColor.GOLD))
            meta.lore(
                listOf(
                    Component.text("Left click on a weed to kill it."),
                ).map { it.decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY) },
            )
        }
        player.inventory.addItem(hose)
        player.inventory.addItem(weedKiller)
        hoses[player.uniqueId] = mutableListOf()
        hosePowers[player.uniqueId] = 5
        player.level = hosePowers[player.uniqueId]!!
    }

    override fun finish() {
        for (player in game.onlinePlayers) {
            resetHose(player)
        }
    }

    override fun handleRejoin(player: Player) {
        if (game.state == GameState.PLAYING) {
            setupPlayer(player)
            player.showBossBar(game.remainingBossBar)
            player.gameMode = GameMode.SURVIVAL
            player.teleport(startPos)
        }
    }

    override fun handleDisconnect(
        player: Player,
        didLeave: Boolean,
    ) {
        resetHose(player)
    }

    override fun handlePlayerMove(event: PlayerMoveEvent) {
        hoses.getOrDefault(event.player.uniqueId, null)?.let { playerHoses ->
            if (playerHoses.isEmpty()) {
                return@let
            }
            val totalHoseDistance = getTotalHoseDistance(event.player)
            val previousHoseDistance = previousHoseDistances[event.player.uniqueId] ?: 0.0
            previousHoseDistances[event.player.uniqueId] = totalHoseDistance
            // if the hose distance has reached 30, knock the player back to the last hose
            if (totalHoseDistance > 30) {
                val lastHose = playerHoses.last()
                // calculate the direction vector from the player to the last hose
                val direction =
                    lastHose.location
                        .clone()
                        .subtract(event.player.location)
                        .toVector()
                        .normalize()
                        .multiply(0.7)
                // add an extra umphf to the y coordinate to make sure the player doesn't get stuck
                direction.y = direction.y * 1.3 + 0.5
                event.player.velocity = direction
                event.player.sendMessage(Component.text("Your hose is too long!", NamedTextColor.RED))
                return@let
            }
            if (event.hasChangedBlock()) {
                // check if the player has moved less than 1 blocks away from the last hose, which isn't the first one
                if (playerHoses.size > 1 && event.to.distance(playerHoses.last().location) < 0.6) {
                    // remove the last hose
                    playerHoses.last().remove()
                    playerHoses.removeLast()
                    // make sure the last hose's leash holder is the player
                    playerHoses.last().setLeashHolder(event.player)
                    return@let
                }
                // every hose is 5 blocks long, so check how many hoses the player has
                // and use that to determine if the player should spawn a new hose
                // important: don't spawn a new hose if the total distance is decreasing
                if (totalHoseDistance > playerHoses.size * 5 && totalHoseDistance > previousHoseDistance) {
                    // spawn a new hose at the player's location, make sure it's sitting on the highest block
                    val highestBlockY = getHighestBlockBelow(event.player.location, 10.0).y + 1.0
                    val hoseLocation = event.player.location.clone()
                    hoseLocation.y = highestBlockY
                    spawnHose(event.player, hoseLocation)
                }
            }
        }
    }

    override fun handleEntityMove(event: EntityMoveEvent) {
        if (event.entityType == EntityType.ARMOR_STAND) {
            val entity = event.entity as ArmorStand
            if (entity.isOnGround) {
                entity.remove()
            }
            val playerUUID =
                entity.persistentDataContainer.get(NamespacedKey(plugin, "player"), UUIDDataType()) ?: return
            val player = Bukkit.getPlayer(playerUUID) ?: return
            val power =
                entity.persistentDataContainer.get(
                    NamespacedKey(plugin, "hosePower"),
                    PersistentDataType.INTEGER,
                ) ?: return
            // to prevent detection issues due to the game's tick rate, calculate all the unique blocks that the armor stand touched
            val touchedBlocks = getTouchedBlocks(event.from.toVector(), event.to.toVector())
            for (block in touchedBlocks) {
                val blockLocation = block.toLocation(event.to.world)
                spawnWater(blockLocation, player, power)
            }
            // now to make a continuous stream of water, we need to spawn water particles along the path of the armor stand
            val segmentPoints = getPointsAlongSegment(event.from.toVector(), event.to.toVector(), 0.1)
            for (segmentPoint in segmentPoints) {
                // spawn a water particle at each point along the segment
                // droplets scales slower than the hose power, since a lower power means
                // the droplets will appear closer, therefore visually it'd be too much
                val droplets = ((11 - power) / 2.0).coerceAtLeast(1.0).toInt()
                val pBuilder =
                    ParticleBuilder(Particle.FALLING_WATER)
                        .count(droplets)
                        .offset(0.08, 0.08, 0.08)
                        .location(segmentPoint.toLocation(event.to.world))
                        .receivers(32)
                        .source(player)
                        .force(true)
                pBuilder.spawn()
            }
        }
    }

    override fun handlePlayerInteract(event: PlayerInteractEvent) {
        // check if the player is right-clicking an iron bars block
        if (event.hand == EquipmentSlot.HAND &&
            event.action.isRightClick &&
            event.clickedBlock?.type == Material.IRON_BARS
        ) {
            // we reset the hose in case the player is not holding a lead
            resetHose(event.player)
            // if the player is holding a lead, spawn a new hose
            if (event.item?.type == Material.LEAD) {
                val blockLocation =
                    event.clickedBlock!!
                        .location
                        .clone()
                        .add(0.5, 0.5, 0.5)
                // spawn the hose
                spawnHose(event.player, blockLocation)
            }
            // check if the player is clicking with a lead and has a hose
        } else if (event.hand == EquipmentSlot.HAND && event.item?.type == Material.LEAD && hasHose(event.player)) {
            val rightClick = event.action.isRightClick
            // increase the power of the hose
            if (rightClick) {
                hosePowers[event.player.uniqueId] = hosePowers[event.player.uniqueId]!! + 1
            } else {
                hosePowers[event.player.uniqueId] = hosePowers[event.player.uniqueId]!! - 1
            }
            hosePowers[event.player.uniqueId] = hosePowers[event.player.uniqueId]!!.coerceIn(0, 10)
            event.player.level = hosePowers[event.player.uniqueId]!!
        }
    }

    override fun handleBlockPhysics(event: BlockPhysicsEvent) {
        val dontCancel = listOf(Material.CACTUS, Material.SUNFLOWER, Material.LILAC, Material.PEONY, Material.ROSE_BUSH)
        if (event.block.type in dontCancel) {
            event.isCancelled = true
        }
    }

    override fun handleEntityCombust(event: EntityCombustEvent) {
        if (event.entity.type == EntityType.ZOMBIE) {
            event.isCancelled = true
        }
    }

    override fun handlePlayerDeath(event: PlayerDeathEvent) {
        event.isCancelled = true
        val player = event.player
        player.teleport(startPos)
        game.addScore(player, -100, "dying... seriously, how?")
    }

    override fun handleBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val item = event.player.inventory.itemInMainHand
        if (item.type == Material.SHEARS) {
            val plant = plants[block.location] ?: return
            plant.killWeed(event.player)
        }
    }

    override fun handlePlayerDropItem(event: PlayerDropItemEvent) {
        if (event.itemDrop.itemStack.type == Material.LEAD) {
            resetHose(event.player)
        }
    }

    override val name: Component
        get() = Component.text("Gardening", NamedTextColor.AQUA)
    override val description: Component
        get() = Component.text("Use your tools to grow plants and remove weeds!", NamedTextColor.AQUA)
}
