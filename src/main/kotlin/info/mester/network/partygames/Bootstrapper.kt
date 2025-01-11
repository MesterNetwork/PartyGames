package info.mester.network.partygames

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import info.mester.network.partygames.admin.GamesUI
import info.mester.network.partygames.admin.InvseeUI
import info.mester.network.partygames.game.GameType
import info.mester.network.partygames.game.HealthShopMinigame
import info.mester.network.partygames.game.SnifferHuntMinigame
import info.mester.network.partygames.game.SpeedBuildersMinigame
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
                        val plugin = PartyGames.plugin
                        val admin = plugin.isAdmin(sender)
                        plugin.setAdmin(sender, !admin)

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
                        // reload
                        Commands
                            .literal("reload")
                            .executes { ctx ->
                                val sender = ctx.source.sender
                                HealthShopMinigame.reload()
                                SpeedBuildersMinigame.reload()
                                SnifferHuntMinigame.reload()
                                PartyGames.plugin.reload()
                                sender.sendMessage(Component.text("Reloaded the configuration!", NamedTextColor.GREEN))
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
                                val plugin = PartyGames.plugin
                                val game = plugin.gameManager.getGameByWorld(sender.world)
                                if (game == null) {
                                    sender.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED))
                                    return@executes 1
                                }
                                game.terminate()
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
            // /join <game>
            commands.register(
                Commands
                    .literal("join")
                    .then(
                        Commands
                            .argument("game", StringArgumentType.word())
                            .suggests { ctx, builder ->
                                kotlin
                                    .runCatching {
                                        val type = StringArgumentType.getString(ctx, "game").uppercase()
                                        for (game in GameType.entries.filter { it.name.uppercase().startsWith(type) }) {
                                            builder.suggest(game.name.lowercase())
                                        }
                                    }.onFailure {
                                        for (game in GameType.entries) {
                                            builder.suggest(game.name.lowercase())
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
                        val gameManager = PartyGames.plugin.gameManager
                        if (gameManager.getQueueOf(sender) != null) {
                            gameManager.removePlayerFromQueue(sender)
                            return@executes Command.SINGLE_SUCCESS
                        }
                        if (gameManager.getGameOf(sender) != null) {
                            val lastLeaveAttempt = gameLeaveAttempts[sender.uniqueId]
                            if (lastLeaveAttempt != null && System.currentTimeMillis() - lastLeaveAttempt < 5000) {
                                // leave the game
                                gameManager.getGameOf(sender)!!.removePlayer(sender)
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

    override fun createPlugin(context: PluginProviderContext): JavaPlugin = PartyGames.plugin
}
