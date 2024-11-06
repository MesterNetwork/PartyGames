package info.mester.network.partygames.game.gardening

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.Bisected
import org.bukkit.util.Vector

class Peony(
    location: Location,
) : Plant(location) {
    override fun spawn() {
        when (level) {
            0 -> placeBlock(Vector(0.0, 0.0, 0.0), Material.PINK_TULIP)
            1 -> {
                val lower = placeBlock(Vector(0.0, 0.0, 0.0), Material.PEONY)
                val upper = placeBlock(Vector(0.0, 1.0, 0.0), Material.PEONY)
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

    override fun getTotalScore() = 3
}
