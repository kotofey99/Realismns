package org.kotofey.realismns.backpack

import org.bukkit.entity.Player

object BedrockUtils {
    fun isBedrock(player: Player): Boolean {
        return runCatching {
            val floodgate = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
            val getInstance = floodgate.getMethod("getInstance")
            val instance = getInstance.invoke(null)
            val isFloodgate = floodgate.getMethod("isFloodgatePlayer", java.util.UUID::class.java)
            isFloodgate.invoke(instance, player.uniqueId) as Boolean
        }.getOrDefault(false)
    }
}
