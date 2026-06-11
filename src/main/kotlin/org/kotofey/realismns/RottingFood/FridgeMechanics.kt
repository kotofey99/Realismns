package org.kotofey.realismns.RottingFood

import org.bukkit.World
import org.bukkit.inventory.ItemStack
import org.kotofey.realismns.backpack.UpgradeItems
import org.kotofey.realismns.backpack.UpgradeType

import org.kotofey.realismns.backpack.BackpackManager
import java.util.UUID

object FridgeMechanics {

    const val MAX_FUEL = 64

    fun refuelFromBackpack(state: FridgeState, backpackId: UUID, manager: BackpackManager): Int {
        if (state.fuel >= MAX_FUEL) return 0
        val pulled = manager.withdrawCoalFromStorage(backpackId, MAX_FUEL - state.fuel)
        if (pulled > 0) state.fuel += pulled
        return pulled
    }

    fun tickUpgrade(
        upgrade: ItemStack,
        backpackId: UUID,
        manager: BackpackManager,
        world: World,
        catalog: RottingFoodCatalog,
        deltaTicks: Long,
    ) {
        if (UpgradeItems.getType(upgrade) != UpgradeType.FRIDGE) return

        val state = FridgeStorage.load(upgrade)

        if (!UpgradeItems.isEnabled(upgrade)) {
            thawAll(state, world, catalog)
            FridgeStorage.save(upgrade, state)
            return
        }

        if (state.running && state.fuel < MAX_FUEL) {
            refuelFromBackpack(state, backpackId, manager)
        }

        val active = state.running && state.fuel > 0 && state.hasFood()

        if (active) {
            state.burnProgress += deltaTicks
            while (state.burnProgress >= catalog.fridgeCoalBurnTicks && state.fuel > 0) {
                state.burnProgress -= catalog.fridgeCoalBurnTicks
                state.fuel--
            }
            if (state.fuel <= 0) {
                refuelFromBackpack(state, backpackId, manager)
                if (state.fuel <= 0) state.running = false
            }
        }

        // Пока еда лежит в холодильнике — всегда заморожена (уголь только тратится при работе).
        freezeAll(state, world, catalog)
        FridgeStorage.save(upgrade, state)
    }

    private fun freezeAll(state: FridgeState, world: World, catalog: RottingFoodCatalog) {
        for (stack in state.food) {
            if (stack == null || !catalog.isPerishable(stack.type)) continue
            val rem = when {
                RottingFoodItem.isPaused(stack) ->
                    RottingFoodItem.getPausedRemainingTicks(stack) ?: RottingFoodItem.remainingTicks(stack, world)
                else -> RottingFoodItem.remainingTicks(stack, world)
            }
            RottingFoodItem.pauseWithRemaining(stack, rem)
            RottingFoodItem.refreshLore(stack, catalog, world)
        }
    }

    private fun thawAll(state: FridgeState, world: World, catalog: RottingFoodCatalog) {
        for (stack in state.food) {
            if (stack == null) continue
            if (RottingFoodItem.isPaused(stack)) RottingFoodItem.resume(stack, world)
            RottingFoodItem.refreshLore(stack, catalog, world)
        }
    }
}
