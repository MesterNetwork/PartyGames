package info.mester.network.partygames.game

import de.exlll.configlib.YamlConfigurationProperties
import de.exlll.configlib.YamlConfigurationStore
import info.mester.network.partygames.PartyGames.Companion.plugin
import info.mester.network.partygames.api.Game
import info.mester.network.partygames.api.Minigame
import info.mester.network.partygames.game.snifferhunt.RideableSniffer
import info.mester.network.partygames.game.snifferhunt.SnifferHuntConfig
import info.mester.network.partygames.game.snifferhunt.TreasureRarity
import info.mester.network.partygames.mm
import io.papermc.paper.event.entity.EntityMoveEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Sniffer
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.logging.Level
import kotlin.math.pow

enum class SnifferHuntState {
    HUNT,
    TRADE,
    CRAFT,
    FIGHT,
}

class SnifferHuntMinigame(
    game: Game,
) : Minigame(game, "snifferhunt") {
    companion object {
        lateinit var config: SnifferHuntConfig
            private set

        init {
            reload()
        }

        fun reload() {
            plugin.logger.info("Loading sniffer hunt config...")
            try {
                val properties = YamlConfigurationProperties.newBuilder().build()
                val store = YamlConfigurationStore(SnifferHuntConfig::class.java, properties)
                val configFile = plugin.dataFolder.resolve("sniffer-hunt.yml").toPath()
                config = store.load(configFile)
            } catch (err: Exception) {
                plugin.logger.log(Level.SEVERE, "An error occurred while reloading the sniffer hunt config!", err)
            }
        }

        private fun generateTreasure(
            player: Player,
            rarity: TreasureRarity,
            treasureValue: Double,
        ) {
            // recursively generate a treasure for each previous rarity too
            val previousRarities = TreasureRarity.getPrevious(rarity)
            for (previousRarity in previousRarities) {
                generateTreasure(player, previousRarity, treasureValue)
            }
            // randomly pick a treasure from the config
            val treasure = config.treasures.filter { it.rarity == rarity }.randomOrNull() ?: return
            // to calculate the amount, normalize the value from a 0-1 range
            // where 0 is the rarity's minimum and 1 is the rarity's maximum
            val rarityMinimum = config.rarityChances[rarity]!!
            val rarityMaximum = config.rarityChances[rarity.next()]!!
            val normalizedValue = ((treasureValue - rarityMinimum) / (rarityMaximum - rarityMinimum)).coerceIn(0.0, 1.0)
            // use an easing function with the normalized value as the x, the y value is the final factor
            val amount =
                ((3 * normalizedValue.pow(2) - 2 * normalizedValue.pow(3)) * (treasure.maxAmount - 0.75) + 1).toInt()
            player.inventory.addItem(ItemStack.of(treasure.item, amount))
        }

        fun giveTreasure(
            player: Player,
            treasureValue: Double,
        ) {
            val rarity =
                config.rarityChances
                    .map { it.key to it.value }
                    .sortedBy { it.second }
                    .firstOrNull { treasureValue <= it.second }
                    ?.first ?: TreasureRarity.COMMON
            generateTreasure(player, rarity, treasureValue)
        }
    }

    private val sniffers = mutableMapOf<UUID, RideableSniffer>()
    var state = SnifferHuntState.HUNT
        private set

    override fun start() {
        super.start()
        // spawn a rideable sniffer for every player and mount them
        game.onlinePlayers.forEach { player ->
            val sniffer = RideableSniffer(player, this)
            sniffers[player.uniqueId] = sniffer
            sniffer.mountPlayer()
        }
        audience.sendMessage(
            mm.deserialize(
                "<yellow>Hunt <green>phase has begun!\n\n" +
                    "<gray>- Use your sniffer to hunt for treasures.\n" +
                    "- Your xp level shows the value of the treasure at your current location.\n" +
                    "- Look at a block to steer the sniffer. Press <yellow>Shift</yellow> to dig for an item.",
            ),
        )
    }

    override fun handlePlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        val sniffer = sniffers[event.player.uniqueId] ?: return
        val rightClicked = event.rightClicked
        if (event.hand == EquipmentSlot.HAND && sniffer.interactedWith(rightClicked)) {
            sniffer.mountPlayer()
        }
    }

    override fun handleEntityDismount(event: EntityDismountEvent) {
        val entity = event.entity
        if (event.entityType != EntityType.PLAYER || event.dismounted !is Sniffer) {
            return
        }
        val sniffer = sniffers[entity.uniqueId] ?: return
        // start the sniff
        if (state == SnifferHuntState.HUNT) {
            event.isCancelled = true
            sniffer.sniff()
            return
        }
    }

    override fun handleEntityMove(event: EntityMoveEvent) {
        val entity = event.entity
        if (entity is Sniffer) {
            val player = entity.passengers.firstOrNull() ?: return
            if (player !is Player) {
                return
            }
            val sniffer = sniffers[player.uniqueId] ?: return
            if (sniffer.sniffing) {
                event.isCancelled = true
                return
            }
            if (!event.hasChangedBlock()) {
                return
            }
            // set the player's exp progress to the value of the value of the treasure map at the sniffer's location
            player.exp = sniffer.getTreasureValue(player.location).toFloat()
        }
    }

    override fun finish() {
        for (sniffer in sniffers.values) {
            sniffer.kill()
        }
    }

    override val name: Component
        get() = Component.text("Sniffer Hunt", NamedTextColor.AQUA)
    override val description: Component
        get() =
            Component.text(
                "Use your sniffer to hunt for items.\nTrade the items and use them to craft weapons and upgrades for the final battle!",
                NamedTextColor.AQUA,
            )
}
