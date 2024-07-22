package info.mester.bedless.tournament

import info.mester.bedless.tournament.admin.PlayerAdminUI
import info.mester.bedless.tournament.game.HealthShopMinigame
import info.mester.bedless.tournament.game.HealthShopMinigameState
import info.mester.bedless.tournament.game.HealthShopUI
import info.mester.bedless.tournament.game.MathMinigame
import info.mester.bedless.tournament.game.RunawayMinigame
import info.mester.bedless.tournament.game.SpeedBuildersMinigame
import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent
import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.ArrowBodyCountChangeEvent
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
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot

class GameListener(
    private val plugin: Tournament,
) : Listener {
    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand == EquipmentSlot.OFF_HAND) {
            return
        }

        if (Tournament.game.isAdmin(event.player)) {
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

        if (event.slotType == InventoryType.SlotType.ARMOR) {
            event.isCancelled = true
            return
        }

        if (holder is Player && event.slot == 40) {
            event.isCancelled = true
            return
        }

        if (holder is PlayerAdminUI) {
            event.isCancelled = true
            holder.onInventoryClick(event)
        }

        if (holder is HealthShopUI) {
            event.isCancelled = true
            holder.onInventoryClick(event)
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
            player.saturation = 5f
            player.sendHealthUpdate()
        }
    }

    @EventHandler
    fun onAsyncChat(event: AsyncChatEvent) {
        if (Tournament.game.isAdmin(event.player)) {
            return
        }
        // only cancel the event if we're in a minigame
        val runningMinigame = Tournament.game.runningMinigame ?: return
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
        if (Tournament.game.isAdmin(event.player)) {
            return
        }
        // however, if we're in the health shop minigame DURING the fight, we can allow the event
        val runningMinigame = Tournament.game.runningMinigame
        if (runningMinigame is HealthShopMinigame && runningMinigame.state == HealthShopMinigameState.FIGHT) {
            return
        }

        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (Tournament.game.isAdmin(event.player)) {
            return
        }
        // disable the event if we're in the health shop minigame during the shop state
        val runningMinigame = Tournament.game.runningMinigame
        if (runningMinigame is HealthShopMinigame && runningMinigame.state == HealthShopMinigameState.SHOP) {
            // we cannot cancel the event, so run onPlayerCloseUI manually
            runningMinigame.handlePlayerCloseUI(event.player.uniqueId)
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (Tournament.game.isAdmin(event.player)) {
            return
        }
        val runningMinigame = Tournament.game.runningMinigame
        // don't let players move if they're in the health shop minigame during the shop state
        if (runningMinigame is HealthShopMinigame && runningMinigame.state == HealthShopMinigameState.SHOP) {
            event.isCancelled = true
        }
        if (runningMinigame is SpeedBuildersMinigame) {
            runningMinigame.handlePlayerMove(event)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        Tournament.game.setAdmin(event.player, false)
        Tournament.game.removePlayer(event.player.uniqueId)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // make sure admins are hidden from new players
        for (admin in Bukkit.getOnlinePlayers().filter { Tournament.game.isAdmin(it) }) {
            event.player.hidePlayer(plugin, admin)
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (Tournament.game.isAdmin(event.player)) {
            return
        }

        event.isCancelled = true
    }

    @EventHandler
    fun onEntityRegainHealth(event: EntityRegainHealthEvent) {
        if (Tournament.game.isAdmin(event.entity)) {
            return
        }
        if (event.entity.type != EntityType.PLAYER) {
            return
        }
        val runningMinigame = Tournament.game.runningMinigame
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
        val runningMinigame = Tournament.game.runningMinigame
        if (runningMinigame is HealthShopMinigame && damager is Player) {
            runningMinigame.handlePlayerHit(damager, damagee, event.finalDamage)
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val runningMinigame = Tournament.game.runningMinigame
        if (runningMinigame is RunawayMinigame) {
            event.isCancelled = true
            return
        }
        if (runningMinigame is HealthShopMinigame && runningMinigame.state == HealthShopMinigameState.FIGHT) {
            runningMinigame.handlePlayerDeath(event)
        }
    }

    @EventHandler
    fun onPlayerItemConsume(event: PlayerItemConsumeEvent) {
        val runningMinigame = Tournament.game.runningMinigame
        if (runningMinigame is HealthShopMinigame && runningMinigame.state == HealthShopMinigameState.FIGHT) {
            runningMinigame.handlePlayerConsumeItem(event)
        }
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (Tournament.game.isAdmin(event.player)) {
            return
        }

        event.isCancelled = true
    }

    @EventHandler
    fun onBlockBreakProgressUpdate(event: BlockBreakProgressUpdateEvent) {
        if (Tournament.game.isAdmin(event.entity)) {
            return
        }
        val runningMinigame = Tournament.game.runningMinigame
        if (runningMinigame is SpeedBuildersMinigame) {
            runningMinigame.handleBlockBreakProgressUpdate(event)
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (Tournament.game.isAdmin(event.player)) {
            return
        }
        val runningMinigame = Tournament.game.runningMinigame
        if (runningMinigame is SpeedBuildersMinigame) {
            runningMinigame.handleBlockPlace(event)
        }
    }

    @EventHandler
    fun onArrowBodyCountChange(event: ArrowBodyCountChangeEvent) {
        // players should not have arrows stuck in their butts
        event.newAmount = 0
    }

    @EventHandler
    fun onEntityShootBow(event: EntityShootBowEvent) {
        if (Tournament.game.isAdmin(event.entity)) {
            return
        }
        // don't allow arrows from being picked up
        val projectile = event.projectile
        if (projectile is Arrow) {
            projectile.pickupStatus = AbstractArrow.PickupStatus.CREATIVE_ONLY
        }
        val runningMinigame = Tournament.game.runningMinigame ?: return
        if (runningMinigame is HealthShopMinigame) {
            runningMinigame.handleEntityShootBow(event)
        }
    }
}
