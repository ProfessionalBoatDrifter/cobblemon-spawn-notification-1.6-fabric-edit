package us.timinc.mc.cobblemon.spawnnotification.items

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Unit
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
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.CURRENT_ENERGY
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.NOTIFY_NO_ENERGY
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.TRACKED_POKEMON
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.config

class PokeTrackerItem(properties: Properties) : Item(properties) {
    // No companion object needed for keys anymore, they are in SpawnNotification

    // getDefaultInstance override is no longer needed
    // Default values are set in SpawnNotification.kt when registering the item

    /**
     * Handles energy drain once per second (every 20 ticks).
     */
    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        if (level.isClientSide || !config.pokeTrackerEnabled || !config.pokeTrackerEnergyEnabled || entity !is Player) return

        // Run once per second
        if (level.gameTime % 20 == 0L) {
            // Use Data Components instead of NBT
            var currentEnergy = stack.getOrDefault(CURRENT_ENERGY, config.pokeTrackerMaxEnergy)

            if (currentEnergy <= 0) {
                // Notify player they are out of energy (but only once)
                if (!stack.has(NOTIFY_NO_ENERGY)) {
                    entity.sendSystemMessage(
                        Component.translatable("spawn_notification.tracker.no_energy")
                            .withStyle(ChatFormatting.RED)
                    )
                    // Set the flag component
                    stack.set(NOTIFY_NO_ENERGY, Unit.INSTANCE)
                }
                return
            } else {
                // Reset notification flag if it has power
                stack.remove(NOTIFY_NO_ENERGY)
            }

            // Get component list (defaults to emptyList if not present)
            val trackedList = stack.getOrDefault(TRACKED_POKEMON, emptyList())

            // Drain idle energy + active energy (if tracking)
            val drain = if (trackedList.isEmpty()) {
                config.pokeTrackerIdleEnergyDrainPerSecond
            } else {
                config.pokeTrackerIdleEnergyDrainPerSecond + config.pokeTrackerActiveEnergyDrainPerSecond
            }

            currentEnergy = (currentEnergy - drain).coerceAtLeast(0)
            // Set the new energy value
            stack.set(CURRENT_ENERGY, currentEnergy)
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

        // Check for energy
        if (config.pokeTrackerEnergyEnabled) {
            if (stack.getOrDefault(CURRENT_ENERGY, 0) <= 0) {
                player.sendSystemMessage(
                    Component.translatable("spawn_notification.tracker.no_energy")
                        .withStyle(ChatFormatting.RED)
                )
                return InteractionResult.FAIL
            }
        }

        val pokemon = interactionTarget.pokemon
        val speciesId = pokemon.species.resourceIdentifier.toString()
        // Get component list (defaults to emptyList if not present)
        val trackedList = stack.getOrDefault(TRACKED_POKEMON, emptyList())

        // Check if tracker is full
        if (trackedList.size >= config.pokeTrackerMaxTrackedPerItem) {
            player.sendSystemMessage(
                Component.translatable("spawn_notification.tracker.full")
                    .withStyle(ChatFormatting.RED)
            )
            return InteractionResult.SUCCESS
        }

        // Add to list if not already present
        if (trackedList.contains(speciesId)) {
            player.sendSystemMessage(
                Component.translatable(
                    "spawn_notification.tracker.already_tracking",
                    pokemon.species.translatedName
                ).withStyle(ChatFormatting.YELLOW)
            )
        } else {
            // Lists from components are immutable, so we create a new list
            val newList = trackedList + speciesId
            stack.set(TRACKED_POKEMON, newList)
            player.sendSystemMessage(
                Component.translatable("spawn_notification.tracker.added", pokemon.species.translatedName)
                    .withStyle(ChatFormatting.GREEN)
            )
        }

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
            // Clear the list by setting it to an empty list
            stack.set(TRACKED_POKEMON, emptyList())
            player.sendSystemMessage(
                Component.translatable("spawn_notification.tracker.cleared")
                    .withStyle(ChatFormatting.GRAY)
            )
        } else {
            // Display the list
            // get() returns List<String>? (nullable)
            val trackedList = stack.get(TRACKED_POKEMON)

            if (trackedList == null || trackedList.isEmpty()) {
                player.sendSystemMessage(
                    Component.translatable("spawn_notification.tracker.empty")
                        .withStyle(ChatFormatting.GRAY)
                )
            } else {
                player.sendSystemMessage(
                    Component.translatable("spawn_notification.tracker.tracking_list")
                        .withStyle(ChatFormatting.AQUA)
                )
                trackedList.forEach { speciesId ->
                    // Fix: Use getByIdentifier and parse the string
                    val species =
                        com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByIdentifier(ResourceLocation.parse(speciesId))
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
    // Fix 1: Correct method name to 'appendHoverText'
    // Fix 2: Correct parameter name to 'list'
    override fun appendHoverText(
        stack: ItemStack,
        context: Item.TooltipContext,
        list: MutableList<Component>, // Changed from 'tooltipComponents' to 'list'
        tooltipFlag: TooltipFlag
    ) {
        if (!config.pokeTrackerEnabled) return

        // Show energy
        if (config.pokeTrackerEnergyEnabled) {
            val currentEnergy = stack.getOrDefault(CURRENT_ENERGY, config.pokeTrackerMaxEnergy)
            list.add( // Use 'list'
                Component.translatable(
                    "spawn_notification.tracker.tooltip.energy",
                    currentEnergy,
                    config.pokeTrackerMaxEnergy
                )
                    .withStyle(ChatFormatting.GRAY)
            )
        }

        // Show tracked list
        // get() returns List<String>? (nullable)
        val trackedList = stack.get(TRACKED_POKEMON)
        if (trackedList != null && trackedList.isNotEmpty()) {
            list.add( // Use 'list'
                Component.translatable("spawn_notification.tracker.tooltip.tracking")
                    .withStyle(ChatFormatting.AQUA)
            )
            trackedList.forEach { speciesId ->
                // Fix 3: Use getByIdentifier and parse the string
                val species =
                    com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByIdentifier(ResourceLocation.parse(speciesId))
                val name = species?.translatedName ?: Component.literal(speciesId).withStyle(ChatFormatting.RED)
                list.add(Component.literal(" - ").append(name).withStyle(ChatFormatting.GRAY)) // Use 'list'
            }
        }
    }

    // --- Energy Bar Display ---

    override fun isBarVisible(stack: ItemStack): Boolean {
        // Show bar only if energy is enabled and not full
        return config.pokeTrackerEnabled &&
                (stack.getOrDefault(CURRENT_ENERGY, config.pokeTrackerMaxEnergy) < config.pokeTrackerMaxEnergy)
    }

    override fun getBarWidth(stack: ItemStack): Int {
        val currentEnergy = stack.getOrDefault(CURRENT_ENERGY, config.pokeTrackerMaxEnergy)
        // Calculate durability bar width (13 pixels max)
        return ((currentEnergy.toDouble() / config.pokeTrackerMaxEnergy) * 13).toInt()
    }

    override fun getBarColor(stack: ItemStack): Int {
        // A nice cyan color for the energy bar
        return 0x00D1FF
    }
}

