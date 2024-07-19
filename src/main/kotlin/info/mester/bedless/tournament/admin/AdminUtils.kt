package info.mester.bedless.tournament.admin

import info.mester.bedless.tournament.Tournament
import org.bukkit.Bukkit
import org.bukkit.entity.Player

fun updateVisibilityOfPlayer(
    playerToChange: Player,
    visible: Boolean,
) {
    // change the player's visibility for everyone who isn't an admin
    for (player in Bukkit
        .getOnlinePlayers()
        .filter { it.uniqueId != playerToChange.uniqueId }
        .filter { !Tournament.game.isAdmin(it.uniqueId) }) {
        if (visible) {
            player.hidePlayer(Tournament.plugin, playerToChange)
        } else {
            player.showPlayer(Tournament.plugin, playerToChange)
        }
    }
}
