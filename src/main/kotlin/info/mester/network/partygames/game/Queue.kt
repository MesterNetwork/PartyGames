package info.mester.network.partygames.game

import info.mester.network.partygames.PartyGames
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.roundToInt

private val mm = MiniMessage.miniMessage()
private val alertTimes = listOf(1, 2, 3, 5, 10, 15, 20, 30, 60, 90, 120)

private class CountdownTask(
    private val queue: Queue,
) : Consumer<BukkitTask> {
    /**
     * The remaining time in seconds.
     * If the time is -1, the countdown is not running.
     */
    private var remainingTime = -1

    private fun sendCountdownMessage() {
        val message = mm.deserialize("<gray>The game will start in <yellow>${remainingTime / 20} <gray>seconds!")
        val sound =
            Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 1.0f, 0.8f)
        queue.audience.sendMessage(message)
        queue.audience.playSound(sound, Sound.Emitter.self())
    }

    override fun accept(t: BukkitTask) {
        if (remainingTime == -1) {
            return
        }
        remainingTime -= 1
        remainingTime = remainingTime.coerceAtLeast(0)
        if (remainingTime % 20 == 0 && (remainingTime / 20) in alertTimes) {
            sendCountdownMessage()
        }
        if (remainingTime == 0) {
            t.cancel()
            queue.start()
        }
    }

    fun setRemainingTime(time: Int) {
        if (time == -1) {
            remainingTime = -1
            return
        }
        val finalTime = (time * 20).coerceAtLeast(0)
        if (finalTime < remainingTime || remainingTime == -1) {
            remainingTime = finalTime
            sendCountdownMessage()
        }
    }
}

class Queue(
    val type: GameType,
    val maxPlayers: Int,
    private val manager: GameManager,
) {
    private val players = mutableListOf<Player>()
    private val countdownTask = CountdownTask(this)
    val id: UUID = UUID.randomUUID()
    val playerCount get() = players.size
    val audience get() = Audience.audience(players)

    init {
        Bukkit.getScheduler().runTaskTimer(PartyGames.plugin, countdownTask, 1, 1)
    }

    private fun sendJoinLeaveMessage(
        player: Player,
        joined: Boolean,
    ) {
        val message =
            player.displayName().append(
                mm.deserialize(
                    " <gray>has ${if (joined) "<green>joined" else "<red>left"} <gray>the queue! (<yellow>${players.size}<dark_gray>/<green>$maxPlayers<gray>)",
                ),
            )
        val sound =
            Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 1.0f, if (joined) 1.0f else 0.5f)
        audience.sendMessage(message)
        audience.playSound(sound, Sound.Emitter.self())
        startCountdown()
    }

    private fun startCountdown() {
        // don't start the countdown if we don't have at least 20% of the max players
        if (players.size in 1..(maxPlayers * 0.0).roundToInt()) {
            countdownTask.setRemainingTime(-1)
            return
        }
        // the remaining time is based on the missing players
        val missingPlayers = (maxPlayers - players.size).coerceAtLeast(0)
        if (missingPlayers == 0) {
            countdownTask.setRemainingTime(5)
        }
        if (missingPlayers == 1) {
            countdownTask.setRemainingTime(10)
        }
        if (missingPlayers == 2) {
            countdownTask.setRemainingTime(20)
        }
        if (missingPlayers == 3) {
            countdownTask.setRemainingTime(30)
        }
        if (missingPlayers in 4..5) {
            countdownTask.setRemainingTime(60)
        }
        countdownTask.setRemainingTime(25)
    }

    private fun addPlayer(player: Player) {
        players.add(player)
        val sound = Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 1.0f, 1.0f)
        player.playSound(sound, Sound.Emitter.self())
        sendJoinLeaveMessage(player, true)
    }

    fun removePlayer(player: Player) {
        players.remove(player)
        if (players.isEmpty()) {
            manager.removeQueue(id)
            return
        }
        sendJoinLeaveMessage(player, false)
    }

    fun addPlayers(players: List<Player>): Boolean {
        if (players.size > maxPlayers - this.players.size) {
            return false
        }
        players.forEach { addPlayer(it) }
        return true
    }

    fun hasPlayer(player: Player) = players.contains(player)

    fun getPlayers() = players

    fun start() {
        manager.startGame(this)
    }
}
