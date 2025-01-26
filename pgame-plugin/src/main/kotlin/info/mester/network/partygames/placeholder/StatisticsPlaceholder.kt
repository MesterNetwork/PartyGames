package info.mester.network.partygames.placeholder

import info.mester.network.partygames.DatabaseManager
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

class StatisticsPlaceholder(
    private val databaseManager: DatabaseManager,
) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "pgstat"

    override fun getAuthor(): String = "Party Games"

    override fun getVersion(): String = "1.0"

    private fun formatTime(seconds: Int): String {
        val weeks = seconds / (7 * 24 * 60 * 60)
        val days = (seconds % (7 * 24 * 60 * 60)) / (24 * 60 * 60)
        val hours = (seconds % (24 * 60 * 60)) / (60 * 60)
        val minutes = (seconds % (60 * 60)) / 60
        val remainingSeconds = seconds % 60
        // Build the formatted string, omitting zero values for brevity
        return buildString {
            if (weeks > 0) append("${weeks}w ")
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (remainingSeconds > 0 || isEmpty()) append("${remainingSeconds}s") // Always include seconds
        }.trim()
    }

    override fun onPlaceholderRequest(
        player: Player?,
        params: String,
    ): String? {
        if (player == null) {
            return null
        }
        val arguments = params.lowercase().split("_")
        if (arguments.getOrNull(0) == "gameswon") {
            val game = arguments.getOrNull(1)
            return databaseManager.getGamesWon(player.uniqueId, game).toString()
        }
        if (arguments.getOrNull(0) == "pointsgained") {
            val game = arguments.getOrNull(1)
            return databaseManager.getPointsGained(player.uniqueId, game).toString()
        }
        if (arguments.getOrNull(0) == "timeplayed") {
            val isFormatted = arguments.getOrNull(arguments.size - 1) == "formatted"
            val game = if (isFormatted && arguments.size == 2) null else arguments.getOrNull(1)
            // If game is null or there's no "formatted" at the end, we use the correct game value
            val timePlayed = databaseManager.getTimePlayed(player.uniqueId, game)
            // If formatted is true, return the formatted time, else return the raw value
            return if (isFormatted) {
                formatTime(timePlayed)
            } else {
                timePlayed.toString()
            }
        }
        return null
    }
}
