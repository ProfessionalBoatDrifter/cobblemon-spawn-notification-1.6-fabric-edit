package us.timinc.mc.cobblemon.spawnnotification.events

import com.cobblemon.mod.common.api.events.entity.SpawnEvent
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.nbt.ListTag
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.config
import us.timinc.mc.cobblemon.spawnnotification.broadcasters.PokeTrackerBroadcaster
import us.timinc.mc.cobblemon.spawnnotification.items.PokeTrackerItem
import us.timinc.mc.cobblemon.spawnnotification.util.Broadcast
import us.timinc.mc.cobblemon.spawnnotification.util.PlayerUtil
import us.timinc.mc.cobblemon.spawnnotification.util.isReallyWild
import java.util.*

object PokeTrackerNotification {
    fun handle(evt: SpawnEvent<PokemonEntity>) {
        // Exit if feature is disabled or we're on the client
        if (!config.pokeTrackerEnabled || evt.ctx.world.isClientSide) return

        val pokemon = evt.entity.pokemon
        // Only track wild, un-owned Pokemon
        if (!pokemon.isReallyWild()) return

        val speciesId = pokemon.species.resourceIdentifier.toString()
        val world = evt.ctx.world
        val pos = evt.ctx.position

        val notifiedPlayers = mutableSetOf<UUID>()
        // Get players in range (or in dimension)
        val validPlayers = PlayerUtil.getValidPlayers(world.dimension(), pos)

        validPlayers.forEach { player ->
            // Don't notify the same player multiple times for one spawn
            if (notifiedPlayers.contains(player.uuid)) return@forEach

            // Check player's inventory
            player.inventory.items.forEach inner@{ itemStack ->
                // Check if item is a Poke Tracker
                if (itemStack.item != SpawnNotification.POKE_TRACKER_ITEM) return@inner

                val nbt = itemStack.getTagElement(PokeTrackerItem.TRACKER_DATA) ?: return@inner

                // Check for energy if enabled
                if (config.pokeTrackerEnergyEnabled) {
                    val currentEnergy = nbt.getInt(PokeTrackerItem.CURRENT_ENERGY)
                    if (currentEnergy <= 0) return@inner // This tracker is out of power
                }

                val trackedList = nbt.getList(PokeTrackerItem.TRACKED_POKEMON, 8) as? ListTag ?: return@inner // 8 = String

                // Check if the spawned species is in this tracker's list
                trackedList.forEach { tag ->
                    if (tag.asString == speciesId) {
                        // We found a match, build and send the personal notification
                        val messages = PokeTrackerBroadcaster(
                            pokemon,
                            pos,
                            evt.ctx.biomeName,
                            world.dimension().location(),
                            player
                        ).getBroadcast()

                        messages.forEach { Broadcast.broadcastMessage(player, it) }
                        notifiedPlayers.add(player.uuid)
                        return@inner // Stop checking this player's inventory (one notification is enough)
                    }
                }
            }
        }
    }
}
