package info.mester.network.partygames.sidebar

import info.mester.network.partygames.game.Queue
import info.mester.network.partygames.mm
import info.mester.network.partygames.shorten
import net.kyori.adventure.text.Component
import net.megavex.scoreboardlibrary.api.sidebar.component.LineDrawable
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent
import kotlin.math.ceil

class QueueSidebarComponent(
    private val queue: Queue,
) : SidebarComponent {
    override fun draw(drawable: LineDrawable) {
        drawable.drawLine(mm.deserialize("<#777777>#${queue.id.shorten().substring(0..8)}"))
        drawable.drawLine(Component.empty())
        drawable.drawLine(mm.deserialize("<white>Queuing for: <yellow>${queue.bundle.displayName}"))
        drawable.drawLine(mm.deserialize("<white>Players: <yellow>${queue.playerCount}<gray>/<green>${queue.maxPlayers}"))
        if (queue.playerCount > 1) {
            drawable.drawLine(mm.deserialize("<white>Ready: <yellow>${queue.readyPlayerCount}<gray>/<green>${queue.playerCount}"))
        }
        drawable.drawLine(Component.empty())
        val remainingTime = queue.remainingTime
        if (remainingTime == -1) {
            drawable.drawLine(mm.deserialize("<white><italic>Waiting for players..."))
        } else {
            val time = ceil(remainingTime / 20.0).toInt()
            drawable.drawLine(mm.deserialize("<white>Starting in <yellow>$time</yellow> seconds!"))
        }
    }
}
