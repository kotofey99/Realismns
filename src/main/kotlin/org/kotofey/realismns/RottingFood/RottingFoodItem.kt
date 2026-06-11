package org.kotofey.realismns.RottingFood

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.World
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType

object RottingFoodItem {

    fun stamp(item: ItemStack, catalog: RottingFoodCatalog, world: World) {
        if (item.type.isAir) return
        val shelfTicks = catalog.shelfLifeTicks(item.type) ?: return
        clearPaused(item)
        setTiming(item, RottingFoodTime.currentTick(world) + shelfTicks, shelfTicks)
        refreshLore(item, catalog, world)
    }

    fun refreshIfNeeded(item: ItemStack, catalog: RottingFoodCatalog, world: World) {
        if (item.type.isAir || !catalog.isPerishable(item.type)) return
        migrateLegacy(item, catalog, world)
        if (getExpiresTick(item) == null && !isPaused(item)) {
            stamp(item, catalog, world)
            return
        }
        refreshLore(item, catalog, world)
    }

    fun getExpiresTick(item: ItemStack): Long? =
        item.itemMeta?.persistentDataContainer?.get(RottingFoodKeys.expiresTickKey, PersistentDataType.LONG)

    fun getShelfLifeTicks(item: ItemStack): Long? =
        item.itemMeta?.persistentDataContainer?.get(RottingFoodKeys.shelfLifeTicksKey, PersistentDataType.LONG)

    fun isPaused(item: ItemStack): Boolean =
        item.itemMeta?.persistentDataContainer?.get(RottingFoodKeys.pausedKey, PersistentDataType.BYTE) == 1.toByte()

    fun getPausedRemainingTicks(item: ItemStack): Long? =
        item.itemMeta?.persistentDataContainer?.get(RottingFoodKeys.pausedRemainingTicksKey, PersistentDataType.LONG)

    fun setTiming(item: ItemStack, expiresTick: Long, shelfTicks: Long) {
        item.editMeta { meta ->
            meta.persistentDataContainer.set(RottingFoodKeys.expiresTickKey, PersistentDataType.LONG, expiresTick)
            meta.persistentDataContainer.set(RottingFoodKeys.shelfLifeTicksKey, PersistentDataType.LONG, shelfTicks)
            meta.persistentDataContainer.remove(RottingFoodKeys.expiresAtKey)
            meta.persistentDataContainer.remove(RottingFoodKeys.shelfLifeMsKey)
        }
    }

    fun pauseWithRemaining(item: ItemStack, remainingTicks: Long) {
        item.editMeta { meta ->
            meta.persistentDataContainer.set(RottingFoodKeys.pausedKey, PersistentDataType.BYTE, 1)
            meta.persistentDataContainer.set(RottingFoodKeys.pausedRemainingTicksKey, PersistentDataType.LONG, remainingTicks.coerceAtLeast(0))
            meta.persistentDataContainer.remove(RottingFoodKeys.expiresTickKey)
        }
    }

    fun resume(item: ItemStack, world: World) {
        if (!isPaused(item)) return
        val remaining = getPausedRemainingTicks(item) ?: 0L
        val shelf = getShelfLifeTicks(item) ?: remaining
        clearPaused(item)
        setTiming(item, RottingFoodTime.currentTick(world) + remaining, shelf)
    }

    fun clearPaused(item: ItemStack) {
        item.editMeta { meta ->
            meta.persistentDataContainer.remove(RottingFoodKeys.pausedKey)
            meta.persistentDataContainer.remove(RottingFoodKeys.pausedRemainingTicksKey)
        }
    }

    fun remainingTicks(item: ItemStack, world: World): Long {
        if (isPaused(item)) return getPausedRemainingTicks(item) ?: 0L
        val expires = getExpiresTick(item) ?: return 0L
        return (expires - RottingFoodTime.currentTick(world)).coerceAtLeast(0)
    }

    fun isExpired(item: ItemStack, world: World): Boolean {
        if (isPaused(item)) return false
        val expires = getExpiresTick(item) ?: return false
        return RottingFoodTime.currentTick(world) >= expires
    }

    fun mergeExpiry(target: ItemStack, source: ItemStack, world: World) {
        val remaining = minOf(remainingTicks(target, world), remainingTicks(source, world))
        val shelf = minOf(getShelfLifeTicks(target) ?: remaining, getShelfLifeTicks(source) ?: remaining)
        if (isPaused(target) || isPaused(source)) {
            pauseWithRemaining(target, remaining)
        } else {
            setTiming(target, RottingFoodTime.currentTick(world) + remaining, shelf)
        }
    }

    fun refreshLore(item: ItemStack, catalog: RottingFoodCatalog, world: World) {
        if (isPaused(item)) {
            val remaining = getPausedRemainingTicks(item) ?: return
            item.editMeta { meta -> meta.lore(buildLore(meta, item, remaining, frozen = true)) }
            return
        }
        val expires = getExpiresTick(item) ?: return
        val remaining = (expires - RottingFoodTime.currentTick(world)).coerceAtLeast(0)
        item.editMeta { meta -> meta.lore(buildLore(meta, item, remaining, frozen = false)) }
    }

    private fun buildLore(meta: ItemMeta, item: ItemStack, remainingTicks: Long, frozen: Boolean): List<Component> {
        val userLore = meta.lore()?.filterNot { isRottingLine(it) } ?: emptyList()
        val freshness = when {
            remainingTicks <= 0 && !frozen -> Component.text("⚠ Испорчено", NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false)
            frozen -> Component.text("❄ Заморожено: ${RottingFoodTime.formatTicks(remainingTicks)}", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
            else -> Component.text("Свежесть: ${RottingFoodTime.formatTicks(remainingTicks)}", freshnessColor(item, remainingTicks))
                .decoration(TextDecoration.ITALIC, false)
        }
        return userLore + freshness
    }

    private fun isRottingLine(line: Component): Boolean {
        val plain = PlainTextComponentSerializer.plainText().serialize(line)
        return plain.startsWith("Свежесть:") || plain.startsWith("⚠ Испорчено") || plain.startsWith("❄ Заморожено:")
    }

    private fun freshnessColor(item: ItemStack, remainingTicks: Long): NamedTextColor {
        val shelf = getShelfLifeTicks(item) ?: return NamedTextColor.GREEN
        val ratio = remainingTicks.toDouble() / shelf
        return when {
            ratio <= 0.15 -> NamedTextColor.RED
            ratio <= 0.4 -> NamedTextColor.GOLD
            else -> NamedTextColor.GREEN
        }
    }

    private fun migrateLegacy(item: ItemStack, catalog: RottingFoodCatalog, world: World) {
        val pdc = item.itemMeta?.persistentDataContainer ?: return
        if (pdc.has(RottingFoodKeys.expiresTickKey, PersistentDataType.LONG)) return
        if (!pdc.has(RottingFoodKeys.expiresAtKey, PersistentDataType.LONG)) return
        stamp(item, catalog, world)
    }
}
