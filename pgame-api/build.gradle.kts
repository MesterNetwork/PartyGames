plugins {
    id("com.gradleup.shadow") version "9.3.0"
    id("io.papermc.paperweight.userdev")
    id("org.jetbrains.dokka") version "2.0.0"
    java
}

group = "info.mester.network.partygames"
version = "1.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.rapture.pw/repository/maven-releases/")
    maven("https://repo.infernalsuite.com/repository/maven-snapshots/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation(kotlin("reflect"))

    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.infernalsuite.asp:api:4.0.0-SNAPSHOT")
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
        dependsOn("dokkaGenerateModuleHtml")
    }

    test {
        useJUnitPlatform()
    }
}

tasks.register<Copy>("copyPluginToRun") {
    dependsOn("build")
    val jarFile =
        layout.buildDirectory
            .file("libs/pgame-api-${project.version}-all.jar")
            .get()
            .asFile
    val destination =
        layout.buildDirectory
            .dir("../../run/plugins")
            .get()
            .asFile
    from(jarFile)
    into(destination)
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
