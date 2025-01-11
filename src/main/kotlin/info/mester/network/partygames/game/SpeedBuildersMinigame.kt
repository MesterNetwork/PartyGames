package info.mester.network.partygames.game

import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.pow
import info.mester.network.partygames.util.WeightedItem
import info.mester.network.partygames.util.selectWeightedRandom
import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Banner
import org.bukkit.block.BlockState
import org.bukkit.block.data.type.ChiseledBookshelf
import org.bukkit.block.data.type.Slab
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta
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
}

// StructureData data class which holds the name, difficulty and file name of the structure
data class StructureData(
    val name: String,
    val difficulty: StructureDifficulty,
) {
    val fileName = "${name.lowercase()}.nbt"
}

const val PLAYER_AREA_SIZE = 7

private fun Location.toBlockVector(): BlockVector3 = BlockVector3.at(blockX, blockY, blockZ)

private fun Location.toPlayerArea(): CuboidRegion {
    val corner1 = clone()
    val corner2 =
        clone().add(PLAYER_AREA_SIZE.toDouble(), PLAYER_AREA_SIZE.toDouble(), PLAYER_AREA_SIZE.toDouble())
    return CuboidRegion(corner1.toBlockVector(), corner2.toBlockVector())
}

private val silkTouchPickaxe =
    ItemStack.of(Material.NETHERITE_PICKAXE).apply {
        addEnchantment(Enchantment.SILK_TOUCH, 1)
    }

/**
 * How much padding should be between the player areas.
 */
const val AREA_OFFSET = 5

class SpeedBuildersMinigame(
    game: Game,
) : Minigame(game, "speedbuilders") {
    companion object {
        val plugin = PartyGames.plugin
        private val structures = mutableListOf<StructureData>()

        fun reload() {
            val config = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "speed-builders.yml"))
            // load structures from "structures" section
            plugin.logger.info("Loading structures...")
            structures.clear()
            config.getConfigurationSection("structures")?.getKeys(false)?.forEach { key ->
                try {
                    val structureConfig = config.getConfigurationSection("structures.$key")!!
                    val difficulty = structureConfig.getString("difficulty")!!
                    structures.add(StructureData(key, StructureDifficulty.valueOf(difficulty.uppercase())))
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to load structure $key")
                    plugin.logger.log(Level.WARNING, e.message, e)
                }
            }
            // load the structure files
            for (structureData in structures) {
                plugin.saveResource("speedbuilders/${structureData.fileName}", true)
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
    private val blockBreakCooldowns = mutableMapOf<UUID, Long>()
    private var round = 0

    private fun getStructure(): Structure {
        // load structure from plugin.dataFolder/speedbuilders/structureData.fileName
        if (currentStructureData == null) {
            throw IllegalStateException("No structure data is selected!")
        }
        val structureData = currentStructureData!!
        val structureFile = structureData.fileName
        return structureManager.loadStructure(File(plugin.dataFolder, "speedbuilders/$structureFile"))
    }

    private fun selectStructure(difficulty: StructureDifficulty?): StructureData {
        // select a random structure based on the difficulty
        // if difficulty is null, select a random structure
        val structureData =
            difficulty?.let { structures.filter { it.difficulty == difficulty }.random() } ?: structures.random()
        return structureData
    }

    private fun calculateAccuracy(
        original: Structure,
        copy: Structure,
    ): Double {
        // for original blocks, disregard the very bottom layer (the floor)
        val originalBlocks = original.palettes[0].blocks.filter { it.type != Material.AIR && it.location.y > 0 }
        val copyBlocks = copy.palettes[0].blocks.filter { it.type != Material.AIR && it.location.y > 0 }
        var correctBlocks = 0
        // go through every block in the original structure
        for (originalBlock in originalBlocks) {
            // get the block in the copy structure at the same location
            val copyBlock = copyBlocks.firstOrNull { it.location == originalBlock.location } ?: continue
            // perform checks to see if the block is the same
            if (originalBlock.type != copyBlock.type) {
                continue
            }
            // special case for white and red mushroom blocks: the sides are too difficult to replicate, so instead just ignore and check only for type
            if (originalBlock.type == Material.RED_MUSHROOM_BLOCK || originalBlock.type == Material.BROWN_MUSHROOM_BLOCK) {
                correctBlocks++
                continue
            }
            if (!originalBlock.blockData.matches(copyBlock.blockData)) {
                continue
            }
            // special code for banners
            if (originalBlock is Banner && copyBlock is Banner) {
                if (originalBlock.patterns != copyBlock.patterns) {
                    continue
                }
            }
            correctBlocks++
        }
        return correctBlocks.toDouble() / originalBlocks.size
    }

    private fun calculateAccuracy(
        original: Structure,
        location: Location,
    ): Double {
        // create a strcture based on the play area (+1 to make sure the edges are included)
        val endPos =
            location
                .clone()
                .toBlockLocation()
                .add(PLAYER_AREA_SIZE.toDouble(), PLAYER_AREA_SIZE.toDouble(), PLAYER_AREA_SIZE.toDouble())
        val copy = structureManager.createStructure()
        copy.fill(location, endPos, true)
        return calculateAccuracy(original, copy)
    }

    private fun giveItemsFromStructure(
        structure: Structure,
        player: Player,
    ) {
        val blocks = structure.palettes[0].blocks.filter { it.type != Material.AIR && it.location.y > 0 }
        blocks.forEach { block ->
            giveItemFromBlock(block, player)
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
        if (blockData is ChiseledBookshelf) {
            val books = ItemStack.of(Material.BOOK, blockData.occupiedSlots.size)
            player.inventory.addItem(books)
        }
        player.inventory.addItem(item)
    }

    override fun handleDisconnect(
        player: Player,
        didLeave: Boolean,
    ) {
        if (game.onlinePlayers.filter { it.gameMode == GameMode.SURVIVAL }.size <= 1) {
            win()
        }
    }

    fun handleBlockBreakProgressUpdate(event: BlockBreakProgressUpdateEvent) {
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
        giveItemFromBlock(event.block.state, player)
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
        if (blockLocation.y.toInt() == playerArea.pos1.y()) {
            // the player is trying to break the floor
            return
        }
        if (!playerArea.contains(blockLocation.toBlockVector())) {
            return
        }
        giveItemFromBlock(event.block.state, player)
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
        val blockVector = BlockVector3.at(blockPos.x, blockPos.y, blockPos.z)
        if (!playerArea.contains(blockVector)) {
            event.isCancelled = true
            player.sendMessage(Component.text("You can only place blocks in your play area!", NamedTextColor.RED))
            return
        }
        // check if the block would replace another block
        val replacedState = event.blockReplacedState
        if (replacedState.type != Material.AIR) {
            giveItemFromBlock(replacedState, player)
        }
    }

    override fun handleBlockPhysics(event: BlockPhysicsEvent) {
        // only listen to blocks that break
        if (event.block.type == Material.AIR) {
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
            event.isCancelled = true
        }
    }

    private fun startMemorise() {
        state = SpeedBuildersState.MEMORISE
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
        for ((playerUUID, playerArea) in playerAreas) {
            val player = Bukkit.getPlayer(playerUUID) ?: continue
            player.sendMessage(Component.text("Memorise the structure!", NamedTextColor.GREEN))
            // place down the structure in the play area
            val pos1 = playerArea.pos1
            val pos1Location = Location(startPos.world, pos1.x().toDouble(), pos1.y().toDouble(), pos1.z().toDouble())
            structure.place(
                pos1Location,
                true,
                StructureRotation.NONE,
                Mirror.NONE,
                0,
                1f,
                Random(),
            )
            // teleport the player to the platform
            player.teleport(pos1Location.clone().add(-0.5, 1.0, -0.5))
            player.isFlying = true
        }
        startCountdown(10000) {
            startBuild()
        }
    }

    private fun startBuild() {
        state = SpeedBuildersState.BUILD
        for ((playerUUID, playerArea) in playerAreas) {
            val player = Bukkit.getPlayer(playerUUID) ?: continue
            // clear the player area
            clearPlayerArea(playerArea, false)
            // give items to the player
            player.inventory.clear()
            giveItemsFromStructure(getStructure(), player)
        }
        audience.sendMessage(Component.text("Build the structure!", NamedTextColor.GREEN))
        startCountdown(32000) {
            startJudge()
        }
    }

    private fun startJudge() {
        state = SpeedBuildersState.JUDGE
        val structure = getStructure()
        val accuracies =
            playerAreas.entries.associate { (player, playerArea) ->
                val pos1 = playerArea.pos1
                val pos1Location =
                    Location(startPos.world, pos1.x().toDouble(), pos1.y().toDouble(), pos1.z().toDouble())
                player to calculateAccuracy(structure, pos1Location)
            }
        val baseScore =
            when (currentStructureData!!.difficulty) {
                StructureDifficulty.EASY -> 5
                StructureDifficulty.MEDIUM -> 12
                StructureDifficulty.HARD -> 25
                StructureDifficulty.INSANE -> 40
            }
        // show the accuracy of the player's structure and add score
        for ((playerUUID, accuracy) in accuracies) {
            val player = Bukkit.getPlayer(playerUUID) ?: continue
            val accuracyString = String.format("%.2f", accuracy * 100)
            player.showTitle(
                Title.title(
                    Component.text("Accuracy: $accuracyString%", NamedTextColor.GREEN),
                    Component.empty(),
                    Title.Times.times(Duration.ofSeconds(0), Duration.ofSeconds(5), Duration.ofSeconds(0)),
                ),
            )
            if (accuracy == 1.0) {
                game.addScore(player, (baseScore * 2.5).toInt(), "Perfect build")
            } else {
                game.addScore(player, (accuracy * baseScore).toInt(), "$accuracyString% accuracy")
            }
        }
        // start a 5-second countdown and eliminate the worst players
        startCountdown(5000, false) {
            // we may only eliminate max 1/5th of the playing players
            // a player is considered playing if they are in the playerAreas map
            val alivePlayers = game.onlinePlayers.filter { player -> playerAreas.containsKey(player.uniqueId) }
            val playersToEliminate = max(5, alivePlayers.size) / 5
            // create a worstPlayers list which is based on the first playerToEliminate
            // elements of the ascending sorted list of accuracies (excluding perfect matches)
            val worstPlayers =
                accuracies.entries
                    .filter { (_, accuracy) -> accuracy < 1.0 }
                    .sortedBy { (_, accuracy) -> accuracy }
                    .take(playersToEliminate)
                    .map { (player, _) -> player }
            if (worstPlayers.isEmpty()) {
                audience.sendMessage(Component.text("Phew, no one got eliminated!", NamedTextColor.GREEN))
            } else {
                worstPlayers.forEach { player -> eliminatePlayer(player) }
            }
            if (alivePlayers.size - worstPlayers.size == 1) {
                win()
            } else {
                // start a 3-second countdown to start the next round
                startCountdown(3000, false) {
                    // clear every player area
                    for (playerArea in playerAreas.values) {
                        clearPlayerArea(playerArea, false)
                    }
                    startMemorise()
                }
            }
        }
    }

    private fun win() {
        val winner = game.onlinePlayers.first { player -> playerAreas.containsKey(player.uniqueId) }
        audience.sendMessage(Component.text("The winner is ${winner.name}!", NamedTextColor.GREEN))
        end()
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

    override val name: Component
        get() = Component.text("Speed builders", NamedTextColor.AQUA)
    override val description: Component
        get() =
            Component.text(
                "You will be given a random structure you have to memorise in 10 seconds.\n" +
                        "After the time runs out, you will have 32 seconds to replicate the structure.",
                NamedTextColor.AQUA,
            )
}
