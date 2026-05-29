package org.kotofey.realismns

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionType

class RealismnsListener(private val plugin: Realismns) : Listener {
    private val protectedMud = mutableSetOf<String>()
    private val trampleCounts = mutableMapOf<String, Int>()
    private val lastStepPos = mutableMapOf<String, String>()

    private val trampleChain = mapOf(
        Material.GRASS_BLOCK to Material.DIRT,
        Material.DIRT to Material.COARSE_DIRT,
        Material.COARSE_DIRT to Material.ROOTED_DIRT,
        Material.ROOTED_DIRT to Material.FARMLAND,
        Material.FARMLAND to Material.DIRT_PATH
    )

    @EventHandler
    fun onMudPlace(event: BlockPlaceEvent) {
        if (event.block.type != Material.MUD) return
        scheduleMudDrying(event.block.location)
    }

    @EventHandler
    fun onWaterBottleUse(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val item = event.item ?: return
        if (item.type != Material.POTION) return
        val meta = item.itemMeta as? PotionMeta ?: return
        if (meta.basePotionType != PotionType.WATER) return

        val block = event.clickedBlock ?: return
        if (block.type != Material.DIRT) return

        event.isCancelled = true
        block.type = Material.MUD

        item.amount -= 1
        val dropLoc = block.location.add(0.5, 1.0, 0.5)
        block.world.dropItemNaturally(dropLoc, org.bukkit.inventory.ItemStack(Material.GLASS_BOTTLE))
        block.world.spawnParticle(Particle.SPLASH, dropLoc, 15, 0.3, 0.3, 0.3, 0.1)
        block.world.playSound(dropLoc, Sound.ITEM_BOTTLE_EMPTY, 1.0f, 1.0f)

        scheduleMudDrying(block.location)
    }

    @EventHandler
    fun onShovelUse(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        val item = event.item ?: return
        if (!item.type.name.endsWith("_SHOVEL")) return

        val block = event.clickedBlock ?: return
        if (block.type != Material.MUD) return

        protectedMud.add(key(block.location))
        val loc = block.location.add(0.5, 1.0, 0.5)
        block.world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 10, 0.3, 0.2, 0.3, 0.05)
        block.world.playSound(loc, Sound.BLOCK_MUD_PLACE, 0.8f, 1.5f)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (!player.isSprinting || !player.isOnGround) return
        val to = event.to ?: return
        if (to.blockX == event.from.blockX && to.blockZ == event.from.blockZ) return

        val block = to.clone().subtract(0.0, 0.5, 0.0).block
        val next = trampleChain[block.type] ?: return

        val k = key(block.location)
        val playerKey = "${player.uniqueId}:$k"
        if (lastStepPos[player.uniqueId.toString()] == k) return
        lastStepPos[player.uniqueId.toString()] = k

        val threshold = plugin.config.getInt("trampling.steps-per-stage", 20)
        val count = (trampleCounts[k] ?: 0) + 1
        trampleCounts[k] = count

        val half = block.location.add(0.5, 0.1, 0.5)
                block.world.spawnParticle(Particle.FALLING_DUST, half, 3, 0.2, 0.0, 0.2, 0.05, block.blockData)

        if (count >= threshold) {
            trampleCounts.remove(k)
            block.type = next
            val cloc = block.location.add(0.5, 0.5, 0.5)
            block.world.spawnParticle(Particle.FALLING_DUST, cloc, 25, 0.4, 0.2, 0.4, 0.1, block.blockData)
            block.world.playSound(cloc, Sound.BLOCK_GRASS_BREAK, 1.0f, 0.8f)
        }
    }

    private fun scheduleMudDrying(location: Location) {
        val delay = plugin.config.getInt("mud-to-clay.delay-seconds", 10) * 20L
        val k = key(location)
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (k in protectedMud) {
                protectedMud.remove(k)
                return@Runnable
            }
            val block = location.block
            if (block.type == Material.MUD) {
                block.type = Material.CLAY
                val cloc = block.location.add(0.5, 0.5, 0.5)
                block.world.spawnParticle(Particle.POOF, cloc, 25, 0.4, 0.4, 0.4, 0.05)
                block.world.playSound(cloc, Sound.BLOCK_MUD_BREAK, 0.8f, 1.0f)
                block.world.playSound(cloc, Sound.BLOCK_GRAVEL_PLACE, 1.0f, 0.8f)
            }
        }, delay)
    }

    private fun key(location: Location): String =
        "${location.world?.name}:${location.blockX}:${location.blockY}:${location.blockZ}"
}

class Realismns : JavaPlugin() {

    override fun onEnable() {
        saveDefaultConfig()
        server.pluginManager.registerEvents(RealismnsListener(this), this)
        server.pluginManager.registerEvents(CropMechanics(this), this)
    }

    override fun onDisable() {
    }
}
