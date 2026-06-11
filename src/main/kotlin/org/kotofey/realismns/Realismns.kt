package org.kotofey.realismns

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
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
import org.kotofey.realismns.backpack.BackpackListener
import org.kotofey.realismns.backpack.BackpackManager
import org.kotofey.realismns.backpack.BackpackRecipes
import org.kotofey.realismns.backpack.UpgradeItems
import org.kotofey.realismns.backpack.UpgradeMechanics
import org.kotofey.realismns.backpack.UpgradeRecipes
import org.kotofey.realismns.RottingFood.RottingFoodCatalog
import org.kotofey.realismns.RottingFood.RottingFoodKeys
import org.kotofey.realismns.RottingFood.RottingFoodListener
import org.kotofey.realismns.RottingFood.RottingFoodTask

class RealismnsListener(
    private val plugin: Realismns,
    private val biomeUtils: BiomeUtils,
    private val features: FeatureFlags,
) : Listener {
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
        if (!features.mudDrying || event.block.type != Material.MUD) return
        scheduleMudDrying(event.block.location)
    }

    @EventHandler
    fun onMagmaNearMud(event: BlockPlaceEvent) {
        if (!features.mudDrying || !isMagmaSource(event.block.type)) return
        val magmaLoc = event.block.location
        val mudBelow = magmaLoc.clone().subtract(0.0, 1.0, 0.0).block
        if (mudBelow.type == Material.MUD) scheduleMudDrying(mudBelow.location)
        val mudAbove = magmaLoc.clone().add(0.0, 1.0, 0.0).block
        if (mudAbove.type == Material.MUD) scheduleMudDrying(mudAbove.location)
    }

    @EventHandler
    fun onWaterBottleUse(event: PlayerInteractEvent) {
        if (!features.mudWaterBottle) return
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

        if (features.mudDrying) scheduleMudDrying(block.location)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!features.trampling) return
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

        val baseThreshold = plugin.config.getInt("trampling.steps-per-stage", 20)
        val threshold = (baseThreshold * biomeUtils.trampleStepsMultiplier(block.location)).toInt().coerceAtLeast(1)
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
        val delay = dryingDelayTicks(location) ?: return
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val block = location.block
            if (block.type != Material.MUD) return@Runnable
            if (dryingDelayTicks(location) == null) return@Runnable
            convertMudToClay(block)
        }, delay)
    }

    private fun dryingDelayTicks(location: Location): Long? {
        val world = location.world ?: return null
        if (world.environment == World.Environment.NETHER) {
            val seconds = plugin.config.getInt("mud-to-clay.nether-delay-seconds", 2)
            return seconds.coerceAtLeast(1).toLong() * 20L
        }
        if (isMudNearMagma(location)) {
            val seconds = plugin.config.getInt("mud-to-clay.magma-delay-seconds", 15)
            return seconds.coerceAtLeast(1).toLong() * 20L
        }
        return null
    }

    private fun isMudNearMagma(location: Location): Boolean {
        val block = location.block
        return isMagmaSource(block.getRelative(0, -1, 0).type)
            || isMagmaSource(block.getRelative(0, 1, 0).type)
    }

    private fun isMagmaSource(material: Material): Boolean =
        material == Material.MAGMA_BLOCK || material == Material.LAVA

    private fun convertMudToClay(block: Block) {
        block.type = Material.CLAY
        val cloc = block.location.add(0.5, 0.5, 0.5)
        block.world.spawnParticle(Particle.POOF, cloc, 25, 0.4, 0.4, 0.4, 0.05)
        block.world.playSound(cloc, Sound.BLOCK_MUD_BREAK, 0.8f, 1.0f)
        block.world.playSound(cloc, Sound.BLOCK_GRAVEL_PLACE, 1.0f, 0.8f)
    }

    private fun key(location: Location): String =
        "${location.world?.name}:${location.blockX}:${location.blockY}:${location.blockZ}"
}

class Realismns : JavaPlugin() {

    lateinit var backpackManager: BackpackManager
        private set
    lateinit var upgradeMechanics: UpgradeMechanics
        private set
    lateinit var features: FeatureFlags
        private set
    private var rottingFoodTask: RottingFoodTask? = null

    override fun onEnable() {
        saveDefaultConfig()
        reloadConfig()
        features = FeatureFlags(this)
        features.load(this)

        val biomeUtils = BiomeUtils(config)
        server.pluginManager.registerEvents(RealismnsListener(this, biomeUtils, features), this)
        if (features.crops) {
            server.pluginManager.registerEvents(CropMechanics(this, biomeUtils), this)
        }

        if (features.rottingFood) {
            RottingFoodKeys.init(this)
        }

        if (features.backpacks) {
            UpgradeItems.init(this)
            backpackManager = BackpackManager(this)
            backpackManager.load()
            upgradeMechanics = UpgradeMechanics(this, backpackManager)
            if (features.backpackUpgrades) {
                server.pluginManager.registerEvents(upgradeMechanics, this)
                upgradeMechanics.startTask()
            }
            BackpackRecipes(this, backpackManager).register()
            if (features.backpackUpgrades) {
                UpgradeRecipes(this).register()
            }
        }

        var rottingCatalog: RottingFoodCatalog? = null
        if (features.rottingFood) {
            rottingCatalog = RottingFoodCatalog(this).also { it.load() }
            server.pluginManager.registerEvents(RottingFoodListener(this, rottingCatalog), this)
        }

        if (features.backpacks) {
            server.pluginManager.registerEvents(
                BackpackListener(backpackManager, upgradeMechanics, rottingCatalog),
                this,
            )
        }

        if (features.rottingFood && rottingCatalog != null) {
            val fridgeBackpack = if (::backpackManager.isInitialized) backpackManager else null
            rottingFoodTask = RottingFoodTask(this, rottingCatalog, fridgeBackpack).also { it.start() }
        }
    }

    override fun onDisable() {
        rottingFoodTask?.stop()
        if (::backpackManager.isInitialized) {
            backpackManager.save()
        }
    }
}
