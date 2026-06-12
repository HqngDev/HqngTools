package tech.qhuyy.hqngTools

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.persistence.PersistentDataType
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ToolMechanics(private val plugin: HqngTools) : Listener {

    // Track the last block-face the player clicked (for drill orientation)
    private val lastClickedFace = ConcurrentHashMap<UUID, BlockFace>()

    // Per-player cooldown tracker (shared across drill / multitool)
    private val lastTriggerTime = ConcurrentHashMap<UUID, Long>()

    // Re-entrancy guard: prevents our internal breakNaturally calls from
    // recursively triggering the event handler again.
    private val isBreakingInternal = ThreadLocal.withInitial { false }

    // Config-driven sets / maps
    private val drillBlacklist    = mutableSetOf<Material>()
    private val chopperBlacklist  = mutableSetOf<Material>()
    private val suppressList      = mutableSetOf<Material>()
    private val customOverrides   = mutableMapOf<Material, ItemStack>()
    private val worldBlacklist    = mutableSetOf<String>()

    init { loadConfig() }

    // -----------------------------------------------------------------------
    // Config loading (called on init and on /htools reload)
    // -----------------------------------------------------------------------
    fun loadConfig() {
        drillBlacklist.clear()
        chopperBlacklist.clear()
        suppressList.clear()
        customOverrides.clear()
        worldBlacklist.clear()

        plugin.config.getStringList("mechanics.drill.blacklist")
            .mapNotNull { Material.matchMaterial(it) }
            .forEach { drillBlacklist.add(it) }

        plugin.config.getStringList("mechanics.tree_chopper.blacklist")
            .mapNotNull { Material.matchMaterial(it) }
            .forEach { chopperBlacklist.add(it) }

        plugin.config.getStringList("drops.suppress-list")
            .mapNotNull { Material.matchMaterial(it) }
            .forEach { suppressList.add(it) }

        plugin.config.getStringList("world-blacklist")
            .filterNotNull()
            .map { it.lowercase() }
            .forEach { worldBlacklist.add(it) }

        plugin.config.getConfigurationSection("drops.custom-overrides")
            ?.getKeys(false)
            ?.forEach { key ->
                val sourceMat = Material.matchMaterial(key) ?: return@forEach
                val targetName = plugin.config.getString("drops.custom-overrides.$key.material") ?: return@forEach
                val targetMat  = Material.matchMaterial(targetName) ?: return@forEach
                val amount     = plugin.config.getInt("drops.custom-overrides.$key.amount", 1)
                customOverrides[sourceMat] = ItemStack(targetMat, amount)
            }
    }

    // -----------------------------------------------------------------------
    // Events
    // -----------------------------------------------------------------------

    /** Track which face the player last clicked so the drill can orient itself. */
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action == Action.LEFT_CLICK_BLOCK) {
            lastClickedFace[event.player.uniqueId] = event.blockFace
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        // Skip our own internal breaks to avoid infinite recursion
        if (isBreakingInternal.get()) return

        val player = event.player
        val block  = event.block
        val tool   = player.inventory.itemInMainHand

        if (tool.type.isAir || !tool.hasItemMeta()) return

        val toolTypeTag = tool.itemMeta
            .persistentDataContainer
            .get(plugin.toolKey, PersistentDataType.STRING) ?: return

        // Sneak bypass: if the player is holding shift, cancel ALL custom mechanics
        // and let vanilla handle the block break normally (single block, normal drops)
        if (player.isSneaking) return

        // World blacklist check
        if (worldBlacklist.contains(block.world.name.lowercase())) {
            event.isCancelled = true
            player.sendMessage(plugin.messageManager.getMessage(Messages.WORLD_BLACKLISTED))
            return
        }

        // Drop management for the PRIMARY block (the one the player actually hit)
        val autoCollect = plugin.config.getBoolean("auto-collect", true)
        val hasCustomOverride = customOverrides.containsKey(block.type)

        if (hasCustomOverride) {
            // Custom override: suppress vanilla drops, then auto-collect the override item
            event.isDropItems = false
            if (autoCollect) {
                val drop = customOverrides[block.type]?.clone() ?: return
                val remaining = giveToInventory(player.inventory, drop)
                remaining.forEach { item ->
                    block.world.dropItemNaturally(block.location, item)
                }
            } else {
                val drop = customOverrides[block.type]?.clone() ?: return
                block.world.dropItemNaturally(block.location, drop)
            }
        } else {
            applyDropOverride(event, block.type)

            // Auto-collect for the primary block: suppress vanilla drops and give items directly
            if (autoCollect && event.isDropItems) {
                event.isDropItems = false
                val drops = block.getDrops(tool, player)
                val remaining = giveToInventory(player.inventory, drops)
                remaining.forEach { item ->
                    block.world.dropItemNaturally(block.location, item)
                }
            }
        }

        // Dispatch to the correct tool handler
        when (toolTypeTag) {
            ToolType.DRILL.tag       -> handleDrill(player, block, tool)
            ToolType.TREE_CHOPPER.tag -> handleChopper(player, block, tool)
            ToolType.MULTI_TOOL.tag  -> handleMultiTool(player, block, tool)
        }
    }

    // -----------------------------------------------------------------------
    // Tool handlers
    // -----------------------------------------------------------------------

    private fun handleDrill(player: Player, block: Block, tool: ItemStack) {
        if (!checkAndSetCooldown(player, "mechanics.drill.cooldown-ms")) return
        if (drillBlacklist.contains(block.type)) return

        val face    = lastClickedFace[player.uniqueId] ?: BlockFace.UP
        val pattern = plugin.config.getString("mechanics.drill.pattern", "3X3")

        val toBreak = getDrillBlocks(block, face, pattern).filter { canDrillBreak(it) }
        processBlockBreakBatch(player, toBreak, tool)
    }

    private fun handleChopper(player: Player, block: Block, tool: ItemStack) {
        if (!isLog(block.type)) return
        if (chopperBlacklist.contains(block.type)) return

        val toBreak = getTreeBlocks(block).filter { canChopperBreak(it) }
        processBlockBreakBatch(player, toBreak, tool)
    }

    private fun handleMultiTool(player: Player, block: Block, tool: ItemStack) {
        val mat = block.type
        if (isLog(mat)) {
            // Act as tree chopper
            if (chopperBlacklist.contains(mat)) return
            if (!checkAndSetCooldown(player, "mechanics.multi_tool.cooldown-ms")) return

            val toBreak = getTreeBlocks(block).filter { canChopperBreak(it) }
            processBlockBreakBatch(player, toBreak, tool)
        } else {
            // Act as drill
            if (drillBlacklist.contains(mat)) return
            if (!checkAndSetCooldown(player, "mechanics.multi_tool.cooldown-ms")) return

            val face    = lastClickedFace[player.uniqueId] ?: BlockFace.UP
            val pattern = plugin.config.getString("mechanics.drill.pattern", "3X3")

            val toBreak = getDrillBlocks(block, face, pattern).filter { canDrillBreak(it) }
            processBlockBreakBatch(player, toBreak, tool)
        }
    }

    // -----------------------------------------------------------------------
    // Helpers: cooldown
    // -----------------------------------------------------------------------

    /** Returns true if the player is NOT on cooldown, and updates the timestamp. */
    private fun checkAndSetCooldown(player: Player, configKey: String): Boolean {
        val now       = System.currentTimeMillis()
        val lastTime  = lastTriggerTime[player.uniqueId] ?: 0L
        val cooldownMs = plugin.config.getLong(configKey, 300L)
        if (now - lastTime < cooldownMs) return false
        lastTriggerTime[player.uniqueId] = now
        return true
    }

    // -----------------------------------------------------------------------
    // Helpers: block predicates
    // -----------------------------------------------------------------------

    private fun canDrillBreak(block: Block): Boolean {
        val mat = block.type
        return !drillBlacklist.contains(mat) && mat != Material.AIR && mat.isBlock && mat.hardness >= 0f
    }

    private fun canChopperBreak(block: Block): Boolean {
        val mat = block.type
        return !chopperBlacklist.contains(mat) && mat != Material.AIR && mat.isBlock && mat.hardness >= 0f
    }

    private fun isLog(mat: Material): Boolean {
        val name = mat.name
        return name.contains("_LOG") || name.contains("_WOOD") || name.contains("_STEM") || name == "MANGROVE_ROOTS"
    }

    private fun isLeaves(mat: Material): Boolean {
        val name = mat.name
        return name.contains("_LEAVES") || name == "NETHER_WART_BLOCK" || name == "WARPED_WART_BLOCK"
    }

    // -----------------------------------------------------------------------
    // Helpers: drop override
    // -----------------------------------------------------------------------

    private fun applyDropOverride(event: BlockBreakEvent, mat: Material) {
        when {
            suppressList.contains(mat) -> event.isDropItems = false
            customOverrides.containsKey(mat) -> {
                event.isDropItems = false
                val drop = customOverrides[mat]?.clone() ?: return
                event.block.world.dropItemNaturally(event.block.location, drop)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Block selection
    // -----------------------------------------------------------------------

    private fun getDrillBlocks(center: Block, face: BlockFace, pattern: String?): List<Block> {
        val isCross = pattern.equals("CROSS", ignoreCase = true)

        val offsets = if (isCross) {
            arrayOf(
                intArrayOf(0, 0), intArrayOf(0, 1), intArrayOf(0, -1),
                intArrayOf(1, 0), intArrayOf(-1, 0)
            )
        } else {
            arrayOf(
                intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1),
                intArrayOf(0, -1),  intArrayOf(0, 0),  intArrayOf(0, 1),
                intArrayOf(1, -1),  intArrayOf(1, 0),  intArrayOf(1, 1)
            )
        }

        return offsets.mapNotNull { (a, b) ->
            val relative = when (face) {
                BlockFace.UP,    BlockFace.DOWN  -> center.getRelative(a, 0, b)
                BlockFace.NORTH, BlockFace.SOUTH -> center.getRelative(a, b, 0)
                BlockFace.EAST,  BlockFace.WEST  -> center.getRelative(0, b, a)
                else                             -> center.getRelative(a, 0, b)
            }
            if (relative.location != center.location) relative else null
        }
    }

    private fun getTreeBlocks(startBlock: Block): List<Block> {
        val logs   = mutableListOf<Block>()
        val leaves = mutableListOf<Block>()

        val queue   = LinkedList<Block>()
        val visited = mutableSetOf<Block>()

        queue.add(startBlock)
        visited.add(startBlock)

        val maxBlocks = plugin.config.getInt("mechanics.tree_chopper.max-blocks", 1000)

        while (queue.isNotEmpty() && visited.size < maxBlocks) {
            val current = queue.poll()

            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        if (dx == 0 && dy == 0 && dz == 0) continue
                        val neighbor = current.getRelative(dx, dy, dz)
                        if (!visited.contains(neighbor)) {
                            val mat = neighbor.type
                            if (isLog(mat) || isLeaves(mat)) {
                                visited.add(neighbor)
                                queue.add(neighbor)
                                if (isLog(mat)) logs.add(neighbor) else leaves.add(neighbor)
                            }
                        }
                    }
                }
            }
        }

        return logs + leaves
    }

    // -----------------------------------------------------------------------
    // Batch block breaking (Folia-compatible)
    // -----------------------------------------------------------------------

    private fun processBlockBreakBatch(player: Player, blocks: List<Block>, tool: ItemStack) {
        if (blocks.isEmpty()) return

        val autoCollect = plugin.config.getBoolean("auto-collect", true)
        val batchSize   = plugin.config.getInt("performance.batch-size", 15)
        val queue       = LinkedList(blocks)
        val anchorLocation: Location = blocks[0].location

        // We need a mutable reference inside the lambda for self-cancellation
        var task: ScheduledTask? = null

        task = Bukkit.getRegionScheduler().runAtFixedRate(plugin, anchorLocation, { _ ->
            repeat(batchSize) {
                val block = queue.poll() ?: run {
                    task?.cancel()
                    return@runAtFixedRate
                }

                if (!block.type.isAir) {
                    isBreakingInternal.set(true)
                    try {
                        val mat = block.type
                        when {
                            suppressList.contains(mat) ->
                                block.type = Material.AIR
                            customOverrides.containsKey(mat) -> {
                                val drop = customOverrides[mat]?.clone()
                                block.type = Material.AIR
                                if (autoCollect) {
                                    val remaining = giveToInventory(player.inventory, drop)
                                    remaining.forEach { item ->
                                        block.world.dropItemNaturally(block.location, item)
                                    }
                                } else {
                                    drop?.let { block.world.dropItemNaturally(block.location, it) }
                                }
                            }
                            else -> {
                                if (autoCollect) {
                                    // Suppress natural drops, break the block, then collect drops ourselves
                                    val drops = block.getDrops(tool, player)
                                    block.type = Material.AIR
                                    val remaining = giveToInventory(player.inventory, drops)
                                    if (remaining.isNotEmpty()) {
                                        remaining.forEach { item ->
                                            block.world.dropItemNaturally(block.location, item)
                                        }
                                        player.sendMessage(plugin.messageManager.getMessage(Messages.INVENTORY_FULL))
                                    }
                                } else {
                                    block.breakNaturally(tool)
                                }
                            }
                        }
                    } finally {
                        isBreakingInternal.set(false)
                    }
                }
            }

            if (queue.isEmpty()) task?.cancel()
        }, 1L, 1L)
    }

    /**
     * Gives the given [drops] to the [inventory]. Items that don't fit are
     * returned in a list so the caller can drop them on the ground.
     */
    private fun giveToInventory(inventory: PlayerInventory, drops: Collection<ItemStack>): List<ItemStack> {
        val leftover = mutableListOf<ItemStack>()
        for (item in drops) {
            if (item.type.isAir) continue
            val result = inventory.addItem(item)
            result.values.forEach { leftover.add(it) }
        }
        return leftover
    }

    /**
     * Convenience overload that accepts a nullable single [ItemStack].
     */
    private fun giveToInventory(inventory: PlayerInventory, item: ItemStack?): List<ItemStack> {
        if (item == null || item.type.isAir) return emptyList()
        return giveToInventory(inventory, listOf(item))
    }
}