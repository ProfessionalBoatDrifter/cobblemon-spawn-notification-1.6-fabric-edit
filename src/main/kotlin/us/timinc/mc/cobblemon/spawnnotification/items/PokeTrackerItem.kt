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
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.PING_COOLDOWN
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.TRACKER_MODE
import us.timinc.mc.cobblemon.spawnnotification.util.isReallyWild

class PokeTrackerItem(properties: Properties) : Item(properties) {
    // No companion object needed for keys anymore, they are in SpawnNotification

    // getDefaultInstance override is no longer needed
    // Default values are set in SpawnNotification.kt when registering the item

    /**
     * Handles energy drain once per second (every 20 ticks).
     */
    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        if (level.isClientSide || entity !is Player) return

        // --- Ping Logic (runs every tick) ---
        if (config.pokeTrackerPingingEnabled) {
            handlePinging(stack, level, entity)
        }

        // --- Energy Drain Logic (runs once per second) ---
        if (level.gameTime % 20 == 0L) {
            if (config.pokeTrackerEnabled && config.pokeTrackerEnergyEnabled) {
                handleEnergyDrain(stack, entity)
            }
        }
    }

    private fun handlePinging(stack: ItemStack, level: Level, player: Player) {
        // 1. Handle Cooldown
        var cooldown = stack.getOrDefault(PING_COOLDOWN, 0)
        if (cooldown > 0) {
            stack.set(PING_COOLDOWN, cooldown - 1)
            return
        }

        // 2. Check if active (energy + tracking list)
        if (config.pokeTrackerEnergyEnabled) {
            val currentEnergy = stack.getOrDefault(CURRENT_ENERGY, 0)
            if (currentEnergy <= 0) return // Out of power
        }

        val trackedList = stack.get(TRACKED_POKEMON) ?: return
        if (trackedList.isEmpty()) return // Not tracking anything

        // 3. We are "actively searching". Find closest tracked Pokemon.
        val searchBox = player.boundingBox.inflate(config.pokeTrackerMaxPingDistance)
        val nearbyPokemon = level.getEntitiesOfClass(PokemonEntity::class.java, searchBox) {
            it.pokemon.isReallyWild() && it.pokemon.species.resourceIdentifier.toString() in trackedList
        }
        val closestPokemon = nearbyPokemon.minByOrNull { it.distanceToSqr(player) }

        // 4. Get the sound event from config
        val pingSoundEvent = SoundEvent.createVariableRangeEvent(ResourceLocation.parse(config.pokeTrackerPingSound))

        // 5. Play sound based on result
        if (closestPokemon == null) {
            // --- No Result ---
            level.playSound(null, player.blockPosition(), pingSoundEvent, SoundSource.PLAYERS, 1.0f, config.pokeTrackerNoResultPitch)
            stack.set(PING_COOLDOWN, config.pokeTrackerNoResultPingInterval)
        } else {
            // --- Result Found ---
            val distance = player.distanceTo(closestPokemon).toDouble()
            // Calculate 1.0 (close) to 0.0 (far)
            val distPercent = 1.0 - (distance / config.pokeTrackerMaxPingDistance).coerceIn(0.0, 1.0)

            // Lerp (Linear Interpolation) to find pitch and interval
            val pitch = Mth.lerp(distPercent, config.pokeTrackerMinPitch.toDouble(), config.pokeTrackerMaxPitch.toDouble()).toFloat()
            val interval = Mth.lerp(distPercent, config.pokeTrackerMinPingInterval.toDouble(), config.pokeTrackerMaxPingInterval.toDouble()).toInt()

            level.playSound(null, player.blockPosition(), pingSoundEvent, SoundSource.PLAYERS, 1.0f, pitch)
            stack.set(PING_COOLDOWN, interval)
        }
    }

    private fun handleEnergyDrain(stack: ItemStack, player: Player) {
        // (This is the logic that was previously inside the inventoryTick's 20-tick check)
        var currentEnergy = stack.getOrDefault(CURRENT_ENERGY, config.pokeTrackerMaxEnergy)

        if (currentEnergy <= 0) {
            if (!stack.has(NOTIFY_NO_ENERGY)) {
                player.sendSystemMessage(
                    Component.translatable("spawn_notification.tracker.no_energy")
                        .withStyle(ChatFormatting.RED)
                )
                stack.set(NOTIFY_NO_ENERGY, Unit.INSTANCE)
            }
            return
        } else {
            stack.remove(NOTIFY_NO_ENERGY)
        }

        val trackedList = stack.getOrDefault(TRACKED_POKEMON, emptyList())
        val drain = if (trackedList.isEmpty()) {
            config.pokeTrackerIdleEnergyDrainPerSecond
        } else {
            config.pokeTrackerIdleEnergyDrainPerSecond + config.pokeTrackerActiveEnergyDrainPerSecond
        }

        currentEnergy = (currentEnergy - drain).coerceAtLeast(0)
        stack.set(CURRENT_ENERGY, currentEnergy)
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

        // Check if item is in the correct mode
        val currentMode = stack.getOrDefault(TRACKER_MODE, PokeTrackerMode.VIEW_LIST)
        if (currentMode != PokeTrackerMode.ADD_POKEMON) {
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
     * Shift-click: Cycles the tracker's mode.
     * Normal click: Performs the action for the current mode.
     */
    override fun use(level: Level, player: Player, interactionHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(interactionHand)
        if (level.isClientSide) return InteractionResultHolder.success(stack)

        val currentMode = stack.getOrDefault(TRACKER_MODE, PokeTrackerMode.VIEW_LIST)

        if (player.isShiftKeyDown) {
            // --- Cycle Mode (Temporary mechanic) ---
            // This will be replaced by sneak + scroll later
            val newMode = currentMode.next()
            stack.set(TRACKER_MODE, newMode)
            player.sendSystemMessage(
                Component.translatable(
                    "spawn_notification.tracker.mode_changed",
                    Component.translatable("spawn_notification.tracker.mode.${newMode.name.lowercase()}")
                ).withStyle(ChatFormatting.YELLOW)
            )
        } else {
            // --- Perform Action Based on Mode ---
            when (currentMode) {
                PokeTrackerMode.VIEW_LIST -> {
                    // Display the list
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
                            val species =
                                com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByIdentifier(
                                    ResourceLocation.parse(speciesId)
                                )
                            val name =
                                species?.translatedName ?: Component.literal(speciesId).withStyle(ChatFormatting.RED)
                            player.sendSystemMessage(Component.literal("- ").append(name))
                        }
                    }
                }

                PokeTrackerMode.CLEAR_LIST -> {
                    // Clear the list by setting it to an empty list
                    stack.set(TRACKED_POKEMON, emptyList())
                    player.sendSystemMessage(
                        Component.translatable("spawn_notification.tracker.cleared")
                            .withStyle(ChatFormatting.GRAY)
                    )
                }

                PokeTrackerMode.ADD_POKEMON -> {
                    // Send instructional message
                    player.sendSystemMessage(
                        Component.translatable("spawn_notification.tracker.mode.add_pokemon.message")
                            .withStyle(ChatFormatting.GREEN)
                    )
                }

                PokeTrackerMode.PING_GLOW -> {
                    // Placeholder for new feature
                    player.sendSystemMessage(
                        Component.translatable("spawn_notification.tracker.mode.ping_glow.message")
                            .withStyle(ChatFormatting.AQUA)
                    )
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

        // Show current mode
        val currentMode = stack.getOrDefault(TRACKER_MODE, PokeTrackerMode.VIEW_LIST)
        list.add(
            Component.translatable(
                "spawn_notification.tracker.tooltip.mode",
                Component.translatable("spawn_notification.tracker.mode.${currentMode.name.lowercase()}")
                    .withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.GRAY)
        )


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
