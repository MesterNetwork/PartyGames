package info.mester.network.partygames.game.gardening

import info.mester.network.partygames.api.Game
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.util.Vector

class Cactus(
    location: Location,
    game: Game,
) : Plant(location, game) {
    override fun spawn() {
        placeBlock(Vector(0.0, -1.0, 0.0), Material.SAND)

        when (level) {
            0 -> placeBlock(Vector(0.0, 0.0, 0.0), Material.CACTUS)
            1 -> {
                placeBlock(Vector(0.0, 1.0, 0.0), Material.CACTUS)
                moveInfoDisplay(Vector(0.5, 2.0, 0.5))
            }

            2 -> {
                placeBlock(Vector(0.0, 2.0, 0.0), Material.CACTUS)
                moveInfoDisplay(Vector(0.5, 3.0, 0.5))
            }

            3 -> {
                placeBlock(Vector(0.0, 3.0, 0.0), Material.CACTUS)
                deactivate()
            }
        }
    }

    override fun getTotalScore() = level * 2 + 1
}
