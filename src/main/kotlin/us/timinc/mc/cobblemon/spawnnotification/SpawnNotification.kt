package us.timinc.mc.cobblemon.spawnnotification

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.mojang.serialization.Codec
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.Unit
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import team.reborn.energy.api.EnergyStorage
import us.timinc.mc.cobblemon.spawnnotification.config.ConfigBuilder
import us.timinc.mc.cobblemon.spawnnotification.config.SpawnNotificationConfig
import us.timinc.mc.cobblemon.spawnnotification.events.*
import us.timinc.mc.cobblemon.spawnnotification.items.PokeTrackerEnergyStorage
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

    @JvmStatic
    var POKE_TRACKER_PING_SOUND_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, "poke_tracker_ping")

    @JvmStatic
    var POKE_TRACKER_PING_SOUND_EVENT: SoundEvent = SoundEvent.createVariableRangeEvent(POKE_TRACKER_PING_SOUND_ID)

    // --- Custom Data Components ---
    val CURRENT_ENERGY: DataComponentType<Int> = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "current_energy"),
        DataComponentType.builder<Int>().persistent(Codec.INT).build()
    )
    val TRACKED_POKEMON: DataComponentType<List<String>> = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "tracked_pokemon"),
        DataComponentType.builder<List<String>>().persistent(Codec.STRING.listOf()).build()
    )
    val NOTIFY_NO_ENERGY: DataComponentType<Unit> = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "notify_no_energy"),
        // Fix: Use Codec.unit() for net.minecraft.util.Unit
        DataComponentType.builder<Unit>().persistent(Codec.unit(net.minecraft.util.Unit.INSTANCE)).build()
    )
    val PING_COOLDOWN: DataComponentType<Int> = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        ResourceLocation.fromNamespaceAndPath(MOD_ID, "ping_cooldown"),
        DataComponentType.builder<Int>().persistent(Codec.INT).build()
    )
    // --- End Custom Data Components ---

    // Define the new item, now with default components
    val POKE_TRACKER_ITEM: Item = PokeTrackerItem(
        Item.Properties().stacksTo(1)
            // Set default energy on creation
            .component(CURRENT_ENERGY, config.pokeTrackerMaxEnergy)
            // Set default empty tracking list on creation
            .component(TRACKED_POKEMON, emptyList())
            .component(PING_COOLDOWN, 0)
    )

    override fun onInitialize() {
        // Register Data Components (this just ensures they are loaded)
        CURRENT_ENERGY
        TRACKED_POKEMON
        NOTIFY_NO_ENERGY
        PING_COOLDOWN

        // Register Item
        Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "poke_tracker"),
            POKE_TRACKER_ITEM
        )
        // Add item to creative tab
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register { content ->
            content.accept(POKE_TRACKER_ITEM)
        }

        // ADD THIS (to register the sound):
        Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            POKE_TRACKER_PING_SOUND_ID,
            POKE_TRACKER_PING_SOUND_EVENT
        )

        // --- Register Energy API ---
        // This tells Fabric: "When another mod asks for the EnergyStorage of a POKE_TRACKER_ITEM,
        // give them a new instance of our PokeTrackerEnergyStorage wrapper."
        // Fix: The lambda provides (ItemVariant, ContainerItemContext). We pass the context to our storage.
        EnergyStorage.ITEM.registerForItems({ variant, context ->
            PokeTrackerEnergyStorage(context)
        }, POKE_TRACKER_ITEM)
        // --------------------------

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

