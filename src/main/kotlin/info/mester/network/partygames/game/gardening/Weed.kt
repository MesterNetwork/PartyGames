package info.mester.network.partygames.game.gardening

import info.mester.network.partygames.game.Game
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.util.Vector

open class Weed(
    location: Location,
    game: Game,
) : Plant(location, game) {
    override fun spawn() {
        when (level) {
            0 -> placeBlock(Vector(0.0, 0.0, 0.0), Material.DEAD_BUSH)
            1 -> {
                placeBlock(Vector(0.0, 0.0, 0.0), Material.AIR)
                deactivate()
            }
        }
    }

    override fun getTotalScore() = -35

    override fun getProgressScale() = 5.0

    override fun getWeedKillScore() = 7
}
