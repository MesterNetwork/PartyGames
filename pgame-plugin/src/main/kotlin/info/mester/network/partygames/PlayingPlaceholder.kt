package info.mester.network.partygames

import info.mester.network.partygames.game.GameType
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

class PlayingPlaceholder : PlaceholderExpansion() {
    private val playingMap: MutableMap<String, Int> = ConcurrentHashMap()

    init {
        val gameTypes = GameType.entries.map { it.name }
        for (gameType in gameTypes) {
            addPlaying(gameType, 0)
        }
    }

    override fun getIdentifier(): String = "playing"

    override fun getAuthor(): String = "MesterNetwork"

    override fun getVersion(): String = "1.0"

    override fun onPlaceholderRequest(
        player: Player?,
        params: String,
    ): String? {
        val key = params.lowercase()
        return playingMap[key]?.toString()
    }

    fun addPlaying(
        key: String,
        value: Int,
    ) {
        val finalKey = key.lowercase()
        if (playingMap.containsKey(finalKey)) {
            playingMap[finalKey] = playingMap[finalKey]!! + value
        } else {
            playingMap[finalKey] = value
        }
    }

    fun removePlaying(
        key: String,
        value: Int,
    ) {
        val finalKey = key.lowercase()
        if (playingMap.containsKey(finalKey)) {
            playingMap[finalKey] = playingMap[finalKey]!! - value
        }
    }
}
