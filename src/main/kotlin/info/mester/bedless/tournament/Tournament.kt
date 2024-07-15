package info.mester.bedless.tournament

import info.mester.bedless.tournament.game.Game
import okhttp3.OkHttpClient
import org.bukkit.plugin.java.JavaPlugin

class Tournament : JavaPlugin() {
    companion object {
        private val INSTANCE = Tournament()
        private val client = OkHttpClient()
        private var game: Game? = null

        /**
         * Get the plugin instance
         *
         * @return the plugin instance
         */
        fun instance(): Tournament {
            return INSTANCE
        }

        /**
         * Get the HTTP client instance
         *
         * @return the HTTP client instance
         */
        fun client(): OkHttpClient {
            return client
        }

        /**
         * Get the game instance
         *
         * @return the game instance
         */
        fun game(): Game {
            return game!!
        }
    }
    override fun onEnable() {
        // we have to initialize the game when the plugin is enabled, otherwise the scheduler will fail
        game = Game(this)

        // Plugin startup logic
        logger.info("Hello world!")
        server.pluginManager.registerEvents(GameListener(this), this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
