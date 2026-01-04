package info.mester.network.partygames.game.gardening

import info.mester.network.partygames.api.Game
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.Bisected
import org.bukkit.util.Vector

class Lilac(
    location: Location,
    game: Game,
) : Plant(location, game) {
    override fun spawn() {
        when (level) {
            0 -> placeBlock(Vector(0.0, 0.0, 0.0), Material.ALLIUM)
            1 -> {
                val lower = placeBlock(Vector(0.0, 0.0, 0.0), Material.LILAC)
                val upper = placeBlock(Vector(0.0, 1.0, 0.0), Material.LILAC)
                (lower.blockData as Bisected).let {
                    it.half = Bisected.Half.BOTTOM
                    lower.blockData = it
                }
                (upper.blockData as Bisected).let {
                    it.half = Bisected.Half.TOP
                    upper.blockData = it
                }
                deactivate()
            }
        }
    }

    override fun getTotalScore() = 1
}
