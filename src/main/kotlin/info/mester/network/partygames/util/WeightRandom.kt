package info.mester.network.partygames.util

import kotlin.random.Random

data class WeightedItem<T>(
    val item: T,
    val weight: Int,
)

fun <T> selectWeightedRandom(items: List<WeightedItem<T>>): T {
    val totalWeight = items.sumOf { it.weight }
    val randomValue = Random(System.currentTimeMillis()).nextInt(totalWeight)
    var currentWeight = 0
    for (item in items) {
        currentWeight += item.weight
        if (currentWeight > randomValue) {
            return item.item
        }
    }
    // this should never happen
    throw IllegalStateException("Weights sum must be greater than 0")
}
