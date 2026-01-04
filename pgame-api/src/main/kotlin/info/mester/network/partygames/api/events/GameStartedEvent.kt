package info.mester.network.partygames.api.events

import info.mester.network.partygames.api.Game
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class GameStartedEvent(
    val game: Game,
    val players: List<Player>,
) : Event() {
    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }

    override fun getHandlers() = HANDLER_LIST
}
