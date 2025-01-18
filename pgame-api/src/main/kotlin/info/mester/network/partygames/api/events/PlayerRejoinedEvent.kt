package info.mester.network.partygames.api.events

import info.mester.network.partygames.api.Game
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class PlayerRejoinedEvent(
    val game: Game,
    val player: Player,
) : Event(),
    Cancellable {
    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLER_LIST
    }

    private var cancelled = false

    override fun getHandlers() = HANDLER_LIST

    override fun isCancelled() = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }
}
