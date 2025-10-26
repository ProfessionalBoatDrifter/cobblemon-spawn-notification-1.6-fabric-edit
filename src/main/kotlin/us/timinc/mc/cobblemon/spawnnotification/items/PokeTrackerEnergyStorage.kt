package us.timinc.mc.cobblemon.spawnnotification.items

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext
import team.reborn.energy.api.EnergyStorage
import us.timinc.mc.cobblemon.spawnnotification.SpawnNotification

class PokeTrackerEnergyStorage(
    private val context: ContainerItemContext
) : EnergyStorage {

    // Helper to get a read-only snapshot of the stack
    private fun getStackSnapshot(): net.minecraft.world.item.ItemStack {
        // .toStack() creates a new snapshot with components
        return context.itemVariant.toStack()
    }

    override fun insert(maxAmount: Long, transaction: TransactionContext): Long {
        val stack = getStackSnapshot()
        // Ensure we are operating on the correct item
        // Use getItem() instead of isOf() to avoid import conflicts with Cobblemon's TagKey extension
        if (stack.item != SpawnNotification.POKE_TRACKER_ITEM) return 0

        val currentEnergy = stack.getOrDefault(SpawnNotification.CURRENT_ENERGY, 0).toLong()
        val maxEnergy = SpawnNotification.config.pokeTrackerMaxEnergy.toLong()
        val availableSpace = (maxEnergy - currentEnergy).coerceAtLeast(0)
        val amountToInsert = maxAmount.coerceAtMost(availableSpace)

        if (amountToInsert > 0) {
            // Create a copy of the stack to modify
            val newStack = stack.copy()
            // Set the new energy value on the copy
            newStack.set(SpawnNotification.CURRENT_ENERGY, (currentEnergy + amountToInsert).toInt())
            // Create a new variant from the modified stack
            val newVariant = ItemVariant.of(newStack)

            // Try to exchange 1 of the old variant for 1 of the new variant
            // This is the correct way to update an item in a slot
            val exchangedCount = context.exchange(newVariant, 1, transaction)

            // If we successfully exchanged 1 item, return the amount of energy we inserted
            if (exchangedCount == 1L) {
                return amountToInsert
            }
        }

        return 0
    }

    override fun extract(maxAmount: Long, transaction: TransactionContext): Long {
        // We don't want to allow extracting energy, only inserting.
        return 0
    }

    override fun getAmount(): Long {
        val stack = getStackSnapshot()
        // Use getItem() instead of isOf() to avoid import conflicts
        if (stack.item != SpawnNotification.POKE_TRACKER_ITEM) return 0
        // Read the current energy value
        return stack.getOrDefault(SpawnNotification.CURRENT_ENERGY, 0).toLong()
    }

    override fun getCapacity(): Long {
        val stack = getStackSnapshot()
        // Use getItem() instead of isOf() to avoid import conflicts
        if (stack.item != SpawnNotification.POKE_TRACKER_ITEM) return 0
        // Read the max energy from config
        return SpawnNotification.config.pokeTrackerMaxEnergy.toLong()
    }

    override fun supportsInsertion(): Boolean {
        return true
    }

    override fun supportsExtraction(): Boolean {
        return false
    }
}

