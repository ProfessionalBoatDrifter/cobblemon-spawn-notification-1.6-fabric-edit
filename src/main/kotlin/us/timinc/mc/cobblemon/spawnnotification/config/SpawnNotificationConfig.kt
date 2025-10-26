package us.timinc.mc.cobblemon.spawnnotification.config

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.MOD_ID

class SpawnNotificationConfig {
    val announceDespawnPlayer = true
    val broadcastShiny = true
    val broadcastCoords = true
    val broadcastBiome = false
    val playShinySound = true
    val playShinySoundPlayer = false
    val announceCrossDimensions = false
    val broadcastCaptures = true
    val broadcastFaints = true
    val broadcastVolatileDespawns = false
    val broadcastSpeciesName = true
    val broadcastPlayerSpawnedOn = false
    val disableWaypoints = false

    // New Poke Tracker Config
    val pokeTrackerEnabled = true
    val pokeTrackerMaxTrackedPerItem = 2
    val pokeTrackerEnergyEnabled = true
    val pokeTrackerMaxEnergy = 1000
    val pokeTrackerIdleEnergyDrainPerSecond = 1 // 1 per second
    val pokeTrackerActiveEnergyDrainPerSecond = 5 // 5 per second (stacks with idle)

    // --- Poke Tracker Pinging ---
    val pokeTrackerPingingEnabled = true
    val pokeTrackerPingSound = "minecraft:block.note_block.pling" // A safe, default sound. Can be changed to any sound ID.
    val pokeTrackerMaxPingDistance = 64.0 // Max distance in blocks to start detecting Pokémon.
    val pokeTrackerNoResultPingInterval = 100 // 5 seconds (100 ticks).
    val pokeTrackerNoResultPitch = 0.5f // Low pitch for "no result".
    val pokeTrackerMinPingInterval = 8 // 0.4 seconds (8 ticks) when very close.
    val pokeTrackerMaxPingInterval = 100 // 5 seconds (100 ticks) when at max distance.
    val pokeTrackerMinPitch = 0.8f // Pitch at max distance.
    val pokeTrackerMaxPitch = 1.2f // Pitch when very close.

    val broadcastRange: Int = -1
    val playerLimit: Int = -1

    val labelsForBroadcast: MutableSet<String> = mutableSetOf("legendary", "mythical", "ultra_beast", "paradox")
    val bucketsForBroadcast: MutableSet<String> = mutableSetOf()
    val pokemonForBroadcast: MutableSet<String> = mutableSetOf()

    val blacklistForBroadcast: MutableSet<String> = mutableSetOf()
    val blacklistForBroadcastEvenIfShiny: MutableSet<String> = mutableSetOf()

    val broadcastXaerosWaypoints
        get() = !disableWaypoints && SpawnNotification.xaerosPresent
    val broadcastJourneyMapWaypoints
        get() = !disableWaypoints && SpawnNotification.journeyMapPresent

    @Suppress("KotlinConstantConditions")
    val broadcastRangeEnabled: Boolean
        get() = broadcastRange > 0

    @Suppress("KotlinConstantConditions")
    val playerLimitEnabled: Boolean
        get() = playerLimit > 0

    val formatting = mutableMapOf(
        "label.legendary" to "LIGHT_PURPLE",
        "bucket.ultra-rare" to "AQUA",
        "shiny" to "GOLD"
    )

    fun getComponent(characteristic: String, vararg props: Any): Component {
        val result = Component.translatable("$MOD_ID.$characteristic", *props)
        val possibleFormatting = formatting[characteristic]?.let(ChatFormatting::getByName)
        return if (possibleFormatting != null) result.withStyle(possibleFormatting) else result
    }

    fun getRawComponent(characteristic: String, vararg props: Any): Component {
        val result = Component.translatable(characteristic, *props)
        val possibleFormatting = formatting[characteristic]?.let(ChatFormatting::getByName)
        return if (possibleFormatting != null) result.withStyle(possibleFormatting) else result
    }
}
