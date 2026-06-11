package org.kotofey.realismns.RottingFood

import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

object RottingFoodKeys {
    lateinit var expiresTickKey: NamespacedKey
        private set
    lateinit var shelfLifeTicksKey: NamespacedKey
        private set
    lateinit var pausedKey: NamespacedKey
        private set
    lateinit var pausedRemainingTicksKey: NamespacedKey
        private set

    /** @deprecated legacy real-time ms */
    lateinit var expiresAtKey: NamespacedKey
        private set
    lateinit var shelfLifeMsKey: NamespacedKey
        private set

    fun init(plugin: JavaPlugin) {
        expiresTickKey = NamespacedKey(plugin, "food_expires_tick")
        shelfLifeTicksKey = NamespacedKey(plugin, "food_shelf_ticks")
        pausedKey = NamespacedKey(plugin, "food_paused")
        pausedRemainingTicksKey = NamespacedKey(plugin, "food_paused_ticks")
        expiresAtKey = NamespacedKey(plugin, "food_expires_at")
        shelfLifeMsKey = NamespacedKey(plugin, "food_shelf_life_ms")
    }
}
