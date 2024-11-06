package info.mester.network.partygames.util

import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

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
