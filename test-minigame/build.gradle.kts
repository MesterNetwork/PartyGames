plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.13"
    java
}

group = "info.mester.network.testminigame"
version = "1.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(kotlin("reflect"))
    compileOnly(project(":pgame-api"))

    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
}
val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    build {
        dependsOn("shadowJar")
    }

    test {
        useJUnitPlatform()
    }
}

sourceSets {
    main {
        java {
            srcDir("src/main/kotlin")
        }
    }
    test {
        java {
            srcDir("src/test/kotlin")
        }
    }
}
