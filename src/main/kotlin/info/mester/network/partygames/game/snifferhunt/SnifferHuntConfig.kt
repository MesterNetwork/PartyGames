package info.mester.network.partygames.game.snifferhunt

import de.exlll.configlib.Configuration
import org.bukkit.Material

enum class TreasureRarity(
    val rank: Int,
) {
    COMMON(0),
    UNCOMMON(1),
    RARE(2),
    EPIC(3),
    LEGENDARY(4),
    ;

    fun next(): TreasureRarity = fromRank(rank + 1) ?: LEGENDARY

    companion object {
        fun getPrevious(rarity: TreasureRarity) = entries.filter { it.rank < rarity.rank }

        fun fromRank(rank: Int) = entries.firstOrNull { it.rank == rank }
    }
}

@Configuration
class SnifferTreasure {
    var item: Material = Material.BARRIER
    var maxAmount: Double = 0.0
    var amountScaleFactor: Double = 0.0
    var rarity: TreasureRarity = TreasureRarity.COMMON
}

@Configuration
class SnifferHuntConfig {
    var rarityChances: Map<TreasureRarity, Double> = emptyMap()
    var treasures: List<SnifferTreasure> = emptyList()
}
