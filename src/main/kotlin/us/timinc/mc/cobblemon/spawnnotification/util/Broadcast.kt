package us.timinc.mc.cobblemon.spawnnotification.util

import com.cobblemon.mod.common.util.server
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

object Broadcast {
    /**
     * Broadcasts a message to all players on the server.
     *
     * @param message The message to send.
     */
    fun broadcastMessage(message: Component) {
        val serverInstance = server() ?: return
        serverInstance.sendSystemMessage(message)
        broadcastMessage(serverInstance.playerList.players, message)
    }

    /**
     * Broadcasts a message to all players in a given level.
     *
     * @param level The level to get the players from.
     * @param message The message to send.
     */
    fun broadcastMessage(level: ServerLevel, message: Component) {
        broadcastMessage(level.players(), message)
    }

    /**
     * Broadcasts a message to all players in a given list.
     *
     * @param players The list of players.
     * @param message The message to send.
     */
    fun broadcastMessage(players: List<ServerPlayer>, message: Component) {
        players.forEach { broadcastMessage(it, message) }
    }

    /**
     * Broadcasts a message to a particular player.
     *
     * @param player The player.
     * @param message The message to send.
     */
    public fun broadcastMessage(player: ServerPlayer, message: Component) {
        player.sendSystemMessage(message)
    }
}