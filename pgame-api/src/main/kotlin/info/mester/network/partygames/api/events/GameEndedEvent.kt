package info.mester.network.partygames.api.events

import info.mester.network.partygames.api.Game
import info.mester.network.partygames.api.PlayerData
import org.bukkit.OfflinePlayer
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class GameEndedEvent(
    val game: Game,
    val topList: List<Pair<OfflinePlayer, PlayerData>>,
) : Event() {
    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLER_LIST
    }

    override fun getHandlers() = HANDLER_LIST
}
