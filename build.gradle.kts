plugins {
    kotlin("jvm") version "2.3.10"
    id("com.gradleup.shadow") version "9.4.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper 1.21 ships with native Adventure + MiniMessage – no extra runtime dep needed.
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        // Minimize to only bundle what we actually use (removes unused stdlib classes)
        minimize()
        archiveClassifier.set("")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
