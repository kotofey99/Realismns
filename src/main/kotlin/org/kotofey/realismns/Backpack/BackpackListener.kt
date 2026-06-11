package org.kotofey.realismns.backpack

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.inventory.SmithItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.CraftingInventory
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.SmithingInventory
import org.bukkit.persistence.PersistentDataType
import org.kotofey.realismns.RottingFood.FridgeGui
import org.kotofey.realismns.RottingFood.RottingFoodCatalog

class BackpackListener(
    private val manager: BackpackManager,
    private val upgradeMechanics: UpgradeMechanics,
    private val rottingCatalog: RottingFoodCatalog?,
) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBackpackOpen(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.player.isSneaking) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.item ?: return
        if (!manager.isBackpack(item)) return

        event.isCancelled = true
        manager.openBackpack(event.player, item)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val fridgeHolder = event.inventory.holder as? FridgeGui
        if (fridgeHolder != null) {
            val player = event.whoClicked as? Player ?: return
            if (event.rawSlot >= event.inventory.size) {
                if (event.isShiftClick && event.currentItem != null) {
                    event.isCancelled = true
                    fridgeHolder.handleShiftFromPlayer(event.currentItem!!, player, event.rawSlot)
                }
                return
            }
            event.isCancelled = true
            fridgeHolder.handleClick(event.rawSlot, event.currentItem, event.cursor ?: ItemStack.empty(), player)
            return
        }

        val controlHolder = event.inventory.holder as? UpgradeControlGui
        if (controlHolder != null) {
            event.isCancelled = true
            if (event.rawSlot < event.inventory.size) {
                controlHolder.handleClick(event.rawSlot, event.currentItem)
            }
            return
        }

        val holder = event.inventory.holder as? BackpackGui ?: return
        val player = event.whoClicked as? Player ?: return
        val rawSlot = event.rawSlot
        val topSize = event.view.topInventory.size

        if (holder.mode == BackpackGui.Mode.WORKSTATION) {
            event.isCancelled = true
            if (rawSlot < topSize) handleWorkstationClick(holder, event.currentItem)
            return
        }

        if (rawSlot >= topSize) {
            if (event.isShiftClick && (manager.isBackpack(event.currentItem) || manager.isPlaceholder(event.currentItem))) {
                event.isCancelled = true
            }
            return
        }

        when {
            holder.isUpgradeSlot(rawSlot) -> handleUpgradeSlotClick(event, holder, player, rawSlot)
            holder.isFooterSlot(rawSlot) -> {
                event.isCancelled = true
                handleFooterClick(holder, event.currentItem)
            }
            holder.isStorageSlot(rawSlot) -> {
                if (wouldInsertBackpack(event) || involvesPlaceholder(event)) {
                    event.isCancelled = true
                }
            }
            else -> event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.inventory.holder is FridgeGui) {
            event.isCancelled = true
            return
        }
        val holder = event.inventory.holder as? BackpackGui ?: return
        if (holder.mode == BackpackGui.Mode.WORKSTATION) {
            event.isCancelled = true
            return
        }
        for (slot in event.rawSlots) {
            if (slot >= event.view.topInventory.size) continue
            if (!holder.isStorageSlot(slot) && !holder.isUpgradeSlot(slot)) {
                event.isCancelled = true
                return
            }
        }
        if (event.newItems.values.any { manager.isBackpack(it) || manager.isPlaceholder(it) }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        (event.inventory.holder as? FridgeGui)?.saveAndClose()

        val holder = event.inventory.holder as? BackpackGui ?: return
        manager.saveFromGui(holder)
        val player = event.player as? Player ?: return
        if (manager.isPlaceholder(player.itemOnCursor)) {
            player.setItemOnCursor(null)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPrepareCraftDowngradeCheck(event: PrepareItemCraftEvent) {
        val inv = event.inventory
        val resultType = manager.getType(inv.result) ?: return
        if (resultType == BackpackType.IRON || resultType == BackpackType.NETHERITE) return

        val center = inv.matrix.getOrNull(4) ?: return
        val centerType = manager.getType(center) ?: run {
            inv.result = ItemStack.empty()
            return
        }
        if (centerType.ordinal >= resultType.ordinal) {
            inv.result = ItemStack.empty()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraftComplete(event: CraftItemEvent) {
        val result = event.currentItem ?: return
        if (manager.getType(result) == null) return
        finalizeUpgrade(event.inventory, result)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSmithComplete(event: SmithItemEvent) {
        val result = event.currentItem ?: return
        if (manager.getType(result) == null) return
        finalizeUpgrade(event.inventory, result)
    }

    private fun handleWorkstationClick(gui: BackpackGui, item: ItemStack?) {
        val action = item?.itemMeta?.persistentDataContainer
            ?.get(manager.guiActionKey, PersistentDataType.STRING) ?: return
        when (action) {
            "ws_craft" -> upgradeMechanics.openCrafting(gui)
            "ws_furnace" -> upgradeMechanics.openFurnace(gui)
            "ws_back" -> gui.openStorageFromWorkstation()
        }
    }

    private fun handleUpgradeSlotClick(event: InventoryClickEvent, gui: BackpackGui, player: Player, rawSlot: Int) {
        event.isCancelled = true
        val index = gui.upgradeSlotIndex(rawSlot)
        val current = event.currentItem
        val cursor = event.cursor

        if (UpgradeItems.getType(current) == UpgradeType.FRIDGE && rottingCatalog != null && cursor.type.isAir) {
            val openFridgeClick = event.isShiftClick
                || event.click == ClickType.RIGHT
                || (event.click == ClickType.LEFT && BedrockUtils.isBedrock(player))
            if (openFridgeClick) {
                openFridge(gui, rawSlot)
                return
            }
        }

        if (event.isShiftClick && UpgradeItems.getType(current)?.hasFilter == true) {
            openUpgradeFilter(gui, current!!, rawSlot)
            return
        }

        val toggleClick = event.click == ClickType.RIGHT
            || (event.click == ClickType.LEFT && BedrockUtils.isBedrock(player) && UpgradeItems.getType(current) != UpgradeType.FRIDGE)
        if (toggleClick && cursor.type.isAir && UpgradeItems.isInstallable(current)) {
            toggleUpgrade(gui, rawSlot)
            return
        }

        if (UpgradeItems.isInstallable(current) && cursor.type.isAir && event.click == ClickType.LEFT) {
            takeUpgrade(gui, player, rawSlot, index, current!!)
            return
        }

        if (manager.isPlaceholder(current)) {
            if (UpgradeItems.isInstallable(cursor) && !cursor.type.isAir) {
                placeUpgrade(gui, player, rawSlot, index, cursor)
            }
            return
        }

        if (UpgradeItems.isInstallable(current)) {
            if (UpgradeItems.isInstallable(cursor)) {
                swapUpgrades(gui, player, rawSlot, index, current!!, cursor)
            }
        }
    }

    private fun placeUpgrade(gui: BackpackGui, player: Player, rawSlot: Int, index: Int, cursor: ItemStack) {
        val upgrade = cursor.clone().also { it.amount = 1 }
        consumeOneFromCursor(player, cursor)
        manager.getUpgrades(gui.backpackId)?.set(index, upgrade)
        gui.backingInventory.setItem(rawSlot, UpgradeItems.withDisplay(upgrade, index + 1))
    }

    private fun takeUpgrade(gui: BackpackGui, player: Player, rawSlot: Int, index: Int, current: ItemStack) {
        player.setItemOnCursor(current.clone())
        manager.getUpgrades(gui.backpackId)?.set(index, null)
        gui.refreshUpgradeSlot(rawSlot)
    }

    private fun swapUpgrades(gui: BackpackGui, player: Player, rawSlot: Int, index: Int, current: ItemStack, cursor: ItemStack) {
        val incoming = cursor.clone().also { it.amount = 1 }
        player.setItemOnCursor(current.clone())
        manager.getUpgrades(gui.backpackId)?.set(index, incoming)
        gui.backingInventory.setItem(rawSlot, UpgradeItems.withDisplay(incoming, index + 1))
        consumeOneFromCursor(player, cursor)
    }

    private fun consumeOneFromCursor(player: Player, cursor: ItemStack) {
        if (cursor.amount <= 1) player.setItemOnCursor(null) else {
            cursor.amount -= 1
            player.setItemOnCursor(cursor)
        }
    }

    private fun finalizeUpgrade(inventory: org.bukkit.inventory.Inventory, result: ItemStack) {
        val resultType = manager.getType(result) ?: return
        val sourceBackpack = when (inventory) {
            is CraftingInventory -> inventory.matrix.firstOrNull { stack ->
                stack != null && manager.isBackpack(stack) && manager.getType(stack) != resultType
            }
            is SmithingInventory -> inventory.inputEquipment?.takeIf {
                manager.isBackpack(it) && manager.getType(it) != resultType
            }
            else -> null
        }
        val newId = manager.ensureId(result)
        if (sourceBackpack != null) {
            val oldId = manager.getId(sourceBackpack) ?: manager.ensureId(sourceBackpack)
            manager.transferStorage(oldId, newId, resultType)
        } else {
            manager.ensureStorage(newId, resultType)
        }
    }

    private fun toggleUpgrade(gui: BackpackGui, rawSlot: Int) {
        val index = gui.upgradeSlotIndex(rawSlot)
        val stored = manager.getUpgrades(gui.backpackId)?.getOrNull(index)?.clone() ?: return
        UpgradeItems.toggleEnabled(stored)
        manager.getUpgrades(gui.backpackId)?.set(index, stored)
        manager.refreshBackpack(gui)
    }

    private fun openFridge(gui: BackpackGui, rawSlot: Int) {
        val catalog = rottingCatalog ?: return
        val index = gui.upgradeSlotIndex(rawSlot)
        val upgrade = manager.getUpgrades(gui.backpackId)?.getOrNull(index)?.clone() ?: return
        manager.saveFromGui(gui)
        FridgeGui(manager, catalog, gui, index, upgrade).open()
    }

    private fun openUpgradeFilter(gui: BackpackGui, upgrade: ItemStack, rawSlot: Int) {
        manager.saveFromGui(gui)
        val slotIndex = gui.upgradeSlotIndex(rawSlot)
        upgradeMechanics.openFilter(gui.player, upgrade) { saved ->
            manager.getUpgrades(gui.backpackId)?.let { upgrades ->
                if (slotIndex in upgrades.indices) upgrades[slotIndex] = saved
            }
            manager.refreshBackpack(gui)
        }
    }

    private fun handleFooterClick(gui: BackpackGui, item: ItemStack?) {
        when (item?.itemMeta?.persistentDataContainer?.get(manager.guiActionKey, PersistentDataType.STRING)) {
            "backpack_prev" -> gui.openPage(gui.page - 1)
            "backpack_next" -> gui.openPage(gui.page + 1)
            "backpack_workshop" -> gui.openWorkstation()
            "backpack_upgrades" -> {
                manager.saveFromGui(gui)
                UpgradeControlGui(manager, gui, upgradeMechanics, rottingCatalog).open()
            }
        }
    }

    private fun involvesPlaceholder(event: InventoryClickEvent): Boolean =
        manager.isPlaceholder(event.currentItem) || manager.isPlaceholder(event.cursor)

    private fun wouldInsertBackpack(event: InventoryClickEvent): Boolean {
        val cursor = event.cursor
        if (manager.isBackpack(cursor)) return true
        if (event.isShiftClick && manager.isBackpack(event.currentItem)) return true
        if (event.action == InventoryAction.HOTBAR_SWAP) {
            if (manager.isBackpack(event.view.bottomInventory.getItem(event.hotbarButton))) return true
        }
        if (event.click == ClickType.NUMBER_KEY) {
            if (manager.isBackpack(event.view.bottomInventory.getItem(event.hotbarButton))) return true
        }
        return false
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        (event.player.openInventory.topInventory.holder as? FridgeGui)?.saveAndClose()
        manager.save()
    }
}
