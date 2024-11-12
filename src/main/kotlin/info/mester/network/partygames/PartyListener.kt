package info.mester.network.partygames

import info.mester.network.partygames.admin.InvseeUI
import info.mester.network.partygames.admin.PlayerAdminUI
import info.mester.network.partygames.game.GameState
import info.mester.network.partygames.game.HealthShopMinigame
import info.mester.network.partygames.game.HealthShopUI
import info.mester.network.partygames.game.MathMinigame
import info.mester.network.partygames.game.SpeedBuildersMinigame
import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent
import io.papermc.paper.event.entity.EntityMoveEvent
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.World
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
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.inventory.EquipmentSlot

class PartyListener(
    private val plugin: PartyGames,
) : Listener {
    private val gameManager = plugin.gameManager

    private fun getMinigameFromWorld(world: World) = gameManager.getGameByWorld(world)?.runningMinigame

    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand == EquipmentSlot.OFF_HAND) {
            return
        }
        // check if the player is an admin and if they right-clicked a player while in a game
        val game = gameManager.getGameByWorld(event.player.world)
        if (PartyGames.plugin.isAdmin(event.player) &&
            event.rightClicked is Player &&
            game != null
        ) {
            event.isCancelled = true
            // setup admin ui
            val playerAdminUI = PlayerAdminUI(game, event.rightClicked as Player)
            event.player.openInventory(playerAdminUI.inventory)
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val clickedInventory = event.clickedInventory ?: return
        val holder = clickedInventory.getHolder(false)
        if (holder is Player) {
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
        if (PartyGames.plugin.isAdmin(event.player)
        ) {
            return
        }
        val game = gameManager.getGameByWorld(event.player.world) ?: return
        // chat is never allowed during the playing state
        if (game.state != GameState.PLAYING) {
            return
        }
        event.isCancelled = true
        val minigame = game.runningMinigame ?: return
        if (minigame is MathMinigame) {
            try {
                val rawText = PlainTextComponentSerializer.plainText().serialize(event.message())
                val intText = rawText.toInt()
                minigame.validateAnswer(event.player, intText)
            } catch (e: NumberFormatException) {
                event.player.sendMessage(Component.text("Please enter a number!", NamedTextColor.RED))
            }

            return
        }
        // send warning message
        event.player.sendMessage(Component.text("You cannot use the chat during a minigame!", NamedTextColor.RED))
    }

    @EventHandler
    fun onPrePlayerAttack(event: PrePlayerAttackEntityEvent) {
        // by default disable the event for every non-admin player
        if (plugin.isAdmin(event.player)
        ) {
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
        // disable the event if we're in the health shop minigame during the shop state
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
        gameManager.removePlayerFromQueue(event.player)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // make sure admins are hidden from new players
        for (admin in Bukkit.getOnlinePlayers().filter { plugin.isAdmin(it) }) {
            event.player.hidePlayer(plugin, admin)
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (plugin.isAdmin(event.player)) {
            return
        }

        event.isCancelled = true
        val minigame = getMinigameFromWorld(event.player.world)
        minigame?.handleBlockBreak(event)
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
        val damagee = event.entity
        if (damagee !is Player) {
            return
        }
        @Suppress("UnstableApiUsage")
        val damager = event.damageSource.causingEntity
        val minigame = getMinigameFromWorld(damagee.world)
        if (minigame is HealthShopMinigame && damager is Player) {
            minigame.handlePlayerHit(damager, damagee, event.finalDamage)
        }
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
        if (PartyGames.plugin.isAdmin(event.player)
        ) {
            return
        }
        val runningMinigame = getMinigameFromWorld(event.player.world)
        runningMinigame?.handlePlayerInteract(event)
    }

    @EventHandler
    fun onEntityMove(event: EntityMoveEvent) {
        val runningMinigame = getMinigameFromWorld(event.entity.world)
        runningMinigame?.handleEntityMove(event)
    }

    @EventHandler
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        val runningMinigame = getMinigameFromWorld(event.block.world)
        runningMinigame?.handleBlockPhysics(event)
    }

    @EventHandler
    fun onEntityCombust(event: EntityCombustEvent) {
        val minigame = getMinigameFromWorld(event.entity.world)
        minigame?.handleEntityCombust(event)
    }

    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        val world = event.world
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false)
        world.setGameRule(GameRule.DO_TILE_DROPS, false)
        world.setGameRule(GameRule.DO_FIRE_TICK, false)
        world.setGameRule(GameRule.DO_MOB_LOOT, false)
        world.setGameRule(GameRule.DO_INSOMNIA, false)
        world.setGameRule(GameRule.NATURAL_REGENERATION, true)
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0)
        world.time = 6000

        if (world.name == "limbo") {
            world.time = 18000
        }
    }
}
