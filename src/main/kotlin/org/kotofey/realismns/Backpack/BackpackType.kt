package org.kotofey.realismns.backpack

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material

enum class BackpackType(
    val storageSlots: Int,
    val upgradeSlots: Int,
    val displayName: Component,
) {
    IRON(9, 2, Component.text("Железный рюкзак", NamedTextColor.GRAY)),
    DIAMOND(27, 3, Component.text("Алмазный рюкзак", NamedTextColor.AQUA)),
    NETHERITE(54, 4, Component.text("Незеритовый рюкзак", NamedTextColor.DARK_PURPLE)),
    ;

    val pageStorageSize: Int = 45

    val pageCount: Int
        get() = (storageSlots + pageStorageSize - 1) / pageStorageSize

    companion object {
        fun fromLegacy(name: String): BackpackType? = when (name) {
            "LEATHER", "COPPER", "IRON" -> IRON
            "GOLD", "DIAMOND" -> DIAMOND
            "NETHERITE" -> NETHERITE
            else -> runCatching { valueOf(name) }.getOrNull()
        }
    }
}
