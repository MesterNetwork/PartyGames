package info.mester.network.partygames.game

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat
import com.sk89q.worldedit.math.transform.AffineTransform
import com.sk89q.worldedit.regions.CuboidRegion
import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.api.Game
import info.mester.network.partygames.api.IntroductionType
import info.mester.network.partygames.api.Minigame
import io.papermc.paper.entity.TeleportFlag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.structure.Structure
import org.bukkit.util.Vector
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Random
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * The current gravity direction.
 *
 * Visually it represents which original direction is "down" for the player if looking in the positive X direction.
 */
private enum class Gravity(
    val rotateAmount: Double,
) {
    DOWN(0.0),
    UP(180.0),
    LEFT(90.0),
    RIGHT(270.0), ;

    fun relativeTo(other: Gravity): Gravity {
        val rotationChange = (other.rotateAmount - this.rotateAmount + 360.0) % 360.0
        return entries.firstOrNull { it.rotateAmount == rotationChange } ?: DOWN
    }
}

private data class RegisteredSection(
    val structure: Structure,
    val file: File,
    val rotated: MutableMap<Gravity, Structure> = mutableMapOf(),
)

class GravjumpMinigame(
    game: Game,
) : Minigame(game, "gravjump", introductionType = IntroductionType.STATIC) {
    companion object {
        const val SECTION_WIDTH = 15
        const val SECTION_LENGTH = 32
        const val SECTIONS_PER_GAME = 10

        private val plugin = PartyGames.plugin
        lateinit var start: Vector
            private set
        lateinit var wallFrom: Vector
            private set
        lateinit var wallTo: Vector
            private set
        lateinit var sectionStart: Vector
            private set
        private val sections = mutableListOf<RegisteredSection>()

        fun reload() {
            val config = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "gravjump.yml"))
            plugin.logger.info("Loading gravjump config...")
            plugin.saveResource("gravjump/start.nbt", true)

            // load positions
            val startConfig = config.getConfigurationSection("start")
            start =
                Vector(
                    startConfig?.getDouble("x") ?: 0.0,
                    startConfig?.getDouble("y") ?: 0.0,
                    startConfig?.getDouble("z") ?: 0.0,
                )
            val wallFromConfig = config.getConfigurationSection("wall-from")
            wallFrom =
                Vector(
                    wallFromConfig?.getDouble("x") ?: 0.0,
                    wallFromConfig?.getDouble("y") ?: 0.0,
                    wallFromConfig?.getDouble("z") ?: 0.0,
                )
            val wallToConfig = config.getConfigurationSection("wall-to")
            wallTo =
                Vector(
                    wallToConfig?.getDouble("x") ?: 0.0,
                    wallToConfig?.getDouble("y") ?: 0.0,
                    wallToConfig?.getDouble("z") ?: 0.0,
                )
            val sectionStartConfig = config.getConfigurationSection("section-start")
            sectionStart =
                Vector(
                    sectionStartConfig?.getDouble("x") ?: 0.0,
                    sectionStartConfig?.getDouble("y") ?: 0.0,
                    sectionStartConfig?.getDouble("z") ?: 0.0,
                )

            // load sections
            val sectionList = config.getStringList("sections")
            sections.clear()
            for (sectionName in sectionList) {
                val structurePath = "gravjump/$sectionName.nbt"

                val structureInJar = plugin.getResource(structurePath) != null
                if (structureInJar) {
                    plugin.saveResource(structurePath, true)
                }

                val structureFile = File(plugin.dataFolder, structurePath)
                if (!structureFile.exists()) {
                    plugin.logger.warning("Structure file $structurePath does not exist!")
                    continue
                }

                val section =
                    RegisteredSection(
                        Bukkit.getStructureManager().loadStructure(structureFile),
                        structureFile,
                    )
                setupSectionRotations(section)

                sections.add(section)
            }
        }

        private fun setupSectionRotations(section: RegisteredSection) {
            section.rotated.clear()
            section.rotated[Gravity.DOWN] = section.structure

            for (gravity in Gravity.entries.filterNot { it == Gravity.DOWN }) {
                val format = BuiltInClipboardFormat.MINECRAFT_STRUCTURE
                val clipboard =
                    format.getReader(FileInputStream(section.file)).use {
                        it.read()
                    }

                // rotate according to the gravity
                val rotate = AffineTransform().rotateX(-gravity.rotateAmount)
                val target = clipboard.transform(rotate)

                val tempFile = File(plugin.dataFolder, "gravjump/temp-${UUID.randomUUID()}-$gravity.nbt")
                format.getWriter(FileOutputStream(tempFile)).use { writer ->
                    writer.write(target)
                }

                val rotatedStructure = Bukkit.getStructureManager().loadStructure(tempFile)
                section.rotated[gravity] = rotatedStructure

                tempFile.delete()
            }
        }
    }

    private val placedSections = mutableListOf<RegisteredSection>()
    private var gravity = Gravity.DOWN

    private fun setupMap() {
        val startStructure =
            Bukkit.getStructureManager().loadStructure(
                File(originalPlugin.dataFolder, "gravjump/start.nbt"),
            )
        startStructure.place(
            start.clone().toLocation(startPos.world),
            true,
            StructureRotation.NONE,
            Mirror.NONE,
            0,
            1f,
            Random(),
        )

        for (i in 0 until SECTIONS_PER_GAME) {
            // randomly select a section from the list
            val section = sections.randomOrNull() ?: continue
            placedSections.add(section)

            val sectionLocation =
                sectionStart.clone().toLocation(startPos.world).add(i * SECTION_LENGTH.toDouble(), 0.0, 0.0)
            section.structure.place(
                sectionLocation,
                true,
                StructureRotation.NONE,
                Mirror.NONE,
                0,
                1f,
                Random(),
            )
        }
    }

    override fun onLoad() {
        setupMap()
        startPos.world.setGameRule(GameRule.DO_TILE_DROPS, false)
        super.onLoad()
    }

    override fun start() {
        super.start()

        startCountdown(5 * 20) {
            // remove the wall
            val wallStart = BukkitAdapter.asBlockVector(wallFrom.toLocation(startPos.world))
            val wallEnd = BukkitAdapter.asBlockVector(wallTo.toLocation(startPos.world))
            val wall = CuboidRegion(wallStart, wallEnd)
            for (vec in wall) {
                val location = Location(startPos.world, vec.x().toDouble(), vec.y().toDouble(), vec.z().toDouble())
                location.block.type = Material.AIR
            }

            Bukkit.getScheduler().runTaskTimer(plugin, { t ->
                if (!running) {
                    t.cancel()
                    return@runTaskTimer
                }

                val newGravity = Gravity.entries.filter { it != gravity }.random()
                audience.sendMessage(
                    Component.text(
                        "Gravity is about to change! Your ${
                            gravity.relativeTo(
                                newGravity,
                            ).name.lowercase()
                        } will be your new down in 3 seconds!",
                        NamedTextColor.GRAY,
                    ),
                )
                startCountdown(3 * 20, false) {
                    flip(newGravity)
                }
            }, 4 * 20, 20 * 20)
        }
    }

    /**
     * Flips the entire map according to the current gravity direction.
     */
    private fun flip(new: Gravity? = null) {
        // first select a random gravity direction
        val originalGravity = gravity
        gravity = new ?: Gravity.entries.filter { it != gravity }.random()

        for (i in 0 until SECTIONS_PER_GAME) {
            val rotatedStructure = placedSections[i].rotated[gravity] ?: continue
            val sectionLocation =
                sectionStart.clone().toLocation(startPos.world).add(i * SECTION_LENGTH.toDouble(), 0.0, 0.0)

            rotatedStructure.place(
                sectionLocation,
                true,
                StructureRotation.NONE,
                Mirror.NONE,
                0,
                1f,
                Random(),
            )
        }

        // finally let's rotate players
        for (player in game.onlinePlayers) {
            // imagine z and y as the x,y coordinates on a 2d plane
            // we'll now rotate it with the origin being this imaginary x-axis
            val y = player.location.y - (sectionStart.y + SECTION_WIDTH / 2.0)
            val z = player.location.z - (sectionStart.z + SECTION_WIDTH / 2.0)

            val degrees = gravity.rotateAmount - originalGravity.rotateAmount
            val radians = Math.toRadians(degrees)
            val cos = cos(radians)
            val sin = sin(radians)
            val newZ = z * cos - y * sin
            val newY = z * sin + y * cos

            val finalLocation =
                player.location.clone().apply {
                    setY((sectionStart.y + SECTION_WIDTH / 2.0) + newY)
                    setZ((sectionStart.z + SECTION_WIDTH / 2.0) + newZ)
                }
            player.teleportAsync(
                finalLocation,
                PlayerTeleportEvent.TeleportCause.PLUGIN,
                TeleportFlag.Relative.VELOCITY_X,
                TeleportFlag.Relative.VELOCITY_Y,
                TeleportFlag.Relative.VELOCITY_Z,
            )
        }
    }

    fun flip() {
        flip(null)
    }

    override fun handlePlayerMove(event: PlayerMoveEvent) {
        if (event.to.y <= sectionStart.y + 1.2) {
            event.to = startPos
        }
        super.handlePlayerMove(event)
    }

    override val name: Component = Component.text("Gravjump", NamedTextColor.AQUA)
    override val description: Component =
        Component.text(
            "Reach the end of a randomly generated course as fast as possible with the help of abilities.\n" +
                "Watch out, your down may change at any time!",
            NamedTextColor.AQUA,
        )
}
