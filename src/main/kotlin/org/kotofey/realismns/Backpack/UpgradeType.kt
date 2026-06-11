package org.kotofey.realismns.backpack

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material

enum class UpgradeType(
    val displayName: Component,
    val icon: Material,
    val tier: Int,
    val installable: Boolean,
    val hasFilter: Boolean,
    val configRadiusKey: String?,
) {
    UPGRADE_BASE(Component.text("Основа улучшения", NamedTextColor.GRAY), Material.IRON_NUGGET, 0, false, false, null),
    PICKUP(Component.text("Подбор", NamedTextColor.YELLOW), Material.STICKY_PISTON, 1, true, false, null),
    ADVANCED_PICKUP(Component.text("Продв. подбор", NamedTextColor.GOLD), Material.HOPPER, 2, true, true, null),
    MAGNET(Component.text("Магнит", NamedTextColor.AQUA), Material.COMPASS, 2, true, false, "magnet"),
    ADVANCED_MAGNET(Component.text("Продв. магнит", NamedTextColor.LIGHT_PURPLE), Material.LODESTONE, 3, true, true, "advanced-magnet"),
    WORKSTATION(Component.text("Печка-верстак", NamedTextColor.GOLD), Material.CRAFTING_TABLE, 2, true, false, null),
    UNLOAD(Component.text("Разгрузка", NamedTextColor.YELLOW), Material.HOPPER, 2, true, true, null),
    FRIDGE(Component.text("Холодильник", NamedTextColor.AQUA), Material.SNOW_BLOCK, 2, true, false, null),
    ;

    val canPickup: Boolean get() = this != UPGRADE_BASE
    val canMagnet: Boolean get() = this == MAGNET || this == ADVANCED_MAGNET
    val canWorkstation: Boolean get() = this == WORKSTATION
    val canUnload: Boolean get() = this == UNLOAD
    val canFridge: Boolean get() = this == FRIDGE
}
