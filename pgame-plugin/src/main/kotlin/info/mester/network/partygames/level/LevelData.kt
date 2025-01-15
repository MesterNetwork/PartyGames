package info.mester.network.partygames.level

data class LevelData(
    var level: Int,
    var xp: Int,
) {
    companion object {
        const val MAX_LEVEL = 5000

        /**
         * Returns the amount of experience required to reach the next level.
         */
        fun getXpToNextLevel(currentLevel: Int) =
            when (currentLevel) {
                in 0..4 -> 100
                in 5..14 -> 500
                in 15..29 -> 1000
                in 30..49 -> 2000
                in 50..99 -> 3000
                else -> 5000
            }
    }

    val xpToNextLevel get() = getXpToNextLevel(level)
    val remainingXp get() = xpToNextLevel - xp
}
