package info.mester.bedless.tournament

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import info.mester.bedless.tournament.admin.InvseeUI
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
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

@Suppress("UnstableApiUsage", "unused")
class TournamentBootstrap : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {
        val manager: LifecycleEventManager<BootstrapContext> = context.lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()

            commands.register(
                Commands
                    .literal("tournament")
                    .requires {
                        it.sender.isOp
                    }
                    // add start subcommand
                    .then(
                        Commands
                            .literal("start")
                            .executes { ctx ->
                                ctx.source.sender.sendMessage(
                                    Component.text(
                                        "Starting tournament...",
                                        NamedTextColor.GREEN,
                                    ),
                                )
                                Tournament.game.start()
                                Command.SINGLE_SUCCESS
                            },
                    )
                    // admin <player>
                    .then(
                        Commands
                            .literal("admin")
                            .then(
                                Commands
                                    .argument("player", ArgumentTypes.player())
                                    .suggests { _, builder ->
                                        // suggest all online player
                                        Bukkit.getOnlinePlayers().map { it.name }.forEach(builder::suggest)
                                        builder.buildFuture()
                                    }.executes { ctx ->
                                        val player =
                                            ctx
                                                .getArgument("player", PlayerSelectorArgumentResolver::class.java)
                                                .resolve(ctx.source)[0]
                                        val game = Tournament.game
                                        val admin = game.isAdmin(player)
                                        game.setAdmin(player, !admin)

                                        ctx.source.sender.sendMessage(
                                            Component.text(
                                                "Admin mode for ${player.name} is now ${!admin}",
                                                NamedTextColor.GREEN,
                                            ),
                                        )
                                        Command.SINGLE_SUCCESS
                                    }
                                    // admin <player> <true/false>
                                    .then(
                                        Commands
                                            .argument("admin", StringArgumentType.word())
                                            .suggests { _, builder ->
                                                builder.suggest("true").suggest("false").buildFuture()
                                            }.executes { ctx ->
                                                val player =
                                                    ctx
                                                        .getArgument(
                                                            "player",
                                                            PlayerSelectorArgumentResolver::class.java,
                                                        ).resolve(ctx.source)[0]
                                                val adminString = StringArgumentType.getString(ctx, "admin")
                                                val admin = adminString == "true"
                                                Tournament.game.setAdmin(player, admin)

                                                ctx.source.sender.sendMessage(
                                                    Component.text(
                                                        "Admin mode for ${player.name} is now $admin",
                                                        NamedTextColor.GREEN,
                                                    ),
                                                )
                                                Command.SINGLE_SUCCESS
                                            },
                                    ),
                            ),
                    )
                    // begin
                    .then(
                        Commands
                            .literal("begin")
                            .executes { ctx ->
                                if (Tournament.game.state != GameState.LOADING) {
                                    ctx.source.sender.sendMessage(
                                        Component.text(
                                            "You can only begin the game when it is loading!",
                                            NamedTextColor.RED,
                                        ),
                                    )
                                    return@executes 0
                                }
                                Tournament.game.begin()
                                Command.SINGLE_SUCCESS
                            },
                    )
                    // next
                    .then(
                        Commands
                            .literal("next")
                            .executes { ctx ->
                                if (Tournament.game.state != GameState.POST_GAME) {
                                    ctx.source.sender.sendMessage(
                                        Component.text(
                                            "You can only start the next minigame when the current one has ended!",
                                            NamedTextColor.RED,
                                        ),
                                    )
                                    return@executes 0
                                }
                                Tournament.game.nextMinigame()
                                Command.SINGLE_SUCCESS
                            },
                    )
                    // end
                    .then(
                        Commands
                            .literal("end")
                            .executes { ctx ->
                                if (Tournament.game.state == GameState.STOPPED) {
                                    ctx.source.sender.sendMessage(
                                        Component.text(
                                            "You cannot end the tournament when it is already stopped!",
                                            NamedTextColor.RED,
                                        ),
                                    )
                                    return@executes 0
                                }
                                Tournament.game.end()
                                Command.SINGLE_SUCCESS
                            },
                    ).build(),
                "Main function for managing tournaments",
            )

            commands.register(
                Commands
                    .literal("invsee")
                    .requires {
                        it.sender.isOp
                    }.then(
                        Commands.argument("player", ArgumentTypes.player()).executes { ctx ->
                            val player =
                                ctx
                                    .getArgument("player", PlayerSelectorArgumentResolver::class.java)
                                    .resolve(ctx.source)[0]
                            val ui = InvseeUI(player)
                            val sender = ctx.source.sender as Player
                            sender.openInventory(ui.getInventory())
                            Command.SINGLE_SUCCESS
                        },
                    ).build(),
                "Opens an inventory for the given player",
            )
        }
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin = Tournament.plugin
}
