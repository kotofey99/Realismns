package org.kotofey.realismns.backpack

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.inventory.PrepareSmithingEvent
import org.bukkit.inventory.CraftingInventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.SmithingTransformRecipe
import org.bukkit.plugin.java.JavaPlugin

class BackpackRecipes(private val plugin: JavaPlugin, private val manager: BackpackManager) : Listener {

    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        registerIron()
        registerDiamond()
        registerNetherite()
    }

    /** 8 железных блоков + сундук в центре */
    private fun registerIron() {
        val key = NamespacedKey(plugin, "backpack_iron")
        val recipe = ShapedRecipe(key, manager.templateBackpack(BackpackType.IRON))
        recipe.shape("III", "ICI", "III")
        recipe.setIngredient('I', Material.IRON_BLOCK)
        recipe.setIngredient('C', Material.CHEST)
        plugin.server.addRecipe(recipe)
    }

    /** 8 алмазов вокруг железного рюкзака */
    private fun registerDiamond() {
        val key = NamespacedKey(plugin, "backpack_diamond")
        val recipe = ShapedRecipe(key, manager.templateBackpack(BackpackType.DIAMOND))
        recipe.shape("DDD", "DBD", "DDD")
        recipe.setIngredient('D', Material.DIAMOND)
        recipe.setIngredient('B', Material.CHEST)
        plugin.server.addRecipe(recipe)
    }

    /** Кузнечный стол: алмазный рюкзак + незеритовый блок */
    private fun registerNetherite() {
        val key = NamespacedKey(plugin, "backpack_netherite")
        val recipe = SmithingTransformRecipe(
            key,
            manager.templateBackpack(BackpackType.NETHERITE),
            RecipeChoice.ExactChoice(ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE)),
            RecipeChoice.MaterialChoice(Material.CHEST),
            RecipeChoice.MaterialChoice(Material.NETHERITE_BLOCK),
        )
        plugin.server.addRecipe(recipe)
    }

    @EventHandler
    fun onPrepareCraft(event: PrepareItemCraftEvent) {
        val inv = event.inventory
        val result = manager.getType(inv.result) ?: return
        when (result) {
            BackpackType.IRON -> validateIron(inv)
            BackpackType.DIAMOND -> validateDiamond(inv)
            BackpackType.NETHERITE -> inv.result = ItemStack.empty()
        }
    }

    @EventHandler
    fun onPrepareSmithing(event: PrepareSmithingEvent) {
        val inv = event.inventory
        if (manager.getType(inv.result) != BackpackType.NETHERITE) return
        val base = inv.inputEquipment ?: run {
            inv.result = ItemStack.empty()
            return
        }
        if (manager.getType(base) != BackpackType.DIAMOND) {
            inv.result = ItemStack.empty()
        }
    }

    private fun validateIron(inv: CraftingInventory) {
        val m = inv.matrix
        val ok = listOf(0, 1, 2, 3, 5, 6, 7, 8).all { m[it]?.type == Material.IRON_BLOCK }
            && m[4]?.type == Material.CHEST
        if (!ok) inv.result = ItemStack.empty()
    }

    private fun validateDiamond(inv: CraftingInventory) {
        val m = inv.matrix
        val ok = m.count { it?.type == Material.DIAMOND } == 8
            && m[4]?.let { manager.getType(it) == BackpackType.IRON } == true
        if (!ok) inv.result = ItemStack.empty()
    }
}
