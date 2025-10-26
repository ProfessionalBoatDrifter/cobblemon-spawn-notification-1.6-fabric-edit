package us.timinc.mc.cobblemon.spawnnotification.broadcasters

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.BUCKET
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.config

/**
 * Broadcaster for the personal Poke Tracker item.
 * This bypasses most broadcast rules (like labels/buckets)
 * and only checks against the "blacklistEvenIfShiny" config.
 */
class PokeTrackerBroadcaster(
    val pokemon: Pokemon,
    val coords: BlockPos,
    val biome: ResourceLocation,
    val dimension: ResourceLocation,
    val player: ServerPlayer, // Player who is being notified
) {
    private val shiny
        get() = pokemon.shiny

    // Trackers bypass all broadcast rules except shiny blacklist
    private val shouldBroadcast
        get() = config.blacklistForBroadcastEvenIfShiny.none {
            PokemonProperties.parse(
                it
            ).matches(pokemon)
        }

    fun getBroadcast(): List<Component> {
        if (!shouldBroadcast) return emptyList()

        // Get label/bucket info if it's relevant, even if tracker doesn't filter on it
        val label = pokemon.form.labels.firstOrNull { it in config.labelsForBroadcast }
        val bucket = if (!pokemon.persistentData.contains(BUCKET)) null else config.bucketsForBroadcast.firstOrNull {
            it == pokemon.persistentData.getString(
                BUCKET
            )
        }

        val list = mutableListOf<Component>()
        list.add(
            config.getComponent(
                "notification.tracker", // The new lang key for the tracker
                if (shiny && config.broadcastShiny) config.getComponent(
                    "notification.shiny",
                    config.getComponent("shiny")
                ) else "",
                if (label != null) config.getComponent(
                    "notification.label",
                    config.getComponent("label.$label")
                ) else "",
                if (bucket != null) config.getComponent(
                    "notification.bucket",
                    config.getComponent("bucket.$bucket")
                ) else "",
                if (config.broadcastSpeciesName) pokemon.species.translatedName else Component.translatable("cobblemon.entity.pokemon"),
                if (config.broadcastBiome) config.getComponent(
                    "notification.biome",
                    config.getRawComponent("biome.${biome.toLanguageKey()}")
                ) else "",
                if (config.broadcastCoords) config.getComponent(
                    "notification.coords",
                    coords.x,
                    coords.y,
                    coords.z
                ) else "",
                if (config.announceCrossDimensions) config.getComponent(
                    "notification.dimension",
                    config.getRawComponent("dimension.${dimension.toLanguageKey()}")
                ) else "",
                // Don't show "spawned by player" for personal tracker
                "",
                if (config.broadcastJourneyMapWaypoints) buildJourneyMapWaypoint() else ""
            )
        )

        if (config.broadcastXaerosWaypoints) {
            list.add(buildXaerosWaypoint())
        }

        return list
    }

    private fun buildXaerosWaypoint() = config.getComponent(
        "notification.waypoints.xaeros",
        if (shiny && config.broadcastShiny) config.getComponent(
            "notification.shiny",
            config.getComponent("shiny")
        ) else "",
        if (pokemon.form.labels.firstOrNull { it in config.labelsForBroadcast } != null) config.getComponent(
            "notification.label",
            config.getComponent("label.${pokemon.form.labels.firstOrNull { it in config.labelsForBroadcast }}")
        ) else "",
        if (pokemon.persistentData.contains(BUCKET) && config.bucketsForBroadcast.firstOrNull {
                it == pokemon.persistentData.getString(
                    BUCKET
                )
            } != null) config.getComponent(
            "notification.bucket",
            config.getComponent("bucket.${pokemon.persistentData.getString(BUCKET)}")
        ) else "",
        if (config.broadcastSpeciesName) pokemon.species.translatedName else Component.translatable("cobblemon.entity.pokemon"),
        coords.x,
        coords.y,
        coords.z,
        dimension.path
    )

    private fun buildJourneyMapWaypoint() = config.getComponent(
        "notification.waypoints.journeymap",
        if (shiny && config.broadcastShiny) config.getComponent(
            "notification.shiny",
            config.getComponent("shiny")
        ) else "",
        if (pokemon.form.labels.firstOrNull { it in config.labelsForBroadcast } != null) config.getComponent(
            "notification.label",
            config.getComponent("label.${pokemon.form.labels.firstOrNull { it in config.labelsForBroadcast }}")
        ) else "",
        if (pokemon.persistentData.contains(BUCKET) && config.bucketsForBroadcast.firstOrNull {
                it == pokemon.persistentData.getString(
                    BUCKET
                )
            } != null) config.getComponent(
            "notification.bucket",
            config.getComponent("bucket.${pokemon.persistentData.getString(BUCKET)}")
        ) else "",
        if (config.broadcastSpeciesName) pokemon.species.translatedName else Component.translatable("cobblemon.entity.pokemon"),
        coords.x,
        coords.y,
        coords.z,
        "${dimension.namespace}:${dimension.path}"
    )
}
