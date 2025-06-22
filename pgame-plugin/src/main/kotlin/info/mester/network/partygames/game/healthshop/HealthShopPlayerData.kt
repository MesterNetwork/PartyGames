package info.mester.network.partygames.game.healthshop

data class HealthShopPlayerData(
    var maxArrows: Int = 0,
    var stealPerk: Boolean = false,
    var healPerk: Boolean = false,
    var doubleJump: Boolean = false,
    var featherFall: Boolean = false,
)
