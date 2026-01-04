package info.mester.network.partygames.api.events

import info.mester.network.partygames.api.Game
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class PlayerRemovedFromGameEvent(
    val game: Game,
    val player: Player,
) : Event() {
    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLER_LIST
    }

    override fun getHandlers() = HANDLER_LIST
}
