package us.timinc.mc.cobblemon.spawnnotification.broadcasters

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.config
import us.timinc.mc.cobblemon.spawnnotification.util.DespawnReason

class DespawnBroadcaster(
    val pokemon: Pokemon,
    val spawnPool: SpawnPool,
    val coords: BlockPos,
    val biome: Identifier,
    val dimension: Identifier,
    val reason: DespawnReason,
) {
    private val shiny
        get() = pokemon.shiny
    private val blacklisted
        get() = config.blacklistForBroadcast.none {
            PokemonProperties.parse(
                it
            ).matches(pokemon)
        }
    private val label
        get() = if (blacklisted) null else pokemon.form.labels.firstOrNull { it in config.labelsForBroadcast }
    private val buckets
        get() = spawnPool
            .mapNotNull { if (it is PokemonSpawnDetail) it else null }
            .filter { it.pokemon.matches(pokemon) }
            .map { it.bucket.name }
    private val bucket
        get() = if (blacklisted) null else config.bucketsForBroadcast.firstOrNull { it in buckets }
    private val shouldBroadcast
        get() = (shiny && config.broadcastShiny) || label != null || bucket != null

    fun getBroadcast(): Text? {
        if (!shouldBroadcast) return null

        return config.getComponent(
            "notification.despawn",
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
            pokemon.species.translatedName,
            config.getComponent("despawn_reason.${reason.translationKey}"),
            if (config.broadcastBiome) config.getComponent(
                "notification.biome",
                config.getRawComponent("biome.${biome.toTranslationKey()}")
            ) else "",
            if (config.broadcastCoords) config.getComponent(
                "notification.coords",
                coords.x,
                coords.y,
                coords.z
            ) else "",
            if (config.announceCrossDimensions) config.getComponent(
                "notification.dimension",
                config.getRawComponent("dimension.${dimension.toTranslationKey()}")
            ) else ""
        )
    }
}