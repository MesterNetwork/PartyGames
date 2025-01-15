package info.mester.network.partygames.game

import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.util.createBasicItem
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.function.Consumer

private val mm = MiniMessage.miniMessage()
private val alertTimes = listOf(1, 2, 3, 5, 10, 15, 20, 30, 60, 90, 120)
private val readyItem =
    createBasicItem(
        Material.LIME_DYE,
        "<green>Ready",
        1,
        "<gray>Right click to ready up.",
        "<gray>If everyone is ready, the game will start immediately.",
    )
private val unReadyItem =
    createBasicItem(
        Material.RED_DYE,
        "<red>Unready",
        1,
        "<gray>Right click to leave the ready list.",
    )
private val leaveItem =
    createBasicItem(
        Material.BARRIER,
        "<red>Leave",
        1,
        "<gray>Right click to leave the queue.",
    )

private class CountdownTask(
    private val queue: Queue,
) : Consumer<BukkitTask> {
    /**
     * The remaining time in seconds.
     * If the time is -1, the countdown is not running.
     */
    private var remainingTime = -1

    fun getRemainingTime() = remainingTime

    private fun sendCountdownMessage() {
        val message = mm.deserialize("<gray>The game will start in <yellow>${remainingTime / 20} <gray>seconds!")
        val sound =
            Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 1.0f, 0.8f)
        queue.audience.sendMessage(message)
        queue.audience.playSound(sound, Sound.Emitter.self())
    }

    override fun accept(t: BukkitTask) {
        if (queue.ended) {
            t.cancel()
            return
        }
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
    private val readyCooldown = mutableMapOf<UUID, Long>()
    private val sidebarManager = PartyGames.plugin.sidebarManager
    private var readyPlayers = 0
    private var _ended = false
    val ended get() = _ended
    val id: UUID = UUID.randomUUID()
    val playerCount get() = players.size
    val readyPlayerCount get() = readyPlayers
    val remainingTime get() = countdownTask.getRemainingTime()
    val audience get() = Audience.audience(players)

    private fun addPlayer(player: Player) {
        players.add(player)
        sidebarManager.openQueueSidebar(player)
        player.inventory.clear()
        player.inventory.setItem(8, leaveItem)
        sendJoinLeaveMessage(player, true)
    }

    fun removePlayer(player: Player) {
        players.remove(player)
        sidebarManager.openLobbySidebar(player)
        player.inventory.clear()
        if (players.isEmpty()) {
            _ended = true
            manager.removeQueue(id)
            return
        }
        readyPlayers = 0
        if (players.size >= 2) {
            for (queuePlayer in players) {
                queuePlayer.inventory.setItem(2, readyItem)
            }
        } else {
            // if there's only one player, remove the ready item completely
            for (queuePlayer in players.filter { !it.hasPermission("partygames.admin") }) {
                queuePlayer.inventory.setItem(2, ItemStack.of(Material.AIR))
            }
        }
        sendJoinLeaveMessage(player, false)
    }

    fun addPlayers(players: List<Player>): Boolean {
        if (players.size > maxPlayers - this.players.size) {
            return false
        }
        players.forEach { addPlayer(it) }
        // if there's more than one player, give everyone the ready item
        // bypass this check if there is an admin
        if (this.players.size >= 2 || players.find { it.hasPermission("partygames.admin") } != null) {
            for (player in this.players) {
                player.inventory.setItem(2, readyItem)
            }
        }
        readyPlayers = 0
        return true
    }

    fun hasPlayer(player: Player) = players.contains(player)

    fun getPlayers() = players

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
        calculateCountdown()
    }

    private fun sendReadyStatus() {
        val message =
            mm.deserialize("<yellow>$readyPlayers<dark_gray>/<green>${players.size} <gray>players are ready!")
        audience.sendMessage(message)
    }

    private fun calculateCountdown() {
        if (players.size <= 1) {
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
        if (missingPlayers == 4) {
            countdownTask.setRemainingTime(60)
        }
        if (missingPlayers == 5) {
            countdownTask.setRemainingTime(120)
        }
        if (missingPlayers == 6) {
            countdownTask.setRemainingTime(180)
        }
        countdownTask.setRemainingTime(240)
    }

    fun start() {
        _ended = true
        manager.startGame(this)
    }

    fun handlePlayerInteract(event: PlayerInteractEvent) {
        // ready up if the item is a lime dye
        val item = event.item ?: return
        if (item.type == Material.LIME_DYE && event.action.isRightClick) {
            event.setUseItemInHand(Event.Result.DENY)
            event.setUseInteractedBlock(Event.Result.DENY)
            if (readyCooldown.containsKey(event.player.uniqueId)) {
                val diff = System.currentTimeMillis() - readyCooldown[event.player.uniqueId]!!
                // 100 milliseconds is enough to fix a player instantly readying then leaving when they double click on the item
                if (diff < 100) {
                    return
                }
            }
            if (players.size == 1 && !event.player.hasPermission("partygames.admin")) {
                event.player.sendMessage(mm.deserialize("<red>You are the only player in the queue!"))
                return
            }
            event.player.inventory.setItem(2, unReadyItem)
            readyPlayers += 1
            readyCooldown[event.player.uniqueId] = System.currentTimeMillis()
            if (readyPlayers == players.size) {
                start()
            } else {
                sendReadyStatus()
            }
        }
        // remove the player from the ready list if the item is a red dye
        if (item.type == Material.RED_DYE && event.action.isRightClick) {
            event.setUseItemInHand(Event.Result.DENY)
            event.setUseInteractedBlock(Event.Result.DENY)
            if (readyCooldown.containsKey(event.player.uniqueId)) {
                val diff = System.currentTimeMillis() - readyCooldown[event.player.uniqueId]!!
                // 100 milliseconds is enough to fix a player instantly readying then leaving when they double click on the item
                if (diff < 100) {
                    return
                }
            }
            event.player.inventory.setItem(2, readyItem)
            readyPlayers = (readyPlayers - 1).coerceAtLeast(0)
            sendReadyStatus()
        }
        // leave the queue if the item is a barrier
        if (item.type == Material.BARRIER && event.action.isRightClick) {
            event.setUseItemInHand(Event.Result.DENY)
            event.setUseInteractedBlock(Event.Result.DENY)
            removePlayer(event.player)
        }
    }
}
