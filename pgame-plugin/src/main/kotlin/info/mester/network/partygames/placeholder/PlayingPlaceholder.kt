package info.mester.network.partygames.placeholder

import info.mester.network.partygames.game.QueueType
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

class PlayingPlaceholder : PlaceholderExpansion() {
    private val playingMap: MutableMap<String, Int> = ConcurrentHashMap()

    init {
        val queueTypes = QueueType.entries.map { it.name }
        for (gameType in queueTypes) {
            addPlaying(gameType, 0)
        }
    }

    override fun getIdentifier(): String = "playing"

    override fun getAuthor(): String = "Party Games"

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
