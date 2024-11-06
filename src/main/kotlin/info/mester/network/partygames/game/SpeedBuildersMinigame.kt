package info.mester.network.partygames.game

import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitWorld
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.world.block.BlockTypes
import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.structure.Structure
import java.io.File
import java.time.Duration
import java.util.Random
import java.util.UUID
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

// StructureData data class which holds the name, difficult and file name of the structure
data class StructureData(
    val name: String,
    val difficulty: StructureDifficulty,
    val fileName: String,
)

val structures =
    listOf(
        StructureData("bed", StructureDifficulty.EASY, "bed.nbt"),
        StructureData("portal", StructureDifficulty.EASY, "portal.nbt"),
    )
const val PLAYER_AREA_SIZE = 6.0

/**
 * How much padding should be between the player areas.
 */
const val AREA_OFFSET = 5

class SpeedBuildersMinigame : Minigame("locations.minigames.speed-builders") {
    private val structureManager = Bukkit.getStructureManager()
    private val playerAreas = mutableMapOf<UUID, Location>()

    // create a silk touch netherite pickaxe
    private val silkTouchPickaxe =
        ItemStack.of(Material.NETHERITE_PICKAXE).apply {
            addEnchantment(Enchantment.SILK_TOUCH, 1)
        }
    private var state = SpeedBuildersState.MEMORISE
    private val playerAreasAsPlayers: Map<Player, Location>
        get() = playerAreas.entries.associate { (key, value) -> Bukkit.getPlayer(key)!! to value }
    private var currentStructureData: StructureData? = null
    private val blockBreakCooldowns = mutableMapOf<UUID, Long>()

    private fun getStructure(structureData: StructureData): Structure {
        // load structure from plugin.dataFolder/speedbuilders/structureData.fileName
        val structureFile = structureData.fileName
        val structure = structureManager.loadStructure(File(game.plugin.dataFolder, "speedbuilders/$structureFile"))
        return structure
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
        val originalBlocks =
            original.palettes[0].blocks.filter { it.type != Material.AIR && it.location.y > 0 }
        val copyBlocks = copy.palettes[0].blocks.filter { it.type != Material.AIR }
        var correctBlocks = 0
        // go through every block in the copy structure
        for (copyBlock in copyBlocks) {
            val originalBlock =
                originalBlocks.firstOrNull { originalBlock ->
                    originalBlock.location
                        .clone()
                        .add(0.0, -1.0, 0.0) == copyBlock.location &&
                        originalBlock.type == copyBlock.type
                }
            if (originalBlock != null) {
                correctBlocks++
            }
        }
        return correctBlocks.toDouble() / originalBlocks.size
    }

    private fun calculateAccuracy(
        original: Structure,
        location: Location,
    ): Double {
        // create a strcture based on the play area (+1 to make sure the edges are included)
        val endPos = location.clone().add(PLAYER_AREA_SIZE + 1, PLAYER_AREA_SIZE + 1, PLAYER_AREA_SIZE + 1)
        val copy = structureManager.createStructure()
        copy.fill(location, endPos, true)
        return calculateAccuracy(original, copy)
    }

    private fun giveItemsFromStructure(
        structure: Structure,
        player: Player,
    ) {
        val items = structure.palettes[0].blocks.filter { it.type != Material.AIR && it.location.y > 0 }
        items.forEach { item ->
            player.inventory.addItem(ItemStack(item.type))
        }
    }

    private fun clearPlayerArea(
        playerArea: Location,
        editSession: EditSession,
        withFloor: Boolean,
    ) {
        // clear the 7*8*7 area
        val clear1 = BlockVector3.at(playerArea.x, playerArea.y - if (withFloor) 1 else 0, playerArea.z)
        val clear2 =
            BlockVector3.at(
                playerArea.x + PLAYER_AREA_SIZE,
                playerArea.y + PLAYER_AREA_SIZE,
                playerArea.z + PLAYER_AREA_SIZE,
            )
        val clearRegion = CuboidRegion(clear1, clear2)
        editSession.setBlocks(clearRegion, BlockTypes.AIR!!.defaultState)
    }

    private fun eliminatePlayer(player: Player) {
        // clear the player area
        WorldEdit.getInstance().newEditSession(BukkitWorld(startPos.world)).use { editSession ->
            val playerArea = playerAreas[player.uniqueId] ?: return@use
            clearPlayerArea(playerArea, editSession, true)
            // clear the platform below too
            val platform1 = BlockVector3.at(playerArea.x, playerArea.y - 1.0, playerArea.z)
            val platform2 =
                BlockVector3.at(
                    playerArea.x + PLAYER_AREA_SIZE,
                    playerArea.y - 1.0,
                    playerArea.z + PLAYER_AREA_SIZE,
                )
            val platformRegion = CuboidRegion(platform1, platform2)
            editSession.setBlocks(platformRegion, BlockTypes.AIR!!.defaultState)
        }
        // remove the player from the playerAreas map
        playerAreas.remove(player.uniqueId)
        if (player.isOnline) {
            // put into spectator mode
            player.gameMode = GameMode.SPECTATOR
        }
        Bukkit.broadcast(Component.text("${player.name} has been eliminated!", NamedTextColor.RED))
    }

    fun handleBlockBreakProgressUpdate(event: BlockBreakProgressUpdateEvent) {
        if (event.entity !is Player) return
        if (state != SpeedBuildersState.BUILD) return
        val player = event.entity as Player
        // first check if the block's coordinates are in the play area
        val playerArea = playerAreas[player.uniqueId]!!
        val blockPos = event.block.location
        if (blockPos.x < playerArea.x ||
            blockPos.x > playerArea.x + PLAYER_AREA_SIZE ||
            blockPos.y < playerArea.y ||
            blockPos.y > playerArea.y + PLAYER_AREA_SIZE ||
            blockPos.z < playerArea.z ||
            blockPos.z > playerArea.z + PLAYER_AREA_SIZE
        ) {
            return
        }
        // check for the cooldown
        val cooldown = blockBreakCooldowns[event.entity.uniqueId]
        if (cooldown != null && System.currentTimeMillis() - cooldown < 150) {
            return
        }
        blockBreakCooldowns[event.entity.uniqueId] = System.currentTimeMillis()
        // simulate the block breaking with silk touch
        kotlin
            .runCatching {
                event.block.getDrops(silkTouchPickaxe).first()
            }.onSuccess { item -> player.inventory.addItem(item) }
        // break the block without dropping it
        event.block.type = Material.AIR
    }

    override fun handleBlockPlace(event: BlockPlaceEvent) {
        if (state != SpeedBuildersState.BUILD) {
            event.isCancelled = true
            return
        }
        val player = event.player
        // check if the block's coordinates are in the player area
        val playerArea = playerAreas[player.uniqueId]!!
        val blockPos = event.block.location
        if (blockPos.x < playerArea.x ||
            blockPos.x > playerArea.x + PLAYER_AREA_SIZE ||
            blockPos.y < playerArea.y ||
            blockPos.y > playerArea.y + PLAYER_AREA_SIZE ||
            blockPos.z < playerArea.z ||
            blockPos.z > playerArea.z + PLAYER_AREA_SIZE
        ) {
            event.isCancelled = true
            player.sendMessage(Component.text("You can only place blocks in your play area!", NamedTextColor.RED))
            return
        }
    }

    override fun handlePlayerMove(event: PlayerMoveEvent) {
        val playerArea = playerAreas[event.player.uniqueId] ?: return
        // the player may not leave the player area
        if (event.to.x < playerArea.x ||
            event.to.x > playerArea.x + PLAYER_AREA_SIZE + 1 ||
            event.to.y < playerArea.y ||
            event.to.y > playerArea.y + PLAYER_AREA_SIZE + 1 ||
            event.to.z < playerArea.z ||
            event.to.z > playerArea.z + PLAYER_AREA_SIZE + 1
        ) {
            event.isCancelled = true
            event.player.sendMessage(Component.text("You can only move in your play area!", NamedTextColor.RED))
        }
        super.handlePlayerMove(event)
    }

    private fun startMemorise() {
        state = SpeedBuildersState.MEMORISE
        // select a random structure
        currentStructureData = selectStructure(null)
        val structure = getStructure(currentStructureData!!)
        for ((player, playerArea) in playerAreasAsPlayers) {
            player.sendMessage(Component.text("Memorise the structure!", NamedTextColor.GREEN))
            // place down the structure in the play area
            structure.place(
                playerArea.clone().add(0.0, -1.0, 0.0),
                true,
                StructureRotation.NONE,
                Mirror.NONE,
                0,
                1f,
                Random(),
            )
            // teleport the player to the platform
            player.teleport(playerArea.clone().add(0.5, 0.0, 0.5))
        }
        startCountdown(10000) {
            startBuild()
        }
    }

    private fun startBuild() {
        state = SpeedBuildersState.BUILD
        WorldEdit.getInstance().newEditSession(BukkitWorld(startPos.world)).use { editSession ->
            for ((player, playerArea) in playerAreasAsPlayers) {
                player.sendMessage(Component.text("Build the structure!", NamedTextColor.GREEN))
                // clear the player area
                clearPlayerArea(playerArea, editSession, false)
                // give items to the player
                player.inventory.clear()
                giveItemsFromStructure(getStructure(currentStructureData!!), player)
            }
        }
        startCountdown(32000) {
            startJudge()
        }
    }

    private fun startJudge() {
        state = SpeedBuildersState.JUDGE
        val structure = getStructure(currentStructureData!!)
        val accuracies =
            playerAreasAsPlayers.entries.associate { (player, playerArea) ->
                player to calculateAccuracy(structure, playerArea)
            }
        // show the accuracy of the player's structure
        for ((player, accuracy) in accuracies) {
            player.showTitle(
                Title.title(
                    Component.text("Accuracy: ${accuracy * 100}%", NamedTextColor.GREEN),
                    Component.empty(),
                    Title.Times.times(Duration.ofSeconds(0), Duration.ofSeconds(5), Duration.ofSeconds(0)),
                ),
            )
        }
        // start a 5-second countdown and eliminate the worst players
        startCountdown(5000, false) {
            if (!running) return@startCountdown
            // we may only eliminate max 1/5th of the playing players
            // a player is considered playing if they are in the playerAreas map
            val alivePlayers = game.players().filter { player -> playerAreas.containsKey(player.uniqueId) }
            val playersToEliminate = max(5, alivePlayers.size) / 5
            // create a worstPlayers list which is based on the first playerToEliminate
            // elements of the ascending sorted list of accuracies (excluding perfect matches)
            val worstPlayers =
                accuracies.entries
                    .filter { (_, accuracy) -> accuracy < 1.0 }
                    .sortedBy { (_, accuracy) -> accuracy }
                    .take(playersToEliminate)
                    .map { (player, _) -> player }
            worstPlayers.forEach { player -> eliminatePlayer(player) }
            if (worstPlayers.isEmpty()) {
                Bukkit.broadcast(Component.text("Phew, no one got eliminated!", NamedTextColor.GREEN))
            }
            if (alivePlayers.size - worstPlayers.size == 1) {
                // we have a winner!
                val winner = game.players().first { player -> playerAreas.containsKey(player.uniqueId) }
                Bukkit.broadcast(Component.text("The winner is ${winner.name}!", NamedTextColor.GREEN))
                end()
                return@startCountdown
            }
            // clear every player area
            WorldEdit.getInstance().newEditSession(BukkitWorld(startPos.world)).use { editSession ->
                for ((_, playerArea) in playerAreas) {
                    clearPlayerArea(playerArea, editSession, false)
                }
            }
            // start a 3-second countdown to start the next round
            startCountdown(3000, false) {
                startMemorise()
            }
        }
    }

    override fun start() {
        super.start()
        // set up the player area for every player
        for ((i, player) in game.players().withIndex()) {
            val playerArea =
                startPos.add(
                    (i % 7) * (PLAYER_AREA_SIZE + AREA_OFFSET + 1),
                    0.0,
                    (i / 7) * (PLAYER_AREA_SIZE + AREA_OFFSET + 1),
                )
            playerAreas[player.uniqueId] = playerArea
            // teleport the player to the platform
            player.teleport(playerArea.clone().add(0.5, 0.0, 0.5))
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
