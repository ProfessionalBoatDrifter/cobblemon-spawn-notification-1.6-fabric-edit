plugins {
    id("java")
    id("fabric-loom") version("1.10-SNAPSHOT")
    kotlin("jvm") version ("2.1.0")
}

group = property("maven_group")!!
version = property("mod_version")!!

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://api.modrinth.com/maven")
    maven("https://maven.fabricmc.net/teamreborn/energy/")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // TechReborn Energy API (Standalone)
    modImplementation("teamreborn:energy:4.1.0")

    // Fabric Kotlin
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")

    // Cobblemon
    modImplementation("com.cobblemon:fabric:${property("cobblemon_version")}")

    // JourneyMap
    modImplementation("maven.modrinth:journeymap:${property("journeymap_version")}")

    // XaerosMinimap
    modImplementation("maven.modrinth:xaeros-minimap:${property("xaeros-minimap_version")}")
    modImplementation("maven.modrinth:xaeros-world-map:${property("xaeros-world-map_version")}")

    // Tech Reborn
    // This provides the EnergyStorage API
    // modImplementation("TechReborn:TechReborn-1.21.1:${property("techreborn_version")}")

    // Oritech
    // Also uses and provides the EnergyStorage API
    // modImplementation("maven.modrinth:oritech:${property("oritech_version")}")
}

tasks {
    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(mutableMapOf("version" to project.version))
        }
    }

    jar {
        from("LICENSE")
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "21"
    }
}