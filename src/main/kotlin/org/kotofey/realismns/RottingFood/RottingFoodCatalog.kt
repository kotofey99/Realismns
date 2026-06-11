package org.kotofey.realismns.RottingFood

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.EnumMap

class RottingFoodCatalog(private val plugin: JavaPlugin) {

    private val file = File(plugin.dataFolder, "rotting-food.yml")
    private var yaml = YamlConfiguration()
    private val hours = EnumMap<Material, Long>(Material::class.java)

    private var poisonDuration = 10
    private var poisonLevel = 1
    private var hungerDuration = 20
    private var hungerLevel = 2
    private var spoiledMessage = "Вы съели испорченную еду!"
    var checkIntervalSeconds = 30
        private set
    var timeDivider = 4
        private set
    var fridgeCoalBurnTicks = RottingFoodTime.TICKS_PER_GAME_DAY
        private set

    fun load() {
        if (!file.exists()) {
            plugin.saveResource("rotting-food.yml", false)
        }
        yaml = YamlConfiguration.loadConfiguration(file)
        hours.clear()

        val section = yaml.getConfigurationSection("shelf-life-hours") ?: return
        for (key in section.getKeys(false)) {
            val material = runCatching { Material.valueOf(key.uppercase()) }.getOrNull() ?: continue
            val h = section.getLong(key)
            if (h > 0) hours[material] = h
        }

        checkIntervalSeconds = yaml.getInt("check-interval-seconds", 30).coerceAtLeast(5)
        timeDivider = yaml.getInt("time-divider", 4).coerceIn(1, 16)
        fridgeCoalBurnTicks = yaml.getLong("fridge.coal-burn-ticks", RottingFoodTime.TICKS_PER_GAME_DAY)
            .coerceAtLeast(RottingFoodTime.TICKS_PER_GAME_HOUR)
        poisonDuration = yaml.getInt("spoiled.poison.duration-seconds", 10)
        poisonLevel = yaml.getInt("spoiled.poison.level", 1)
        hungerDuration = yaml.getInt("spoiled.hunger.duration-seconds", 20)
        hungerLevel = yaml.getInt("spoiled.hunger.level", 2)
        spoiledMessage = yaml.getString("spoiled.message", spoiledMessage) ?: spoiledMessage
    }

    fun shelfLifeHours(material: Material): Long? = hours[material]

    fun shelfLifeTicks(material: Material): Long? =
        shelfLifeHours(material)?.let { RottingFoodTime.shelfLifeTicks(this, it) }

    fun isPerishable(material: Material): Boolean = material in hours

    fun spoiledPoisonDuration(): Int = poisonDuration
    fun spoiledPoisonLevel(): Int = poisonLevel
    fun spoiledHungerDuration(): Int = hungerDuration
    fun spoiledHungerLevel(): Int = hungerLevel
    fun spoiledMessage(): String = spoiledMessage
}
