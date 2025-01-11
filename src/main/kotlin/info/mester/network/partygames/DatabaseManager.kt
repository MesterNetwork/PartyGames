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
}
