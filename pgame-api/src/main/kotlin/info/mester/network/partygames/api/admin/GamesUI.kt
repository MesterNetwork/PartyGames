package info.mester.network.partygames.api.admin

import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.shorten
import info.mester.network.partygames.util.createBasicItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.InventoryHolder

class GamesUI : InventoryHolder {
    private val inventory = Bukkit.createInventory(this, 3 * 9, Component.text("Games", NamedTextColor.DARK_GRAY))
    private val gameManager = PartyGames.plugin.gameManager
    private var page = 0

    init {
        reload()
    }

    private fun reload() {
        // fill the entire inventory with a black glass panel
        inventory.clear()
        val border =
            createBasicItem(Material.BLACK_STAINED_GLASS_PANE, "").apply {
                editMeta { meta ->
                    meta.isHideTooltip = true
                }
            }
        for (i in 0 until inventory.size) {
            inventory.setItem(i, border)
        }
        val games = gameManager.getGames()
        for (index in page * 27 until page * 27 + 27) {
            if (index >= games.size) {
                break
            }
            val game = games[index]
            val players = game.onlinePlayers
            val playersString = players.take(5).map { "<dark_gray>- ${it.name}" }.toTypedArray()
            val gameItem =
                createBasicItem(
                    Material.GREEN_CONCRETE,
                    "${game.id.shorten().substring(0..16)}...",
                    1,
                    "<yellow>Type: <aqua>${game.type.name}",
                    "<yellow>Players: <aqua>${players.size}",
                    *playersString,
                    "<dark_gray>...",
                )
            inventory.setItem(index % 27, gameItem)
        }
    }

    override fun getInventory() = inventory
}
