package org.kotofey.realismns

import org.bukkit.Location
import org.bukkit.configuration.file.FileConfiguration

enum class BiomeCategory { HOT, WET, COLD, TEMPERATE }

class BiomeUtils(private val config: FileConfiguration) {

    fun category(location: Location): BiomeCategory {
        val block = location.block
        val temp = block.temperature
        val hum = block.humidity
        val hotMin = config.getDouble("biomes.thresholds.hot-min-temp", 1.5)
        val dryMax = config.getDouble("biomes.thresholds.dry-max-hum", 0.2)
        val coldMax = config.getDouble("biomes.thresholds.cold-max-temp", 0.3)
        val wetMin = config.getDouble("biomes.thresholds.wet-min-hum", 0.7)

        return when {
            temp >= hotMin && hum <= dryMax -> BiomeCategory.HOT
            temp <= coldMax -> BiomeCategory.COLD
            hum >= wetMin -> BiomeCategory.WET
            else -> BiomeCategory.TEMPERATE
        }
    }

    fun modifier(path: String, location: Location): Double {
        val cat = category(location).name.lowercase()
        return config.getDouble("biomes.modifiers.$path.$cat", 1.0)
    }

    fun witherChanceMultiplier(location: Location): Double = modifier("crop-wither", location)
    fun trampleStepsMultiplier(location: Location): Double = modifier("trample", location)
}
