package info.mester.network.partygames.api.events

import info.mester.network.partygames.api.Game
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class GameStartedEvent(
    val game: Game,
) : Event() {
    companion object {
        private val HANDLER_LIST = HandlerList()

        fun getHandlerList() = HANDLER_LIST
    }

    override fun getHandlers() = HANDLER_LIST
}
