package info.mester.network.partygames.game

import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.api.Game
import info.mester.network.partygames.api.Minigame
import info.mester.network.partygames.mm
import info.mester.network.partygames.pow
import info.mester.network.partygames.util.WeightedItem
import info.mester.network.partygames.util.selectWeightedRandom
import info.mester.network.partygames.util.snapTo90
import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import net.kyori.adventure.translation.GlobalTranslator
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.Banner
import org.bukkit.block.BlockState
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.type.ChiseledBookshelf
import org.bukkit.block.data.type.Fence
import org.bukkit.block.data.type.Gate
import org.bukkit.block.data.type.GlassPane
import org.bukkit.block.data.type.Slab
import org.bukkit.block.data.type.Stairs
import org.bukkit.block.data.type.TrapDoor
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta
import org.bukkit.inventory.meta.SpawnEggMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.structure.Structure
import java.io.File
import java.time.Duration
import java.util.Random
import java.util.UUID
import java.util.logging.Level
import kotlin.math.exp
import kotlin.math.max

enum class StructureDifficulty {
    EASY,
    MEDIUM,
    HARD,
    INSANE,
}

enum class SpeedBuildersState {
    MEMORISE,
    BUILD,
    JUDGE,
    FINISHED,
}

// StructureData data class which holds the name, difficulty and file name of the structure
private data class StructureData(
    val name: String,
    val difficulty: StructureDifficulty,
    val displayName: String,
) {
    val fileName = "${name.lowercase()}.nbt"
}

private data class StructureAccuracy(
    val accuracy: Double,
    val incorrectBlocks: List<Pair<BlockData, BlockData?>>,
)

/**
 * The size of the player area in blocks.
 * The player area is a cuboid region with the size of PLAYER_AREA_SIZE x PLAYER_AREA_SIZE x PLAYER_AREA_SIZE (not including floor).
 */
const val PLAYER_AREA_SIZE = 7

/**
 * How much padding should be between the player areas.
 */
const val AREA_OFFSET = 5

private fun Location.toBlockVector(): BlockVector3 = BlockVector3.at(blockX, blockY, blockZ)

private fun Location.toPlayerArea(): CuboidRegion {
    val corner1 = clone()
    val corner2 =
        clone().add((PLAYER_AREA_SIZE - 1).toDouble(), PLAYER_AREA_SIZE.toDouble(), (PLAYER_AREA_SIZE - 1).toDouble())
    return CuboidRegion(corner1.toBlockVector(), corner2.toBlockVector())
}

private fun CuboidRegion.toLocation(world: World): Location = Location(world, pos1.x().toDouble(), pos1.y().toDouble(), pos1.z().toDouble())

class SpeedBuildersMinigame(
    game: Game,
) : Minigame(game, "speedbuilders") {
    companion object {
        private val plugin = PartyGames.plugin
        private val structures = mutableListOf<StructureData>()
        private val scores = mutableMapOf<StructureDifficulty, Int>()
        private val gotItems = mutableListOf<UUID>()
        val SPAWNED_ENTITY_KEY = NamespacedKey(plugin, "spawned")

        fun reload() {
            val config = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "speed-builders.yml"))
            // load structures from "structures" section
            plugin.logger.info("Loading structures...")
            structures.clear()
            config.getConfigurationSection("structures")?.getKeys(false)?.forEach { key ->
                try {
                    val structureConfig = config.getConfigurationSection("structures.$key")!!
                    val difficulty = structureConfig.getString("difficulty")!!
                    val displayName = structureConfig.getString("display_name", "Unknown")!!
                    val structureData =
                        StructureData(key, StructureDifficulty.valueOf(difficulty.uppercase()), displayName)
                    val structurePath = "speedbuilders/${structureData.fileName}"
                    // step 1: check if the structure file is inside the jar
                    val structureInJar = plugin.getResource(structurePath) != null
                    if (structureInJar) {
                        plugin.saveResource(structurePath, true)
                    } else {
                        // step 2: check if the structure file is already the plugin's data folder
                        val structureInDataFolder = File(plugin.dataFolder, structurePath).exists()
                        if (!structureInDataFolder) {
                            throw IllegalStateException("Structure file $structurePath not found!")
                        }
                    }
                    structures.add(structureData)
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to load structure $key")
                    plugin.logger.log(Level.WARNING, e.message, e)
                }
            }
            // load scores
            config.getConfigurationSection("scores")?.getKeys(false)?.forEach { key ->
                try {
                    val difficulty = StructureDifficulty.valueOf(key.uppercase())
                    val score = config.getConfigurationSection("scores")!!.getInt(key)
                    scores[difficulty] = score
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to load score for $key")
                    plugin.logger.log(Level.WARNING, e.message, e)
                }
            }
        }

        init {
            reload()
        }
    }

    private val structureManager = Bukkit.getStructureManager()
    private val playerAreas = mutableMapOf<UUID, CuboidRegion>()
    private var state = SpeedBuildersState.MEMORISE
    private var currentStructureData: StructureData? = null
    private var currentStructure: Structure? = null
    private val blockBreakCooldowns = mutableMapOf<UUID, Long>()
    private var round = 0

    /**
     * Load the structure based on the current structure data.
     * @return the loaded structure
     */
    private fun getStructure(): Structure {
        // load structure from plugin.dataFolder/speedbuilders/structureData.fileName
        if (currentStructureData == null) {
            throw IllegalStateException("No structure data is selected!")
        }
        val structureData = currentStructureData!!
        val structurePath = "speedbuilders/${structureData.fileName}"
        return structureManager.loadStructure(File(originalPlugin.dataFolder, structurePath))
    }

    private fun selectStructure(difficulty: StructureDifficulty?): StructureData {
        // select a random structure based on the difficulty
        // if difficulty is null, select a random structure
        val structureData =
            difficulty?.let { structures.filter { it.difficulty == difficulty }.randomOrNull() } ?: structures.random()
        return structureData
    }

    private fun calculateAccuracy(
        original: Structure,
        copy: Structure,
    ): StructureAccuracy {
        // for original blocks, disregard the very bottom layer (the floor)
        val originalBlocks = original.palettes[0].blocks.filter { it.type != Material.AIR && it.location.y > 0 }
        val copyBlocks = copy.palettes[0].blocks.filter { it.type != Material.AIR && it.location.y > 0 }
        val incorrectBlocks = mutableListOf<Pair<BlockData, BlockData?>>()
        var correctBlocks = 0
        // go through every block in the original structure
        for (originalBlock in originalBlocks) {
            // get the block in the copy structure at the same location
            val copyBlock = copyBlocks.firstOrNull { it.location == originalBlock.location }
            if (copyBlock == null) {
                // if the block is not found, it is incorrect
                incorrectBlocks.add(originalBlock.blockData to null)
                continue
            }
            val originalBlockData = originalBlock.blockData
            val copyBlockData = copyBlock.blockData
            val incorrectBlock = originalBlockData to copyBlockData
            incorrectBlocks.add(incorrectBlock)
            // perform checks to see if the block is the same
            if (originalBlock.type != copyBlock.type) {
                continue
            }
            // special case for mushroom blocks: the sides are too difficult to replicate, so instead just ignore and check only for type
            if (originalBlock.type == Material.RED_MUSHROOM_BLOCK ||
                originalBlock.type == Material.BROWN_MUSHROOM_BLOCK ||
                originalBlock.type == Material.MUSHROOM_STEM
            ) {
                correctBlocks++
                incorrectBlocks.remove(incorrectBlock)
                continue
            }
            // special case for trapdoors
            if (originalBlockData is TrapDoor && copyBlockData is TrapDoor) {
                if (originalBlockData.isOpen != copyBlockData.isOpen) {
                    continue
                }
                // check for half (must be same when not open)
                if (!originalBlockData.isOpen && originalBlockData.half != copyBlockData.half) {
                    continue
                }
                // check for direction (must be same when powered)
                if (originalBlockData.isOpen && originalBlockData.facing != copyBlockData.facing) {
                    continue
                }
                correctBlocks++
                incorrectBlocks.remove(incorrectBlock)
                continue
            }
            // special case for gates
            if (originalBlockData is Gate && copyBlockData is Gate) {
                if (originalBlockData.isOpen != copyBlockData.isOpen) {
                    continue
                }
                // check for direction (same when open, same or opposite when closed)
                if (originalBlockData.isOpen && originalBlockData.facing != copyBlockData.facing) {
                    continue
                }
                if (!originalBlockData.isOpen &&
                    originalBlockData.facing != copyBlockData.facing.oppositeFace &&
                    originalBlockData.facing != copyBlockData.facing
                ) {
                    continue
                }
                correctBlocks++
                incorrectBlocks.remove(incorrectBlock)
                continue
            }
            // blocks where only type should be checked (the data is too complicated)
            if (originalBlockData is Fence || originalBlockData is GlassPane) {
                correctBlocks++
                incorrectBlocks.remove(incorrectBlock)
                continue
            }
            // special code for stairs
            if (originalBlockData is Stairs && copyBlockData is Stairs) {
                // check for half
                if (originalBlockData.half != copyBlockData.half) {
                    continue
                }
                // check if shape is STRAIGHT and facing is the same
                if (originalBlockData.shape == Stairs.Shape.STRAIGHT &&
                    (originalBlockData.shape != copyBlockData.shape || originalBlockData.facing != copyBlockData.facing)
                ) {
                    continue
                }
                // the rest is too damn difficult to check
                correctBlocks++
                incorrectBlocks.remove(incorrectBlock)
                continue
            }
            // general block data check
            if (!originalBlockData.matches(copyBlockData)) {
                continue
            }
            // special code for banners
            if (originalBlock is Banner && copyBlock is Banner) {
                if (originalBlock.patterns != copyBlock.patterns) {
                    continue
                }
            }

            correctBlocks++
            incorrectBlocks.remove(incorrectBlock)
        }
        val originalEntities = original.entities
        val copyEntities = copy.entities
        var correctEntities = 0
        // go through every entity in the copy structure
        for (copyEntity in copyEntities) {
            val originalEntity =
                originalEntities.firstOrNull { originalEntity ->
                    if (originalEntity.type != copyEntity.type) {
                        return@firstOrNull false
                    }
                    val originalLocation = originalEntity.location
                    val copyLocation = copyEntity.location
                    originalLocation.blockX == copyLocation.blockX &&
                        originalLocation.blockY == copyLocation.blockY &&
                        originalLocation.blockZ == copyLocation.blockZ
                }
            if (originalEntity == null) {
                continue
            }
            // check if the rotation is the same
            if (originalEntity.location.yaw != copyEntity.location.yaw) {
                continue
            }
            correctEntities++
        }
        val accuracy = (correctBlocks + correctEntities).toDouble() / (originalBlocks.size + originalEntities.size)
        return StructureAccuracy(accuracy, incorrectBlocks)
    }

    private fun calculateAccuracy(
        original: Structure,
        location: Location,
    ): StructureAccuracy {
        // create a strcture based on the play area
        val endPos =
            location
                .clone()
                .toBlockLocation()
                .add(PLAYER_AREA_SIZE.toDouble(), PLAYER_AREA_SIZE.toDouble(), PLAYER_AREA_SIZE.toDouble())
        val copy = structureManager.createStructure()
        copy.fill(location, endPos, true)
        return calculateAccuracy(original, copy)
    }

    private fun giveItemFromEntity(
        entity: Entity,
        player: Player,
    ) {
        val entityType = entity.type
        if (!entityType.isSpawnable || !entityType.isAlive) {
            return
        }
        // attempt to get the spawn egg material
        val spawnEggMaterialName = "${entityType.name}_SPAWN_EGG"
        val spawnEggMaterial = Material.matchMaterial(spawnEggMaterialName)
        if (spawnEggMaterial != null) {
            player.inventory.addItem(ItemStack.of(spawnEggMaterial))
        } else {
            // just create a polar spawn egg that spawns the entity
            val spawnEgg = ItemStack.of(Material.POLAR_BEAR_SPAWN_EGG)
            spawnEgg.editMeta { meta ->
                meta as SpawnEggMeta
                meta.displayName(Component.text("${entityType.name} Spawn Egg"))
                meta.customSpawnedType = entityType
            }
            player.inventory.addItem(spawnEgg)
        }
    }

    private fun giveItemsFromStructure(
        structure: Structure,
        player: Player,
    ) {
        val blocks = structure.palettes[0].blocks.filter { it.type != Material.AIR && it.location.y > 0 }
        blocks.forEach { block ->
            giveItemFromBlock(block, player)
        }
        val entities = structure.entities
        entities.forEach { entity ->
            giveItemFromEntity(entity, player)
        }
    }

    private fun clearPlayerArea(
        playerArea: CuboidRegion,
        withFloor: Boolean,
    ) {
        val clearRegion =
            when (withFloor) {
                true -> playerArea

                false -> {
                    // offset the player area by 1 block
                    val pos1 = playerArea.pos1.add(0, 1, 0)
                    CuboidRegion(pos1, playerArea.pos2)
                }
            }
        for (vec in clearRegion) {
            val location = Location(startPos.world, vec.x().toDouble(), vec.y().toDouble(), vec.z().toDouble())
            location.block.type = Material.AIR
        }
        for (entity in startPos.world.entities.filter { entity ->
            playerArea.contains(entity.location.toBlockVector()) &&
                entity.persistentDataContainer.has(
                    SPAWNED_ENTITY_KEY,
                    PersistentDataType.BOOLEAN,
                )
        }) {
            entity.remove()
        }
    }

    private fun eliminatePlayer(playerUUID: UUID) {
        // clear the player area
        val playerArea = playerAreas[playerUUID] ?: return
        clearPlayerArea(playerArea, true)
        // remove the player from the playerAreas map
        playerAreas.remove(playerUUID)
        val player = Bukkit.getPlayer(playerUUID) ?: return
        if (player.isOnline) {
            // put into spectator mode
            player.gameMode = GameMode.SPECTATOR
        }
        audience.sendMessage(Component.text("${player.name} has been eliminated!", NamedTextColor.RED))
    }

    private fun giveItemFromBlock(
        blockState: BlockState,
        player: Player,
        ignoreBisected: Boolean = false,
    ) {
        // special code for fire
        if (blockState.type == Material.FIRE) {
            player.inventory.addItem(ItemStack.of(Material.FIRE_CHARGE))
            return
        }
        val blockData = blockState.blockData
        val item = ItemStack.of(blockData.placementMaterial)
        // special code for banners
        if (blockState is Banner) {
            item.editMeta { meta ->
                meta as BannerMeta
                meta.patterns = blockState.patterns
            }
        }
        // special code for slabs
        if (blockData is Slab) {
            // give twice the item if the slab is double-height
            if (blockData.type == Slab.Type.DOUBLE) {
                item.amount = 2
            }
        }
        // special code for chiseled bookshelves
        if (blockData is ChiseledBookshelf && blockData.occupiedSlots.size > 0) {
            val books = ItemStack.of(Material.BOOK, blockData.occupiedSlots.size)
            player.inventory.addItem(books)
        }
        // special code for double blocks (door, tall grass, tall flowers etc. make sure to ignore stairs and trapdoors)
        if (!ignoreBisected &&
            blockData is Bisected &&
            blockData.half == Bisected.Half.TOP &&
            blockData !is Stairs &&
            blockData !is TrapDoor
        ) {
            // by returning, we only give an item for the bottom half of the block, instead of giving an item twice
            return
        }
        player.inventory.addItem(item)
    }

    override fun handleDisconnect(
        player: Player,
        didLeave: Boolean,
    ) {
        if (didLeave) {
            eliminatePlayer(player.uniqueId)
            if (game.onlinePlayers.filter { it.gameMode == GameMode.SURVIVAL }.size <= 1) {
                win()
            }
        }
    }

    override fun handleRejoin(player: Player) {
        player.allowFlight = true
        player.isFlying = true

        when (state) {
            SpeedBuildersState.MEMORISE -> {
                player.gameMode = GameMode.SURVIVAL
                player.showBossBar(game.remainingBossBar)
                val playerArea = playerAreas[player.uniqueId]!!
                val location = playerArea.toLocation(startPos.world)
                player.teleport(location.clone().add(-0.5, 1.0, -0.5))
            }

            SpeedBuildersState.BUILD -> {
                player.gameMode = GameMode.SURVIVAL
                player.showBossBar(game.remainingBossBar)

                if (!gotItems.contains(player.uniqueId)) {
                    // give the player the items from the structure
                    player.inventory.clear()
                    giveItemsFromStructure(currentStructure!!, player)
                    gotItems.add(player.uniqueId)
                }
            }

            SpeedBuildersState.JUDGE -> {
                player.gameMode = GameMode.SPECTATOR
            }

            SpeedBuildersState.FINISHED -> {
                player.gameMode = GameMode.SPECTATOR
            }
        }

        super.handleRejoin(player)
    }

    override fun handleBlockBreakProgressUpdate(event: BlockBreakProgressUpdateEvent) {
        if (event.entity !is Player) return
        if (state != SpeedBuildersState.BUILD) return
        val player = event.entity as Player
        // first check if the block's coordinates are in the play area
        val playerArea = playerAreas[player.uniqueId] ?: return
        val blockPos = event.block.location
        if (blockPos.y.toInt() == playerArea.pos1.y()) {
            // the player is trying to break the floor
            return
        }
        if (!playerArea.contains(blockPos.toBlockVector())) {
            return
        }
        // check for the cooldown
        val cooldown = blockBreakCooldowns[event.entity.uniqueId]
        if (cooldown != null && System.currentTimeMillis() - cooldown < 150) {
            return
        }
        blockBreakCooldowns[event.entity.uniqueId] = System.currentTimeMillis()
        // give the player the item from the block
        giveItemFromBlock(event.block.state, player, true)
        // break the block without dropping it
        event.block.type = Material.AIR
    }

    override fun handleBlockBreak(event: BlockBreakEvent) {
        if (state != SpeedBuildersState.BUILD) {
            return
        }
        val player = event.player
        val playerArea = playerAreas[player.uniqueId] ?: return
        val blockLocation = event.block.location
        if (blockLocation.blockY == playerArea.pos1.y()) {
            // the player is trying to break the floor
            return
        }
        if (!playerArea.contains(blockLocation.toBlockVector())) {
            return
        }
        blockBreakCooldowns[event.player.uniqueId] = System.currentTimeMillis()
        giveItemFromBlock(event.block.state, player)
        event.block.type = Material.AIR
    }

    private fun checkForPerfect(player: Player) {
        val structure = currentStructure ?: return
        if (player.gameMode != GameMode.SURVIVAL || state != SpeedBuildersState.BUILD) {
            return
        }
        val playerArea = playerAreas[player.uniqueId]!!
        val accuracyResult = calculateAccuracy(structure, playerArea.toLocation(startPos.world))
        if (accuracyResult.accuracy == 1.0) {
            player.gameMode = GameMode.SPECTATOR
            audience.sendMessage(mm.deserialize("<yellow>${player.name} has a perfect build!"))
            if (onlinePlayers.none { it.gameMode == GameMode.SURVIVAL }) {
                startJudge()
            }
        }
    }

    override fun handleBlockPlace(event: BlockPlaceEvent) {
        if (state != SpeedBuildersState.BUILD) {
            event.isCancelled = true
            return
        }
        val player = event.player
        // check if the block's coordinates are in the player area
        val playerArea = playerAreas[player.uniqueId] ?: return
        val blockPos = event.block.location
        if (!playerArea.contains(blockPos.toBlockVector())) {
            event.isCancelled = true
            player.sendMessage(Component.text("You can only place blocks in your play area!", NamedTextColor.RED))
            return
        }
        // check if the block would replace another block
        val replacedState = event.blockReplacedState
        if (replacedState.type != Material.AIR) {
            giveItemFromBlock(replacedState, player)
        }
        checkForPerfect(player)
    }

    override fun handleBlockPhysics(event: BlockPhysicsEvent) {
        // only listen to blocks that break
        if (event.block.type == Material.AIR) {
            return
        }
        // ignore floor blocks
        if (event.block.location.blockY == startPos.blockY) {
            event.isCancelled = true
            return
        }
        // check if the block is still supported
        if (event.block.blockData.isSupported(event.block.location)) {
            return
        }
        // find the player location
        val location = event.block.location.toBlockVector()
        val playerArea = playerAreas.entries.firstOrNull { it.value.contains(location) } ?: return
        val player = Bukkit.getPlayer(playerArea.key) ?: return
        // give the player the item from the block
        giveItemFromBlock(event.block.state, player)
        // break the block without dropping it
        event.block.type = Material.AIR
    }

    override fun handlePlayerMove(event: PlayerMoveEvent) {
        if (event.player.gameMode == GameMode.SPECTATOR) {
            return
        }
        val playerArea = playerAreas[event.player.uniqueId] ?: return
        // the player may not leave the player area
        val pos = playerArea.pos1
        if (event.to.x < pos.x() - 2 ||
            event.to.x > pos.x() + PLAYER_AREA_SIZE + 2 ||
            event.to.y < pos.y() ||
            event.to.y > pos.y() + PLAYER_AREA_SIZE + 1 ||
            event.to.z < pos.z() - 2 ||
            event.to.z > pos.z() + PLAYER_AREA_SIZE + 2
        ) {
            event.isCancelled = true
            event.player.sendMessage(Component.text("You cannot leave your play area!", NamedTextColor.RED))
        }
    }

    override fun handleInventoryOpen(event: InventoryOpenEvent) {
        // only let players open their own inventory
        if (event.inventory.holder !is Player) {
            event.isCancelled = true
        }
    }

    override fun handlePlayerInteract(event: PlayerInteractEvent) {
        // no interactions during the memorise phase
        if (state == SpeedBuildersState.MEMORISE) {
            event.setUseInteractedBlock(Event.Result.DENY)
            event.setUseItemInHand(Event.Result.DENY)
        }
        if (state == SpeedBuildersState.BUILD) {
            // check if the player tried to place a spawn egg and cancel the event if it was in a wrong position
            kotlin.run {
                val item = event.item ?: return@run
                if (item.itemMeta !is SpawnEggMeta) return@run
                val block = event.clickedBlock ?: return@run
                val finalBlock = block.getRelative(event.blockFace)
                val playerArea = playerAreas[event.player.uniqueId] ?: return@run
                if (!playerArea.contains(finalBlock.location.toBlockVector())) {
                    event.setUseInteractedBlock(Event.Result.DENY)
                    event.setUseItemInHand(Event.Result.DENY)
                }
            }
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable {
                    if (state != SpeedBuildersState.BUILD) {
                        return@Runnable
                    }
                    checkForPerfect(event.player)
                },
                1,
            )
        }
    }

    override fun handleCreatureSpawn(
        event: CreatureSpawnEvent,
        player: Player,
    ) {
        val snappedAngle = snapTo90(player.location.yaw)
        val spawnee = event.entity
        spawnee.setRotation(snappedAngle, 0.0f)
        spawnee.setAI(false)
        spawnee.isSilent = true
        spawnee.persistentDataContainer.set(
            SPAWNED_ENTITY_KEY,
            PersistentDataType.BOOLEAN,
            true,
        )
    }

    override fun handlePrePlayerAttack(event: PrePlayerAttackEntityEvent) {
        val player = event.player
        val target = event.attacked
        if (state != SpeedBuildersState.BUILD) {
            return
        }
        if (!target.persistentDataContainer.has(SPAWNED_ENTITY_KEY, PersistentDataType.BOOLEAN)) {
            return
        }
        val playerArea = playerAreas[player.uniqueId] ?: return
        if (!playerArea.contains(target.location.toBlockVector())) {
            return
        }
        target.remove()
        giveItemFromEntity(target, player)
    }

    private fun startMemorise() {
        state = SpeedBuildersState.MEMORISE
        // make sure that every player who became a spectator the last game due to perfect build is in survival again
        for (player in game.onlinePlayers.filter { playerAreas.containsKey(it.uniqueId) }) {
            player.gameMode = GameMode.SURVIVAL
            player.allowFlight = true
            player.isFlying = true
        }
        // calculate the chances of each difficulty based on the round
        round += 1
        val easyWeight = (-0.006 * round.pow(3) + 0.25 * round.pow(2) - 4.1 * round + 25).coerceAtLeast(0.0)
        val mediumWeight = (-0.01 * round.pow(2) + 0.4 * round + 4).coerceAtLeast(0.0)
        val hardWeight = (-0.0625 * round.pow(2) + 3.375 * round - 31.5).coerceAtLeast(0.0)
        val insaneWeight = (exp(0.095 * round) - 10).coerceAtLeast(0.0)
        val difficulty =
            listOf(
                WeightedItem(StructureDifficulty.EASY, (easyWeight * 100).toInt()),
                WeightedItem(StructureDifficulty.MEDIUM, (mediumWeight * 100).toInt()),
                WeightedItem(StructureDifficulty.HARD, (hardWeight * 100).toInt()),
                WeightedItem(StructureDifficulty.INSANE, (insaneWeight * 100).toInt()),
            ).selectWeightedRandom()
        // select a random structure based on the difficulty
        currentStructureData = selectStructure(difficulty)
        val structure = getStructure()
        currentStructure = structure
        audience.sendTitlePart(
            TitlePart.TIMES,
            Title.Times.times(Duration.ofSeconds(0), Duration.ofSeconds(2), Duration.ofSeconds(0)),
        )
        audience.sendTitlePart(TitlePart.TITLE, mm.deserialize("<yellow>${currentStructureData!!.displayName}"))
        audience.sendMessage(Component.text("Memorise the structure!", NamedTextColor.GREEN))
        // set up player areas
        for ((playerUUID, playerArea) in playerAreas) {
            clearPlayerArea(playerArea, false)
            val player = Bukkit.getPlayer(playerUUID) ?: continue
            // place down the structure in the play area
            val location = playerArea.toLocation(startPos.world)
            structure.place(
                location,
                true,
                StructureRotation.NONE,
                Mirror.NONE,
                0,
                1f,
                Random(),
            )
            // teleport the player to the platform
            player.teleport(location.clone().add(-0.5, 1.0, -0.5))
            player.inventory.clear()
        }
        startCountdown(10 * 20) {
            startBuild()
        }
    }

    private fun startBuild() {
        state = SpeedBuildersState.BUILD
        gotItems.clear()
        val structure = currentStructure!!
        for ((playerUUID, playerArea) in playerAreas) {
            // clear the player area
            clearPlayerArea(playerArea, false)
            // give items to the player
            val player = Bukkit.getPlayer(playerUUID) ?: continue
            player.inventory.clear()
            giveItemsFromStructure(structure, player)
            gotItems.add(playerUUID)
        }
        // remove every entity spawned by the structure
        for (entity in game.world.entities.filter {
            it.persistentDataContainer.has(
                NamespacedKey(
                    plugin,
                    "spawned",
                ),
                PersistentDataType.BOOLEAN,
            )
        }) {
            entity.remove()
        }
        audience.sendMessage(Component.text("Build the structure!", NamedTextColor.GREEN))
        // the duration of the build phase is 30 seconds + 1 second per 5 blocks in the structure
        // to get the block count, just check the player's inventory and count how many items it has in total
        val blocksInStructure =
            game.onlinePlayers
                .first()
                .inventory.contents
                .filterNotNull()
                .sumOf { it.amount }
        val buildDuration = (30 + blocksInStructure / 5) * 20
        startCountdown(buildDuration) {
            startJudge()
        }
    }

    private fun startJudge() {
        // we need to stop the countdown in case we got here due to everyone reaching a perfect build
        stopCountdown()

        state = SpeedBuildersState.JUDGE
        val structure = currentStructure!!
        val accuracies =
            playerAreas.entries.associate { (player, playerArea) ->
                val location = playerArea.toLocation(startPos.world)
                player to calculateAccuracy(structure, location)
            }
        val baseScore = scores.getOrDefault(currentStructureData!!.difficulty, 0)

        for (player in game.onlinePlayers) {
            player.gameMode = GameMode.SPECTATOR
        }
        // show the accuracy of the player's structure and add score
        for ((playerUUID, structureAccuracy) in accuracies) {
            val player = Bukkit.getPlayer(playerUUID) ?: continue
            val accuracyString = String.format("%.2f", structureAccuracy.accuracy * 100)
            player.showTitle(
                Title.title(
                    Component.text("Accuracy: $accuracyString%", NamedTextColor.GREEN),
                    Component.empty(),
                    Title.Times.times(Duration.ofSeconds(0), Duration.ofSeconds(5), Duration.ofSeconds(0)),
                ),
            )
            if (structureAccuracy.accuracy == 1.0) {
                game.addScore(player, (baseScore * 2.5).toInt(), "Perfect build")
            } else {
                game.addScore(player, (structureAccuracy.accuracy * baseScore).toInt(), "$accuracyString% accuracy")
                val incorrectMessage =
                    Component.text("Incorrect blocks: ", NamedTextColor.GRAY).append(
                        Component.join(
                            JoinConfiguration.commas(true),
                            structureAccuracy.incorrectBlocks.map { (original, copy) ->
                                val hoverText =
                                    buildString {
                                        append("Expected: $original, got: ${copy?.toString() ?: "nothing"}}")
                                    }
                                val copyType = copy?.material ?: Material.AIR
                                GlobalTranslator.render(
                                    Component
                                        .translatable(
                                            copyType.translationKey(),
                                            NamedTextColor.RED,
                                        ).hoverEvent(Component.text(hoverText)),
                                    player.locale(),
                                )
                            },
                        ),
                    )
                player.sendMessage(incorrectMessage)
            }
        }
        // start a 5-second countdown and eliminate the worst players
        startCountdown(5 * 20, false) {
            // we may only eliminate max 1/5th of the playing players
            // a player is considered playing if they are in the playerAreas map
            val alivePlayers = game.onlinePlayers.filter { player -> playerAreas.containsKey(player.uniqueId) }
            val playersToEliminate = max(5, alivePlayers.size) / 5
            // create a worstPlayers list which is based on the first playerToEliminate
            // elements of the ascending sorted list of accuracies (excluding perfect matches)
            val worstPlayers =
                accuracies.entries
                    .filter { (_, accuracy) -> accuracy.accuracy < 1.0 }
                    .sortedBy { (_, accuracy) -> accuracy.accuracy }
                    .take(playersToEliminate)
                    .map { (player, _) -> player }
            if (worstPlayers.isEmpty()) {
                audience.sendMessage(Component.text("Phew, no one got eliminated!", NamedTextColor.GREEN))
            } else {
                worstPlayers.forEach { player -> eliminatePlayer(player) }
            }
            // look for win condition (only one player remaining)
            if (alivePlayers.size - worstPlayers.size <= 1 && System.getProperty("partygames.dev", "false") != "true") {
                win()
            } else {
                // start a 3-second countdown to start the next round
                startCountdown(3 * 20, false) {
                    startMemorise()
                }
            }
        }
    }

    private fun win() {
        state = SpeedBuildersState.FINISHED
        val winner = game.onlinePlayers.firstOrNull { player -> playerAreas.containsKey(player.uniqueId) }
        audience.sendMessage(Component.text("The winner is ${winner?.name ?: "Nobody"}!", NamedTextColor.GREEN))
        if (winner != null) {
            game.addScore(winner, 25, "You won!")
        }
        end()
    }

    override fun onLoad() {
        game.world.difficulty = Difficulty.NORMAL
        super.onLoad()
    }

    override fun start() {
        super.start()
        // set up the player area for every player
        for ((i, player) in onlinePlayers.withIndex()) {
            val playerAreaRoot =
                startPos.add(
                    (i % 7) * (PLAYER_AREA_SIZE + AREA_OFFSET).toDouble(),
                    0.0,
                    (i / 7) * (PLAYER_AREA_SIZE + AREA_OFFSET).toDouble(),
                )
            playerAreas[player.uniqueId] = playerAreaRoot.toPlayerArea()
            player.allowFlight = true
        }

        startMemorise()
    }

    override val name = Component.text("Speed builders", NamedTextColor.AQUA)
    override val description =
        Component.text(
            "You will be given a random structure you have to memorise in 10 seconds.\n" +
                "After the time runs out, you will have a little time to replicate the structure.",
            NamedTextColor.AQUA,
        )
}
