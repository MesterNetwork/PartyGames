package info.mester.bedless.tournament

import info.mester.bedless.tournament.game.Game
import okhttp3.OkHttpClient
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin

class Tournament : JavaPlugin() {
    companion object {
        private val _plugin = Tournament()
        private val _client = OkHttpClient()
        private var _game: Game? = null

        val plugin: Tournament
            get() = _plugin
        val client: OkHttpClient
            get() = _client
        val game: Game
            get() = _game!!
    }
    override fun onEnable() {
        saveResource("config.yml", true)

        // we have to initialize the game when the plugin is enabled, otherwise the scheduler will fail
        _game = Game(this)

        // Plugin startup logic
        logger.info("Hello world!")
        server.pluginManager.registerEvents(GameListener(this), this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
