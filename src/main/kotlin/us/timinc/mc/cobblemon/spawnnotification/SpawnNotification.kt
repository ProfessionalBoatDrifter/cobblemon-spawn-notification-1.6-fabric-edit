package us.timinc.mc.cobblemon.spawnnotification

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import us.timinc.mc.cobblemon.spawnnotification.config.ConfigBuilder
import us.timinc.mc.cobblemon.spawnnotification.config.SpawnNotificationConfig
import us.timinc.mc.cobblemon.spawnnotification.events.*
import us.timinc.mc.cobblemon.spawnnotification.items.PokeTrackerItem

object SpawnNotification : ModInitializer {
    const val MOD_ID = "spawn_notification"
    const val SPAWN_BROADCASTED = "spawn_notification:spawn_broadcasted"
    const val BUCKET = "spawn_notification:bucket"
    const val SHOULD_BROADCAST_FAINT = "spawn_notification:should_broadcast_faint"
    const val FAINT_ENTITY = "spawn_notification:faint_entity"
    var config: SpawnNotificationConfig = ConfigBuilder.load(SpawnNotificationConfig::class.java, MOD_ID)
    var journeyMapPresent: Boolean = false
    var xaerosPresent: Boolean = false

    @JvmStatic
    var SHINY_SOUND_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, "pla_shiny")

    @JvmStatic
    var SHINY_SOUND_EVENT: SoundEvent = SoundEvent.createVariableRangeEvent(SHINY_SOUND_ID)

    // Define the new item
    val POKE_TRACKER_ITEM: Item = PokeTrackerItem(Item.Properties().stacksTo(1))

    override fun onInitialize() {
        // Register Item
        // Fix 1: Use fromNamespaceAndPath instead of private constructor
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(MOD_ID, "poke_tracker"), POKE_TRACKER_ITEM)
        // Add item to creative tab
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register { content ->
            // Fix 2: Use accept() instead of add()
            content.accept(POKE_TRACKER_ITEM)
        }

        // Register Events
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.LOWEST, AttachBucket::handle)
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.LOWEST, BroadcastSpawn::handle)
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.LOWEST, PlayShinySound::handle)
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.LOWEST, PokeTrackerNotification::handle) // New handler

        CobblemonEvents.POKEMON_SENT_POST.subscribe(Priority.LOWEST, PlayShinyPlayerSound::handle)
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.LOWEST, BroadcastCapture::handle)
        CobblemonEvents.POKEMON_FAINTED.subscribe(Priority.LOWEST, BroadcastFaint::handle)
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.LOWEST, BroadcastFaint::handle)
        ServerEntityEvents.ENTITY_LOAD.register(BroadcastUnnaturalSpawn::handle)
        ServerEntityEvents.ENTITY_UNLOAD.register(BroadcastFaint::handle)
        ServerEntityEvents.ENTITY_UNLOAD.register(BroadcastDespawn::handle)
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { _, _, _ ->
            config = ConfigBuilder.load(SpawnNotificationConfig::class.java, MOD_ID)
        }
    }

    fun onInitializeJourneyMap() {
        journeyMapPresent = true
    }

    fun onInitializeXaeros() {
        xaerosPresent = true
    }
}

