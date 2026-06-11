package org.kotofey.realismns.backpack

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Chest
import org.bukkit.block.Container
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class UpgradeMechanics(private val plugin: JavaPlugin, private val manager: BackpackManager) : Listener {

    private val pendingReturn = mutableMapOf<UUID, BackpackGui>()
    private val unloadCooldown = ConcurrentHashMap<UUID, Long>()

    fun startTask() {
        val interval = plugin.config.getLong("backpack-upgrades.tick-interval", 5L)
        plugin.server.scheduler.runTaskTimer(plugin, Runnable { tickMagnets() }, interval, interval)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val ctx = findHeldBackpack(player) ?: return
        if (!UpgradeItems.hasPickup(ctx.upgrades)) return
        val stack = event.item.itemStack
        if (manager.isBackpack(stack)) return
        if (!UpgradeItems.passesFilter(stack, UpgradeItems.pickupFilter(ctx.upgrades))) return
        val left = manager.tryAddItem(ctx.id, ctx.backpackType, stack)
        if (left.amount <= 0) {
            event.isCancelled = true
            event.item.remove()
        } else {
            event.item.itemStack = left
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onSneakUnload(event: PlayerToggleSneakEvent) {
        if (!event.isSneaking) return
        val player = event.player
        if (!hasWorldInventoryOpen(player)) return

        val now = System.currentTimeMillis()
        if (now - (unloadCooldown[player.uniqueId] ?: 0L) < 500L) return

        val item = player.inventory.itemInMainHand
        if (!manager.isBackpack(item)) return

        val type = manager.getType(item) ?: return
        val id = manager.getId(item) ?: return
        manager.ensureStorage(id, type)
        val upgrades = manager.getUpgrades(id) ?: return
        val unload = UpgradeItems.unloadUpgrade(upgrades) ?: return

        val container = containerBelow(player) ?: return
        unloadCooldown[player.uniqueId] = now
        val moved = manager.unloadToContainer(id, unload, container)
        if (moved > 0) {
            player.sendMessage(Component.text("Разгружено: $moved шт.", NamedTextColor.GREEN))
        }
    }

    @EventHandler
    fun onFilterClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? FilterGui ?: return
        val rawSlot = event.rawSlot
        if (rawSlot >= holder.inventory.size) return
        when {
            rawSlot == FilterGui.TOGGLE_SLOT -> {
                event.isCancelled = true
                holder.toggleMode()
            }
            rawSlot !in 0 until FilterGui.FILTER_SLOTS -> event.isCancelled = true
            else -> holder.normalizeFilterSlot(rawSlot)
        }
    }

    @EventHandler
    fun onFilterDrag(event: org.bukkit.event.inventory.InventoryDragEvent) {
        val holder = event.inventory.holder as? FilterGui ?: return
        for (slot in event.rawSlots) {
            if (slot < holder.inventory.size && slot !in 0 until FilterGui.FILTER_SLOTS && slot != FilterGui.TOGGLE_SLOT) {
                event.isCancelled = true
                return
            }
        }
    }

    @EventHandler
    fun onFilterClose(event: InventoryCloseEvent) {
        (event.inventory.holder as? FilterGui)?.save()
    }

    @EventHandler
    fun onWorkstationReturn(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val pending = pendingReturn.remove(player.uniqueId) ?: return
        val holder = event.inventory.holder
        if (holder is BackpackGui || holder is UpgradeControlGui || holder is FilterGui) return
        manager.openBackpackPage(
            player, pending.backpackItem, pending.backpackType, pending.backpackId, pending.page, pending.mode,
        )
    }

    fun openCrafting(gui: BackpackGui) {
        pendingReturn[gui.player.uniqueId] = gui
        gui.player.openWorkbench(null, true)
    }

    fun openFurnace(gui: BackpackGui) {
        pendingReturn[gui.player.uniqueId] = gui
        val title = Component.text("Печка рюкзака", NamedTextColor.GOLD)
        gui.player.openInventory(Bukkit.createInventory(gui.player, InventoryType.FURNACE, title))
    }

    fun openFilter(player: Player, upgrade: ItemStack, onSave: (ItemStack) -> Unit) {
        FilterGui(player, upgrade.clone(), onSave).open()
    }

    private fun containerBelow(player: Player): Inventory? {
        val world = player.world
        val bx = player.location.blockX
        val bz = player.location.blockZ
        for (y in player.location.blockY downTo player.location.blockY - 2) {
            val block = world.getBlockAt(bx, y, bz)
            if (block.type.isAir) continue
            containerInventory(block.state)?.let { return it }
        }
        return null
    }

    private fun containerInventory(state: org.bukkit.block.BlockState?): Inventory? {
        if (state == null) return null
        if (state is Chest) return state.blockInventory
        if (state is Container) return state.inventory
        return (state as? InventoryHolder)?.inventory
    }

    private fun hasWorldInventoryOpen(player: Player): Boolean {
        val holder = player.openInventory.topInventory.holder
        return holder == null || holder is Player
    }

    private fun canCollect(stack: ItemStack): Boolean = !manager.isBackpack(stack)

    private fun tickMagnets() {
        for (player in plugin.server.onlinePlayers) {
            val ctx = findHeldBackpack(player) ?: findAnyBackpack(player) ?: continue
            val (magnetType, magnetStack) = UpgradeItems.magnetUpgrade(ctx.upgrades) ?: continue
            val radius = radius(magnetType)
            for (entity in player.world.getNearbyEntities(player.location, radius, radius, radius)) {
                if (entity !is Item) continue
                val stack = entity.itemStack
                if (!canCollect(stack)) continue
                if (!UpgradeItems.passesFilter(stack, magnetStack)) continue
                if (entity.location.distanceSquared(player.location) < 2.25) {
                    val left = manager.tryAddItem(ctx.id, ctx.backpackType, stack)
                    if (left.amount <= 0) entity.remove() else entity.itemStack = left
                } else {
                    val dir = player.location.add(0.0, 0.5, 0.0).toVector()
                        .subtract(entity.location.toVector()).normalize().multiply(0.4)
                    entity.velocity = dir
                }
            }
        }
    }

    private fun radius(type: UpgradeType): Double {
        val key = type.configRadiusKey ?: return 0.0
        return plugin.config.getDouble("backpack-upgrades.magnet-radius.$key", if (type == UpgradeType.MAGNET) 4.0 else 8.0)
    }

    private fun findHeldBackpack(player: Player): UpgradeContext? {
        val hand = player.inventory.itemInMainHand
        if (!manager.isBackpack(hand)) return null
        return contextFromStack(hand)
    }

    private fun findAnyBackpack(player: Player): UpgradeContext? {
        for (stack in player.inventory.contents + player.inventory.extraContents) {
            if (stack == null || !manager.isBackpack(stack)) continue
            return contextFromStack(stack)
        }
        return null
    }

    private fun contextFromStack(stack: ItemStack): UpgradeContext? {
        val backpackType = manager.getType(stack) ?: return null
        val id = manager.getId(stack) ?: manager.ensureId(stack)
        val upgrades = manager.getUpgrades(id) ?: return null
        return UpgradeContext(id, backpackType, upgrades)
    }

    private data class UpgradeContext(
        val id: UUID,
        val backpackType: BackpackType,
        val upgrades: Array<ItemStack?>,
    )
}

class FilterGui(
    private val player: Player,
    private val upgrade: ItemStack,
    private val onSave: (ItemStack) -> Unit,
) : InventoryHolder {

    companion object {
        const val FILTER_SLOTS = 9
        const val TOGGLE_SLOT = 22
    }

    private lateinit var backingInventory: Inventory

    fun open() {
        backingInventory = Bukkit.createInventory(this, 27, title())
        fillBackground()
        fillToggle()
        fillFilters()
        player.openInventory(backingInventory)
    }

    private fun title(): Component {
        val label = when (UpgradeItems.getType(upgrade)) {
            UpgradeType.ADVANCED_PICKUP -> "Фильтр подбора"
            UpgradeType.UNLOAD -> "Фильтр разгрузки"
            else -> "Фильтр магнита"
        }
        return Component.text("$label (до 9 типов)", NamedTextColor.LIGHT_PURPLE)
    }

    private fun fillBackground() {
        val pane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        pane.editMeta { it.displayName(Component.empty()) }
        for (i in FILTER_SLOTS until backingInventory.size) {
            if (i != TOGGLE_SLOT) backingInventory.setItem(i, pane)
        }
    }

    private fun fillFilters() {
        UpgradeItems.getFilterMaterials(upgrade).forEachIndexed { i, mat ->
            if (i < FILTER_SLOTS) backingInventory.setItem(i, ItemStack(mat))
        }
    }

    private fun fillToggle() {
        val whitelist = UpgradeItems.isWhitelist(upgrade)
        val item = ItemStack(if (whitelist) Material.WHITE_WOOL else Material.BLACK_WOOL)
        item.editMeta { meta ->
            meta.displayName(Component.text(
                if (whitelist) "Белый список (нажми)" else "Чёрный список (нажми)", NamedTextColor.YELLOW,
            ))
        }
        backingInventory.setItem(TOGGLE_SLOT, item)
    }

    fun toggleMode() {
        UpgradeItems.setWhitelist(upgrade, !UpgradeItems.isWhitelist(upgrade))
        fillToggle()
    }

    fun normalizeFilterSlot(rawSlot: Int) {
        if (rawSlot !in 0 until FILTER_SLOTS) return
        val stack = backingInventory.getItem(rawSlot) ?: return
        if (stack.type.isAir || !stack.type.isItem) {
            backingInventory.setItem(rawSlot, null)
            return
        }
        if (stack.amount > 1) {
            val one = stack.clone()
            one.amount = 1
            backingInventory.setItem(rawSlot, one)
        }
    }

    fun save() {
        val materials = (0 until FILTER_SLOTS).mapNotNull { backingInventory.getItem(it)?.type?.takeIf { it.isItem } }.toSet()
        UpgradeItems.setFilterMaterials(upgrade, materials)
        for (i in 0 until FILTER_SLOTS) {
            val mat = backingInventory.getItem(i)?.type?.takeIf { it.isItem } ?: continue
            backingInventory.setItem(i, ItemStack(mat))
        }
        onSave(upgrade)
    }

    override fun getInventory(): Inventory = backingInventory
}
