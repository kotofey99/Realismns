package org.kotofey.realismns.backpack

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import kotlin.math.min

class BackpackManager(private val plugin: JavaPlugin) {

    val typeKey: NamespacedKey = NamespacedKey(plugin, "backpack_type")
    val idKey: NamespacedKey = NamespacedKey(plugin, "backpack_id")
    val guiActionKey: NamespacedKey = NamespacedKey(plugin, "gui_action")
    val placeholderKey: NamespacedKey = NamespacedKey(plugin, "gui_placeholder")

    private val storageFile = File(plugin.dataFolder, "backpacks.yml")
    private val contents = mutableMapOf<UUID, Array<ItemStack?>>()
    private val upgradeContents = mutableMapOf<UUID, Array<ItemStack?>>()

    fun load() {
        if (!storageFile.exists()) return
        val yaml = YamlConfiguration.loadConfiguration(storageFile)
        for (idStr in yaml.getKeys(false)) {
            val id = runCatching { UUID.fromString(idStr) }.getOrNull() ?: continue
            val section = yaml.getConfigurationSection(idStr) ?: continue
            val storageSize = section.getInt("storage-size", 27)
            val upgradeSize = section.getInt("upgrade-size", 1)
            contents[id] = loadItems(section, "contents", storageSize)
            upgradeContents[id] = loadItems(section, "upgrades", upgradeSize)
        }
    }

    fun save() {
        val yaml = YamlConfiguration()
        for ((id, items) in contents) {
            val path = id.toString()
            yaml.set("$path.storage-size", items.size)
            yaml.set("$path.upgrade-size", upgradeContents[id]?.size ?: 0)
            saveItems(yaml, "$path.contents", items)
            upgradeContents[id]?.let { saveItems(yaml, "$path.upgrades", it) }
        }
        storageFile.parentFile.mkdirs()
        yaml.save(storageFile)
    }

    private fun loadItems(section: ConfigurationSection, path: String, size: Int): Array<ItemStack?> {
        val result = arrayOfNulls<ItemStack>(size)
        val items = section.getConfigurationSection(path) ?: return result
        for (key in items.getKeys(false)) {
            val index = key.toIntOrNull() ?: continue
            if (index in result.indices) {
                result[index] = items.getItemStack(key)
            }
        }
        return result
    }

    private fun saveItems(yaml: YamlConfiguration, path: String, items: Array<ItemStack?>) {
        items.forEachIndexed { index, stack ->
            if (stack != null && !stack.type.isAir) {
                yaml.set("$path.$index", stack)
            }
        }
    }

    fun createBackpack(type: BackpackType, id: UUID = UUID.randomUUID()): ItemStack {
        val item = ItemStack(Material.CHEST)
        item.editMeta { meta ->
            meta.displayName(type.displayName.decoration(TextDecoration.ITALIC, false))
            meta.lore(
                listOf(
                    Component.text("ПКМ — открыть", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                    Component.text("Хранилище: ${type.storageSlots} слотов", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                    Component.text("Улучшения: ${type.upgradeSlots}", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                ),
            )
            meta.persistentDataContainer.set(typeKey, PersistentDataType.STRING, type.name)
            meta.persistentDataContainer.set(idKey, PersistentDataType.STRING, id.toString())
        }
        ensureStorage(id, type)
        return item
    }

    fun templateBackpack(type: BackpackType): ItemStack {
        val item = ItemStack(Material.CHEST)
        item.editMeta { meta ->
            meta.displayName(type.displayName.decoration(TextDecoration.ITALIC, false))
            meta.persistentDataContainer.set(typeKey, PersistentDataType.STRING, type.name)
        }
        return item
    }

    fun getType(item: ItemStack?): BackpackType? {
        if (item == null || item.type != Material.CHEST) return null
        val name = item.itemMeta?.persistentDataContainer?.get(typeKey, PersistentDataType.STRING) ?: return null
        return runCatching { BackpackType.valueOf(name) }.getOrNull()
            ?: BackpackType.fromLegacy(name)
    }

    fun getId(item: ItemStack?): UUID? {
        if (item == null) return null
        val idStr = item.itemMeta?.persistentDataContainer?.get(idKey, PersistentDataType.STRING) ?: return null
        return runCatching { UUID.fromString(idStr) }.getOrNull()
    }

    fun isBackpack(item: ItemStack?): Boolean = getType(item) != null

    fun isPlaceholder(item: ItemStack?): Boolean {
        if (item == null) return false
        return item.itemMeta?.persistentDataContainer?.has(placeholderKey, PersistentDataType.BYTE) == true
    }

    fun createUpgradePlaceholder(index: Int): ItemStack {
        val item = ItemStack(Material.LIME_STAINED_GLASS_PANE)
        item.editMeta { meta ->
            meta.displayName(
                Component.text("Слот улучшения #$index", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false),
            )
            meta.lore(
                listOf(
                    Component.text("Сюда можно положить модуль улучшения", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                ),
            )
            meta.persistentDataContainer.set(placeholderKey, PersistentDataType.BYTE, 1)
        }
        return item
    }

    fun sanitizeStoredItem(stack: ItemStack?): ItemStack? {
        if (stack == null || stack.type.isAir || isPlaceholder(stack) || isBackpack(stack)) return null
        return stack.clone()
    }

    fun ensureId(item: ItemStack): UUID {
        val existing = getId(item)
        if (existing != null) return existing
        val type = getType(item) ?: error("Not a backpack")
        val id = UUID.randomUUID()
        item.editMeta { meta ->
            meta.persistentDataContainer.set(idKey, PersistentDataType.STRING, id.toString())
        }
        ensureStorage(id, type)
        return id
    }

    fun ensureStorage(id: UUID, type: BackpackType) {
        if (id !in contents) {
            contents[id] = arrayOfNulls(type.storageSlots)
        } else {
            resizeStorage(id, type.storageSlots, contents[id]!!)
        }
        if (id !in upgradeContents) {
            upgradeContents[id] = arrayOfNulls(type.upgradeSlots)
        } else {
            resizeStorage(id, type.upgradeSlots, upgradeContents[id]!!)
        }
    }

    private fun resizeStorage(id: UUID, newSize: Int, old: Array<ItemStack?>) {
        if (old.size == newSize) return
        val resized = arrayOfNulls<ItemStack>(newSize)
        old.copyInto(resized, endIndex = minOf(old.size, newSize))
        if (contents[id] === old) contents[id] = resized else upgradeContents[id] = resized
    }

    fun transferStorage(fromId: UUID, toId: UUID, newType: BackpackType) {
        val oldStorage = contents.remove(fromId) ?: arrayOfNulls(0)
        val oldUpgrades = upgradeContents.remove(fromId) ?: arrayOfNulls(0)
        ensureStorage(toId, newType)
        val newStorage = contents[toId]!!
        val newUpgrades = upgradeContents[toId]!!
        oldStorage.copyInto(newStorage, endIndex = minOf(oldStorage.size, newStorage.size))
        oldUpgrades.copyInto(newUpgrades, endIndex = minOf(oldUpgrades.size, newUpgrades.size))
    }

    fun openBackpackPage(player: Player, item: ItemStack, type: BackpackType, id: UUID, page: Int, mode: BackpackGui.Mode = BackpackGui.Mode.STORAGE) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            BackpackGui(this, player, item, type, id, page, mode).open()
        })
    }

    fun persistUpgrade(backpackId: UUID, index: Int, item: ItemStack) {
        getUpgrades(backpackId)?.set(index, item)
    }

    fun refreshBackpack(gui: BackpackGui) {
        if (gui.player.openInventory.topInventory.holder === gui) {
            saveStorageFromGui(gui)
        }
        openBackpackPage(gui.player, gui.backpackItem, gui.backpackType, gui.backpackId, gui.page, gui.mode)
    }

    fun saveStorageFromGui(gui: BackpackGui) {
        if (gui.mode != BackpackGui.Mode.STORAGE) return
        val storage = contents[gui.backpackId] ?: return
        val type = gui.backpackType
        val pageOffset = gui.page * type.pageStorageSize
        val pageSize = minOf(type.pageStorageSize, type.storageSlots - pageOffset)
        for (i in 0 until pageSize) {
            storage[pageOffset + i] = sanitizeStoredItem(gui.backingInventory.getItem(i))
        }
    }

    fun openBackpack(player: Player, item: ItemStack) {
        val type = getType(item) ?: return
        val id = ensureId(item)
        ensureStorage(id, type)
        openBackpackPage(player, item, type, id, page = 0)
    }

    fun getStorage(id: UUID): Array<ItemStack?>? = contents[id]

    fun unloadToContainer(id: UUID, filterUpgrade: ItemStack?, container: org.bukkit.inventory.Inventory): Int {
        val storage = contents[id] ?: return 0
        var moved = 0
        for (i in storage.indices) {
            val stack = storage[i] ?: continue
            if (filterUpgrade != null && !UpgradeItems.passesFilter(stack, filterUpgrade)) continue
            val clone = stack.clone()
            val leftover = container.addItem(clone)
            if (leftover.isEmpty()) {
                moved += stack.amount
                storage[i] = null
            } else {
                val rem = leftover.values.first()
                val transferred = stack.amount - rem.amount
                if (transferred > 0) {
                    moved += transferred
                    if (rem.amount > 0) stack.amount = rem.amount else storage[i] = null
                }
            }
        }
        return moved
    }

    fun getUpgrades(id: UUID): Array<ItemStack?>? = upgradeContents[id]

    fun forEachUpgrade(action: (UUID, ItemStack) -> Unit) {
        for ((id, arr) in upgradeContents) {
            for (stack in arr) {
                if (stack != null && !stack.type.isAir) action(id, stack)
            }
        }
    }

    /** Забирает уголь из хранилища рюкзака (до maxAmount). */
    fun withdrawCoalFromStorage(backpackId: UUID, maxAmount: Int): Int {
        if (maxAmount <= 0) return 0
        val storage = contents[backpackId] ?: return 0
        var pulled = 0
        for (i in storage.indices) {
            if (pulled >= maxAmount) break
            val stack = storage[i] ?: continue
            if (stack.type != Material.COAL) continue
            val take = min(stack.amount, maxAmount - pulled)
            pulled += take
            stack.amount -= take
            if (stack.amount <= 0) storage[i] = null
        }
        return pulled
    }

    fun saveFromGui(gui: BackpackGui) {
        saveStorageFromGui(gui)
    }

    private fun cloneOrNull(stack: ItemStack?): ItemStack? = sanitizeStoredItem(stack)

    fun tryAddItem(id: UUID, type: BackpackType, incoming: ItemStack): ItemStack {
        if (isBackpack(incoming)) return incoming
        ensureStorage(id, type)
        val storage = contents[id]!!
        var left = incoming.amount
        for (i in storage.indices) {
            val slot = storage[i] ?: continue
            if (!slot.isSimilar(incoming)) continue
            val move = min(left, slot.maxStackSize - slot.amount)
            if (move <= 0) continue
            slot.amount += move
            left -= move
            if (left <= 0) return ItemStack.empty()
        }
        for (i in storage.indices) {
            if (storage[i] != null && !storage[i]!!.type.isAir) continue
            val stack = incoming.clone()
            stack.amount = left
            storage[i] = stack
            return ItemStack.empty()
        }
        val rem = incoming.clone()
        rem.amount = left
        return rem
    }
}

class BackpackGui(
    private val manager: BackpackManager,
    val player: Player,
    val backpackItem: ItemStack,
    val backpackType: BackpackType,
    val backpackId: UUID,
    val page: Int,
    val mode: Mode = Mode.STORAGE,
) : InventoryHolder {

    enum class Mode { STORAGE, WORKSTATION }

    companion object {
        const val FOOTER_SIZE = 9
    }

    lateinit var backingInventory: Inventory
        private set

    val upgradeSlotStart: Int
        get() = pageStorageOnThisPage

    val pageStorageOnThisPage: Int
        get() {
            val offset = page * backpackType.pageStorageSize
            return minOf(backpackType.pageStorageSize, backpackType.storageSlots - offset)
        }

    fun open() {
        backingInventory = buildInventory()
        player.openInventory(backingInventory)
    }

    fun openPage(newPage: Int) {
        if (mode == Mode.STORAGE) manager.saveFromGui(this)
        manager.openBackpackPage(player, backpackItem, backpackType, backpackId, newPage.coerceIn(0, backpackType.pageCount - 1), Mode.STORAGE)
    }

    fun openWorkstation() {
        if (mode == Mode.STORAGE) manager.saveFromGui(this)
        manager.openBackpackPage(player, backpackItem, backpackType, backpackId, page, Mode.WORKSTATION)
    }

    fun openStorageFromWorkstation() {
        manager.openBackpackPage(player, backpackItem, backpackType, backpackId, page, Mode.STORAGE)
    }

    private fun buildInventory(): Inventory {
        return if (mode == Mode.WORKSTATION) buildWorkstationInventory() else buildStorageInventory()
    }

    private fun buildStorageInventory(): Inventory {
        val size = pageStorageOnThisPage + FOOTER_SIZE
        val title = if (backpackType.pageCount > 1) {
            backpackType.displayName.append(
                Component.text(" (${page + 1}/${backpackType.pageCount})", NamedTextColor.GRAY),
            )
        } else {
            backpackType.displayName
        }
        val inv = Bukkit.createInventory(this, size, title)
        fillStorage(inv)
        fillFooter(inv)
        return inv
    }

    private fun buildWorkstationInventory(): Inventory {
        val title = backpackType.displayName.append(Component.text(" — мастерская", NamedTextColor.GOLD))
        val inv = Bukkit.createInventory(this, 27, title)
        for (i in 0 until 27) inv.setItem(i, fillerPane())
        inv.setItem(11, navItem(Material.CRAFTING_TABLE, "Верстак", "ws_craft"))
        inv.setItem(15, navItem(Material.FURNACE, "Печка", "ws_furnace"))
        inv.setItem(22, navItem(Material.ARROW, "Назад", "ws_back"))
        return inv
    }

    private fun fillStorage(inv: Inventory) {
        val storage = manager.getStorage(backpackId) ?: return
        val offset = page * backpackType.pageStorageSize
        val count = pageStorageOnThisPage
        for (i in 0 until count) {
            manager.sanitizeStoredItem(storage[offset + i])?.let { inv.setItem(i, it) }
        }
    }

    private fun fillFooter(inv: Inventory) {
        val footerStart = pageStorageOnThisPage
        for (i in footerStart until footerStart + FOOTER_SIZE) {
            inv.setItem(i, fillerPane())
        }

        val upgrades = manager.getUpgrades(backpackId) ?: return
        for (u in upgrades.indices) {
            val slot = footerStart + u
            if (upgrades[u] != null) {
                inv.setItem(slot, UpgradeItems.withDisplay(upgrades[u]!!, u + 1))
            } else {
                inv.setItem(slot, manager.createUpgradePlaceholder(u + 1))
            }
        }

        if (backpackType.pageCount > 1) {
            if (page > 0) {
                inv.setItem(footerStart + 7, navItem(Material.ARROW, "Предыдущая страница", "backpack_prev"))
            }
            inv.setItem(footerStart + 4, navItem(Material.PAPER, "Стр. ${page + 1}/${backpackType.pageCount}", "backpack_info"))
            if (page < backpackType.pageCount - 1) {
                inv.setItem(footerStart + 8, navItem(Material.ARROW, "Следующая страница", "backpack_next"))
            }
        }

        if (UpgradeItems.workstationUpgrade(upgrades) != null) {
            inv.setItem(footerStart + 6, navItem(Material.CRAFTING_TABLE, "Мастерская", "backpack_workshop"))
        }
        if (upgrades.any { it != null && UpgradeItems.isInstallable(it) }) {
            inv.setItem(footerStart + 5, navItem(Material.COMPARATOR, "Улучшения ⚙", "backpack_upgrades"))
        }
    }

    private fun fillerPane(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        item.editMeta { meta ->
            meta.displayName(Component.empty())
            meta.persistentDataContainer.set(manager.placeholderKey, PersistentDataType.BYTE, 1)
        }
        return item
    }

    private fun navItem(material: Material, name: String, tag: String): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta: ItemMeta ->
            meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
            meta.persistentDataContainer.set(manager.guiActionKey, PersistentDataType.STRING, tag)
        }
        return item
    }

    override fun getInventory(): Inventory = backingInventory

    fun isStorageSlot(rawSlot: Int): Boolean = rawSlot in 0 until pageStorageOnThisPage

    fun isUpgradeSlot(rawSlot: Int): Boolean {
        val footerStart = pageStorageOnThisPage
        return rawSlot in footerStart until footerStart + backpackType.upgradeSlots
    }

    fun upgradeSlotIndex(rawSlot: Int): Int = rawSlot - upgradeSlotStart

    fun isFooterSlot(rawSlot: Int): Boolean {
        val footerStart = pageStorageOnThisPage
        return rawSlot in footerStart until footerStart + FOOTER_SIZE
    }

    fun refreshUpgradeSlot(rawSlot: Int) {
        val index = upgradeSlotIndex(rawSlot)
        val stored = manager.getUpgrades(backpackId)?.getOrNull(index)
        backingInventory.setItem(
            rawSlot,
            if (stored != null) UpgradeItems.withDisplay(stored, index + 1)
            else manager.createUpgradePlaceholder(index + 1),
        )
    }
}
