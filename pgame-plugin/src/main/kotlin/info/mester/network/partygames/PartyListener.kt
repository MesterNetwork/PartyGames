package info.mester.network.partygames

import info.mester.network.partygames.api.GameState
import info.mester.network.partygames.api.events.GameEndedEvent
import info.mester.network.partygames.api.events.GameStartedEvent
import info.mester.network.partygames.api.events.GameTerminatedEvent
import info.mester.network.partygames.api.events.PlayerRejoinedEvent
import info.mester.network.partygames.api.events.PlayerRemovedFromGameEvent
import info.mester.network.partygames.game.HealthShopMinigame
import info.mester.network.partygames.game.SpeedBuildersMinigame
import info.mester.network.partygames.game.healthshop.HealthShopUI
import info.mester.network.partygames.util.snapTo90
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ArrowBodyCountChangeEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.meta.SpawnEggMeta
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import kotlin.math.floor
import kotlin.math.roundToInt

class PartyListener(
    private val plugin: PartyGames,
) : Listener {
    private val gameManager = plugin.queueManager
    private val core = plugin.core
    private val sidebarManager = plugin.sidebarManager

    @EventHandler
    fun onAsyncChat(event: AsyncChatEvent) {
        if (core.isAdmin(event.player)) {
            return
        }
        // rewrite viewers so only players in the same world can see the message
        if (!event.player.hasPermission("partygames.globalchat")) {
            val viewers = event.viewers()
            viewers.clear()
            for (player in event.player.world.players) {
                viewers.add(player)
            }
        }
        val plainText = PlainTextComponentSerializer.plainText().serialize(event.message())
        val game = core.gameRegistry.getGameOf(event.player) ?: return
        // special code for saying "fire map"
        if (game.state == GameState.PRE_GAME &&
            game.runningMinigame is HealthShopMinigame &&
            game.runningMinigame?.worldIndex == 0 &&
            plainText == "fire map"
        ) {
            game.awardPhrase(event.player, plainText, 25, "FIRE MAP!!!!")
        }
        // special code for saying "gg"
        if (game.state == GameState.POST_GAME && !game.hasNextMinigame() && plainText.lowercase() == "gg") {
            game.awardPhrase(event.player, "gg", 15, "Good Game")
        }
        // remove points for saying "ez" when the game is ending
        if (game.state == GameState.POST_GAME &&
            !game.hasNextMinigame() &&
            plainText
                .split(" ")
                .any { it.lowercase() == "ez" }
        ) {
            game.awardPhrase(event.player, "ez", -30, "Disrespectful behaviour")
        }
        // special code for saying "i wanna lose"
        if (plainText.lowercase() == "i wanna lose") {
            game.awardPhrase(event.player, "minuspoints", -200, "You wanted it")
        }
        // special code for "givex" and "losex"
        if (plainText.lowercase().startsWith("give") && event.player.hasPermission("partygames.admin")) {
            val amount = plainText.substringAfter("give").toIntOrNull() ?: return
            game.addScore(event.player, amount, "admin command")
        }
        if (plainText.lowercase().startsWith("lose") && event.player.hasPermission("partygames.admin")) {
            val amount = plainText.substringAfter("lose").toIntOrNull() ?: return
            game.addScore(event.player, -amount, "admin command")
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        gameManager.removePlayerFromQueue(event.player)
        plugin.sidebarManager.unregisterPlayer(event.player)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        plugin.showPlayerLevel(event.player)
        plugin.sidebarManager.openLobbySidebar(event.player)
    }

    @EventHandler
    fun onArrowBodyCountChange(event: ArrowBodyCountChangeEvent) {
        // players should not have arrows stuck in their butts
        event.newAmount = 0
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (plugin.core.isAdmin(event.player)) {
            return
        }
        plugin.queueManager.getQueueOf(event.player)?.handlePlayerInteract(event)
    }

    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        val world = event.world
        PartyGames.initWorld(world)
    }

    @EventHandler
    fun onGameStarted(event: GameStartedEvent) {
        val game = event.game
        plugin.playingPlaceholder.addPlaying(game.bundle.name, event.players.size)
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                for (player in event.players) {
                    sidebarManager.openGameSidebar(player)
                }
            },
            1,
        )
    }

    @EventHandler
    fun onGameTerminated(event: GameTerminatedEvent) {
        val game = event.game
        plugin.playingPlaceholder.removePlaying(game.bundle.name, event.playerCount)
        for (player in event.game.onlinePlayers) {
            plugin.sidebarManager.openLobbySidebar(player)
            plugin.showPlayerLevel(player)
        }
        event.setSpawnLocation(plugin.spawnLocation)
    }

    @EventHandler
    fun onGameEnded(event: GameEndedEvent) {
        /**
         * Time played in seconds.
         */
        val timeElapsed = (Bukkit.getCurrentTick() - event.game.startTime) / 20

        for ((placement, topList) in event.topList.withIndex()) {
            val player = topList.player
            val data = topList.data

            // 0.8 * total points gained
            val xpFromScore = (data.totalScore.coerceAtLeast(0) * 0.8).roundToInt()
            // 15 xp every 30 seconds of playtime
            val xpFromPlayTime = (timeElapsed / 30) * 15
            // 50 xp for top 1
            // 40 xp for top 2
            // 30 xp for top 3
            // 10 xp for top 5
            val xpFromPlacement =
                when (placement) {
                    0 -> 50
                    1 -> 40
                    2 -> 30
                    in 3..4 -> 10
                    else -> 0
                }
            // 20 xp for each star
            val xpFromStars = data.stars * 20
            val totalXp = xpFromScore + xpFromPlayTime + xpFromPlacement + xpFromStars

            plugin.databaseManager.addPointsGained(
                player.uniqueId,
                event.game.bundle.name,
                data.totalScore.coerceAtLeast(0),
            )
            plugin.databaseManager.addTimePlayed(player.uniqueId, event.game.bundle.name, timeElapsed)

            // process boosters
            val boosters = plugin.boosterManager.getBooster(player)
            val boosterMultiplier = boosters.fold(1.0) { acc, booster -> acc * booster.multiplier }
            val finalXp = (totalXp * boosterMultiplier).toInt()

            val oldLevel = plugin.levelManager.levelDataOf(player.uniqueId)
            plugin.levelManager.addXp(player.uniqueId, finalXp)
            val onlinePlayer = Bukkit.getPlayer(player.uniqueId) ?: return

            // send level up message
            val newLevel = plugin.levelManager.levelDataOf(player.uniqueId)
            val levelUpMessage =
                buildString {
                    val levelString = "<gray>Level: <yellow>${oldLevel.level}"
                    append(levelString)
                    val leveledUp = newLevel.level > oldLevel.level
                    if (leveledUp) {
                        appendLine(" <dark_gray>-> <green>${newLevel.level} <green><bold>LEVEL UP!</bold>")
                    } else {
                        appendLine()
                    }
                    append("<gray>Progress: ")
                    append("<yellow>${newLevel.xp} <dark_gray>[")
                    val maxSquares = 15
                    // render the progress bar (we have progressLength squares available)
                    val progress = (newLevel.xp / newLevel.xpToNextLevel.toFloat())
                    val previousProgress = (oldLevel.xp / oldLevel.xpToNextLevel.toFloat())
                    val filledSquares = floor(progress * maxSquares).toInt()
                    var previousFilledSquares = if (leveledUp) 0 else floor(previousProgress * maxSquares).toInt()
                    // if there are no additional squares, that means we've only earned very little progress
                    // in that case, the last progress square should always be green to indicate that
                    var additionalSquares = filledSquares - previousFilledSquares
                    if (additionalSquares == 0 && (newLevel.xp - oldLevel.xp) > 0) {
                        previousFilledSquares -= 1
                        additionalSquares = 1
                    }
                    append("<yellow>" + "■".repeat(previousFilledSquares))
                    append("<green>" + "■".repeat(additionalSquares))
                    append("<gray>" + "■".repeat(maxSquares - filledSquares))
                    appendLine("<dark_gray>] <green>${newLevel.xpToNextLevel}")

                    appendLine("<yellow>+$xpFromStars XP <gray>(Stars Earned)")
                    appendLine("<yellow>+$xpFromScore XP <gray>(Points Gained)")
                    appendLine("<yellow>+$xpFromPlacement XP <gray>(Placement)")
                    appendLine("<yellow>+$xpFromPlayTime XP <gray>(Time Played)")
                    for (booster in boosters) {
                        append(
                            "<yellow>+${((booster.multiplier - 1) * 100).toInt()}% <gray>(${booster.name})\n",
                        )
                    }
                    appendLine("<gold>= <yellow>$finalXp XP")
                    append("<dark_gray>${"-".repeat(30)}")
                }
            onlinePlayer.sendMessage(mm.deserialize(levelUpMessage))
        }

        // increase games won stat
        plugin.databaseManager.addGameWon(
            event.topList
                .first()
                .player.uniqueId,
            event.game.bundle.name,
        )
    }

    @EventHandler
    fun onPlayerRejoined(event: PlayerRejoinedEvent) {
        val player = event.player
        Bukkit.getScheduler().runTaskLater(
            plugin,
            Runnable {
                plugin.sidebarManager.openGameSidebar(player)
            },
            1,
        )
    }

    @EventHandler
    fun onPlayerRemovedFromGame(event: PlayerRemovedFromGameEvent) {
        val player = event.player
        if (player.isOnline) {
            plugin.sidebarManager.openLobbySidebar(player)
            player.teleport(plugin.spawnLocation)
        }
        plugin.playingPlaceholder.removePlaying(event.game.bundle.name, 1)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (core.isAdmin(player)) {
            return
        }
        if (player.world.name == "world" && event.to.y < 50 && player.gameMode == GameMode.SURVIVAL) {
            player.teleport(plugin.spawnLocation)
        }
    }

    private val spawnEggUseMap: MutableMap<UUID, Long> = mutableMapOf()

    @EventHandler
    fun playerUsesSpawnEgg(event: PlayerInteractEvent) {
        val player = event.player
        if (!player.hasPermission("partygames.spawnegg")) {
            return
        }
        if (core.gameRegistry.getGameOf(player) != null) {
            return
        }
        val item = event.item ?: return
        // Check if the item is a spawn egg
        if (item.itemMeta !is SpawnEggMeta) return
        // Ensure the player is using the item in their main hand
        if (event.hand != EquipmentSlot.HAND) return
        // Track the player using the spawn egg
        spawnEggUseMap[player.uniqueId] = System.currentTimeMillis()
    }

    @EventHandler
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        // We only care about spawns caused by spawn eggs
        if (event.spawnReason != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return
        // Find the player responsible for this spawn
        val player =
            spawnEggUseMap.entries
                .firstOrNull { System.currentTimeMillis() - it.value < 1000 } // Within 1 second
                ?.key
                ?.let { Bukkit.getPlayer(it) } ?: return
        val snappedAngle = snapTo90(player.location.yaw)
        val spawnee = event.entity
        spawnee.setRotation(snappedAngle, 0.0f)
        spawnee.setAI(false)
        spawnee.isSilent = true
        spawnee.persistentDataContainer.set(
            SpeedBuildersMinigame.SPAWNED_ENTITY_KEY,
            PersistentDataType.BOOLEAN,
            true,
        )
    }

    @EventHandler
    fun onPrePlayerAttack(event: PrePlayerAttackEntityEvent) {
        val player = event.player
        if (core.gameRegistry.getGameOf(player) != null) {
            return
        }
        if (!player.hasPermission("partygames.spawnegg")) {
            return
        }
        val target = event.attacked
        if (target.persistentDataContainer.has(SpeedBuildersMinigame.SPAWNED_ENTITY_KEY, PersistentDataType.BOOLEAN)) {
            target.remove()
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.clickedInventory?.getHolder(false)
        if (holder is HealthShopUI) {
            // we handle Health Shop UI clicks here so we can create a test UI
            event.isCancelled = true
            holder.onInventoryClick(event)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder
        val player = event.player as? Player ?: return
        if (holder is HealthShopUI && player.persistentDataContainer.has(Bootstrapper.TEST_HEALTHSHOP_UI_KEY)) {
            player.getAttribute(Attribute.MAX_HEALTH)?.apply {
                baseValue = defaultValue
                player.health = baseValue
                player.sendHealthUpdate()
            }
            player.persistentDataContainer.remove(Bootstrapper.TEST_HEALTHSHOP_UI_KEY)
        }
    }
}
