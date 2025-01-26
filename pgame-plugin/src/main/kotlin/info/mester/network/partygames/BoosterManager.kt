package info.mester.network.partygames

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

data class Booster(
    val name: String,
    val multiplier: Double,
)

class BoosterManager {
    private fun getRankBooster(player: Player): Booster? {
        if (player.hasPermission("group.insane")) {
            return Booster("<dark_red><bold>Insane <gold>Booster</gold></bold></dark_red>", 1.5)
        }
        if (player.hasPermission("group.pro")) {
            return Booster("<red>Pro <gold>Booster</gold></red>", 1.2)
        }
        if (player.hasPermission("group.advanced")) {
            return Booster("<aqua>Advanced <gold>Booster</gold></aqua>", 1.1)
        }
        if (player.hasPermission("group.beginner")) {
            return Booster("<green>Beginner <gold>Booster</gold></green>", 1.05)
        }
        return null
    }

    /**
     * Returns a list of every booster applicable to the player.
     * This includes: rank booster, personal booster and global booster.
     * @param offlinePlayer the player to get the boosters for
     * @return a list of boosters, may be empty
     */
    fun getBooster(offlinePlayer: OfflinePlayer): List<Booster> {
        val player = Bukkit.getPlayer(offlinePlayer.uniqueId) ?: return emptyList()
        val boosters = mutableListOf<Booster>()
        // process rank booster
        val rankBooster = getRankBooster(player)
        if (rankBooster != null) {
            boosters.add(rankBooster)
        }
        return boosters
    }
}
