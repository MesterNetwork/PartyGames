package info.mester.network.partygames

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import info.mester.network.partygames.api.PartyGamesCore
import info.mester.network.partygames.game.GravjumpMinigame
import info.mester.network.partygames.game.HealthShopMinigame
import info.mester.network.partygames.game.healthshop.HealthShopUI
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

@Suppress("UnstableApiUsage", "unused")
class Bootstrapper : PluginBootstrap {
    companion object {
        val TEST_HEALTHSHOP_UI_KEY = NamespacedKey("partygames", "test_healthshop_ui")
    }

    val gameLeaveAttempts = mutableMapOf<UUID, Long>()

    override fun bootstrap(context: BootstrapContext) {
        System.setProperty("net.megavex.scoreboardlibrary.forceModern", "true") // force modern implementation for ScoreboardLibrary

        val manager: LifecycleEventManager<BootstrapContext> = context.lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()
            // /partygames
            commands.register(
                Commands
                    .literal("partygames")
                    .requires { it.sender.hasPermission("partygames.admin") }
                    .then(
                        // reload
                        Commands
                            .literal("reload")
                            .executes { ctx ->
                                val sender = ctx.source.sender
                                PartyGames.plugin.reload()
                                sender.sendMessage(Component.text("Reloaded the configuration!", NamedTextColor.GREEN))
                                Command.SINGLE_SUCCESS
                            },
                    ).build(),
            )
            // /join <game>
            commands.register(
                Commands
                    .literal("join")
                    .then(
                        Commands
                            .argument("game", StringArgumentType.word())
                            .suggests { ctx, builder ->
                                val plugin = PartyGames.plugin
                                val bundles =
                                    PartyGamesCore
                                        .getInstance()
                                        .gameRegistry
                                        .getBundles()
                                        .filter { it.plugin.name == plugin.name }
                                        .map { it.name.lowercase() }
                                kotlin
                                    .runCatching {
                                        val type = StringArgumentType.getString(ctx.child, "game").lowercase()
                                        for (game in bundles.filter { it.startsWith(type) }) {
                                            builder.suggest(game.lowercase())
                                        }
                                    }.onFailure {
                                        for (game in bundles) {
                                            builder.suggest(game.lowercase())
                                        }
                                    }
                                builder.buildFuture()
                            }.executes { ctx ->
                                val sender = ctx.source.sender
                                if (sender !is Player) {
                                    sender.sendMessage(
                                        MiniMessage
                                            .miniMessage()
                                            .deserialize("<red>You have to be a player to run this command!"),
                                    )
                                    return@executes 1
                                }
                                val bundleRaw = StringArgumentType.getString(ctx, "game").uppercase()
                                val bundle =
                                    PartyGamesCore
                                        .getInstance()
                                        .gameRegistry
                                        .getBundle(bundleRaw)
                                        ?: run {
                                            sender.sendMessage(
                                                MiniMessage
                                                    .miniMessage()
                                                    .deserialize("<red>Game $bundleRaw not found!"),
                                            )
                                            return@executes 1
                                        }

                                val currentQueue = PartyGames.plugin.queueManager.getQueueOf(sender)
                                if (currentQueue != null && currentQueue.bundle == bundle) {
                                    sender.sendMessage(
                                        Component.text(
                                            "You are already in a queue for this game!",
                                            NamedTextColor.RED,
                                        ),
                                    )
                                    return@executes 1
                                }
                                PartyGames.plugin.queueManager.joinQueue(bundle, sender)
                                Command.SINGLE_SUCCESS
                            },
                    ).build(),
            )

            // gravjump
            commands.register(
                Commands
                    .literal("gravjump")
                    .requires { it.sender.hasPermission("partygames.gravjump") }
                    .then(
                        Commands.literal("flip").executes { ctx ->
                            // get the game
                            val sender = ctx.source.sender as? Player ?: return@executes 1
                            val game =
                                PartyGamesCore.getInstance().gameRegistry.getGameByWorld(sender.world)
                                    ?: return@executes 1
                            val minigame = game.runningMinigame as? GravjumpMinigame ?: return@executes 1

                            minigame.flip()
                            Command.SINGLE_SUCCESS
                        },
                    ).build(),
            )

            // healthshop
            commands.register(
                Commands
                    .literal("healthshop")
                    .requires { it.sender.hasPermission("partygames.healthshop") }
                    .then(
                        Commands.literal("testui").executes { ctx ->
                            val player = ctx.source.sender as? Player ?: return@executes 1
                            val healthShop = HealthShopUI(player.uniqueId, HealthShopMinigame.startingHealth)
                            player.openInventory(healthShop.inventory)
                            player.persistentDataContainer.set(TEST_HEALTHSHOP_UI_KEY, PersistentDataType.BOOLEAN, true)

                            player.getAttribute(Attribute.MAX_HEALTH)?.apply {
                                baseValue = HealthShopMinigame.startingHealth
                                player.health = baseValue
                                player.sendHealthUpdate()
                            }

                            Command.SINGLE_SUCCESS
                        },
                    ).build(),
            )
        }
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin = PartyGames()
}
