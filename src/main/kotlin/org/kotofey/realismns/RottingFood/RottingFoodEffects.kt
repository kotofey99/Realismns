package org.kotofey.realismns.RottingFood

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

object RottingFoodEffects {

    fun applySpoiled(player: Player, catalog: RottingFoodCatalog) {
        player.sendMessage(Component.text(catalog.spoiledMessage(), NamedTextColor.RED))
        player.addPotionEffect(
            PotionEffect(
                PotionEffectType.POISON,
                catalog.spoiledPoisonDuration() * 20,
                catalog.spoiledPoisonLevel() - 1,
            ),
        )
        player.addPotionEffect(
            PotionEffect(
                PotionEffectType.HUNGER,
                catalog.spoiledHungerDuration() * 20,
                catalog.spoiledHungerLevel() - 1,
            ),
        )
        player.foodLevel = (player.foodLevel - 4).coerceAtLeast(0)
    }
}
