package info.mester.network.partygames.level

import info.mester.network.partygames.PartyGames
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import java.util.UUID

class LevelManager(
    plugin: PartyGames,
) {
    private val database = plugin.databaseManager
    private val logger = plugin.logger
    private val cachedLevels = mutableMapOf<UUID, LevelData>()

    init {
        // save duration is stored in minutes, we need to convert it to ticks
        val saveDuration = plugin.config.getLong("save-interval", 5) * 60 * 20
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { saveAll() }, saveDuration, saveDuration)
    }

    private fun getLevelData(uuid: UUID): LevelData {
        if (cachedLevels.containsKey(uuid)) {
            return cachedLevels[uuid]!!
        }
        val levelData = database.getLevel(uuid) ?: LevelData(0, 0)
        cachedLevels[uuid] = levelData
        return levelData
    }

    private fun save(uuid: UUID) {
        if (!cachedLevels.containsKey(uuid)) {
            throw IllegalArgumentException("UUID $uuid is not cached, but it should be!")
        }
        val levelData = cachedLevels[uuid]!!
        database.saveLevel(uuid, levelData)
    }

    private fun saveAll() {
        logger.info("Saving all cached levels...")
        for (uuid in cachedLevels.keys) {
            save(uuid)
        }
        cachedLevels.clear()
    }

    fun stop() {
        saveAll()
    }

    /**
     * Returns a copy of the level data for the given UUID.
     */
    fun levelDataOf(uuid: UUID): LevelData = getLevelData(uuid).copy()

    fun addXp(
        uuid: UUID,
        amount: Int,
    ) {
        val levelData = getLevelData(uuid)
        var remainingXp = amount
        var levelledUp = false
        // if the amount is equal to or greater than the remaining exp, level up until that's no longer the case
        while (remainingXp >= levelData.remainingXp) {
            levelledUp = true
            remainingXp -= levelData.remainingXp
            // level up
            levelData.xp = 0
            levelData.level++
            if (levelData.level >= LevelData.MAX_LEVEL) {
                // Cap at MAX_LEVEL and stop further processing
                levelData.level = LevelData.MAX_LEVEL
                break
            }
        }
        // add the remaining xp to the current level
        levelData.xp += remainingXp

        if (levelledUp) {
            // Optionally broadcast level-up event
            Bukkit.getPlayer(uuid)?.sendMessage(
                Component.text(
                    "Congratulations! You've leveled up to level ${levelData.level}!",
                    NamedTextColor.GREEN,
                ),
            )
        }
    }
}
