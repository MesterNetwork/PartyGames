package info.mester.network.partygames.sidebar

import info.mester.network.partygames.game.Game
import info.mester.network.partygames.game.GameState
import info.mester.network.partygames.mm
import info.mester.network.partygames.shorten
import net.kyori.adventure.text.Component
import net.megavex.scoreboardlibrary.api.sidebar.component.LineDrawable
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent
import java.util.UUID

class GameSidebarComponent(
    private val game: Game,
    private val player: UUID,
) : SidebarComponent {
    private fun renderLeaderboard(drawable: LineDrawable) {
        drawable.drawLine(Component.empty())
        val topList = game.topPlayers(8)
        drawable.drawLine(mm.deserialize("<white>Top players:"))
        for (i in 0 until 8) {
            if (i >= topList.size) {
                break
            }
            val data = topList[i]
            val player = data.first
            val playerData = data.second
            drawable.drawLine(
                mm.deserialize(
                    // display the player's name in gray if they're offline
                    "<yellow>${i + 1}. ${if (player.isOnline) player.name else "<gray>${player.name}"} <gray>- <green>${playerData.score}",
                ),
            )
        }
    }

    override fun draw(drawable: LineDrawable) {
        drawable.drawLine(mm.deserialize("<#777777>#${game.id.shorten().substring(0..8)}"))
        drawable.drawLine(Component.empty())
        // draw the game state
        when (game.state) {
            GameState.STARTING -> {
                drawable.drawLine(mm.deserialize("<white>Starting..."))
            }

            GameState.PRE_GAME -> {
                val minigame = game.runningMinigame!!
                drawable.drawLine(mm.deserialize("<white>Loading: ").append(minigame.name))
                drawable.drawLine(mm.deserialize("<green>Get ready!"))
                renderLeaderboard(drawable)
            }

            GameState.PLAYING -> {
                val minigame = game.runningMinigame!!
                drawable.drawLine(mm.deserialize("<white>Playing: ").append(minigame.name))
                drawable.drawLine(Component.empty())
                drawable.drawLine(mm.deserialize("<white>Your score: <yellow>${game.playerData(player)!!.score}"))
                renderLeaderboard(drawable)
            }

            GameState.POST_GAME -> {
                drawable.drawLine(mm.deserialize("<green>Minigame over!"))
                renderLeaderboard(drawable)
            }

            else -> {
                drawable.drawLine(mm.deserialize("<white>Game state: <yellow>${game.state}"))
            }
        }
    }
}
