package info.mester.network.partygames.api

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import info.mester.network.partygames.api.admin.GamesUI
import info.mester.network.partygames.api.admin.InvseeUI
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
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
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
            // /admin
            commands.register(
                // toggle admin mode
                Commands
                    .literal("admin")
                    .requires {
                        it.sender.hasPermission("partygames.admin")
                    }.executes { ctx ->
                        val sender = ctx.source.sender
                        if (sender !is Player) {
                            return@executes 1
                        }
                        val core = PartyGamesCore.getInstance()
                        val admin = core.isAdmin(sender)
                        core.setAdmin(sender, !admin)

                        sender.sendMessage(
                            MiniMessage
                                .miniMessage()
                                .deserialize(
                                    "<gold>Admin mode has been ${if (admin) "<red>disabled</red>" else "<green>enabled</green>"}!",
                                ),
                        )
                        Command.SINGLE_SUCCESS
                    }.then(
                        // games
                        Commands
                            .literal("games")
                            .executes { ctx ->
                                val sender = ctx.source.sender
                                if (sender !is Player) {
                                    return@executes 1
                                }
                                val ui = GamesUI()
                                sender.openInventory(ui.getInventory())
                                Command.SINGLE_SUCCESS
                            },
                    ).then(
                        // end
                        Commands
                            .literal("end")
                            .executes { ctx ->
                                val sender = ctx.source.sender
                                if (sender !is Player) {
                                    return@executes 1
                                }
                                val core = PartyGamesCore.getInstance()
                                val game = core.gameRegistry.getGameByWorld(sender.world)
                                if (game == null) {
                                    sender.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED))
                                    return@executes 1
                                }
                                game.terminate()
                                Command.SINGLE_SUCCESS
                            },
                    ).then(
                        // start
                        Commands
                            .literal("start")
                            .then(
                                Commands
                                    .argument("bundle", StringArgumentType.word())
                                    .suggests { ctx, builder ->
                                        val core = PartyGamesCore.getInstance()
                                        val bundles = core.gameRegistry.getBundles()
                                        runCatching {
                                            val bundleName = StringArgumentType.getString(ctx.child, "bundle")
                                            bundles
                                                .filter { it.name.uppercase().startsWith(bundleName.uppercase()) }
                                                .map { it.name }
                                                .forEach(builder::suggest)
                                        }.onFailure {
                                            bundles.map { it.name.uppercase() }.forEach(builder::suggest)
                                        }
                                        builder.buildFuture()
                                    }.executes { ctx ->
                                        val core = PartyGamesCore.getInstance()
                                        val bundleName = StringArgumentType.getString(ctx, "bundle")
                                        val players = Bukkit.getOnlinePlayers().toList()
                                        core.gameRegistry.startGame(players, bundleName)
                                        Command.SINGLE_SUCCESS
                                    },
                            ),
                    ).then(
                        // skip
                        Commands.literal("skip").executes { ctx ->
                            val sender = ctx.source.sender
                            if (sender !is Player) {
                                return@executes 1
                            }
                            val core = PartyGamesCore.getInstance()
                            val game = core.gameRegistry.getGameByWorld(sender.world)
                            if (game == null) {
                                sender.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED))
                                return@executes 1
                            }
                            game.runningMinigame?.end()
                            Command.SINGLE_SUCCESS
                        },
                    ).build(),
                "Main function for managing tournaments",
            )
            // /invsee <player>
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
            // leave
            commands.register(
                Commands
                    .literal("leave")
                    .executes { ctx ->
                        val sender = ctx.source.sender
                        if (sender !is Player) {
                            sender.sendMessage(
                                MiniMessage
                                    .miniMessage()
                                    .deserialize("<red>You have to be a player to run this command!"),
                            )
                            return@executes 1
                        }
                        val gameRegistry = PartyGamesCore.getInstance().gameRegistry
                        if (gameRegistry.getGameOf(sender) != null) {
                            val lastLeaveAttempt = gameLeaveAttempts[sender.uniqueId]
                            if (lastLeaveAttempt != null && System.currentTimeMillis() - lastLeaveAttempt < 5000) {
                                // leave the game
                                gameRegistry.getGameOf(sender)!!.removePlayer(sender)
                                return@executes Command.SINGLE_SUCCESS
                            }
                            gameLeaveAttempts[sender.uniqueId] = System.currentTimeMillis()
                            sender.sendMessage(
                                MiniMessage
                                    .miniMessage()
                                    .deserialize(
                                        "<red>You are attempting to leave the game! Run /leave again within 5 seconds to confirm.",
                                    ),
                            )
                            return@executes Command.SINGLE_SUCCESS
                        }
                        sender.sendMessage(
                            MiniMessage.miniMessage().deserialize("<red>You are not in a game or a queue!"),
                        )
                        Command.SINGLE_SUCCESS
                    }.build(),
            )
        }
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin = PartyGamesCore()
}
