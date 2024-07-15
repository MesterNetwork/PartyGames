package info.mester.bedless.tournament.game

import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.Location

abstract class Minigame(private val game: Game, private val startPos: Location) {
    private var running = false

    /**
     * Function to start the minigame
     */
    open fun start() {
        running = true

        game.players().forEach { player ->
            player.teleport(startPos)
            player.gameMode = GameMode.ADVENTURE
        }
    }


    /**
     * Function to stop and evaluate the minigame (also set player scores)
     */
    open fun end() {
        running = false
    }

    fun running(): Boolean {
        return running
    }

    fun game(): Game {
        return game
    }

    fun startPos(): Location {
        return startPos.clone()
    }

    abstract fun name(): Component
    abstract fun description(): Component
}