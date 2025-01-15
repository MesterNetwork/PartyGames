rootProject.name = "partygames"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

include("pgame-api")
include("pgame-plugin")
