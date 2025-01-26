package info.mester.network.partygames.placeholder

import info.mester.network.partygames.level.LevelData
import info.mester.network.partygames.level.LevelManager
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

class LevelPlaceholder(
    private val levelManager: LevelManager,
) : PlaceholderExpansion() {
    override fun getIdentifier() = "level"

    override fun getAuthor() = "Party Games"

    override fun getVersion() = "1.0"

    override fun onPlaceholderRequest(
        player: Player?,
        params: String,
    ): String? {
        if (player == null) {
            return null
        }
        val levelData = levelManager.levelDataOf(player.uniqueId)
        return when (params) {
            "level" -> levelData.level.toString()
            "xp" -> levelData.xp.toString()
            "remaining" -> levelData.remainingXp.toString()
            "needed" -> LevelData.getXpToNextLevel(levelData.level).toString()
            else -> null
        }
    }
}
