package us.timinc.mc.cobblemon.spawnnotification.items

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.config

class PokeTrackerItem(properties: Properties) : Item(properties) {
    companion object {
        const val TRACKER_DATA = "poke_tracker_data"
        const val CURRENT_ENERGY = "current_energy"
        const val TRACKED_POKEMON = "tracked_pokemon"
    }

    // Add energy on creation
    override fun getDefaultInstance(): ItemStack {
        val stack = super.getDefaultInstance()
        if (config.pokeTrackerEnergyEnabled) {
            // Fix: Use vanilla NBT access
            val mainTag = stack.getOrCreateTag()
            val trackerTag = mainTag.getCompound(TRACKER_DATA)
            trackerTag.putInt(CURRENT_ENERGY, config.pokeTrackerMaxEnergy)
            mainTag.put(TRACKER_DATA, trackerTag) // Put the sub-tag back
        }
        return stack
    }

    /**
     * Handles energy drain once per second (every 20 ticks).
     */
    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        if (level.isClientSide || !config.pokeTrackerEnabled || !config.pokeTrackerEnergyEnabled || entity !is Player) return

        // Run once per second
        if (level.gameTime % 20 == 0L) {
            // Fix: Use vanilla NBT access
            val mainTag = stack.getOrCreateTag()
            val nbt = mainTag.getCompound(TRACKER_DATA)
            var currentEnergy = nbt.getInt(CURRENT_ENERGY)

            if (currentEnergy <= 0) {
                // Notify player they are out of energy (but only once)
                if (!nbt.getBoolean("notify_no_energy")) {
                    entity.sendSystemMessage(Component.translatable("spawn_notification.tracker.no_energy").withStyle(ChatFormatting.RED))
                    nbt.putBoolean("notify_no_energy", true)
                }
                mainTag.put(TRACKER_DATA, nbt) // Save NBT changes
                return
            } else {
                // Reset notification flag if it has power
                nbt.remove("notify_no_energy")
            }

            val trackedList = nbt.getList(TRACKED_POKEMON, 8) as ListTag // 8 = String

            // Drain idle energy + active energy (if tracking)
            val drain = if (trackedList.isEmpty()) {
                config.pokeTrackerIdleEnergyDrainPerSecond
            } else {
                config.pokeTrackerIdleEnergyDrainPerSecond + config.pokeTrackerActiveEnergyDrainPerSecond
            }

            currentEnergy = (currentEnergy - drain).coerceAtLeast(0)
            nbt.putInt(CURRENT_ENERGY, currentEnergy)

            mainTag.put(TRACKER_DATA, nbt) // Save NBT changes
        }
    }

    /**
     * Handles right-clicking a Pokémon to track it.
     */
    override fun interactLivingEntity(
        stack: ItemStack,
        player: Player,
        interactionTarget: LivingEntity,
        interactionHand: InteractionHand
    ): InteractionResult {
        if (player.level().isClientSide || !config.pokeTrackerEnabled || interactionTarget !is PokemonEntity) {
            return InteractionResult.PASS
        }

        // Fix: Use vanilla NBT access
        val mainTag = stack.getOrCreateTag()

        // Check for energy
        if (config.pokeTrackerEnergyEnabled) {
            val nbt = mainTag.getCompound(TRACKER_DATA) // Get sub-tag
            if (nbt.getInt(CURRENT_ENERGY) <= 0) {
                player.sendSystemMessage(Component.translatable("spawn_notification.tracker.no_energy").withStyle(ChatFormatting.RED))
                return InteractionResult.FAIL
            }
        }

        val pokemon = interactionTarget.pokemon
        val speciesId = pokemon.species.resourceIdentifier.toString()
        val nbt = mainTag.getCompound(TRACKER_DATA) // Get sub-tag
        val trackedList = nbt.getList(TRACKED_POKEMON, 8) as ListTag

        // Check if tracker is full
        if (trackedList.size >= config.pokeTrackerMaxTrackedPerItem) {
            player.sendSystemMessage(Component.translatable("spawn_notification.tracker.full").withStyle(ChatFormatting.RED))
            return InteractionResult.SUCCESS
        }

        // Add to list if not already present
        if (trackedList.any { (it as StringTag).asString == speciesId }) {
            player.sendSystemMessage(Component.translatable("spawn_notification.tracker.already_tracking", pokemon.species.translatedName).withStyle(ChatFormatting.YELLOW))
        } else {
            trackedList.add(StringTag.valueOf(speciesId))
            nbt.put(TRACKED_POKEMON, trackedList)
            player.sendSystemMessage(Component.translatable("spawn_notification.tracker.added", pokemon.species.translatedName).withStyle(ChatFormatting.GREEN))
        }

        mainTag.put(TRACKER_DATA, nbt) // Save NBT changes
        return InteractionResult.SUCCESS
    }

    /**
     * Handles right-clicking in the air.
     * Shift-click: Clears the tracker list.
     * Normal click: Shows the current list.
     */
    override fun use(level: Level, player: Player, interactionHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(interactionHand)
        if (level.isClientSide) return InteractionResultHolder.success(stack)

        if (player.isShiftKeyDown) {
            // Clear the list
            // Fix: Use vanilla NBT access
            val mainTag = stack.getOrCreateTag()
            val nbt = mainTag.getCompound(TRACKER_DATA)
            nbt.put(TRACKED_POKEMON, ListTag())
            mainTag.put(TRACKER_DATA, nbt) // Save NBT changes
            player.sendSystemMessage(Component.translatable("spawn_notification.tracker.cleared").withStyle(ChatFormatting.GRAY))
        } else {
            // Display the list
            // Fix: Use vanilla read-only NBT access
            val nbt = stack.getTag()?.getCompound(TRACKER_DATA)
            val trackedList = nbt?.getList(TRACKED_POKEMON, 8) // No cast needed here

            if (trackedList == null || trackedList.isEmpty()) {
                player.sendSystemMessage(Component.translatable("spawn_notification.tracker.empty").withStyle(ChatFormatting.GRAY))
            } else {
                player.sendSystemMessage(Component.translatable("spawn_notification.tracker.tracking_list").withStyle(ChatFormatting.AQUA))
                trackedList.forEach {
                    // Fix: Cast 'it' from Tag to StringTag to safely use asString
                    val speciesId = (it as StringTag).asString
                    val species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.get(speciesId)
                    val name = species?.translatedName ?: Component.literal(speciesId).withStyle(ChatFormatting.RED)
                    player.sendSystemMessage(Component.literal("- ").append(name))
                }
            }
        }

        return InteractionResultHolder.success(stack)
    }

    /**
     * Adds tooltip info for energy and tracked species.
     */
    // Fix: Correct 'appendTooltip' signature using TooltipFlag
    override fun appendTooltip(
        stack: ItemStack,
        context: Item.TooltipContext, // Use Item.TooltipContext
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag // Use TooltipFlag
    ) {
        if (!config.pokeTrackerEnabled) return

        // Fix: Use vanilla read-only NBT access
        val nbt = stack.getTag()?.getCompound(TRACKER_DATA)

        // Show energy
        if (config.pokeTrackerEnergyEnabled) {
            val currentEnergy = nbt?.getInt(CURRENT_ENERGY) ?: config.pokeTrackerMaxEnergy
            tooltipComponents.add(
                Component.translatable("spawn_notification.tracker.tooltip.energy", currentEnergy, config.pokeTrackerMaxEnergy)
                    .withStyle(ChatFormatting.GRAY)
            )
        }

        // Show tracked list
        val trackedList = nbt?.getList(TRACKED_POKEMON, 8)
        if (trackedList != null && trackedList.isNotEmpty()) {
            tooltipComponents.add(Component.translatable("spawn_notification.tracker.tooltip.tracking").withStyle(ChatFormatting.AQUA))
            trackedList.forEach {
                // Fix: Cast 'it' from Tag to StringTag to safely use asString
                val speciesId = (it as StringTag).asString
                val species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.get(speciesId)
                val name = species?.translatedName ?: Component.literal(speciesId).withStyle(ChatFormatting.RED)
                tooltipComponents.add(Component.literal(" - ").append(name).withStyle(ChatFormatting.GRAY))
            }
        }
    }

    // --- Energy Bar Display ---

    override fun isBarVisible(stack: ItemStack): Boolean {
        // Fix: Use vanilla read-only NBT access
        // Show bar only if energy is enabled and not full
        return config.pokeTrackerEnergyEnabled && (stack.getTag()?.getCompound(TRACKER_DATA)?.getInt(CURRENT_ENERGY) ?: config.pokeTrackerMaxEnergy) < config.pokeTrackerMaxEnergy
    }

    override fun getBarWidth(stack: ItemStack): Int {
        // Fix: Use vanilla read-only NBT access
        val nbt = stack.getTag()?.getCompound(TRACKER_DATA)
        val currentEnergy = nbt?.getInt(CURRENT_ENERGY) ?: config.pokeTrackerMaxEnergy
        // Calculate durability bar width (13 pixels max)
        return ((currentEnergy.toDouble() / config.pokeTrackerMaxEnergy) * 13).toInt()
    }

    override fun getBarColor(stack: ItemStack): Int {
        // A nice cyan color for the energy bar
        return 0x00D1FF
    }
}


