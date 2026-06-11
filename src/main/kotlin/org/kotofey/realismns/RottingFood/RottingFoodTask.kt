package org.kotofey.realismns.RottingFood

import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.kotofey.realismns.backpack.BackpackManager

class RottingFoodTask(
    private val plugin: JavaPlugin,
    private val catalog: RottingFoodCatalog,
    private val backpackManager: BackpackManager?,
) {

    private var task: BukkitTask? = null
    private val lastWorldTick = mutableMapOf<World, Long>()

    fun start() {
        val interval = catalog.checkIntervalSeconds.coerceAtLeast(5) * 20L
        task?.cancel()
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { tick() }, interval, interval)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun tick() {
        val defaultWorld = plugin.server.worlds.firstOrNull { it.environment == World.Environment.NORMAL }
            ?: plugin.server.worlds.firstOrNull() ?: return

        for (player in plugin.server.onlinePlayers) {
            refreshPlayer(player, player.world)
        }

        val now = defaultWorld.fullTime
        val last = lastWorldTick[defaultWorld] ?: now
        val delta = (now - last).coerceAtLeast(0)
        lastWorldTick[defaultWorld] = now

        backpackManager?.forEachUpgrade { backpackId, upgrade ->
            FridgeMechanics.tickUpgrade(upgrade, backpackId, backpackManager, defaultWorld, catalog, delta.coerceAtLeast(20))
        }
    }

    private fun refreshPlayer(player: Player, world: World) {
        refreshInventory(player.inventory.contents, world)
        refreshInventory(player.inventory.extraContents, world)
    }

    private fun refreshInventory(stacks: Array<ItemStack?>, world: World) {
        for (stack in stacks) {
            if (stack == null || stack.type.isAir) continue
            if (!catalog.isPerishable(stack.type)) continue
            RottingFoodItem.refreshIfNeeded(stack, catalog, world)
        }
    }
}
