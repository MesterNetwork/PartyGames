plugins {
    kotlin("jvm") version "2.0.20-Beta2"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("xyz.jpenilla.run-paper") version "2.3.0"
    id("org.sonarqube") version "4.2.1.3168"
    id("io.papermc.paperweight.userdev") version "1.7.1"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

group = "info.mester.bedless"
version = "a1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(kotlin("reflect"))
    implementation("net.objecthunter:exp4j:0.4.8")
    paperweight.paperDevBundle("1.21-R0.1-SNAPSHOT")
    ktlint("com.pinterest:ktlint:0.49.0") // Ktlint dependency
}
val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21")
    }

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

    assemble {
        dependsOn("reobfJar")
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
}

sonar {
    properties {
        property("sonar.projectKey", "Bedless-Tournament")
        property("sonar.projectName", "Bedless Tournament")
    }
}

ktlint {
    version.set("0.49.1")
    enableExperimentalRules.set(true)
}
