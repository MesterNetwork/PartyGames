package info.mester.network.partygames.admin

import com.google.gson.JsonParser
import com.mojang.authlib.properties.Property
import info.mester.network.partygames.PartyGames
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import java.io.InputStreamReader
import java.net.URI

fun updateVisibilityOfPlayer(
    playerToChange: Player,
    visible: Boolean,
) {
    // change the player's visibility for everyone who isn't an admin
    for (player in Bukkit
        .getOnlinePlayers()
        .filter { it.uniqueId != playerToChange.uniqueId }
        .filter {
            !PartyGames.plugin.isAdmin(it)
        }) {
        if (visible) {
            player.hidePlayer(PartyGames.plugin, playerToChange)
        } else {
            player.showPlayer(PartyGames.plugin, playerToChange)
        }
    }
}

enum class SkinType {
    STEVE,
    OWN,
}

fun changePlayerSkin(
    player: Player,
    skinType: SkinType,
) {
    val plugin = PartyGames.plugin
    val entityPlayer = (player as CraftPlayer).handle
    val profile = entityPlayer.gameProfile
    // first we have to decide the skin property
    var skinProperty =
        Property(
            "textures",
            plugin.config.getString("steve-skin.value")!!,
            plugin.config.getString("steve-skin.signature")!!,
        )
    if (skinType == SkinType.OWN) {
        // let's ask Mojang for the player's skin
        // if we fail (most likely on offline mode), use the default skin
        val uuid = player.uniqueId.toString().replace("-", "")
        runCatching {
            val url =
                URI("https://sessionserver.mojang.com/session/minecraft/profile/$uuid?unsigned=false").toURL()
            val reader = InputStreamReader(url.openStream())
            val properties =
                JsonParser
                    .parseReader(reader)
                    .asJsonObject
                    .get("properties")
                    .asJsonArray
                    .get(0)
                    .asJsonObject
            val value = properties.get("value").asString
            val signature = properties.get("signature").asString
            skinProperty = Property("textures", value, signature)
        }.onFailure {
            plugin.logger.warning("Failed to get skin for ${player.name} ($uuid), are we in offline mode?")
        }
    }
    profile.properties.clear()
    profile.properties.put("textures", skinProperty)
    val removeInfo = ClientboundPlayerInfoRemovePacket(listOf(entityPlayer.uuid))
    val addInfo =
        ClientboundPlayerInfoUpdatePacket(
            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
            entityPlayer,
        )
    val destroyEntity = ClientboundRemoveEntitiesPacket(entityPlayer.id)
    Bukkit
        .getOnlinePlayers()
        // admins should still see their skin
        .filter {
            it.uniqueId != player.uniqueId && !plugin.isAdmin(it)
        }.forEach { onlinePlayer ->
            val connection = (onlinePlayer as CraftPlayer).handle.connection
            connection.send(removeInfo)
            connection.send(destroyEntity)
            connection.send(addInfo)
            // force the player to re-render
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable {
                    onlinePlayer.hidePlayer(plugin, player)
                    onlinePlayer.showPlayer(plugin, player)
                },
                1,
            )
        }
}
