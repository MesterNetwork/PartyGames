package info.mester.network.partygames.util

import org.bukkit.HeightMap
import org.bukkit.Location
import org.bukkit.entity.Player
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

fun spreadPlayers(
    players: List<Player>,
    center: Location,
    radius: Int,
) {
    val random = Random(System.currentTimeMillis())
    for (player in players) {
        // first generate a random angle between 0 and 360 degrees
        val angle = random.nextDouble() * 2 * Math.PI
        // distance should be between 0 and radius
        val distance = random.nextDouble() * radius
        val x = center.x + distance * cos(angle)
        val z = center.z + distance * sin(angle)
        center.world.getHighestBlockAt(x.toInt(), z.toInt(), HeightMap.WORLD_SURFACE).let {
            player.teleport(it.location.clone().add(0.5, 1.0, 0.5))
        }
    }
}
