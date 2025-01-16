package info.mester.network.partygames.api

import com.destroystokyo.paper.event.block.AnvilDamagedEvent
import info.mester.network.partygames.api.admin.InvseeUI
import info.mester.network.partygames.api.admin.PlayerAdminUI
import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent
import io.papermc.paper.event.entity.EntityMoveEvent
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.ArrowBodyCountChangeEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.inventory.EquipmentSlot

class PartyGamesListener(
    private val core: PartyGamesCore,
) : Listener {
    private val gameRegistry = core.gameRegistry

    private fun getMinigameFromWorld(world: World) = gameRegistry.getGameByWorld(world)?.runningMinigame

    @EventHandler
    fun onPlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        if (event.hand == EquipmentSlot.OFF_HAND) {
            return
        }
        // check if the player is an admin and if they right-clicked a player while in a game
        val game = gameRegistry.getGameByWorld(event.player.world)
        if (core.isAdmin(event.player) &&
            event.rightClicked is Player &&
            game != null
        ) {
            event.isCancelled = true
            // setup admin ui
            val playerAdminUI = PlayerAdminUI(game, event.rightClicked as Player)
            event.player.openInventory(playerAdminUI.inventory)
            return
        }
        // execute the event on the minigame
        val minigame = getMinigameFromWorld(event.player.world)
        minigame?.handlePlayerInteractAtEntity(event)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val clickedInventory = event.clickedInventory ?: return
        val holder = clickedInventory.getHolder(false)
        if (holder is Player && !core.isAdmin(event.whoClicked)) {
            // don't let players interact with their armor and offhand
            if (event.slotType == InventoryType.SlotType.ARMOR || event.slot == 40) {
                event.isCancelled = true
                return
            }
        }

        if (holder is PlayerAdminUI) {
            event.isCancelled = true
            holder.onInventoryClick(event)
        }

        if (holder is HealthShopUI) {
            event.isCancelled = true
            holder.onInventoryClick(event)
        }

        if (holder is InvseeUI) {
            event.isCancelled = true
            return
        }
        val minigame = getMinigameFromWorld(event.whoClicked.world)
        minigame?.handleInventoryClick(event, clickedInventory)
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        // cancel fall damage
        if (event.entity.type == EntityType.PLAYER && event.cause == EntityDamageEvent.DamageCause.FALL) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        if (event.entity.type == EntityType.PLAYER) {
            event.isCancelled = true
            val player = event.entity as Player
            player.foodLevel = 20
            player.saturation = 0f
            player.sendHealthUpdate()
        }
    }

    @EventHandler
    fun onAsyncChat(event: AsyncChatEvent) {
        if (PartyGames.plugin.isAdmin(event.player)) {
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
        val game = gameRegistry.getGameOf(event.player) ?: return
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
        val minigame = getMinigameFromWorld(event.player.world)
        minigame?.handlePlayerChat(event)
    }

    @EventHandler
    fun onPrePlayerAttack(event: PrePlayerAttackEntityEvent) {
        // by default disable the event for every non-admin player
        if (plugin.isAdmin(event.player)) {
            return
        }
        event.isCancelled = true
        // however, the minigame may override the cancellation, allowing the event
        val minigame = getMinigameFromWorld(event.player.world) ?: return
        minigame.handlePrePlayerAttack(event)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (plugin.isAdmin(event.player)) {
            return
        }
        val minigame = getMinigameFromWorld(event.player.world)
        minigame?.handleInventoryClose(event)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (plugin.isAdmin(event.player)) {
            return
        }
        val minigame = getMinigameFromWorld(event.player.world)
        minigame?.handlePlayerMove(event)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        plugin.setAdmin(event.player, false)
        gameRegistry.getQueueOf(event.player)?.removePlayer(event.player)
        gameRegistry.getGameOf(event.player)?.handleDisconnect(event.player, false)
        plugin.sidebarManager.unregisterPlayer(event.player)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        Game.resetPlayer(event.player)
        plugin.showPlayerLevel(event.player)
        plugin.sidebarManager.openLobbySidebar(event.player)
        // make sure admins are hidden from new players
        for (admin in Bukkit.getOnlinePlayers().filter { plugin.isAdmin(it) }) {
            event.player.hidePlayer(plugin, admin)
        }
        // reset some attributes
        val attribute = event.player.getAttribute(Attribute.MAX_HEALTH)!!
        attribute.baseValue = attribute.defaultValue
        event.player.sendHealthUpdate()
        // check if the player is still in a game
        gameRegistry.getGameOf(event.player)?.handleRejoin(event.player)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (plugin.isAdmin(event.player)) {
            return
        }
        val minigame = getMinigameFromWorld(event.player.world) ?: return
        event.isCancelled = true // only cancel the event if we're in a minigame
        minigame.handleBlockBreak(event)
    }

    @EventHandler
    fun onEntityRegainHealth(event: EntityRegainHealthEvent) {
        if (plugin.isAdmin(event.entity)) {
            return
        }
        if (event.entityType != EntityType.PLAYER) {
            return
        }
        val runningMinigame = getMinigameFromWorld(event.entity.world)
        if (runningMinigame is HealthShopMinigame) {
            runningMinigame.handleEntityRegainHealth(event)
        }
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val minigame = getMinigameFromWorld(event.entity.world)
        minigame?.handleEntityDamageByEntity(event)
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val minigame = getMinigameFromWorld(event.entity.world)
        minigame?.handlePlayerDeath(event)
    }

    @EventHandler
    fun onPlayerItemConsume(event: PlayerItemConsumeEvent) {
        val minigame = getMinigameFromWorld(event.player.world)
        if (minigame is HealthShopMinigame) {
            minigame.handlePlayerItemConsume(event)
        }
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (plugin.isAdmin(event.player)) {
            return
        }

        event.isCancelled = true
        val minigame = getMinigameFromWorld(event.player.world)
        minigame?.handlePlayerDropItem(event)
    }

    @EventHandler
    fun onBlockBreakProgressUpdate(event: BlockBreakProgressUpdateEvent) {
        if (plugin.isAdmin(event.entity)) {
            return
        }
        val minigame = getMinigameFromWorld(event.entity.world)
        if (minigame is SpeedBuildersMinigame) {
            minigame.handleBlockBreakProgressUpdate(event)
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (plugin.isAdmin(event.player)) {
            return
        }
        val minigame = getMinigameFromWorld(event.player.world)
        minigame?.handleBlockPlace(event)
    }

    @EventHandler
    fun onArrowBodyCountChange(event: ArrowBodyCountChangeEvent) {
        // players should not have arrows stuck in their butts
        event.newAmount = 0
    }

    @EventHandler
    fun onEntityShootBow(event: EntityShootBowEvent) {
        if (plugin.isAdmin(event.entity)) {
            return
        }
        // don't allow arrows from being picked up
        val projectile = event.projectile
        if (projectile is Arrow) {
            projectile.pickupStatus = AbstractArrow.PickupStatus.CREATIVE_ONLY
        }
        val minigame = getMinigameFromWorld(event.entity.world)
        if (minigame is HealthShopMinigame) {
            minigame.handleEntityShootBow(event)
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (PartyGames.plugin.isAdmin(event.player)) {
            return
        }
        plugin.gameManager.getQueueOf(event.player)?.handlePlayerInteract(event)
        getMinigameFromWorld(event.player.world)?.handlePlayerInteract(event)
    }

    @EventHandler
    fun onEntityMove(event: EntityMoveEvent) {
        val minigame = getMinigameFromWorld(event.entity.world)
        minigame?.handleEntityMove(event)
    }

    @EventHandler
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        val minigame = getMinigameFromWorld(event.block.world)
        minigame?.handleBlockPhysics(event)
    }

    @EventHandler
    fun onEntityCombust(event: EntityCombustEvent) {
        val minigame = getMinigameFromWorld(event.entity.world)
        minigame?.handleEntityCombust(event)
    }

    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        val world = event.world
        PartyGames.initWorld(world)
    }

    @EventHandler
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        event.isCancelled = true
    }

    @EventHandler
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        val runningMinigame = getMinigameFromWorld(event.entity.world)
        runningMinigame?.handleEntityChangeBlock(event)
    }

    @EventHandler
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val runningMinigame = getMinigameFromWorld(event.player.world)
        runningMinigame?.handleInventoryOpen(event)
    }

    @EventHandler
    fun onPlayerToogleFlight(event: PlayerToggleFlightEvent) {
        val runningMinigame = getMinigameFromWorld(event.player.world)
        runningMinigame?.handlePlayerToggleFlight(event)
    }

    @EventHandler
    fun onEntityDismount(event: EntityDismountEvent) {
        val runningMinigame = getMinigameFromWorld(event.entity.world)
        runningMinigame?.handleEntityDismount(event)
    }

    @EventHandler
    fun onAnvilDamaged(event: AnvilDamagedEvent) {
        event.isCancelled = true
    }
}
