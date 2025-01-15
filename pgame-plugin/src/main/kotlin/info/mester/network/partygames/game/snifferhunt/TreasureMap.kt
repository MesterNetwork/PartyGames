package info.mester.network.partygames.game.snifferhunt

import org.bukkit.util.noise.PerlinNoiseGenerator
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class TreasureMap {
    companion object {
        val validXRange = 0..59
        val validZRange = 0..59
        const val DAMPING_RADIUS = 40
        const val DAMPING_FALLOFF = 1.5
    }

    private val noiseGenerators =
        Array(4) {
            PerlinNoiseGenerator(Random.nextLong())
        }
    private val values: Array<DoubleArray> =
        Array(60) { x ->
            DoubleArray(60) { z ->
                generateNoise(x / 59.0, z / 59.0)
            }
        }

    /**
     * Blends the outputs of the 4 Perlin noise generators.
     * @param x Normalized x-coordinate (0.0 to 1.0)
     * @param z Normalized z-coordinate (0.0 to 1.0)
     * @return A blended noise value.
     */
    private fun generateNoise(
        x: Double,
        z: Double,
    ): Double =
        (
            (
                // use a different "resolution" for each noise generator
                (noiseGenerators[0].noise(x, 0.0, z) + 1) * 0.42 +
                    (noiseGenerators[1].noise(x * 2, 0.0, z * 2) + 1) * 0.32 +
                    (noiseGenerators[2].noise(x * 4, 0.0, z * 4) + 1) * 0.22 +
                    (noiseGenerators[3].noise(x * 8, 0.0, z * 8) + 1) * 0.12
            ) / 2.0
        ).coerceIn(0.0, 1.0)

    /**
     * Applies a damping effect to the values in the treasure map.
     * Damping is applied to all values within a radius of [DAMPING_RADIUS] around the center.
     * This is used to prevent the players from being able to get infinite amount of the highest value.
     * @param centerX The center x-coordinate of the area to apply the damping to.
     * @param centerZ The center z-coordinate of the area to apply the damping to.
     */
    fun applyDamping(
        centerX: Int,
        centerZ: Int,
    ) {
        require(centerX in validXRange && centerZ in validZRange) { "Coordinates must be within the 60x60 grid." }

        fun calculateDampingEffect(distance: Double): Double {
            if (distance > DAMPING_RADIUS) return 0.0
//            val normalizedDistance = distanceSquared.toDouble() / DAMPING_RADIUS_SQUARED
//            return (1 - normalizedDistance) * (1 - normalizedDistance) * DAMPING_FALLOFF
            val normalizedDistance = distance / DAMPING_RADIUS
            return (1 - normalizedDistance).pow(3) * DAMPING_FALLOFF
        }

        for (x in (centerX - DAMPING_RADIUS)..(centerX + DAMPING_RADIUS)) {
            for (z in (centerZ - DAMPING_RADIUS)..(centerZ + DAMPING_RADIUS)) {
                if (x in values.indices && z in values[x].indices) {
                    val dx = (x - centerX).toDouble()
                    val dz = (z - centerZ).toDouble()
                    val distance = sqrt(dx * dx + dz * dz)
                    val damping = calculateDampingEffect(distance)
                    values[x][z] = (values[x][z] * (1 - damping)).coerceIn(0.0, 1.0)
                }
            }
        }
    }

    /**
     * Gets the pre-generated treasure value at the given x, z coordinates.
     * @param x X-coordinate in the 60x60 grid.
     * @param z Z-coordinate in the 60x60 grid.
     * @return The noise value at the given coordinates.
     */
    fun getTreasureValue(
        x: Int,
        z: Int,
    ): Double {
        require(x in validXRange && z in validZRange) { "Coordinates must be within the 60x60 grid." }
        return values[x][z]
    }
}
