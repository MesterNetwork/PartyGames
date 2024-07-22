package info.mester.bedless.tournament.game

import info.mester.bedless.tournament.Tournament
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location

abstract class Minigame {
    private val _game = Tournament.game
    private var _running = false

    @Suppress("ktlint:standard:backing-property-naming")
    protected var _startPos: Location =
        Location(
            game.plugin.server.worlds
                .first(),
            0.0,
            0.0,
            0.0,
        )
    val startPos: Location
        get() = _startPos.clone()
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
            player.isFlying = false
            player.gameMode = GameMode.SURVIVAL
        }
    }

    /**
     * Function to stop and evaluate the minigame (also set player scores)
     */
    open fun end(nextGame: Boolean) {
        _running = false

        if (nextGame) {
            _game.endMinigame()
        }
    }

    open fun end() {
        end(true)
    }

    /**
     * Function to terminate the minigame without the underlying game ending
     */
    open fun terminate() {
        end(false)
    }

    private fun updateRemainingTime(
        startTime: Long,
        duration: Long,
    ): Boolean {
        val bar = game.remainingBossBar
        val remainingTime = startTime + duration - System.currentTimeMillis()
        if (remainingTime < 0) {
            Audience.audience(Bukkit.getOnlinePlayers()).hideBossBar(bar)
            return false
        }
        val time = remainingTime / 1000
        val minutes = time / 60
        val seconds = time % 60

        bar.name(
            Component.text("Time remaining: ", NamedTextColor.GREEN).append(
                Component
                    .text(minutes.toString(), NamedTextColor.RED)
                    .append(Component.text(":", NamedTextColor.GRAY))
                    .append(Component.text(seconds.toString(), NamedTextColor.RED)),
            ),
        )
        bar.progress(remainingTime.toFloat() / duration.toFloat())

        return true
    }

    fun startCountdown(
        duration: Long,
        showBar: Boolean,
        onEnd: () -> Unit,
    ) {
        if (showBar) Audience.audience(Bukkit.getOnlinePlayers()).showBossBar(game.remainingBossBar)
        val startTime = System.currentTimeMillis()
        game.plugin.server.scheduler.runTaskTimer(
            game.plugin,
            { t ->
                if (!running) {
                    t.cancel()
                    return@runTaskTimer
                }
                if (!updateRemainingTime(startTime, duration)) {
                    t.cancel()
                    onEnd()
                }
            },
            0,
            1,
        )
    }

    fun startCountdown(
        duration: Long,
        onEnd: () -> Unit,
    ) {
        startCountdown(duration, true, onEnd)
    }

    open val name: Component
        get() = Component.text("[DEFAULT MINIGAME NAME]", NamedTextColor.DARK_RED)
    open val description: Component
        get() = Component.text("[DEFAULT MINIGAME DESCRIPTION]", NamedTextColor.DARK_RED)
}
