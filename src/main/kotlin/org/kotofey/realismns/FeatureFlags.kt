package org.kotofey.realismns

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class FeatureFlags(plugin: JavaPlugin) {

    private val file = File(plugin.dataFolder, "features.yml")
    private var yaml = YamlConfiguration()

    fun load(plugin: JavaPlugin) {
        if (!file.exists()) {
            plugin.saveResource("features.yml", false)
        }
        yaml = YamlConfiguration.loadConfiguration(file)
    }

    val trampling: Boolean get() = yaml.getBoolean("trampling", true)
    val mudWaterBottle: Boolean get() = yaml.getBoolean("mud.water-bottle", true)
    val mudDrying: Boolean get() = yaml.getBoolean("mud.drying", true)
    val crops: Boolean get() = yaml.getBoolean("crops", true)
    val backpacks: Boolean get() = yaml.getBoolean("backpacks", true)
    val backpackUpgrades: Boolean get() = yaml.getBoolean("backpack-upgrades", true)
    val rottingFood: Boolean get() = yaml.getBoolean("rotting-food", true)
}
