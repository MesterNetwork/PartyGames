package info.mester.network.testminigame

import info.mester.network.partygames.api.Game
import info.mester.network.partygames.api.Minigame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack

class PlaceBlockMinigame(
    game: Game,
) : Minigame(game, "place_block") {
    override fun start() {
        super.start()
        // access the world using game.world
        val world = game.world
        world.worldBorder.size = 30.0
        world.worldBorder.center = startPos

        audience.sendMessage(Component.text("Place blocks to win!", NamedTextColor.AQUA))
        // get every player in the game with onlinePlayers
        for (player in onlinePlayers) {
            player.inventory.addItem(ItemStack.of(Material.OBSIDIAN, 64))
        }

        startCountdown(20 * 1000, false) {
            end()
        }
    }

    // override functions to add custom functionality to the minigame
    override fun handleBlockPlace(event: BlockPlaceEvent) {
        game.addScore(event.player, 1, "Placed a block")
    }

    override val name = Component.text("Place Block", NamedTextColor.AQUA)
    override val description = Component.text("Place blocks to win!", NamedTextColor.AQUA)
}
