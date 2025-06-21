package info.mester.network.partygames.game.healthshop

import info.mester.network.partygames.PartyGames
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

data class HealthShopPlayerData(
    var maxArrows: Int = 0,
    var stealPerk: Boolean = false,
    var healPerk: Boolean = false,
    var doubleJump: Boolean = false,
)

class HealthShopPlayerDataType : PersistentDataType<PersistentDataContainer, HealthShopPlayerData> {
    companion object {
        private val plugin = PartyGames.plugin

        private val MAX_ARROWS_KEY = NamespacedKey(plugin, "max_arrows")
        private val STEAL_PERK_KEY = NamespacedKey(plugin, "steal_perk")
        private val HEAL_PERK_KEY = NamespacedKey(plugin, "heal_perk")
        private val DOUBLE_JUMP_KEY = NamespacedKey(plugin, "double_jump")
    }

    override fun getPrimitiveType(): Class<PersistentDataContainer> = PersistentDataContainer::class.java

    override fun getComplexType(): Class<HealthShopPlayerData> = HealthShopPlayerData::class.java

    override fun toPrimitive(
        complex: HealthShopPlayerData,
        context: PersistentDataAdapterContext,
    ): PersistentDataContainer {
        val container = context.newPersistentDataContainer()
        container.set(MAX_ARROWS_KEY, PersistentDataType.INTEGER, complex.maxArrows)
        container.set(STEAL_PERK_KEY, PersistentDataType.BOOLEAN, complex.stealPerk)
        container.set(HEAL_PERK_KEY, PersistentDataType.BOOLEAN, complex.healPerk)
        container.set(DOUBLE_JUMP_KEY, PersistentDataType.BOOLEAN, complex.doubleJump)
        return container
    }

    override fun fromPrimitive(
        primitive: PersistentDataContainer,
        context: PersistentDataAdapterContext,
    ): HealthShopPlayerData {
        val maxArrows = primitive.get(MAX_ARROWS_KEY, PersistentDataType.INTEGER) ?: 0
        val stealPerk = primitive.get(STEAL_PERK_KEY, PersistentDataType.BOOLEAN) ?: false
        val healPerk = primitive.get(HEAL_PERK_KEY, PersistentDataType.BOOLEAN) ?: false
        val doubleJump = primitive.get(DOUBLE_JUMP_KEY, PersistentDataType.BOOLEAN) ?: false
        return HealthShopPlayerData(
            maxArrows = maxArrows,
            stealPerk = stealPerk,
            healPerk = healPerk,
            doubleJump = doubleJump,
        )
    }
}
