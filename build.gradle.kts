plugins {
    kotlin("jvm") version "2.1.0" apply false
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19" apply false
    id("org.sonarqube") version "4.2.1.3168"
}

allprojects {
    group = "info.mester.network.partygames"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
}

sonar {
    properties {
        property("sonar.projectKey", "Bedless-Tournament")
        property("sonar.projectName", "Bedless Tournament")
    }
}
