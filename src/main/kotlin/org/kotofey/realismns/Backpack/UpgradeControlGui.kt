package org.kotofey.realismns.backpack

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

import org.kotofey.realismns.RottingFood.FridgeGui
import org.kotofey.realismns.RottingFood.RottingFoodCatalog

class UpgradeControlGui(
    private val manager: BackpackManager,
    private val parent: BackpackGui,
    private val upgradeMechanics: UpgradeMechanics,
    private val rottingCatalog: RottingFoodCatalog?,
) : InventoryHolder {

    private lateinit var inv: Inventory
    private var entries: List<Pair<Int, ItemStack>> = emptyList()

    fun open() {
        inv = Bukkit.createInventory(this, 27, Component.text("Управление улучшениями", NamedTextColor.GOLD))
        fill()
        parent.player.openInventory(inv)
    }

    private fun fill() {
        for (i in 0 until inv.size) inv.setItem(i, null)
        val upgrades = manager.getUpgrades(parent.backpackId) ?: return
        entries = UpgradeItems.filterableUpgrades(upgrades) + upgrades.mapIndexedNotNull { i, s ->
            if (s != null && UpgradeItems.getType(s)?.installable == true && UpgradeItems.getType(s)?.hasFilter == false) {
                i to s
            } else null
        }.distinctBy { it.first }

        // Все установленные улучшения — вкл/выкл; с фильтром — ещё кнопка фильтра
        var row = 0
        upgrades.forEachIndexed { slotIndex, stack ->
            if (stack == null || !UpgradeItems.isInstallable(stack)) return@forEachIndexed
            if (row >= 8) return@forEachIndexed
            val base = row * 3
            inv.setItem(base, UpgradeItems.withDisplay(stack.clone(), slotIndex + 1))
            inv.setItem(base + 1, toggleButton(slotIndex, UpgradeItems.isEnabled(stack)))
            if (UpgradeItems.getType(stack)?.hasFilter == true) {
                inv.setItem(base + 2, filterButton(slotIndex, UpgradeItems.getType(stack)!!))
            } else if (UpgradeItems.getType(stack)?.canFridge == true && rottingCatalog != null) {
                inv.setItem(base + 2, fridgeButton(slotIndex))
            }
            row++
        }
        inv.setItem(26, backButton())
    }

    private fun toggleButton(slotIndex: Int, enabled: Boolean): ItemStack {
        val item = ItemStack(if (enabled) Material.LIME_DYE else Material.GRAY_DYE)
        item.editMeta { meta ->
            meta.displayName(Component.text(
                if (enabled) "Выключить #$slotIndex" else "Включить #$slotIndex",
                if (enabled) NamedTextColor.RED else NamedTextColor.GREEN,
            ))
        }
        return tag(item, "uc_toggle_$slotIndex")
    }

    private fun filterButton(slotIndex: Int, type: UpgradeType): ItemStack {
        val item = ItemStack(Material.WRITABLE_BOOK)
        item.editMeta { meta ->
            meta.displayName(Component.text("Фильтр: ${type.displayName}", NamedTextColor.AQUA))
        }
        return tag(item, "uc_filter_$slotIndex")
    }

    private fun fridgeButton(slotIndex: Int): ItemStack {
        val item = ItemStack(Material.SNOW_BLOCK)
        item.editMeta { meta ->
            meta.displayName(Component.text("Холодильник", NamedTextColor.AQUA))
        }
        return tag(item, "uc_fridge_$slotIndex")
    }

    private fun backButton(): ItemStack {
        val item = ItemStack(Material.ARROW)
        item.editMeta { meta ->
            meta.displayName(Component.text("Назад", NamedTextColor.YELLOW))
        }
        return tag(item, "uc_back")
    }

    private fun tag(item: ItemStack, action: String): ItemStack {
        item.editMeta { meta ->
            meta.persistentDataContainer.set(manager.guiActionKey, org.bukkit.persistence.PersistentDataType.STRING, action)
        }
        return item
    }

    fun handleClick(rawSlot: Int, item: ItemStack?) {
        if (rawSlot == 26) {
            reopenBackpack()
            return
        }

        val action = item?.itemMeta?.persistentDataContainer
            ?.get(manager.guiActionKey, org.bukkit.persistence.PersistentDataType.STRING)
        if (action != null) {
            when {
                action == "uc_back" -> reopenBackpack()
                action.startsWith("uc_toggle_") -> {
                    val idx = action.removePrefix("uc_toggle_").toIntOrNull() ?: return
                    toggleAt(idx)
                }
                action.startsWith("uc_filter_") -> {
                    val idx = action.removePrefix("uc_filter_").toIntOrNull() ?: return
                    openFilter(idx)
                }
                action.startsWith("uc_fridge_") -> {
                    val idx = action.removePrefix("uc_fridge_").toIntOrNull() ?: return
                    openFridge(idx)
                }
            }
            return
        }

        // Bedrock часто не читает PDC — переключение по колонке строки
        if (rawSlot >= 24) return
        val row = rawSlot / 3
        val slotIndex = installableSlotIndex(row) ?: return
        when (rawSlot % 3) {
            0, 1 -> toggleAt(slotIndex)
            2 -> {
                val stack = manager.getUpgrades(parent.backpackId)?.getOrNull(slotIndex) ?: return
                when {
                    UpgradeItems.getType(stack)?.hasFilter == true -> openFilter(slotIndex)
                    UpgradeItems.getType(stack)?.canFridge == true -> openFridge(slotIndex)
                }
            }
        }
    }

    private fun installableSlotIndex(row: Int): Int? {
        val upgrades = manager.getUpgrades(parent.backpackId) ?: return null
        return upgrades.indices.filter { upgrades[it] != null && UpgradeItems.isInstallable(upgrades[it]) }.getOrNull(row)
    }

    private fun toggleAt(index: Int) {
        val upgrades = manager.getUpgrades(parent.backpackId) ?: return
        val stored = upgrades.getOrNull(index)?.clone() ?: return
        UpgradeItems.toggleEnabled(stored)
        upgrades[index] = stored
        reopenBackpack()
    }

    private fun openFridge(index: Int) {
        val catalog = rottingCatalog ?: return
        val upgrade = manager.getUpgrades(parent.backpackId)?.getOrNull(index)?.clone() ?: return
        manager.saveFromGui(parent)
        FridgeGui(manager, catalog, parent, index, upgrade).open()
    }

    private fun openFilter(index: Int) {
        val upgrade = manager.getUpgrades(parent.backpackId)?.getOrNull(index)?.clone() ?: return
        manager.saveFromGui(parent)
        upgradeMechanics.openFilter(parent.player, upgrade) { saved ->
            manager.getUpgrades(parent.backpackId)?.set(index, saved)
            reopenBackpack()
        }
    }

    private fun reopenBackpack() {
        manager.refreshBackpack(parent)
    }

    override fun getInventory(): Inventory = inv
}
