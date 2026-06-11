package org.kotofey.realismns.backpack

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.plugin.java.JavaPlugin

class UpgradeRecipes(private val plugin: JavaPlugin) : Listener {

    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        val key = NamespacedKey(plugin, "upgrade_base")
        val recipe = ShapedRecipe(key, UpgradeItems.create(UpgradeType.UPGRADE_BASE))
        recipe.shape("SIS", "ILI", "SIS")
        recipe.setIngredient('S', Material.STRING)
        recipe.setIngredient('I', Material.IRON_INGOT)
        recipe.setIngredient('L', Material.LEATHER)
        plugin.server.addRecipe(recipe)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPrepareCraft(event: PrepareItemCraftEvent) {
        val matrix = event.inventory.matrix
        val matched = when {
            validateBase(matrix) -> UpgradeType.UPGRADE_BASE
            validatePickup(matrix) -> UpgradeType.PICKUP
            validateMagnet(matrix) -> UpgradeType.MAGNET
            validateAdvMagnet(matrix) -> UpgradeType.ADVANCED_MAGNET
            validateAdvPickup(matrix) -> UpgradeType.ADVANCED_PICKUP
            validateWorkstation(matrix) -> UpgradeType.WORKSTATION
            validateUnload(matrix) -> UpgradeType.UNLOAD
            validateFridge(matrix) -> UpgradeType.FRIDGE
            else -> null
        }
        if (matched != null) {
            event.inventory.result = UpgradeItems.create(matched)
        }
    }

    private fun validateBase(matrix: Array<ItemStack?>): Boolean {
        return matrix.getOrNull(0)?.type == Material.STRING
            && matrix.getOrNull(1)?.type == Material.IRON_INGOT
            && matrix.getOrNull(2)?.type == Material.STRING
            && matrix.getOrNull(3)?.type == Material.IRON_INGOT
            && matrix.getOrNull(4)?.type == Material.LEATHER
            && matrix.getOrNull(5)?.type == Material.IRON_INGOT
            && matrix.getOrNull(6)?.type == Material.STRING
            && matrix.getOrNull(7)?.type == Material.IRON_INGOT
            && matrix.getOrNull(8)?.type == Material.STRING
    }

    /**  P / SBS / RRR */
    private fun validatePickup(matrix: Array<ItemStack?>): Boolean {
        return isEmpty(matrix, 0, 2)
            && matrix.getOrNull(1)?.type == Material.STICKY_PISTON
            && matrix.getOrNull(3)?.type == Material.STRING
            && UpgradeItems.isUpgrade(matrix.getOrNull(4), UpgradeType.UPGRADE_BASE)
            && matrix.getOrNull(5)?.type == Material.STRING
            && listOf(6, 7, 8).all { matrix.getOrNull(it)?.type == Material.REDSTONE }
    }

    /** EIE / IPI / R L */
    private fun validateMagnet(matrix: Array<ItemStack?>): Boolean {
        return matrix.getOrNull(0)?.type == Material.ENDER_PEARL
            && matrix.getOrNull(1)?.type == Material.IRON_INGOT
            && matrix.getOrNull(2)?.type == Material.ENDER_PEARL
            && matrix.getOrNull(3)?.type == Material.IRON_INGOT
            && UpgradeItems.isUpgrade(matrix.getOrNull(4), UpgradeType.PICKUP)
            && matrix.getOrNull(5)?.type == Material.IRON_INGOT
            && matrix.getOrNull(6)?.type == Material.REDSTONE
            && isEmpty(matrix, 7)
            && matrix.getOrNull(8)?.type == Material.LAPIS_LAZULI
    }

    /**  D / GMG / RRR */
    private fun validateAdvMagnet(matrix: Array<ItemStack?>): Boolean {
        return isEmpty(matrix, 0, 2)
            && matrix.getOrNull(1)?.type == Material.DIAMOND
            && matrix.getOrNull(3)?.type == Material.GOLD_INGOT
            && UpgradeItems.isUpgrade(matrix.getOrNull(4), UpgradeType.MAGNET)
            && matrix.getOrNull(5)?.type == Material.GOLD_INGOT
            && listOf(6, 7, 8).all { matrix.getOrNull(it)?.type == Material.REDSTONE }
    }

    /**  D / GPG / RRR */
    private fun validateAdvPickup(matrix: Array<ItemStack?>): Boolean {
        return isEmpty(matrix, 0, 2)
            && matrix.getOrNull(1)?.type == Material.DIAMOND
            && matrix.getOrNull(3)?.type == Material.GOLD_INGOT
            && UpgradeItems.isUpgrade(matrix.getOrNull(4), UpgradeType.PICKUP)
            && matrix.getOrNull(5)?.type == Material.GOLD_INGOT
            && listOf(6, 7, 8).all { matrix.getOrNull(it)?.type == Material.REDSTONE }
    }

    /**  P / B / H */
    private fun validateUnload(matrix: Array<ItemStack?>): Boolean {
        return isEmpty(matrix, 0, 2, 3, 5, 6, 8)
            && matrix.getOrNull(1)?.type == Material.PAPER
            && UpgradeItems.isUpgrade(matrix.getOrNull(4), UpgradeType.UPGRADE_BASE)
            && matrix.getOrNull(7)?.type == Material.HOPPER
    }

    /** SSS / SBS / RRR */
    private fun validateFridge(matrix: Array<ItemStack?>): Boolean {
        return listOf(0, 1, 2, 3, 5).all { isFridgeShell(matrix.getOrNull(it)?.type) }
            && UpgradeItems.isUpgrade(matrix.getOrNull(4), UpgradeType.UPGRADE_BASE)
            && listOf(6, 7, 8).all { matrix.getOrNull(it)?.type == Material.REDSTONE }
    }

    /** CCC / WBF / RRR */
    private fun validateWorkstation(matrix: Array<ItemStack?>): Boolean {
        return listOf(0, 1, 2).all { matrix.getOrNull(it)?.type == Material.COAL }
            && matrix.getOrNull(3)?.type == Material.CRAFTING_TABLE
            && UpgradeItems.isUpgrade(matrix.getOrNull(4), UpgradeType.UPGRADE_BASE)
            && matrix.getOrNull(5)?.type == Material.FURNACE
            && listOf(6, 7, 8).all { matrix.getOrNull(it)?.type == Material.REDSTONE }
    }

    private fun isFridgeShell(type: Material?): Boolean =
        type == Material.SNOW_BLOCK || type == Material.IRON_BLOCK

    private fun isEmpty(matrix: Array<ItemStack?>, vararg slots: Int): Boolean =
        slots.all { matrix.getOrNull(it).isAirOrNull() }

    private fun ItemStack?.isAirOrNull(): Boolean = this == null || type.isAir
}
