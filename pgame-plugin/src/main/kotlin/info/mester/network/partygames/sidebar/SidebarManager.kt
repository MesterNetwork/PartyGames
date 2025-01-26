package info.mester.network.partygames.sidebar

import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.api.Game
import info.mester.network.partygames.game.Queue
import info.mester.network.partygames.mm
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.Date
import java.util.UUID

data class SidebarWithLayout(
    val sidebar: Sidebar,
    private var _layout: ComponentSidebarLayout,
) {
    var layout: ComponentSidebarLayout
        get() = _layout
        set(value) {
            sidebar.clearLines()
            _layout = value
            layout.apply(sidebar)
        }

    fun update() {
        layout.apply(sidebar)
    }
}

class SidebarManager(
    private val plugin: PartyGames,
) {
    companion object {
        private val title = SidebarComponent.staticLine(mm.deserialize("<aqua><bold>Party Games"))
        private val dtf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        private fun applyFooter(builder: SidebarComponent.Builder) {
            builder
                .addBlankLine()
                .addDynamicLine {
                    val time = dtf.format(Date())
                    mm.deserialize("<gray>$time")
                }.addStaticLine(mm.deserialize("<yellow>play.mester.info"))
        }
    }

    init {
        // start the timer that updates the sidebars
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            Runnable {
                updateSidebars()
            },
            0,
            1,
        )
    }

    private val scoreboardLibrary = plugin.scoreboardLibrary
    private val sidebars = mutableMapOf<UUID, SidebarWithLayout>()

    private fun updateSidebars() {
        for (sidebar in sidebars.values) {
            sidebar.update()
        }
    }

    private fun createLobbyLayout(player: Player): ComponentSidebarLayout {
        val builder =
            SidebarComponent
                .builder()
                .addComponent(LobbySidebarComponent(player, plugin.levelManager.levelDataOf(player.uniqueId)))
        applyFooter(builder)
        return ComponentSidebarLayout(title, builder.build())
    }

    private fun createQueueLayout(queue: Queue): ComponentSidebarLayout {
        val builder =
            SidebarComponent
                .builder()
                .addComponent(QueueSidebarComponent(queue))
        applyFooter(builder)
        return ComponentSidebarLayout(title, builder.build())
    }

    private fun createGameLayout(
        game: Game,
        player: Player,
    ): ComponentSidebarLayout {
        val builder =
            SidebarComponent
                .builder()
                .addComponent(GameSidebarComponent(game, player.uniqueId))
        applyFooter(builder)
        return ComponentSidebarLayout(title, builder.build())
    }

    private fun createSidebar(
        player: Player,
        layout: ComponentSidebarLayout,
    ) {
        unregisterPlayer(player)
        val sidebar = scoreboardLibrary.createSidebar()
        sidebars[player.uniqueId] = SidebarWithLayout(sidebar, layout)
        sidebar.addPlayer(player)
    }

    fun openLobbySidebar(player: Player) {
        createSidebar(player, createLobbyLayout(player))
    }

    fun openQueueSidebar(player: Player) {
        val queue = plugin.queueManager.getQueueOf(player) ?: return
        createSidebar(player, createQueueLayout(queue))
    }

    fun openGameSidebar(player: Player) {
        val game = plugin.core.gameRegistry.getGameOf(player) ?: return
        createSidebar(player, createGameLayout(game, player))
    }

    fun unregisterPlayer(player: Player) {
        sidebars[player.uniqueId]?.let { sidebar ->
            sidebar.sidebar.removePlayer(player)
            sidebar.sidebar.close()
            sidebars.remove(player.uniqueId)
        }
    }
}
