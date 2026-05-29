package org.kotofey.realismns

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.data.type.Farmland
import org.bukkit.entity.ThrownPotion
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockFertilizeEvent
import org.bukkit.event.block.BlockGrowEvent
import org.bukkit.event.entity.PotionSplashEvent
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType
import kotlin.random.Random

class CropMechanics(private val plugin: Realismns) : Listener {
    private val random = Random

    private val cropTypes = setOf(
        Material.WHEAT,
        Material.CARROTS,
        Material.POTATOES,
        Material.BEETROOTS,
        Material.MELON_STEM,
        Material.PUMPKIN_STEM
    )

    @EventHandler
    fun onCropGrow(event: BlockGrowEvent) {
        val crop = event.block
        if (crop.type !in cropTypes) return

        val below = crop.location.subtract(0.0, 1.0, 0.0).block
        if (below.type != Material.FARMLAND) return

        val farmland = below.blockData as? Farmland ?: return
        if (farmland.moisture > 0) return

        event.isCancelled = true

        if (random.nextDouble() < plugin.config.getDouble("crops.wither-chance", 0.3)) {
            val loc = crop.location.add(0.5, 0.5, 0.5)
            crop.type = Material.DEAD_BUSH
            crop.world.spawnParticle(Particle.POOF, loc, 15, 0.3, 0.3, 0.3, 0.02)
            crop.world.playSound(loc, Sound.BLOCK_GRASS_BREAK, 0.8f, 0.7f)
        }
    }

    @EventHandler
    fun onBoneMeal(event: BlockFertilizeEvent) {
        val crop = event.block
        if (crop.type !in cropTypes) return

        val below = crop.location.subtract(0.0, 1.0, 0.0).block
        if (below.type != Material.FARMLAND) return

        val farmland = below.blockData as? Farmland ?: return
        if (farmland.moisture > 0) return

        event.setCancelled(true)
    }

    @EventHandler
    fun onPotionSplash(event: PotionSplashEvent) {
        val thrown = event.entity
        val item = thrown.item
        if (item.type != Material.SPLASH_POTION) return

        val meta = item.itemMeta as? PotionMeta ?: return
        if (meta.basePotionType != PotionType.WATER) return

        val radius = plugin.config.getInt("crops.splash-radius", 6)
        hydrateNearby(thrown.location, radius)
    }

    private fun hydrateNearby(center: Location, radius: Int) {
        val world = center.world ?: return
        val cx = center.blockX
        val cy = center.blockY
        val cz = center.blockZ

        world.spawnParticle(Particle.SPLASH, center, 50, 0.5, 0.5, 0.5, 0.1)
        world.playSound(center, Sound.ITEM_BOTTLE_EMPTY, 1.0f, 0.8f)

        for (x in cx - radius..cx + radius) {
            for (z in cz - radius..cz + radius) {
                val dx = x - cx
                val dz = z - cz
                if (dx * dx + dz * dz > radius * radius) continue

                for (y in cy - radius..cy + radius) {
                    val block = world.getBlockAt(x, y, z)
                    if (block.type != Material.FARMLAND) continue

                    val farmland = block.blockData as? Farmland ?: continue
                    farmland.moisture = 7
                    block.blockData = farmland

                    world.spawnParticle(Particle.FALLING_WATER, block.location.add(0.5, 1.0, 0.5), 3, 0.2, 0.1, 0.2, 0.02)
                }
            }
        }
    }
}
