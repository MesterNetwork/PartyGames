package info.mester.bedless.tournament.game

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Location

abstract class Minigame(private val _game: Game) {
    private var _running = false
    protected var _startPos: Location? = null

    val startPos: Location
        get() = _startPos!!.clone()
    val running: Boolean
        get() = _running
    val game: Game
        get() = _game

    /**
     * Function to start the minigame
     */
    open fun start() {
        _running = true

        _game.players().forEach { player ->
            player.teleport(startPos)
            player.gameMode = GameMode.ADVENTURE
        }
    }


    /**
     * Function to stop and evaluate the minigame (also set player scores)
     */
    open fun end() {
        _running = false

        _game.endMinigame()
    }

    open val name: Component
        get() = Component.text("[DEFAULT MINIGAME NAME]", NamedTextColor.DARK_RED)
    open val description: Component
        get() = Component.text("[DEFAULT MINIGAME DESCRIPTION]", NamedTextColor.DARK_RED)
}