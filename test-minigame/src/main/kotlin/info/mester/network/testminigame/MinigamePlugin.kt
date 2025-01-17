package info.mester.network.testminigame

import com.mojang.brigadier.Command
import info.mester.network.partygames.api.MinigameWorld
import info.mester.network.partygames.api.PartyGamesCore
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector

class MinigamePlugin : JavaPlugin() {
    override fun onEnable() {
        val core = PartyGamesCore.getInstance()
        core.gameRegistry.registerMinigame(
            this,
            PlaceBlockMinigame::class.qualifiedName!!,
            "place_block",
            listOf(
                MinigameWorld("placeblock", Vector(0.5, 60.0, 0.5)),
            ),
            "Place Block",
        )
        // register /start command
        @Suppress("UnstableApiUsage")
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()
            commands.register(
                Commands
                    .literal("start")
                    .executes { ctx ->
                        val sender = ctx.source.sender
                        if (sender !is Player) {
                            return@executes 1
                        }
                        val players = Bukkit.getOnlinePlayers().toList()
                        core.gameRegistry.startGame(players, "place_block")
                        Command.SINGLE_SUCCESS
                    }.build(),
            )
        }
        println("PlaceBlockMinigame enabled!")
    }
}
