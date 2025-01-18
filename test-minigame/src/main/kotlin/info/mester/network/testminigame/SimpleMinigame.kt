package info.mester.network.testminigame

import info.mester.network.partygames.api.Game
import info.mester.network.partygames.api.Minigame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class SimpleMinigame(
    game: Game,
) : Minigame(game, "simple") {
    override fun start() {
        super.start()
        // use audience to send messages to all players in the game
        audience.sendMessage(Component.text("Welcome to Simple Minigame!", NamedTextColor.YELLOW))
        // use startCountdown to create a countdown timer with a bar on top of the screen
        startCountdown(20 * 1000) {
            end()
        }
    }

    // override name and description to change the display name and description of the minigame
    override val name = Component.text("Simple Minigame", NamedTextColor.AQUA)
    override val description =
        Component.text("Simple Minigame, super boring but generic, which is somehow good?", NamedTextColor.AQUA)
}
