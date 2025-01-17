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

## API Usage

### Including the API as dependency

To include the API as a dependency, add the following to your `build.gradle.kts`:

```kotlin
dependencies {
    compileOnly(project(":pgame-api"))
}
```

Or if you're working in a different project, compile the API, copy pgame-api/build/libs/pgame-api-<version>-all.jar into
your project's libs folder and add it as a dependency.

```kotlin
dependencies {
    compileOnly(files("libs/pgame-api-<version>-all.jar"))
}
```

Next, add `PartyGamesCore` as a dependency in your `paper-plugin.yml`:

```yaml
dependencies:
  server:
    PartyGamesCore:
      load: BEFORE
      required: true
      join-classpath: true
```

### Registering minigames

To register a minigame, create a class that extends ˙Minigame`.

The base structure of a minigame class is as follows:

```kotlin
class MyMinigame(
    game: Game,
) : Minigame(game, "minigame_name")
```

Don't deviate from this structure, it's important for the PartyGamesCore plugin to work properly. The minigame's name
can be anything you want, the convention is one word, using underscores for spaces. It is also case-insensitive so "
minigame_name" and "Minigame_Name" are the same and will result in an error when both are registered.

Next, you need to override `name`, `description` and `start`.

`name` is a `Component` and serves as a display name for the minigame. It is shown when the minigame is about to begin.

`description` is also a `Component` and serves as a description for the minigame.

`start` is a function that is executed when the minigame begins. You can use it to initialize the minigame, give items
to the players, summon mobs, etc.

Here is an example of a simple minigame that gives a stone to every player when the minigame begins:

```kotlin
class MyMinigame(
    game: Game,
) : Minigame(game, "my_minigame") {
    override fun start() {
        super.start()
        for (player in game.onlinePlayers) {
            player.inventory.addItem(Material.STONE.createItem(1))
        }
    }

    override val name = Component.text("My Minigame")
    override val description = Component.text("This is a minigame!")
}
```

To extend the functionality of the minigame, you can override event functions such as `handlePlayerInteract`
or `handleBlockBreak`. To view a full list of events, see
the [Minigame](pgame-api/src/main/kotlin/info/mester/network/partygames/api/Minigame.kt) class.

#### Listening to more events

If you want to add more events to the minigame, you can extend the `Minigame` class and override the event functions.

For example, you might want to listen to the `BlockBreakBlockEvent`. To do that, first extend
the [PartyGamesListener](pgame-api/src/main/kotlin/info/mester/network/partygames/api/PartyGamesListener.kt) file with
the new event:

```kotlin
@EventHandler
fun onBlockBreakBlock(event: BlockBreakBlockEvent) {
    // to access the minigame, you can use the getMinigameFromWorld function, which takes a World object and returns a nullable Minigame
    // this also means that the event you're listening to MUST have a way to get the world it's happening in
    val minigame = getMinigameFromWorld(event.block.world)
    minigame?.handleBlockBreakBlock(event) // the question mark is used to only call the function if the minigame is not null
}
```

Then, add this new function to the [Minigame](pgame-api/src/main/kotlin/info/mester/network/partygames/api/Minigame.kt)
class:

```kotlin
open fun handleBlockBreakBlock(event: BlockBreakBlockEvent) {}
```

By marking the function as open, you can override it in your custom minigame class.

### Registering bundles

The API makes a distinction between singular minigames and bundles. A bundle is a collection of at least one minigame,
and this is what's actually playable.

To register a bundle, you first need to register your minigames.

In your plugin's `onEnable` function, first get the PartyGamesCore instance:

```kotlin
val core = PartyGamesCore.getInstance()
```

Then, register your minigames:

```kotlin
core.gameRegistry.registerMinigame(
    this, // plugin
    MyMinigame::class.qualifiedName!!, // className
    "my_minigame", // name
    listOf(
        // worlds
        MinigameWorld("my_minigame", org.bukkit.util.Vector(0.5, 63.0, 0.5)),
    ),
)
```

Let's break this down:

- `this` is the JavaPlugin instance of your plugin.
- `MyMinigame::class.qualifiedName!!` is the fully qualified name of your minigame class. (something
  like `info.mester.network.testminigame.MyMinigame`)
- `"my_minigame"` is the name of your minigame. This HAS to be the same name you used in your minigame class.

The final parameter is a list of `MinigameWorld` objects. Each `MinigameWorld` object has a `name` and a `startPos`
property. You can think of tese worlds as the "maps" for your minigame. So if you specify more worlds, the API will
randomly pick one of them to load your minigame in, essentially providing multiple map layouts for the same minigame.
The `name` property is the name of the AdvancedSlimePaper world for the map, and the `startPos` is the starting position
of the minigame. This is where players will spawn when the minigame begins.

By itself, this minigame is not yet playable, as it is not part of a bundle. You can provide a `registerAs` parameter to
this function to register the minigame as a bundle.

```kotlin
core.gameRegistry.registerMinigame(
    this,
    MyMinigame::class.qualifiedName!!,
    "my_minigame",
    listOf(
        MinigameWorld("my_minigame", org.bukkit.util.Vector(0.5, 63.0, 0.5)),
    ),
    "My Minigame", // registerAs
)
```

The `registerAs` parameter is the user-friendly display name for the bundle. If you use register a minigame like this, a
bundle with the same name as the minigame will be created automatically.

Alternatively, you can register a bundle manually:

```kotlin
core.gameRegistry.registerBundle(
    this, // plugin
    listOf("my_minigame"), // minigames
    "my_bundle", // name
    "My Bundle", // displayName
)
```

This will create a bundle with the name "my_bundle" and use only the "my_minigame" as its minigames. This is mostly
useful if you want to register a bundle that contains multiple minigames.

### Starting a game

To start a game, you need to get the PartyGamesCore instance:

```kotlin
val core = PartyGamesCore.getInstance()
```

Then, you can start a game using the `startGame` function:

```kotlin
core.gameRegistry.startGame(players, "my_bundle")
```

The `players` parameter is a list of `Player` objects, and the `bundleName` parameter is the name of the bundle to start
the game in.

This immediately starts the game. If the bundle contains multiple minigames, their order will be randomly selected.
