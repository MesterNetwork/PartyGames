package info.mester.network.partygames.game

import info.mester.network.partygames.api.Game
import info.mester.network.partygames.api.Minigame
import info.mester.network.partygames.mm
import info.mester.network.partygames.pow
import info.mester.network.partygames.util.WeightedItem
import info.mester.network.partygames.util.selectWeightedRandom
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemEnchantments
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.entity.Spider
import org.bukkit.entity.Zombie
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import kotlin.math.exp
import kotlin.math.floor
import kotlin.random.Random

private fun exponentialRandom(
    range: IntRange,
    rate: Double,
): Int {
    // Calculate weights using an exponential function
    val weights =
        range.map { number ->
            exp(-rate * (number - range.first))
        }
    // Calculate the cumulative sum of weights
    val cumulativeWeights = weights.runningFold(0.0) { acc, weight -> acc + weight }
    // Generate a random number in [0, totalWeight)
    val totalWeight = cumulativeWeights.last()
    val randomValue = Random.nextDouble(totalWeight)
    // Find the corresponding number in the range
    return range.first + cumulativeWeights.indexOfFirst { randomValue < it } - 1
}

data class DamageDealerItem(
    val experience: Int,
    val weapon: ItemStack,
    val enchantments: List<ItemStack>,
    val lapis: Int,
    val books: Int,
) {
    companion object {
        @Suppress("UnstableApiUsage")
        fun generate(level: Int): DamageDealerItem {
            val experience = Random.nextDouble(2 * level.pow(2), 4.5 * level.pow(2) + 5).toInt()
            val lapis = Random.nextInt(0, floor(level * 0.25 + 2).toInt())
            val books = Random.nextInt(-6, 2)
            val giveWeapon = level % 5 == 0 || Random.nextInt(0, 3) == 0
            val weapon =
                if (giveWeapon) {
                    val material =
                        listOf(
                            // Swords
                            WeightedItem(Material.WOODEN_SWORD, 12),
                            WeightedItem(Material.STONE_SWORD, 8),
                            WeightedItem(Material.IRON_SWORD, 5),
                            WeightedItem(Material.DIAMOND_SWORD, 3),
                            WeightedItem(Material.NETHERITE_SWORD, 2),
                            // Axes
                            WeightedItem(Material.WOODEN_AXE, 10),
                            WeightedItem(Material.STONE_AXE, 6),
                            WeightedItem(Material.IRON_AXE, 4),
                            WeightedItem(Material.DIAMOND_AXE, 3),
                            WeightedItem(Material.NETHERITE_AXE, 2),
                            // single mace
                            WeightedItem(Material.MACE, 1),
                        ).selectWeightedRandom()
                    ItemStack.of(material).apply {
                        editMeta { meta ->
                            meta as Damageable
                            val damage = if (Random.nextInt(0, 6) == 0) 2 else 1
                            meta.setMaxDamage(damage)
                            meta.damage = 0
                        }
                    }
                } else {
                    ItemStack.of(Material.AIR)
                }
            // by adding three random numbers together, that may be negative, we may get a positive number, but rarely
            val enchantmentCount =
                floor(
                    Random.nextInt(-3, 2) +
                        Random.nextDouble(
                            (-3 + level * 0.25).coerceAtMost(1.49),
                            1.5,
                        ) +
                        Random
                            .nextDouble(
                                (-1 + level * 0.01).coerceAtMost(0.0),
                                (2 - level * 0.01).coerceAtLeast(0.5),
                            ),
                ).toInt()
            val enchantments = mutableListOf<ItemStack>()
            for (i in 0 until enchantmentCount) {
                val enchantment =
                    listOf(
                        WeightedItem(Enchantment.UNBREAKING, 12),
                        WeightedItem(Enchantment.SMITE, 7),
                        WeightedItem(Enchantment.BANE_OF_ARTHROPODS, 7),
                        WeightedItem(Enchantment.SHARPNESS, 4),
                        WeightedItem(Enchantment.DENSITY, 1),
                    ).selectWeightedRandom()
                val enchantmentLevel =
                    exponentialRandom(1..enchantment.maxLevel, 0.6).coerceAtLeast(0)
                val enchantmentItem = ItemStack.of(Material.ENCHANTED_BOOK, 1)
                val enchantmentData =
                    ItemEnchantments
                        .itemEnchantments()
                        .add(enchantment, enchantmentLevel)
                        .showInTooltip(true)
                        .build()
                enchantmentItem.setData(DataComponentTypes.STORED_ENCHANTMENTS, enchantmentData)
                enchantments.add(enchantmentItem)
            }
            return DamageDealerItem(
                experience,
                weapon,
                enchantments,
                lapis,
                books,
            )
        }
    }
}

@Suppress("Unused")
class DamageDealerMinigame(
    game: Game,
) : Minigame(game, "damagedealer") {
    private val levelItems = mutableListOf<DamageDealerItem>()

    init {
        for (i in 0..19) {
            levelItems.add(DamageDealerItem.generate(i))
        }
    }

    private fun giveItems(
        player: Player,
        level: Int,
    ) {
        val items = levelItems[level]
        for (item in items.enchantments) {
            player.inventory.addItem(item)
        }
        player.inventory.addItem(items.weapon)
        player.giveExp(items.experience)
        if (items.lapis > 0) {
            player.inventory.addItem(ItemStack.of(Material.LAPIS_LAZULI, items.lapis))
        }
        if (items.books > 0) {
            player.inventory.addItem(ItemStack.of(Material.BOOK, items.books))
        }
    }

    private fun <T : Monster> spawnTarget(
        pos: Location,
        type: Class<T>,
    ): T =
        pos.world.spawn(pos, type) { entity ->
            entity.setAI(false)
            entity.customName(mm.deserialize("<red><bold>Deal as much damage as possible!"))
            entity.isCustomNameVisible = true
        }

    override fun onLoad() {
        game.world.setGameRule(GameRule.NATURAL_REGENERATION, false)
    }

    override fun start() {
        super.start()
        val posSpider = startPos.clone().add(2.0, 0.0, 0.0)
        val posZombie = startPos.clone().add(-2.0, 0.0, 0.0)
        spawnTarget(posSpider, Spider::class.java)
        val zombie = spawnTarget(posZombie, Zombie::class.java)
        zombie.equipment.helmet = ItemStack.of(Material.IRON_HELMET)
        game.onlinePlayers.forEach { player ->
            giveItems(player, 0)
        }
        val introductionMessage =
            "<yellow>Damage Dealer\n" +
                "<gray>- Try to deal as much damage to either of your targets as possible.\n" +
                "- Each hit gives you new items to increase your damage.\n" +
                "- You only have 20 tries!"
        audience.sendMessage(mm.deserialize(introductionMessage))

        startCountdown(3 * 60 * 1000) {
            end()
        }
    }

    override fun handleEntityCombust(event: EntityCombustEvent) {
        // disable fire aspect
        event.isCancelled = true
    }

    override fun handleEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (event.entity is Player) {
            event.isCancelled = true
        }
        val damage = event.finalDamage
        event.damage = 0.0
        val player = event.damager
        if (player !is Player) {
            return
        }
        val level = 20 - player.health.toInt()
        val multiplier = 1.5 - level * 0.02
        val finalScore = floor(damage * multiplier).toInt()
        game.addScore(player, finalScore, "Dealt ${String.format("%.1f", damage)} damage")
        // increment the level by dealing a damage
        if (player.health <= 1) {
            // the player is done with their turn
            player.gameMode = GameMode.SPECTATOR
            if (game.onlinePlayers.none { it.gameMode == GameMode.SURVIVAL }) {
                end()
                return
            }
            audience.sendMessage(mm.deserialize("<yellow>${player.name} has finished!"))
        } else {
            player.damage(1.0)
            giveItems(player, 20 - player.health.toInt())
        }
    }

    override fun handlePrePlayerAttack(event: PrePlayerAttackEntityEvent) {
        event.isCancelled = false
    }

    override val name: Component
        get() = Component.text("Damage Dealer", NamedTextColor.AQUA)
    override val description: Component
        get() =
            Component.text(
                "Deal as much damage as possible!\nAfter each hit, you lose 1 health and get new items!",
                NamedTextColor.AQUA,
            )
}
