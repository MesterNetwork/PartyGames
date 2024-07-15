package info.mester.bedless.tournament

import info.mester.bedless.tournament.admin.PlayerAdminUI
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
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

        val adminStatus = Tournament.game().isAdmin(event.player.uniqueId)
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
}