package info.mester.network.partygames.game.gardening

import info.mester.network.partygames.game.Game
import info.mester.network.partygames.pow
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.util.Vector

class RainbowFlower(
    location: Location,
    game: Game,
) : Plant(location, game) {
    override fun spawn() {
        val flower =
            when (level) {
                0 -> Material.DANDELION
                1 -> Material.POPPY
                2 -> Material.BLUE_ORCHID
                3 -> Material.ALLIUM
                4 -> Material.AZURE_BLUET
                5 -> Material.RED_TULIP
                6 -> Material.ORANGE_TULIP
                7 -> Material.WHITE_TULIP
                8 -> Material.PINK_TULIP
                9 -> Material.OXEYE_DAISY
                10 -> Material.CORNFLOWER
                else -> Material.AIR
            }
        placeBlock(Vector(0.0, 0.0, 0.0), flower)
        if (level == 10) {
            deactivate()
        }
    }

    override fun getTotalScore() = (0.15 * level.pow(2) + 5).toInt()

    override fun getProgressScale() = -0.09 * level + 1
}
