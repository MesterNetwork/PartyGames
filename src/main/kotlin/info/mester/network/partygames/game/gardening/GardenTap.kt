package info.mester.network.partygames.game.gardening

import info.mester.network.partygames.roundTo
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay

const val MAX_WATER_LEVEL = 750

class GardenTap(
    private val _location: Location,
) {
    val location: Location
        get() = _location.clone()
    private var waterLevel = MAX_WATER_LEVEL
    private val infoDisplay: TextDisplay =
        location.world.spawn(location.clone().add(0.5, 1.0, 0.5), TextDisplay::class.java) { entity ->
            entity.viewRange = 0.4f
            entity.billboard = Display.Billboard.CENTER
            entity.alignment = TextDisplay.TextAlignment.CENTER
        }

    init {
        updateInfoDisplay()
        spawn()
    }

    private fun updateInfoDisplay() {
        // first calculate the color by interpolating between (170, 170, 170) for 0% and (85, 85, 255) for 100%
        val clampedWaterLevel = getFullness()
        val startColor = Triple(170, 170, 170) // RGB for (170, 170, 170)
        val endColor = Triple(85, 85, 255) // RGB for (85, 85, 255)
        val red = (startColor.first + (endColor.first - startColor.first) * clampedWaterLevel).toInt()
        val green = (startColor.second + (endColor.second - startColor.second) * clampedWaterLevel).toInt()
        val blue = (startColor.third + (endColor.third - startColor.third) * clampedWaterLevel).toInt()
        infoDisplay.text(
            Component.text(
                "${(clampedWaterLevel * 100).roundTo(2)}%",
                TextColor.color(red, green, blue),
            ),
        )
    }

    fun spawn() {
        _location.block.type = Material.IRON_BARS
    }

    fun takeWater(): Boolean {
        if (waterLevel <= 0) {
            return false
        }
        waterLevel -= 1
        updateInfoDisplay()
        return true
    }

    fun addWater() {
        if (waterLevel >= MAX_WATER_LEVEL) {
            return
        }
        waterLevel += 1
        updateInfoDisplay()
    }

    fun getFullness(): Double = waterLevel.toDouble() / MAX_WATER_LEVEL.toDouble()
}
