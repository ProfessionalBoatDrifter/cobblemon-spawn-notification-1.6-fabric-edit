package us.timinc.mc.cobblemon.spawnnotification.items

import com.mojang.serialization.Codec
import net.minecraft.util.StringRepresentable

/**
 * Defines the different operational modes for the PokeTracker.
 */
enum class PokeTrackerMode(private val id: String) : StringRepresentable {
    VIEW_LIST("view_list"),
    ADD_POKEMON("add_pokemon"),
    PING_GLOW("ping_glow"),
    CLEAR_LIST("clear_list");

    /**
     * Cycles to the next mode in the enum.
     */
    fun next(): PokeTrackerMode {
        return when (this) {
            VIEW_LIST -> ADD_POKEMON
            ADD_POKEMON -> PING_GLOW
            PING_GLOW -> CLEAR_LIST
            CLEAR_LIST -> VIEW_LIST
        }
    }

    override fun getSerializedName(): String = this.id

    companion object {
        /**
         * Codec for serializing/deserializing the mode to/from NBT.
         */
        val CODEC: Codec<PokeTrackerMode> = StringRepresentable.fromEnum(PokeTrackerMode::values)
    }
}
