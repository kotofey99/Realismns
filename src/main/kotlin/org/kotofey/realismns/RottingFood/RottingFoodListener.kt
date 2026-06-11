package org.kotofey.realismns.RottingFood

import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockCookEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class RottingFoodListener(
    private val plugin: JavaPlugin,
    private val catalog: RottingFoodCatalog,
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemSpawn(event: ItemSpawnEvent) {
        stampEntityItem(event.entity)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        val who = event.whoClicked as? Player ?: return
        val result = event.currentItem ?: return
        stampStack(result, who.world)
        for (item in who.inventory.contents) {
            if (item != null) refreshStack(item, who.world)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSmelt(event: FurnaceSmeltEvent) {
        val world = event.block.world
        stampStack(event.result, world)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCampfireCook(event: BlockCookEvent) {
        stampStack(event.result, event.block.world)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        stampEntityItem(event.item)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onConsume(event: PlayerItemConsumeEvent) {
        val item = event.item
        if (!catalog.isPerishable(item.type)) return
        val world = event.player.world
        RottingFoodItem.refreshIfNeeded(item, catalog, world)
        if (!RottingFoodItem.isExpired(item, world)) return

        event.isCancelled = true
        RottingFoodEffects.applySpoiled(event.player, catalog)
        consumeFromHand(event.player, event.hand, item.type)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val who = event.whoClicked as? Player ?: return
        val world = who.world
        val cursor = event.cursor
        val current = event.currentItem
        if (cursor.type.isAir || current == null || current.type.isAir) return
        if (cursor.type != current.type || !cursor.isSimilar(current)) return
        if (!catalog.isPerishable(cursor.type)) return

        val cursorExpiry = RottingFoodItem.getExpiresTick(cursor) ?: RottingFoodItem.getPausedRemainingTicks(cursor)
        val currentExpiry = RottingFoodItem.getExpiresTick(current) ?: RottingFoodItem.getPausedRemainingTicks(current)
        if (cursorExpiry == null && currentExpiry == null) return

        if (event.action != InventoryAction.PLACE_ALL &&
            event.action != InventoryAction.PLACE_ONE &&
            event.action != InventoryAction.PLACE_SOME &&
            event.action != InventoryAction.SWAP_WITH_CURSOR
        ) return

        plugin.server.scheduler.runTask(plugin, Runnable {
            val slotItem = event.view.getItem(event.rawSlot) ?: return@Runnable
            if (slotItem.type != cursor.type) return@Runnable
            RottingFoodItem.mergeExpiry(slotItem, cursor, world)
            RottingFoodItem.refreshLore(slotItem, catalog, world)
            val onCursor = event.view.cursor
            if (!onCursor.type.isAir && onCursor.type == cursor.type && onCursor.amount > 0) {
                RottingFoodItem.mergeExpiry(onCursor, slotItem, world)
                RottingFoodItem.refreshLore(onCursor, catalog, world)
            }
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClickMonitor(event: InventoryClickEvent) {
        val who = event.whoClicked as? Player ?: return
        plugin.server.scheduler.runTask(plugin, Runnable {
            refreshStack(event.view.cursor, who.world)
            event.view.getItem(event.rawSlot)?.let { refreshStack(it, who.world) }
        })
    }

    private fun consumeFromHand(player: Player, hand: EquipmentSlot, type: org.bukkit.Material) {
        val stack = when (hand) {
            EquipmentSlot.HAND -> player.inventory.itemInMainHand
            EquipmentSlot.OFF_HAND -> player.inventory.itemInOffHand
            else -> return
        }
        if (stack.type != type) return
        if (stack.amount <= 1) {
            when (hand) {
                EquipmentSlot.HAND -> player.inventory.setItemInMainHand(null)
                EquipmentSlot.OFF_HAND -> player.inventory.setItemInOffHand(null)
                else -> {}
            }
        } else {
            stack.amount -= 1
        }
    }

    private fun stampEntityItem(entity: Item) {
        val stack = entity.itemStack
        if (stampStack(stack, entity.world)) entity.itemStack = stack
    }

    private fun stampStack(stack: ItemStack, world: org.bukkit.World): Boolean {
        if (!catalog.isPerishable(stack.type)) return false
        if (RottingFoodItem.getExpiresTick(stack) != null || RottingFoodItem.isPaused(stack)) {
            RottingFoodItem.refreshLore(stack, catalog, world)
            return true
        }
        RottingFoodItem.stamp(stack, catalog, world)
        return true
    }

    private fun refreshStack(stack: ItemStack, world: org.bukkit.World) {
        RottingFoodItem.refreshIfNeeded(stack, catalog, world)
    }
}
