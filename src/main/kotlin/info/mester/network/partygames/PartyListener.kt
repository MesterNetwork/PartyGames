package info.mester.network.partygames

import info.mester.bedless.tournament.admin.InvseeUI
import info.mester.bedless.tournament.admin.PlayerAdminUI
import info.mester.bedless.tournament.game.HealthShopMinigame
import info.mester.bedless.tournament.game.HealthShopMinigameState
import info.mester.bedless.tournament.game.HealthShopUI
import info.mester.bedless.tournament.game.MathMinigame
import info.mester.bedless.tournament.game.SpeedBuildersMinigame
import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent
import io.papermc.paper.event.entity.EntityMoveEvent
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.GameRule
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
    private val plugin: _root_ide_package_.info.mester.network.partygames.PartyGames,
) : Listener {
    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand == EquipmentSlot.OFF_HAND) {
            return
        }

        if (_root_ide_package_.info.mester.network.partygames.PartyGames.game
                .isAdmin(event.player) &&
            event.rightClicked is Player
        ) {
            event.isCancelled = true
            // setup admin ui
            val playerAdminUI = PlayerAdminUI(event.rightClicked)
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
        if (event.entity.type == EntityType.PLAYER) {
            // cancel fall damage
            if (event.cause == EntityDamageEvent.DamageCause.FALL) {
                event.isCancelled = true
                return
            }
        }
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        if (event.entity.type == EntityType.PLAYER) {
            event.isCancelled = true
            val player = Bukkit.getPlayer(event.entity.uniqueId)!!
            player.foodLevel = 20
            player.saturation = 20f
            // normally, saturation should be full, but during the fight state in the health shop minigame
            // we want to avoid the fast regeneration, so set saturation to 0
            _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame?.let { minigame ->
                if (minigame is HealthShopMinigame) {
                    player.saturation = 0f
                }
            }
            player.sendHealthUpdate()
        }
    }

    @EventHandler
    fun onAsyncChat(event: AsyncChatEvent) {
        if (_root_ide_package_.info.mester.network.partygames.PartyGames.game
                .isAdmin(event.player)
        ) {
            return
        }
        // only cancel the event if we're in a minigame
        val runningMinigame =
            _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame ?: return
        // event.isCancelled = true
        if (runningMinigame is MathMinigame) {
            try {
                val rawText = PlainTextComponentSerializer.plainText().serialize(event.message())
                val intText = rawText.toInt()
                runningMinigame.validateAnswer(event.player, intText)
            } catch (e: NumberFormatException) {
                event.player.sendMessage(Component.text("Please enter a number!", NamedTextColor.RED))
            }

            return
        }
        // send warning message
        // event.player.sendMessage(Component.text("You cannot use the chat during this minigame!", NamedTextColor.RED))
    }

    @EventHandler
    fun onPrePlayerAttack(event: PrePlayerAttackEntityEvent) {
        // by default disable the event for every non-admin player
        if (_root_ide_package_.info.mester.network.partygames.PartyGames.game
                .isAdmin(event.player)
        ) {
            return
        }
        // however, if we're in the health shop minigame DURING the fight, we can allow the event
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        if (runningMinigame is HealthShopMinigame && runningMinigame.state == HealthShopMinigameState.FIGHT) {
            return
        }

        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (_root_ide_package_.info.mester.network.partygames.PartyGames.game
                .isAdmin(event.player)
        ) {
            return
        }
        // disable the event if we're in the health shop minigame during the shop state
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        if (runningMinigame is HealthShopMinigame && runningMinigame.state == HealthShopMinigameState.SHOP) {
            // we cannot cancel the event, so run onPlayerCloseUI manually
            runningMinigame.handlePlayerCloseUI(event.player.uniqueId)
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (_root_ide_package_.info.mester.network.partygames.PartyGames.game
                .isAdmin(event.player)
        ) {
            return
        }
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        runningMinigame?.handlePlayerMove(event)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        _root_ide_package_.info.mester.network.partygames.PartyGames.game
            .setAdmin(event.player, false)
        _root_ide_package_.info.mester.network.partygames.PartyGames.game
            .removePlayer(event.player.uniqueId)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // make sure admins are hidden from new players
        for (admin in Bukkit
            .getOnlinePlayers()
            .filter {
                _root_ide_package_.info.mester.network.partygames.PartyGames.game
                    .isAdmin(it)
            }) {
            event.player.hidePlayer(plugin, admin)
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (_root_ide_package_.info.mester.network.partygames.PartyGames.game
                .isAdmin(event.player)
        ) {
            return
        }

        event.isCancelled = true
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        runningMinigame?.handleBlockBreak(event)
    }

    @EventHandler
    fun onEntityRegainHealth(event: EntityRegainHealthEvent) {
        if (_root_ide_package_.info.mester.network.partygames.PartyGames.game
                .isAdmin(event.entity)
        ) {
            return
        }
        if (event.entity.type != EntityType.PLAYER) {
            return
        }
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
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
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        if (runningMinigame is HealthShopMinigame && damager is Player) {
            runningMinigame.handlePlayerHit(damager, damagee, event.finalDamage)
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        runningMinigame?.handlePlayerDeath(event)
    }

    @EventHandler
    fun onPlayerItemConsume(event: PlayerItemConsumeEvent) {
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        if (runningMinigame is HealthShopMinigame) {
            runningMinigame.handlePlayerItemConsume(event)
        }
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (_root_ide_package_.info.mester.network.partygames.PartyGames.game
                .isAdmin(event.player)
        ) {
            return
        }

        event.isCancelled = true
    }

    @EventHandler
    fun onBlockBreakProgressUpdate(event: BlockBreakProgressUpdateEvent) {
        if (_root_ide_package_.info.mester.network.partygames.PartyGames.game
                .isAdmin(event.entity)
        ) {
            return
        }
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        if (runningMinigame is SpeedBuildersMinigame) {
            runningMinigame.handleBlockBreakProgressUpdate(event)
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (_root_ide_package_.info.mester.network.partygames.PartyGames.game
                .isAdmin(event.player)
        ) {
            return
        }
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        runningMinigame?.handleBlockPlace(event)
    }

    @EventHandler
    fun onArrowBodyCountChange(event: ArrowBodyCountChangeEvent) {
        // players should not have arrows stuck in their butts
        event.newAmount = 0
    }

    @EventHandler
    fun onEntityShootBow(event: EntityShootBowEvent) {
        if (_root_ide_package_.info.mester.network.partygames.PartyGames.game
                .isAdmin(event.entity)
        ) {
            return
        }
        // don't allow arrows from being picked up
        val projectile = event.projectile
        if (projectile is Arrow) {
            projectile.pickupStatus = AbstractArrow.PickupStatus.CREATIVE_ONLY
        }
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        if (runningMinigame is HealthShopMinigame) {
            runningMinigame.handleEntityShootBow(event)
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (_root_ide_package_.info.mester.network.partygames.PartyGames.game
                .isAdmin(event.player)
        ) {
            return
        }
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        runningMinigame?.handlePlayerInteract(event)
    }

    @EventHandler
    fun onEntityMove(event: EntityMoveEvent) {
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        runningMinigame?.handleEntityMove(event)
    }

    @EventHandler
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        runningMinigame?.handleBlockPhysics(event)
    }

    @EventHandler
    fun onEntityCombust(event: EntityCombustEvent) {
        val runningMinigame = _root_ide_package_.info.mester.network.partygames.PartyGames.game.runningMinigame
        runningMinigame?.handleEntityCombust(event)
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
    }
}
