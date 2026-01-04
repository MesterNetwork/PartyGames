package info.mester.network.partygames.api.events

import info.mester.network.partygames.api.Game
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class GameTerminatedEvent(
    val game: Game,
    val playerCount: Int,
) : Event() {
    companion object {
        private val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList() = HANDLER_LIST
    }

    private var spawnLocation = Bukkit.getWorld("world")!!.spawnLocation

    fun getSpawnLocation() = spawnLocation

    fun setSpawnLocation(spawnLocation: Location) {
        this.spawnLocation = spawnLocation
    }

    override fun getHandlers() = HANDLER_LIST
}
