package info.mester.network.partygames.game

import info.mester.network.partygames.api.Game
import info.mester.network.partygames.api.GameState
import info.mester.network.partygames.api.Minigame
import info.mester.network.partygames.api.createBasicItem
import info.mester.network.partygames.mm
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Pig
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.util.Vector
import java.util.UUID

@Suppress("Unused")
class MaceBounceMinigame(
    game: Game,
) : Minigame(game, "macebounce") {
    private val highestY = mutableMapOf<UUID, Double>()
    private val below45 = mutableMapOf<UUID, Int>()

    override fun start() {
        super.start()

        val mace =
            createBasicItem(Material.MACE, "Mace", 1, "<gray>Use this to bounce!").apply {
                addUnsafeEnchantment(Enchantment.WIND_BURST, 4)
            }
        for (player in onlinePlayers) {
            player.inventory.clear()
            player.give(mace)
        }

        val worldBorder = startPos.world.worldBorder
        worldBorder.setCenter(0.5, 0.5)
        worldBorder.changeSize(101.0, 0)

        startCountdown(3 * 60 * 20) {
            end()
        }

        // start a task that will spawn in a random entity
        Bukkit.getScheduler().runTaskTimer(plugin, { t ->
            if (!running) {
                t.cancel()
                return@runTaskTimer
            }

            // get a random player's position
            val player = onlinePlayers.randomOrNull() ?: return@runTaskTimer
            val loc = player.location.clone()

            // spawn a pig +-3 x/z from the player, at y = max(player.y - 10, 45)
            val xOffset = (-3..3).random()
            val zOffset = (-3..3).random()
            val yPos = maxOf(loc.y - 10, 45.0)
            loc.x += xOffset
            loc.y = yPos
            loc.z += zOffset

            startPos.world.spawn(loc, Pig::class.java)
        }, 0L, 2 * 20)

        // give a slight nudge to all pigs every second to keep them bouncing
        Bukkit.getScheduler().runTaskTimer(plugin, { t ->
            if (!running) {
                t.cancel()
                return@runTaskTimer
            }

            for (entity in startPos.world.entities) {
                if (entity !is Pig) {
                    continue
                }

                if (entity.y > 50) {
                    continue
                }

                val vel = entity.velocity
                if (vel.length() > 0.8) {
                    continue
                }

                entity.velocity = vel.clone().add(Vector(0.0, 1.0, 0.0))
            }
        }, 0L, 20L)
    }

    override fun handleEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        event.damage = 0.0
        val damager = event.damager as? Player ?: return
        val weapon = damager.inventory.itemInMainHand
        if (weapon.type != Material.MACE) return
        if (damager.attackCooldown <= 0.8) return // require almost-full swing
        game.addScore(damager, 5, "hitting an entity")
    }

    override fun handlePrePlayerAttack(event: PrePlayerAttackEntityEvent) {
        event.isCancelled = false
    }

    override fun handlePlayerMove(event: PlayerMoveEvent) {
        if (game.state != GameState.PLAYING) {
            return
        }
        val player = event.player
        val uuid = player.uniqueId
        val y = player.location.y

        // check if we reached a new record
        val highest = highestY.getOrDefault(uuid, startPos.y + 5)
        if (y > highest) {
            val pointsToGive = ((y - highest) * 10).toInt()
            game.addScore(player, pointsToGive, "reached a new height of ${y.toInt()}!")
            highestY[uuid] = y
        }

        // count up if we're below y=45, teleport back to y = 60 after 30 ticks
        if (y < 45) {
            val count = below45.getOrDefault(uuid, 0) + 1
            if (count >= 30) {
                player.teleport(player.location.clone().apply { this.y = 60.0 })
                game.addScore(player, -20, "falling too low!")
                below45.remove(uuid)
            } else {
                below45[uuid] = count
            }
        } else {
            below45.remove(uuid)
        }
    }

    override val name: Component
        get() = mm("<aqua>Mace Bounce")
    override val description: Component
        get() = mm("<aqua>Use your mace to bounce as high as you can!")
}
