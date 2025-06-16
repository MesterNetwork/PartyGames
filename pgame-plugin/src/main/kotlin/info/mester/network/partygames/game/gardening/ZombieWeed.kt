package info.mester.network.partygames.game.gardening

import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.api.Game
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Zombie
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

class ZombieWeed(
    location: Location,
    game: Game,
) : Weed(location, game) {
    override fun spawn() {
        super.spawn()
        if (level == 1) {
            spawnEnemy()
        }
    }

    private fun spawnEnemy() {
        // spawn an invisible zombie that'll act as the weed enemy
        location.world.spawn(location.clone().add(0.5, 0.0, 0.5), Zombie::class.java) { zombie ->
            zombie.isInvisible = true
            zombie.isCollidable = false
            zombie.isSilent = true
            zombie.isInvulnerable = true
            zombie.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, -1, 255, false, false, false))
            zombie.getAttribute(Attribute.FOLLOW_RANGE)?.baseValue = 64.0
            zombie.getAttribute(Attribute.ATTACK_DAMAGE)?.baseValue = 2.0
            zombie.setShouldBurnInDay(false)
            zombie.target = location.world.getNearbyPlayers(zombie.location, 16.0).firstOrNull()
            // spawn a block display to act as the weed
            location.world.spawn(location, BlockDisplay::class.java) { blockDisplay ->
                blockDisplay.block = Material.DEAD_BUSH.createBlockData()
                blockDisplay.brightness = Display.Brightness(15, 15)
                // change the translation of the block display to correctly position it
                blockDisplay.transformation =
                    Transformation(
                        Vector3f(-0.5f, 0.0f, -0.5f),
                        Quaternionf(0.0f, 0.0f, 0.0f, 1.0f),
                        Vector3f(1.0f, 1.0f, 1.0f),
                        Quaternionf(0.0f, 0.0f, 0.0f, 1.0f),
                    )
                // start a timer that teleports the block display to the enemy
                Bukkit
                    .getScheduler()
                    .runTaskTimer(PartyGames.plugin, { t ->
                        if (!zombie.isValid) {
                            t.cancel()
                            blockDisplay.remove()
                            return@runTaskTimer
                        }
                        blockDisplay.teleport(zombie.location)
                    }, 0, 1)
            }
        }
    }
}
