plugins {
    kotlin("jvm") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.sonarqube") version "4.2.1.3168"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.13"
    java
}

group = "info.mester.network.partygames"
version = "a1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.rapture.pw/repository/maven-releases/")
    maven("https://repo.infernalsuite.com/repository/maven-snapshots/")
    maven("https://repo.viaversion.com")
    maven("https://haoshoku.xyz:8081/repository/default")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(kotlin("reflect"))
    // set up paper
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")

    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("net.objecthunter:exp4j:0.4.8")
    // WorldEdit
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.10-SNAPSHOT")
    // AdvancedSlimePaper
    compileOnly("com.infernalsuite.aswm:api:3.0.0-SNAPSHOT")
    // ViaVersion
    compileOnly("com.viaversion:viaversion:5.2.1")
    // Testing
    testImplementation(kotlin("test"))
    // ScoreboardLibrary
    val scoreboardLibraryVersion = "2.2.2"
    implementation("net.megavex:scoreboard-library-api:$scoreboardLibraryVersion")
    runtimeOnly("net.megavex:scoreboard-library-implementation:$scoreboardLibraryVersion")
    implementation("net.megavex:scoreboard-library-extra-kotlin:$scoreboardLibraryVersion") // Kotlin specific extensions (optional)
    runtimeOnly("net.megavex:scoreboard-library-modern:$scoreboardLibraryVersion:mojmap")
    // PlaceholderAPI
    compileOnly("me.clip:placeholderapi:2.11.6")
    // ConfigLib
    implementation("de.exlll:configlib-paper:4.5.0")
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

    register("writeVersion") {
        doLast {
            val versionFile =
                layout.buildDirectory
                    .file("version.txt")
                    .get()
                    .asFile
            versionFile.writeText(project.version.toString())
        }
    }

    test {
        useJUnitPlatform()
    }
}

tasks.register<Copy>("copyPluginToRun") {
    dependsOn("build")
    from(buildDir.resolve("libs").resolve("partygames-${project.version}-all.jar"))
    into(rootDir.resolve("run").resolve("plugins"))
}

sonar {
    properties {
        property("sonar.projectKey", "Bedless-Tournament")
        property("sonar.projectName", "Bedless Tournament")
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
