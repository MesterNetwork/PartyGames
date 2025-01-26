package info.mester.network.partygames.api

import com.destroystokyo.paper.event.block.AnvilDamagedEvent
import info.mester.network.partygames.api.admin.InvseeUI
import info.mester.network.partygames.api.admin.PlayerAdminUI
import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent
import io.papermc.paper.event.entity.EntityMoveEvent
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.ArrowBodyCountChangeEvent
import org.bukkit.event.entity.CreatureSpawnEvent
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
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.meta.SpawnEggMeta
import java.util.UUID

class PartyGamesListener(
    private val core: PartyGamesCore,
) : Listener {
    private val gameRegistry = core.gameRegistry
    private val spawnEggUseMap: MutableMap<UUID, Long> = mutableMapOf()

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
        if (core.isAdmin(event.player)) {
            return
        }
        val minigame = getMinigameFromWorld(event.player.world)
        minigame?.handlePlayerChat(event)
    }

    @EventHandler
    fun onPrePlayerAttack(event: PrePlayerAttackEntityEvent) {
        // by default disable the event for every non-admin player
        if (core.isAdmin(event.player)) {
            return
        }
        event.isCancelled = true
        // however, the minigame may override the cancellation, allowing the event
        val minigame = getMinigameFromWorld(event.player.world) ?: return
        minigame.handlePrePlayerAttack(event)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (core.isAdmin(event.player)) {
            return
        }
        val minigame = getMinigameFromWorld(event.player.world)
        minigame?.handleInventoryClose(event)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (core.isAdmin(event.player)) {
            return
        }
        val minigame = getMinigameFromWorld(event.player.world)
        minigame?.handlePlayerMove(event)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        core.setAdmin(event.player, false)
        gameRegistry.getGameOf(event.player)?.handleDisconnect(event.player, false)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        Game.resetPlayer(event.player)
        // make sure admins are hidden from new players
        for (admin in Bukkit.getOnlinePlayers().filter { core.isAdmin(it) }) {
            event.player.hidePlayer(core, admin)
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
        if (core.isAdmin(event.player)) {
            return
        }
        val minigame = getMinigameFromWorld(event.player.world) ?: return
        event.isCancelled = true // only cancel the event if we're in a minigame
        minigame.handleBlockBreak(event)
    }

    @EventHandler
    fun onEntityRegainHealth(event: EntityRegainHealthEvent) {
        if (core.isAdmin(event.entity)) {
            return
        }
        if (event.entityType != EntityType.PLAYER) {
            return
        }
        val runningMinigame = getMinigameFromWorld(event.entity.world)
        runningMinigame?.handleEntityRegainHealth(event)
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
        minigame?.handlePlayerItemConsume(event)
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (core.isAdmin(event.player)) {
            return
        }

        event.isCancelled = true
        val minigame = getMinigameFromWorld(event.player.world)
        minigame?.handlePlayerDropItem(event)
    }

    @EventHandler
    fun onBlockBreakProgressUpdate(event: BlockBreakProgressUpdateEvent) {
        if (core.isAdmin(event.entity)) {
            return
        }
        val minigame = getMinigameFromWorld(event.entity.world)
        minigame?.handleBlockBreakProgressUpdate(event)
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (core.isAdmin(event.player)) {
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
        if (core.isAdmin(event.entity)) {
            return
        }
        // don't allow arrows from being picked up
        val projectile = event.projectile
        if (projectile is Arrow) {
            projectile.pickupStatus = AbstractArrow.PickupStatus.CREATIVE_ONLY
        }
        val minigame = getMinigameFromWorld(event.entity.world)
        minigame?.handleEntityShootBow(event)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (core.isAdmin(event.player)) {
            return
        }
        getMinigameFromWorld(event.player.world)?.handlePlayerInteract(event)
        if (event.useItemInHand() == Event.Result.DENY) {
            return
        }
        // look for spawn eggs
        val item = event.item ?: return
        if (item.itemMeta !is SpawnEggMeta) return
        // Ensure the player is using the item in their main hand
        if (event.hand != EquipmentSlot.HAND) return
        // Track the player using the spawn egg
        spawnEggUseMap[event.player.uniqueId] = System.currentTimeMillis()
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

    @EventHandler
    fun onPlayerKicked(event: PlayerKickEvent) {
        val game = core.gameRegistry.getGameOf(event.player) ?: return
        // hacky way to fix this weird bug where the player gets kicked during the introduction
        if (game.state == GameState.PRE_GAME && event.cause == PlayerKickEvent.Cause.FLYING_PLAYER) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val minigame = getMinigameFromWorld(event.location.world) ?: return
        // We only care about spawns caused by spawn eggs
        if (event.spawnReason != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return
        // Find the player responsible for this spawn
        val player =
            spawnEggUseMap.entries
                .firstOrNull { System.currentTimeMillis() - it.value < 1000 } // Within 1 second
                ?.key
                ?.let { Bukkit.getPlayer(it) } ?: return
        minigame.handleCreatureSpawn(event, player)
    }
}
