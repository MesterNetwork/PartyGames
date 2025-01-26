package info.mester.network.partygames

import info.mester.network.partygames.level.LevelData
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class DatabaseManager(
    databaseFile: File,
) {
    private val connection: Connection

    init {
        // init JDBC driver
        Class.forName("org.sqlite.JDBC")
        // create database file
        if (!databaseFile.exists()) {
            databaseFile.createNewFile()
        }
        // connect to database
        connection = DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}")
        // create tables if they don't exist
        connection.createStatement().use { statement ->
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS levels (uuid CHAR(32) PRIMARY KEY, level INTEGER, exp INTEGER)")
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS games_won (
                    uuid CHAR(32),
                    game TEXT,
                    amount INTEGER,
                    PRIMARY KEY (uuid, game)
                );
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS points_gained (
                    uuid CHAR(32),
                    game TEXT,
                    amount INTEGER,
                    PRIMARY KEY (uuid, game)
                );
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS time_played (
                    uuid CHAR(32),
                    game TEXT,
                    amount INTEGER,
                    PRIMARY KEY (uuid, game)
                );
                """.trimIndent(),
            )
        }
    }

    fun getLevel(uuid: UUID): LevelData? {
        val statement = connection.prepareStatement("SELECT * FROM levels WHERE uuid = ?")
        statement.setString(1, uuid.shorten())
        val resultSet = statement.executeQuery()
        if (resultSet.next()) {
            val level = resultSet.getInt("level")
            val exp = resultSet.getInt("exp")
            return LevelData(level, exp)
        } else {
            return null
        }
    }

    fun saveLevel(
        uuid: UUID,
        levelData: LevelData,
    ) {
        val statement = connection.prepareStatement("INSERT OR REPLACE INTO levels (uuid, level, exp) VALUES (?, ?, ?)")
        statement.setString(1, uuid.shorten())
        statement.setInt(2, levelData.level)
        statement.setInt(3, levelData.xp)
        statement.executeUpdate()
    }

    fun addGameWon(
        uuid: UUID,
        game: String?,
    ) {
        val statement =
            connection.prepareStatement(
                """
                INSERT INTO games_won (uuid, game, amount)
                VALUES (?, ?, 1)
                ON CONFLICT(uuid, game) DO UPDATE SET amount = amount + 1;
                """.trimIndent(),
            )
        statement.setString(1, uuid.shorten())
        statement.setString(2, game?.lowercase() ?: "__global")
        statement.executeUpdate()
    }

    fun getGamesWon(
        uuid: UUID,
        game: String?,
    ): Int {
        val statementString =
            if (game == null) {
                "SELECT amount FROM games_won WHERE uuid = ?"
            } else {
                "SELECT amount FROM games_won WHERE uuid = ? AND game = ?"
            }
        val statement = connection.prepareStatement(statementString)
        statement.setString(1, uuid.shorten())
        if (game != null) {
            statement.setString(2, game.lowercase())
        }
        val resultSet = statement.executeQuery()
        var totalGamesWon = 0
        while (resultSet.next()) {
            totalGamesWon += resultSet.getInt("amount")
        }

        return totalGamesWon
    }

    fun addPointsGained(
        uuid: UUID,
        game: String,
        amount: Int,
    ) {
        val statement =
            connection.prepareStatement(
                """
                INSERT INTO points_gained (uuid, game, amount)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid, game) DO UPDATE SET amount = amount + ?;
                """.trimIndent(),
            )
        statement.setString(1, uuid.shorten())
        statement.setString(2, game.lowercase())
        statement.setInt(3, amount)
        statement.setInt(4, amount)
        statement.executeUpdate()
    }

    fun getPointsGained(
        uuid: UUID,
        game: String?,
    ): Int {
        val statementString =
            if (game == null) {
                "SELECT amount FROM points_gained WHERE uuid = ?"
            } else {
                "SELECT amount FROM points_gained WHERE uuid = ? AND game = ?"
            }
        val statement = connection.prepareStatement(statementString)
        statement.setString(1, uuid.shorten())
        if (game != null) {
            statement.setString(2, game.lowercase())
        }
        val resultSet = statement.executeQuery()
        var totalPointsGained = 0
        while (resultSet.next()) {
            totalPointsGained += resultSet.getInt("amount")
        }

        return totalPointsGained
    }

    fun addTimePlayed(
        uuid: UUID,
        game: String,
        amount: Int,
    ) {
        val statement =
            connection.prepareStatement(
                """
                INSERT INTO time_played (uuid, game, amount)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid, game) DO UPDATE SET amount = amount + ?;
                """.trimIndent(),
            )
        statement.setString(1, uuid.shorten())
        statement.setString(2, game.lowercase())
        statement.setInt(3, amount)
        statement.setInt(4, amount)
        statement.executeUpdate()
    }

    fun getTimePlayed(
        uuid: UUID,
        game: String?,
    ): Int {
        val statementString =
            if (game == null) {
                "SELECT amount FROM time_played WHERE uuid = ?"
            } else {
                "SELECT amount FROM time_played WHERE uuid = ? AND game = ?"
            }
        val statement = connection.prepareStatement(statementString)
        statement.setString(1, uuid.shorten())
        if (game != null) {
            statement.setString(2, game.lowercase())
        }
        val resultSet = statement.executeQuery()
        // if game is null -> add everything together
        // else -> just return the amount for that game
        var totalTimePlayed = 0
        while (resultSet.next()) {
            totalTimePlayed += resultSet.getInt("amount")
        }

        return totalTimePlayed
    }
}
