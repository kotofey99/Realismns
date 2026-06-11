package org.kotofey.realismns.RottingFood

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.kotofey.realismns.backpack.BackpackGui
import org.kotofey.realismns.backpack.BackpackManager

class FridgeGui(
    private val manager: BackpackManager,
    private val catalog: RottingFoodCatalog,
    private val parent: BackpackGui,
    private val upgradeIndex: Int,
    private val upgradeItem: ItemStack,
) : InventoryHolder {

    companion object {
        const val FUEL_SLOT = 0
        const val TOGGLE_SLOT = 1
        const val FOOD_START = 2
        const val FOOD_SLOTS = 4
        const val BACK_SLOT = 8
    }

    private lateinit var inv: Inventory
    private var state: FridgeState = FridgeStorage.load(upgradeItem)

    fun open() {
        inv = Bukkit.createInventory(this, 9, Component.text("Холодильник", NamedTextColor.AQUA))
        FridgeMechanics.refuelFromBackpack(state, parent.backpackId, manager)
        persistState()
        render()
        parent.player.openInventory(inv)
    }

    fun render() {
        for (i in 0 until inv.size) inv.setItem(i, null)
        if (state.fuel > 0) inv.setItem(FUEL_SLOT, ItemStack(Material.COAL, state.fuel.coerceAtMost(64)))
        inv.setItem(TOGGLE_SLOT, toggleButton())
        for (i in 0 until FOOD_SLOTS) state.food[i]?.let { inv.setItem(FOOD_START + i, it) }
        inv.setItem(BACK_SLOT, backButton())
        for (i in 0 until inv.size) {
            if (i == FUEL_SLOT || i == TOGGLE_SLOT || i == BACK_SLOT || i in FOOD_START until FOOD_START + FOOD_SLOTS) continue
            if (inv.getItem(i) == null) inv.setItem(i, filler())
        }
    }

    private fun toggleButton(): ItemStack {
        val on = state.running
        val item = ItemStack(if (on) Material.LIME_DYE else Material.GRAY_DYE)
        item.editMeta { meta ->
            meta.displayName(Component.text(if (on) "Холод: ВКЛ" else "Холод: ВЫКЛ", if (on) NamedTextColor.GREEN else NamedTextColor.RED))
            meta.lore(listOf(Component.text("Нужен уголь", NamedTextColor.GRAY)))
            meta.persistentDataContainer.set(manager.guiActionKey, PersistentDataType.STRING, "fridge_toggle")
        }
        return item
    }

    private fun backButton(): ItemStack {
        val item = ItemStack(Material.ARROW)
        item.editMeta { meta ->
            meta.displayName(Component.text("Назад", NamedTextColor.YELLOW))
            meta.persistentDataContainer.set(manager.guiActionKey, PersistentDataType.STRING, "fridge_back")
        }
        return item
    }

    private fun filler(): ItemStack {
        val item = ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
        item.editMeta { it.displayName(Component.empty()) }
        return item
    }

    fun handleShiftFromPlayer(stack: ItemStack, player: Player, rawSlot: Int) {
        if (stack.type == Material.COAL && state.fuel < 64) {
            val add = stack.amount.coerceAtMost(64 - state.fuel)
            if (add > 0) {
                state.fuel += add
                stack.amount -= add
                player.openInventory.setItem(rawSlot, stack.takeIf { it.amount > 0 })
                render()
            }
            return
        }
        if (!catalog.isPerishable(stack.type)) return
        val world = player.world
        var remaining = stack.amount
        for (i in 0 until FOOD_SLOTS) {
            if (remaining <= 0) break
            remaining -= addFoodToSlot(i, stack, world, remaining)
        }
        if (remaining != stack.amount) {
            stack.amount = remaining
            player.openInventory.setItem(rawSlot, stack.takeIf { it.amount > 0 })
            render()
        }
    }

    fun handleClick(rawSlot: Int, clicked: ItemStack?, cursor: ItemStack, player: Player): Boolean {
        when (rawSlot) {
            BACK_SLOT -> {
                saveAndClose()
                manager.openBackpackPage(
                    parent.player, parent.backpackItem, parent.backpackType, parent.backpackId, parent.page, parent.mode,
                )
                return true
            }
            TOGGLE_SLOT -> {
                if (!state.running) {
                    FridgeMechanics.refuelFromBackpack(state, parent.backpackId, manager)
                    persistState()
                }
                state.running = !state.running
                render()
                return true
            }
            FUEL_SLOT -> return handleFuel(cursor, clicked, player)
            in FOOD_START until FOOD_START + FOOD_SLOTS -> return handleFood(rawSlot - FOOD_START, cursor, clicked, player)
        }
        return true
    }

    private fun handleFuel(cursor: ItemStack, clicked: ItemStack?, player: Player): Boolean {
        if (cursor.type == Material.COAL && cursor.amount > 0) {
            val add = cursor.amount.coerceAtMost(64 - state.fuel)
            if (add <= 0) return true
            state.fuel += add
            if (cursor.amount <= add) player.setItemOnCursor(null) else cursor.amount -= add
            render()
            return true
        }
        if (cursor.type.isAir && clicked?.type == Material.COAL) {
            val take = clicked.amount.coerceAtMost(64)
            player.setItemOnCursor(ItemStack(Material.COAL, take))
            state.fuel = (state.fuel - take).coerceAtLeast(0)
            render()
            return true
        }
        return true
    }

    private fun handleFood(index: Int, cursor: ItemStack, clicked: ItemStack?, player: Player): Boolean {
        val world = player.world
        if (!cursor.type.isAir) {
            if (!catalog.isPerishable(cursor.type)) return true
            val existing = state.food[index]
            if (existing != null && (existing.type != cursor.type || !existing.isSimilar(cursor))) {
                returnToPlayer(player, existing)
                state.food[index] = null
            }
            val moved = addFoodToSlot(index, cursor, world, cursor.amount)
            if (moved <= 0) return true
            if (cursor.amount <= moved) player.setItemOnCursor(null) else cursor.amount -= moved
            render()
            return true
        }
        if (clicked != null && !clicked.type.isAir && catalog.isPerishable(clicked.type)) {
            val stack = state.food[index] ?: return true
            if (state.running && state.fuel > 0) RottingFoodItem.resume(stack, world)
            RottingFoodItem.refreshLore(stack, catalog, world)
            player.setItemOnCursor(stack)
            state.food[index] = null
            render()
            return true
        }
        return true
    }

    /** @return сколько предметов добавлено в слот */
    private fun addFoodToSlot(index: Int, source: ItemStack, world: World, maxAmount: Int): Int {
        if (!catalog.isPerishable(source.type) || maxAmount <= 0) return 0
        val existing = state.food[index]
        if (existing == null) {
            val move = maxAmount.coerceAtMost(source.maxStackSize)
            val incoming = source.clone().also { it.amount = move }
            prepareFoodForFridge(incoming, world)
            state.food[index] = incoming
            return move
        }
        if (existing.type != source.type || !existing.isSimilar(source)) return 0
        val space = existing.maxStackSize - existing.amount
        if (space <= 0) return 0
        val move = maxAmount.coerceAtMost(space)
        val adding = source.clone().also { it.amount = move }
        RottingFoodItem.mergeExpiry(existing, adding, world)
        existing.amount += move
        prepareFoodForFridge(existing, world)
        RottingFoodItem.refreshLore(existing, catalog, world)
        return move
    }

    private fun prepareFoodForFridge(stack: ItemStack, world: World) {
        RottingFoodItem.refreshIfNeeded(stack, catalog, world)
        val rem = RottingFoodItem.remainingTicks(stack, world)
        RottingFoodItem.pauseWithRemaining(stack, rem)
        RottingFoodItem.refreshLore(stack, catalog, world)
    }

    private fun returnToPlayer(player: Player, stack: ItemStack) {
        val leftover = player.inventory.addItem(stack)
        leftover.values.forEach { player.world.dropItemNaturally(player.location, it) }
    }

    fun saveAndClose() {
        syncFoodFromInventory()
        val world = parent.player.world
        for (stack in state.food) {
            if (stack == null || !catalog.isPerishable(stack.type)) continue
            val rem = RottingFoodItem.remainingTicks(stack, world)
            RottingFoodItem.pauseWithRemaining(stack, rem)
        }
        persistState()
    }

    private fun persistState() {
        FridgeStorage.save(upgradeItem, state)
        manager.getUpgrades(parent.backpackId)?.set(upgradeIndex, upgradeItem)
    }

    private fun syncFoodFromInventory() {
        for (i in 0 until FOOD_SLOTS) {
            val slot = FOOD_START + i
            val inInv = inv.getItem(slot)
            state.food[i] = inInv?.takeIf { catalog.isPerishable(it.type) }
        }
        val coal = inv.getItem(FUEL_SLOT)
        state.fuel = if (coal?.type == Material.COAL) coal.amount else 0
    }

    override fun getInventory(): Inventory = inv
}
