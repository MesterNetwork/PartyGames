package info.mester.bedless.tournament

import info.mester.bedless.tournament.game.Game
import okhttp3.OkHttpClient
import org.bukkit.plugin.java.JavaPlugin

class Tournament : JavaPlugin() {
    companion object {
        private val _plugin = Tournament()
        private val _client = OkHttpClient()
        private var _game: Game = Game(_plugin)

        val plugin: Tournament
            get() = _plugin
        val client: OkHttpClient
            get() = _client
        val game: Game
            get() = _game
    }

    override fun onEnable() {
        saveResource("config.yml", true)
        saveResource("health-shop.yml", true)

        // Plugin startup logic
        server.pluginManager.registerEvents(GameListener(this), this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
