package org.kotofey.realismns.backpack

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

object UpgradeItems {
    lateinit var typeKey: NamespacedKey
    lateinit var enabledKey: NamespacedKey
    lateinit var filterModeKey: NamespacedKey
    lateinit var filterListKey: NamespacedKey
    lateinit var fridgeDataKey: NamespacedKey

    fun init(plugin: JavaPlugin) {
        typeKey = NamespacedKey(plugin, "upgrade_type")
        enabledKey = NamespacedKey(plugin, "upgrade_enabled")
        filterModeKey = NamespacedKey(plugin, "filter_whitelist")
        filterListKey = NamespacedKey(plugin, "filter_materials")
        fridgeDataKey = NamespacedKey(plugin, "fridge_data")
    }

    fun create(type: UpgradeType): ItemStack = withDisplay(applyDefaults(ItemStack(type.icon), type), null)

    private fun applyDefaults(item: ItemStack, type: UpgradeType): ItemStack {
        item.editMeta { meta ->
            meta.persistentDataContainer.set(typeKey, PersistentDataType.STRING, type.name)
            meta.persistentDataContainer.set(enabledKey, PersistentDataType.BYTE, 1)
            if (type.hasFilter) {
                meta.persistentDataContainer.set(filterModeKey, PersistentDataType.BYTE, 0)
                meta.persistentDataContainer.set(filterListKey, PersistentDataType.STRING, "")
            }
        }
        return item
    }

    fun getType(item: ItemStack?): UpgradeType? {
        val name = item?.itemMeta?.persistentDataContainer?.get(typeKey, PersistentDataType.STRING) ?: return null
        return runCatching { UpgradeType.valueOf(name) }.getOrNull()
    }

    fun isUpgrade(item: ItemStack?, type: UpgradeType): Boolean = getType(item) == type

    fun isInstallable(item: ItemStack?): Boolean = getType(item)?.installable == true

    fun isEnabled(item: ItemStack?): Boolean {
        if (item == null) return false
        val v = item.itemMeta?.persistentDataContainer?.get(enabledKey, PersistentDataType.BYTE) ?: return true
        return v == 1.toByte()
    }

    fun setEnabled(item: ItemStack, enabled: Boolean) {
        item.editMeta { meta ->
            meta.persistentDataContainer.set(enabledKey, PersistentDataType.BYTE, if (enabled) 1 else 0)
        }
    }

    fun toggleEnabled(item: ItemStack) = setEnabled(item, !isEnabled(item))

    fun withDisplay(item: ItemStack, slotIndex: Int?): ItemStack {
        val type = getType(item) ?: return item
        val clone = item.clone()
        clone.editMeta { meta ->
            meta.displayName(type.displayName.decoration(TextDecoration.ITALIC, false))
            val lore = mutableListOf<Component>()
            slotIndex?.let {
                lore.add(Component.text("Слот улучшения #$it", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
            }
            lore.add(
                Component.text(
                    if (isEnabled(clone)) "● Включено" else "○ Выключено",
                    if (isEnabled(clone)) NamedTextColor.GREEN else NamedTextColor.RED,
                ).decoration(TextDecoration.ITALIC, false),
            )
            lore.add(Component.text("ПКМ / ЛКМ — вкл/выкл", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            if (type.hasFilter) {
                lore.add(Component.text("Кнопка ⚙ — фильтр", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            }
            if (type.canFridge) {
                lore.add(Component.text("ПКМ / Shift — холодильник", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                lore.add(Component.text("Bedrock: ЛКМ — холодильник", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
            }
            meta.lore(lore)
        }
        return clone
    }

    private fun active(upgrades: Array<ItemStack?>): List<ItemStack> =
        upgrades.filterNotNull().filter { isEnabled(it) }

    fun hasPickup(upgrades: Array<ItemStack?>): Boolean =
        active(upgrades).any { getType(it)?.canPickup == true }

    fun pickupFilter(upgrades: Array<ItemStack?>): ItemStack? =
        active(upgrades).firstOrNull { getType(it) == UpgradeType.ADVANCED_PICKUP }

    fun magnetUpgrade(upgrades: Array<ItemStack?>): Pair<UpgradeType, ItemStack?>? =
        active(upgrades).mapNotNull { stack -> getType(stack)?.let { it to stack } }
            .filter { it.first.canMagnet }
            .maxByOrNull { it.first.tier }

    fun workstationUpgrade(upgrades: Array<ItemStack?>): ItemStack? =
        active(upgrades).firstOrNull { getType(it)?.canWorkstation == true }

    fun unloadUpgrade(upgrades: Array<ItemStack?>): ItemStack? =
        active(upgrades).firstOrNull { getType(it)?.canUnload == true }

    fun fridgeUpgrade(upgrades: Array<ItemStack?>): ItemStack? =
        active(upgrades).firstOrNull { getType(it)?.canFridge == true }

    fun filterableUpgrades(upgrades: Array<ItemStack?>): List<Pair<Int, ItemStack>> =
        upgrades.mapIndexedNotNull { i, stack ->
            if (stack != null && getType(stack)?.hasFilter == true) i to stack else null
        }

    fun isWhitelist(item: ItemStack): Boolean =
        item.itemMeta?.persistentDataContainer?.get(filterModeKey, PersistentDataType.BYTE) == 1.toByte()

    fun setWhitelist(item: ItemStack, whitelist: Boolean) {
        item.editMeta { meta ->
            meta.persistentDataContainer.set(filterModeKey, PersistentDataType.BYTE, if (whitelist) 1 else 0)
        }
    }

    fun getFilterMaterials(item: ItemStack): Set<Material> {
        val raw = item.itemMeta?.persistentDataContainer?.get(filterListKey, PersistentDataType.STRING) ?: return emptySet()
        if (raw.isBlank()) return emptySet()
        return raw.split(',').mapNotNull { runCatching { Material.valueOf(it) }.getOrNull() }.toSet()
    }

    fun setFilterMaterials(item: ItemStack, materials: Set<Material>) {
        item.editMeta { meta ->
            meta.persistentDataContainer.set(filterListKey, PersistentDataType.STRING, materials.joinToString(",") { it.name })
        }
    }

    fun passesFilter(item: ItemStack, upgrade: ItemStack?): Boolean {
        val type = getType(upgrade) ?: return true
        if (!type.hasFilter || upgrade == null) return true
        val filter = getFilterMaterials(upgrade)
        if (filter.isEmpty()) return true
        val match = item.type in filter
        return if (isWhitelist(upgrade)) match else !match
    }
}
