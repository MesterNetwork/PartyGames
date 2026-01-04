package info.mester.network.testminigame

import info.mester.network.partygames.api.MinigameWorld
import info.mester.network.partygames.api.PartyGamesCore
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
        core.gameRegistry.registerMinigame(
            this,
            SimpleMinigame::class.qualifiedName!!,
            "simple",
            listOf(
                MinigameWorld("simple", Vector(0.5, 60.0, 0.5)),
            ),
            "Simple Minigame",
        )
        core.gameRegistry.registerMinigame(
            this,
            JavaMinigame::class.qualifiedName!!,
            "java",
            listOf(
                MinigameWorld("java", Vector(0.5, 60.0, 0.5)),
            ),
            "Java Minigame",
        )
    }
}
