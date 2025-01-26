package info.mester.network.partygames.game

import info.mester.network.partygames.PartyGames
import info.mester.network.partygames.api.Game
import info.mester.network.partygames.api.Minigame
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.ChunkSnapshot
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.event.block.BlockPhysicsEvent
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

private fun levenshteinDistance(
    a: String,
    b: String,
): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    // Initialize the base cases
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    // Fill the DP table
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] =
                minOf(
                    dp[i - 1][j] + 1, // Deletion
                    dp[i][j - 1] + 1, // Insertion
                    dp[i - 1][j - 1] + cost, // Substitution
                )
        }
    }

    return dp[a.length][b.length]
}

enum class MineguessrState {
    LOADING,
    GUESSING,
}

@Suppress("Unused")
class MineguessrMinigame(
    game: Game,
) : Minigame(game, "mineguessr") {
    companion object {
        private var sourceWorld: World = Bukkit.getWorld("world")!!
        private var maxSize = 1000
        private val disallowedBiomes = listOf(Biome.DRIPSTONE_CAVES)

        init {
            reload()
        }

        fun reload() {
            val plugin = PartyGames.plugin
            plugin.logger.info("Loading mineguessr config...")
            val worldName = plugin.config.getString("mineguessr.world")!!
            val maxSize = plugin.config.getInt("mineguessr.max-size")
            sourceWorld = Bukkit.getWorld(worldName)!!
            this.maxSize = maxSize
        }
    }

    private var remainingRounds = 10
    private var state = MineguessrState.LOADING
    private val biomeList = mutableListOf<Biome>()
    private val guessed = mutableListOf<UUID>()
    private var selectedBiomeIndex = 0

    private fun getRandomChunkAsync(): CompletableFuture<ChunkSnapshot> {
        var worldSize = sourceWorld.worldBorder.size
        worldSize -= sourceWorld.worldBorder.size % 16
        worldSize /= 32 // 16 blocks per chunk, and an extra 2 division to make it a radius
        val worldSizeInt = worldSize.toInt().coerceAtMost(maxSize)
        val chunkX = Random.nextInt(-worldSizeInt, worldSizeInt)
        val chunkZ = Random.nextInt(-worldSizeInt, worldSizeInt)
        val future = sourceWorld.getChunkAtAsync(chunkX, chunkZ, true)
        return future.thenApply { chunk ->
            chunk.getChunkSnapshot(true, true, false, false)
        }
    }

    private fun copyChunk(chunk: ChunkSnapshot) {
        // we only want to copy the top 32 blocks into startPos' chunk
        // first, find the highest block
        var highestBlockY = -999
        for (x in 0..15) {
            for (z in 0..15) {
                val highestBlock = chunk.getHighestBlockYAt(x, z)
                if (highestBlock > highestBlockY) {
                    highestBlockY = highestBlock
                }
            }
        }
        // now we can begin the copy
        for (x in 0..15) {
            for (z in 0..15) {
                for (y in 0..32) {
                    val chunkY = (highestBlockY - 32 + y).coerceAtLeast(-64)
                    val chunkBlockData = chunk.getBlockData(x, chunkY, z)
                    val gameBlock = game.world.getBlockAt(x, y, z)
                    gameBlock.type = chunkBlockData.material
                    gameBlock.blockData = chunkBlockData.clone()
                    val chunkBiome = chunk.getBiome(x, chunkY, z)
                    game.world.setBiome(x, y, z, chunkBiome)
                    if (!biomeList.contains(chunkBiome) && !disallowedBiomes.contains(chunkBiome)) {
                        biomeList.add(chunkBiome)
                    }
                }
            }
        }
        // send a chunk update to everyone
        game.world.refreshChunk(0, 0)
    }

    private fun loadChunk(): CompletableFuture<Void> {
        audience.sendActionBar(Component.text("Loading chunk...", NamedTextColor.YELLOW))
        biomeList.clear()
        val future = getRandomChunkAsync()
        return future.thenCompose { chunk ->
            copyChunk(chunk)
            audience.sendMessage(
                MiniMessage.miniMessage().deserialize(
                    "<dark_gray>${"-".repeat(30)}\n" +
                        "<yellow>Loading finished, time to guess!\n" +
                        "This chunk contains <aqua>${biomeList.size}</aqua> biomes.",
                ),
            )
            selectedBiomeIndex = Random.nextInt(0, biomeList.size)
            // turn biome text into underscores, "EXAMPLE_BIOME" -> "_______ _____"
            val biomeText =
                biomeList[selectedBiomeIndex]
                    .name()
                    .map {
                        if (it == '_') {
                            ' '
                        } else {
                            "_"
                        }
                    }.joinToString("")
            audience.sendMessage(
                MiniMessage.miniMessage().deserialize("<gray>Hint for one biome: <yellow>$biomeText"),
            )

            CompletableFuture.completedFuture(null)
        }
    }

    private fun startRound() {
        state = MineguessrState.LOADING
        loadChunk().thenRun {
            guessed.clear()
            state = MineguessrState.GUESSING
            startCountdown(15 * 20) {
                finishRound()
            }
        }
    }

    private fun formatBiomeName(biome: Biome): String =
        biome
            .name()
            .split('_') // Split by underscores
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() } // Capitalize first letter
            }

    private fun finishRound() {
        stopCountdown()
        // turn biomes into this: "Biome Name, "Biome Name2", "Biome Name3"
        val biomeText = biomeList.joinToString("<gray>,</gray> ") { formatBiomeName(it) }
        audience.sendMessage(
            MiniMessage.miniMessage().deserialize("<yellow>The chunk had these biomes: <aqua>$biomeText"),
        )
        guessed.clear()
        // delay the new round by 1 tick, to give time for the end message to appear
        Bukkit.getScheduler().runTaskLater(
            PartyGames.plugin,
            Runnable {
                remainingRounds--
                if (remainingRounds > 0) {
                    startRound()
                } else {
                    end()
                }
            },
            1,
        )
    }

    override fun finish() {
        guessed.clear()
    }

    override fun onLoad() {
        game.world.setGameRule(GameRule.REDUCED_DEBUG_INFO, true)
    }

    override fun start() {
        super.start()

        for (player in game.onlinePlayers) {
            player.gameMode = GameMode.SPECTATOR
        }

        startRound()
    }

    override fun handleBlockPhysics(event: BlockPhysicsEvent) {
        event.isCancelled = true
    }

    override fun handlePlayerChat(event: AsyncChatEvent) {
        if (guessed.contains(event.player.uniqueId)) {
            event.player.sendMessage(Component.text("You already guessed!", NamedTextColor.RED))
            event.isCancelled = true
            return
        }
        val plainText = PlainTextComponentSerializer.plainText().serialize(event.message())
        val biomes = biomeList.map { it.name().replace("_", " ").uppercase() }
        if (plainText.uppercase() in biomes) {
            audience.sendMessage(
                MiniMessage
                    .miniMessage()
                    .deserialize(("<yellow>${event.player.name} <green>guessed the biome as <gold>#${guessed.size + 1}</gold>!")),
            )
            val score =
                when (guessed.size) {
                    0 -> 15
                    1 -> 10
                    2 -> 5
                    else -> 3
                }
            game.addScore(event.player, score, "Correct guess")
            guessed.add(event.player.uniqueId)
            event.isCancelled = true
            // check if everyone has guessed already
            if (guessed.size >= onlinePlayers.size) {
                finishRound()
            }
            return
        }
        val minDistance = biomes.minOfOrNull { levenshteinDistance(it, plainText.uppercase()) } ?: Int.MAX_VALUE
        if (minDistance <= 3) {
            event.player.sendMessage(Component.text("You are close!", NamedTextColor.YELLOW))
            event.isCancelled = true
        }
    }

    override val name = Component.text("Mineguessr", NamedTextColor.AQUA)
    override val description =
        Component.text("Guess the chunk based on a random segment of the world!", NamedTextColor.AQUA)
}
