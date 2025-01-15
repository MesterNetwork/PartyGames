package info.mester.network.partygames.game.snifferhunt

import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.game.SnifferHuntMinigame
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.entity.Sniffer

class RideableSniffer(
    private val player: Player,
    private val game: SnifferHuntMinigame,
) {
    private val sniffer: Sniffer =
        player.world.spawn(player.location, Sniffer::class.java) { entity ->
            entity.isInvulnerable = true
            val movementSpeed = entity.getAttribute(Attribute.MOVEMENT_SPEED)!!
            movementSpeed.baseValue = 0.3
        }
    private val treasureMap = TreasureMap()
    var sniffing = false
        private set

    private fun attemptWalk() {
        // find the block the player is looking at
        val raycast = player.rayTraceBlocks(25.0) ?: return
        if (raycast.hitBlock == null) {
            return
        }
        val finalBlock = raycast.hitBlock!!.getRelative(raycast.hitBlockFace ?: BlockFace.UP)
        sniffer.pathfinder.stopPathfinding()
        sniffer.pathfinder.moveTo(finalBlock.location)
    }

    fun interactedWith(entity: Entity) = entity.uniqueId == sniffer.uniqueId

    fun mountPlayer() {
        sniffer.addPassenger(player)
        sniffer.setAI(true)
        // start the pathfinding timer (run every half a second)
        Bukkit.getScheduler().runTaskTimer(PartyGames.plugin, { t ->
            if (sniffer.isDead || sniffer.passengers.isEmpty() || !game.running) {
                t.cancel()
                return@runTaskTimer
            }
            if (!sniffing) {
                attemptWalk()
            }
        }, 0, 10)
    }

    fun sniff() {
        if (sniffing) {
            return
        }
        sniffing = true
        sniffer.pathfinder.stopPathfinding()
        sniffer.pose = Pose.SNIFFING
        // sniff for 3 seconds
        Bukkit.getScheduler().runTaskLater(
            PartyGames.plugin,
            Runnable {
                sniffer.pose = Pose.DIGGING
                // dig for 5 seconds
                Bukkit.getScheduler().runTaskLater(
                    PartyGames.plugin,
                    Runnable {
                        val x = (sniffer.location.blockX + 30).coerceIn(TreasureMap.validXRange)
                        val z = (sniffer.location.blockZ + 30).coerceIn(TreasureMap.validZRange)
                        // generate treasure
                        val treasureValue = treasureMap.getTreasureValue(x, z)
                        SnifferHuntMinigame.giveTreasure(player, treasureValue)
                        // apply damping to the treasure map
                        treasureMap.applyDamping(x, z)
                        sniffing = false
                        sniffer.pose = Pose.STANDING
                    },
                    5 * 20,
                )
            },
            3 * 20,
        )
    }

    fun kill() {
        sniffer.remove()
    }

    fun getTreasureValue(location: Location): Double {
        val x = (location.blockX + 30).coerceIn(TreasureMap.validXRange)
        val z = (location.blockZ + 30).coerceIn(TreasureMap.validZRange)
        return treasureMap.getTreasureValue(x, z)
    }
}
