## Dependencies

- AdvancedSlimePaper for MC 1.21.4
- WorldEdit 7.3.10
- ViaVersion 5.2.1
- ScoreboardLibrary 2.2.2
- PlaceholderAPI 2.11.6

## Structure

The project is divided into two parts: the core API and the plugin for Mester Network.

### Core API

The core API is located in `pgame-api`. It is responsible for any game-related logic, such as loading the world, keeping
track of players, handling game events etc.

By itself it does not contain any minigames, they have to be registered by external plugins.

### Plugin

The plugin is located in `pgame-plugin`. It's the Party Games plugin for Mester Network and contains the specific
minigames for that server and other non-game related logic, including a leveling system.