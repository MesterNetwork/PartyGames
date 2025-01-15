package info.mester.network.partygames.game.healthshop

import info.mester.network.partygames.game.HealthShopMinigame
import org.bukkit.scheduler.BukkitTask
import java.util.function.Consumer
import kotlin.math.exp
import kotlin.random.Random

private const val STEEPNESS = 2.0

class SupplyChestTimer(
    private val minigame: HealthShopMinigame,
    private val maxTime: Int,
) : Consumer<BukkitTask> {
    private var offset = 0.0
    private var currentTime = 0

    override fun accept(t: BukkitTask) {
        if (!minigame.running) {
            t.cancel()
            return
        }
        require(currentTime in 0..maxTime) { "currentTime must be between 0 and maxTime" }
        val normalizedTime = currentTime.toDouble() / maxTime.toDouble()
        // calculate [e^(k*t)-1]/[e^k-1]
        val expFactor = exp(STEEPNESS * normalizedTime)
        val functionValue = 0.7 * (expFactor - 1) / (exp(STEEPNESS) - 1) + offset
        // generate a random number and compare
        val randomValue = Random.nextDouble()
        val result = randomValue < functionValue
        // update the offset
        if (result) {
            offset -= 0.035 // the next chest will have a lower chance of spawning
            minigame.spawnSupplyChest()
        }
        currentTime += 1
    }
}
