package info.mester.network.partygames.sidebar

import info.mester.network.partygames.level.LevelData
import info.mester.network.partygames.mm
import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.megavex.scoreboardlibrary.api.sidebar.component.LineDrawable
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent
import org.bukkit.entity.Player

class LobbySidebarComponent(
    private val player: Player,
    private val levelData: LevelData,
) : SidebarComponent {
    override fun draw(drawable: LineDrawable) {
        drawable.drawLine(mm.deserialize("<white>Level: <yellow>${levelData.level}"))
        drawable.drawLine(mm.deserialize("<white>Progress: <yellow>${levelData.xp}<gray>/<green>${levelData.xpToNextLevel}"))
        val progress = (levelData.xp / levelData.xpToNextLevel.toFloat()).coerceIn(0f..1f) * 100
        // turn progress into a number between 0 and 10
        val filledSquares = ((progress + 5) / 10).toInt().coerceIn(0, 10)
        val progressBar =
            buildString {
                if (filledSquares > 0) {
                    append("<green>")
                    append("■".repeat(filledSquares))
                    append("</green>")
                }
                if (filledSquares < 10) {
                    append("<gray>")
                    append("■".repeat(10 - filledSquares))
                    append("</gray>")
                }
            }
        drawable.drawLine(mm.deserialize(" <#777777>[$progressBar]"))
        drawable.drawLine(Component.empty())
        drawable.drawLine(
            mm.deserialize(
                "<white>Games Won: <gold>${
                    PlaceholderAPI.setPlaceholders(
                        player,
                        "%pgstat_gameswon%",
                    )
                }",
            ),
        )
        drawable.drawLine(
            mm.deserialize(
                "<white>Points Gained: <gold>${
                    PlaceholderAPI.setPlaceholders(
                        player,
                        "%pgstat_pointsgained%",
                    )
                }",
            ),
        )
        drawable.drawLine(
            mm.deserialize(
                "<white>Time Played: <gold>${
                    PlaceholderAPI.setPlaceholders(
                        player,
                        "%pgstat_timeplayed_formatted%",
                    )
                }",
            ),
        )
    }
}
