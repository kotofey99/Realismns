package org.kotofey.realismns.RottingFood

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

data class FridgeState(
    var running: Boolean,
    var fuel: Int,
    var burnProgress: Long,
    val food: Array<ItemStack?>,
) {
    fun hasFood(): Boolean = food.any { it != null && !it.type.isAir }
}

object FridgeStorage {
    private const val DATA_KEY = "fridge_data"

    fun load(upgrade: ItemStack): FridgeState {
        val raw = upgrade.itemMeta?.persistentDataContainer?.get(
            org.kotofey.realismns.backpack.UpgradeItems.fridgeDataKey,
            PersistentDataType.STRING,
        ) ?: return emptyState()
        val yaml = YamlConfiguration()
        yaml.loadFromString(raw)
        val food = Array(FridgeGui.FOOD_SLOTS) { i ->
            yaml.getItemStack("food.$i")?.takeIf { !it.type.isAir }
        }
        return FridgeState(
            running = yaml.getBoolean("running", false),
            fuel = yaml.getInt("fuel", 0).coerceAtLeast(0),
            burnProgress = yaml.getLong("burn-progress", 0).coerceAtLeast(0),
            food = food,
        )
    }

    fun save(upgrade: ItemStack, state: FridgeState) {
        val yaml = YamlConfiguration()
        yaml.set("running", state.running)
        yaml.set("fuel", state.fuel)
        yaml.set("burn-progress", state.burnProgress)
        state.food.forEachIndexed { i, stack ->
            if (stack != null && !stack.type.isAir) yaml.set("food.$i", stack) else yaml.set("food.$i", null)
        }
        upgrade.editMeta { meta ->
            meta.persistentDataContainer.set(
                org.kotofey.realismns.backpack.UpgradeItems.fridgeDataKey,
                PersistentDataType.STRING,
                yaml.saveToString(),
            )
        }
    }

    fun emptyState() = FridgeState(running = false, fuel = 0, burnProgress = 0, food = arrayOfNulls(FridgeGui.FOOD_SLOTS))
}
