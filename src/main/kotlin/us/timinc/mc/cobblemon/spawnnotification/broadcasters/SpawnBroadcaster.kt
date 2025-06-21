package us.timinc.mc.cobblemon.spawnnotification.broadcasters

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.BUCKET
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.config

class SpawnBroadcaster(
    val pokemon: Pokemon,
    val coords: BlockPos,
    val biome: ResourceLocation,
    val dimension: ResourceLocation,
    val player: ServerPlayer?,
) {
    private val shiny
        get() = pokemon.shiny
    private val blacklisted
        get() = config.blacklistForBroadcast.any {
            PokemonProperties.parse(
                it
            ).matches(pokemon)
        }
    private val label
        get() = if (blacklisted) null else pokemon.form.labels.firstOrNull { it in config.labelsForBroadcast }
    private val bucket
        get() = if (blacklisted || !pokemon.persistentData.contains(BUCKET)) null else config.bucketsForBroadcast.firstOrNull {
            it == pokemon.persistentData.getString(
                BUCKET
            )
        }
    private val customPokemon
        get() = config.pokemonForBroadcast.any {
            PokemonProperties.parse(
                it
            ).matches(pokemon)
        }
    private val shouldBroadcast
        get() = ((shiny && config.broadcastShiny) || label != null || bucket != null || customPokemon) && config.blacklistForBroadcastEvenIfShiny.none {
            PokemonProperties.parse(
                it
            ).matches(pokemon)
        }

    fun getBroadcast(): List<Component> {
        if (!shouldBroadcast) return emptyList()

        val list = mutableListOf<Component>()
        list.add(
            config.getComponent(
                "notification.spawn",
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
                if (config.broadcastPlayerSpawnedOn && player != null) config.getComponent(
                    "notification.player",
                    player.name
                ) else "",
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
        if (label != null) config.getComponent(
            "notification.label",
            config.getComponent("label.$label")
        ) else "",
        if (bucket != null) config.getComponent(
            "notification.bucket",
            config.getComponent("bucket.$bucket")
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
        if (label != null) config.getComponent(
            "notification.label",
            config.getComponent("label.$label")
        ) else "",
        if (bucket != null) config.getComponent(
            "notification.bucket",
            config.getComponent("bucket.$bucket")
        ) else "",
        if (config.broadcastSpeciesName) pokemon.species.translatedName else Component.translatable("cobblemon.entity.pokemon"),
        coords.x,
        coords.y,
        coords.z,
        "${dimension.namespace}:${dimension.path}"
    )
}