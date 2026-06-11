package org.kotofey.realismns.RottingFood

import org.bukkit.Material
import org.bukkit.World

object RottingFoodTime {
    const val TICKS_PER_GAME_HOUR = 1000L
    const val TICKS_PER_GAME_DAY = 24 * TICKS_PER_GAME_HOUR

    fun shelfLifeTicks(catalog: RottingFoodCatalog, hours: Long): Long {
        val adjusted = (hours.toDouble() / catalog.timeDivider).coerceAtLeast(0.25)
        return (adjusted * TICKS_PER_GAME_HOUR).toLong().coerceAtLeast(100L)
    }

    fun currentTick(world: World): Long = world.fullTime

    fun formatTicks(ticks: Long): String {
        val totalHours = ticks / TICKS_PER_GAME_HOUR
        val days = totalHours / 24
        val hours = totalHours % 24
        val minutes = (ticks % TICKS_PER_GAME_HOUR) * 60 / TICKS_PER_GAME_HOUR
        return when {
            days > 0 -> "${days}д ${hours}ч ${minutes}м"
            hours > 0 -> "${hours}ч ${minutes}м"
            else -> "${minutes.coerceAtLeast(1)}м"
        }
    }
}
