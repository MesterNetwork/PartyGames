package info.mester.bedless.tournament

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import info.mester.bedless.tournament.game.GameState
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.plugin.java.JavaPlugin

@Suppress("UnstableApiUsage", "unused")
class TournamentBootstrap : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {
        val manager: LifecycleEventManager<BootstrapContext> = context.lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()

            commands.register(
                Commands.literal("tournament")
                    .requires {
                        it.sender.isOp
                    }
                    // add start subcommand
                    .then(Commands.literal("start")
                        .executes { ctx ->
                            ctx.source.sender.sendMessage(
                                Component.text(
                                    "Starting tournament...",
                                    NamedTextColor.GREEN
                                )
                            )
                            Tournament.game.start()
                            Command.SINGLE_SUCCESS
                        }
                        .build()
                    )
                    // admin <player> [true/false, optional]
                    .then(
                        Commands.literal("admin")
                            .then(
                                Commands.argument("player", ArgumentTypes.player())
                                    .executes { ctx ->
                                        val player =
                                            ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                                                .resolve(ctx.source)[0]

                                        // get admin status
                                        val game = Tournament.game
                                        val admin = game.isAdmin(player.uniqueId)

                                        // toggle admin status
                                        game.setAdmin(player.uniqueId, !admin)

                                        ctx.source.sender.sendMessage(
                                            Component.text(
                                                "Admin mode for ${player.name} is now ${!admin}",
                                                NamedTextColor.GREEN
                                            )
                                        )
                                        Command.SINGLE_SUCCESS
                                    }
                                    .then(Commands.argument(
                                        "admin", StringArgumentType.word()
                                    ).executes { ctx ->
                                        val player =
                                            ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                                                .resolve(ctx.source)[0]
                                        val admin = StringArgumentType.getString(ctx, "admin")
                                        ctx.source.sender.sendMessage(
                                            Component.text(
                                                "Admin mode for ${player.name} is now $admin",
                                                NamedTextColor.GREEN
                                            )
                                        )
                                        Command.SINGLE_SUCCESS
                                    })
                            )
                    )
                    // begin
                    .then(Commands.literal("begin")
                        .executes { ctx ->
                            if (Tournament.game.state != GameState.LOADING) {
                                ctx.source.sender.sendMessage(
                                    Component.text(
                                        "You can only begin the game when it is loading!",
                                        NamedTextColor.RED
                                    )
                                )
                                return@executes 0
                            }
                            Tournament.game.begin()
                            Command.SINGLE_SUCCESS
                        })
                    // next
                    .then(Commands.literal("next")
                        .executes { ctx ->
                            if (Tournament.game.state != GameState.POST_GAME) {
                                ctx.source.sender.sendMessage(
                                    Component.text(
                                        "You can only start the next minigame when the current one has ended!",
                                        NamedTextColor.RED
                                    )
                                )
                                return@executes 0
                            }
                            Tournament.game.nextMinigame()
                            Command.SINGLE_SUCCESS
                        })
                    .build(),
                "Main function for managing tournaments",
                listOf("tm")
            )
        }
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin {
        return Tournament.plugin
    }
}