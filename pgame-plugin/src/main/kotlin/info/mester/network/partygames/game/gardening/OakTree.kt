package info.mester.network.partygames.game.gardening

import info.mester.network.partygames.api.Game
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.TreeType
import org.bukkit.util.Vector

class OakTree(
    location: Location,
    game: Game,
) : Plant(location, game) {
    override fun spawn() {
        when (level) {
            0 -> placeBlock(Vector(0.0, 0.0, 0.0), Material.OAK_SAPLING)
            1 -> {
                placeBlock(Vector(0.0, 0.0, 0.0), Material.OAK_LOG)
                placeBlock(Vector(0.0, 1.0, 0.0), Material.OAK_LEAVES)
                moveInfoDisplay(Vector(0.5, 2.0, 0.5))
            }

            2 -> {
                placeBlock(Vector(0.0, 0.0, 0.0), Material.OAK_LOG)
                placeBlock(Vector(0.0, 1.0, 0.0), Material.OAK_LOG)
                // leaves on second level, filling orthogonally
                placeBlock(Vector(1.0, 1.0, 0.0), Material.OAK_LEAVES)
                placeBlock(Vector(-1.0, 1.0, 0.0), Material.OAK_LEAVES)
                placeBlock(Vector(0.0, 1.0, 1.0), Material.OAK_LEAVES)
                placeBlock(Vector(0.0, 1.0, -1.0), Material.OAK_LEAVES)
                // leaves on third level, filling corners
                placeBlock(Vector(0.0, 2.0, 0.0), Material.OAK_LOG)
                placeBlock(Vector(1.0, 2.0, 0.0), Material.OAK_LEAVES)
                placeBlock(Vector(-1.0, 2.0, 0.0), Material.OAK_LEAVES)
                placeBlock(Vector(1.0, 2.0, 1.0), Material.OAK_LEAVES)
                placeBlock(Vector(-1.0, 2.0, 1.0), Material.OAK_LEAVES)
                placeBlock(Vector(1.0, 2.0, -1.0), Material.OAK_LEAVES)
                placeBlock(Vector(-1.0, 2.0, -1.0), Material.OAK_LEAVES)
                placeBlock(Vector(0.0, 2.0, 1.0), Material.OAK_LEAVES)
                placeBlock(Vector(0.0, 2.0, -1.0), Material.OAK_LEAVES)
                // leaves on top
                placeBlock(Vector(0.0, 3.0, 0.0), Material.OAK_LEAVES)
                moveInfoDisplay(Vector(0.5, 4.0, 0.5))
            }

            3 -> {
                location.world.generateTree(location, TreeType.TREE)
                deactivate()
            }
        }
    }

    override fun getTotalScore(): Int =
        when (level) {
            0 -> 3
            1 -> 8
            2 -> 14
            else -> 0
        }

    override fun getProgressScale(): Double =
        when (level) {
            0 -> 1.0
            1 -> 0.8
            2 -> 0.5
            else -> super.getProgressScale()
        }
}
