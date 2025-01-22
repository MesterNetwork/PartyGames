package info.mester.network.partygames

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import info.mester.network.partygames.api.PartyGamesCore
import info.mester.network.partygames.game.GameType
import info.mester.network.partygames.game.HealthShopMinigame
import info.mester.network.partygames.game.MineguessrMinigame
import info.mester.network.partygames.game.SnifferHuntMinigame
import info.mester.network.partygames.game.SpeedBuildersMinigame
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

@Suppress("UnstableApiUsage", "unused")
class Bootstrapper : PluginBootstrap {
    val gameLeaveAttempts = mutableMapOf<UUID, Long>()

    override fun bootstrap(context: BootstrapContext) {
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
                                HealthShopMinigame.reload()
                                SpeedBuildersMinigame.reload()
                                SnifferHuntMinigame.reload()
                                MineguessrMinigame.reload()
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
                                val typeRaw = StringArgumentType.getString(ctx, "game").uppercase()
                                if (!GameType.entries.any { it.name.uppercase() == typeRaw }) {
                                    return@executes 1
                                }
                                val type = GameType.valueOf(typeRaw)
                                val currentQueue = PartyGames.plugin.gameManager.getQueueOf(sender)
                                if (currentQueue != null && currentQueue.type == type) {
                                    sender.sendMessage(
                                        Component.text(
                                            "You are already in a queue for this game!",
                                            NamedTextColor.RED,
                                        ),
                                    )
                                    return@executes 1
                                }
                                PartyGames.plugin.gameManager.joinQueue(type, listOf(sender))
                                Command.SINGLE_SUCCESS
                            },
                    ).build(),
            )
        }
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin = PartyGames()
}
