package info.mester.network.partygames.util

import org.bukkit.FluidCollisionMode
import org.bukkit.HeightMap
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Kindla like [org.bukkit.World.getHighestBlockAt] but it returns the highest block below the given location.
 * @param location the location to get the highest block below.
 * @param maxDistance the maximum distance to check for the highest block.
 * @return the highest block below the given location or the given location if there is no block below it.
 */
fun getHighestBlockBelow(
    location: Location,
    maxDistance: Double,
): Location {
    val highestBlock =
        location.world.rayTraceBlocks(
            location,
            Vector(0.0, -1.0, 0.0),
            maxDistance,
            FluidCollisionMode.NEVER,
            true,
        )
    return highestBlock?.hitBlock?.location ?: location
}

fun getTouchedBlocks(
    start: Vector,
    end: Vector,
): Set<Vector> {
    val touchedBlocks = mutableSetOf<Vector>()
    // perform Bresenham's line algorithm to calculate the blocks that are touched by the segment
    // formed between the start and end vector
    var x0 = start.x
    var y0 = start.y
    var z0 = start.z
    val dx = abs(end.x - x0)
    val dy = abs(end.y - y0)
    val dz = abs(end.z - z0)
    val n = max(max(dx, dy), dz).toInt()
    val xStep = (end.x - x0) / n
    val yStep = (end.y - y0) / n
    val zStep = (end.z - z0) / n
    for (i in 0..n) {
        // floor the coordinates to get the block coordinates
        touchedBlocks.add(Vector(floor(x0), floor(y0), floor(z0)))
        x0 += xStep
        y0 += yStep
        z0 += zStep
    }
    return touchedBlocks
}

fun getPointsAlongSegment(
    start: Vector,
    end: Vector,
    interval: Double,
): List<Vector> {
    val points = mutableListOf<Vector>()
    val direction = end.clone().subtract(start)
    val length = direction.length()
    val steps = (length / interval).toInt()
    val stepVector = direction.normalize().multiply(interval)

    for (i in 0..steps) {
        val point = start.clone().add(stepVector.clone().multiply(i))
        points.add(point)
    }

    return points
}

private val disallowedBlocks = listOf(Material.MAGMA_BLOCK, Material.NETHER_WART_BLOCK, Material.WARPED_WART_BLOCK)

fun spreadPlayers(
    players: List<Player>,
    center: Location,
    radius: Int,
) {
    val random = Random(System.currentTimeMillis())
    for (player in players) {
        var attempts = 5
        var success = false
        while (attempts > 0) {
            // first generate a random angle between 0 and 360 degrees
            val angle = random.nextDouble() * 2 * Math.PI
            // distance should be between 0 and radius
            val distance = random.nextDouble() * radius
            val x = center.x + distance * cos(angle)
            val z = center.z + distance * sin(angle)
            center.world.getHighestBlockAt(x.toInt(), z.toInt(), HeightMap.WORLD_SURFACE).let {
                // check if the block is solid (not a fluid)
                if (it.type.isSolid && !disallowedBlocks.contains(it.type)) {
                    player.teleport(it.location.clone().add(0.5, 1.0, 0.5))
                    success = true
                    attempts = 0
                }
            }
            attempts--
        }
        if (!success) {
            player.teleport(center.clone().add(0.5, 1.0, 0.5))
        }
    }
}

fun snapTo90(angle: Float): Float {
    // Normalize to -180 to 180 range
    var normalized = ((angle + 180) % 360) - 180
    if (normalized < -180) normalized += 360
    // Snap to nearest 90
    return when {
        normalized > 135 -> 180f
        normalized > 45 -> 90f
        normalized > -45 -> 0f
        normalized > -135 -> -90f
        else -> -180f
    }
}
