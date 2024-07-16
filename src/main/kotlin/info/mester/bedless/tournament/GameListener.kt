package info.mester.bedless.tournament

import info.mester.bedless.tournament.admin.PlayerAdminUI
import info.mester.bedless.tournament.game.GameState
import info.mester.bedless.tournament.game.MathMinigame
import info.mester.bedless.tournament.game.RunawayMinigame
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot

class GameListener(private val plugin: Tournament) : Listener {
    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand == EquipmentSlot.OFF_HAND) {
            return
        }

        val adminStatus = Tournament.game.isAdmin(event.player.uniqueId)
        if (adminStatus) {
            event.isCancelled = true

            // setup admin ui
            val playerAdminUI = PlayerAdminUI(event.rightClicked)
            event.player.openInventory(playerAdminUI.inventory)
        }
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val inventory = event.inventory
        if (inventory.getHolder(false) is PlayerAdminUI) {
            event.isCancelled = true

            val playerAdminUI = inventory.getHolder(false) as PlayerAdminUI
            playerAdminUI.onInventoryClick(event)
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        /*
        if(Tournament.game().state() == GameState.BEGINNING && event.player.gameMode == GameMode.SPECTATOR) {
            event.isCancelled = true
        }*/
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        if (Tournament.game.state != GameState.PLAYING) {
            return
        }

        if (event.entity.type == EntityType.PLAYER) {
            val runningMinigame = Tournament.game.runningMinigame ?: return

            if (runningMinigame is RunawayMinigame) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        if (event.entity.type == EntityType.PLAYER) {
            event.foodLevel = 20
        }
    }

    @EventHandler
    fun onAsyncChat(event: AsyncChatEvent) {
        if (Tournament.game.state != GameState.PLAYING) {
            return
        }

        val runningMinigame = Tournament.game.runningMinigame ?: return

        if (runningMinigame is MathMinigame) {
            event.isCancelled = true

            try {
                val mathMinigame = runningMinigame as MathMinigame
                val rawText = PlainTextComponentSerializer.plainText().serialize(event.message())
                val intText = rawText.toInt()
                mathMinigame.validateAnswer(event.player, intText)
            } catch (e: NumberFormatException) {
                event.player.sendMessage(Component.text("Please enter a number!", NamedTextColor.RED))
            }
        }
    }
}