package tech.qhuyy.hqngTools

import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ToolMechanics(
    private val plugin: HqngTools
) : Listener {
    private val lastClickedFace = ConcurrentHashMap<UUID, BlockFace>()
    private val lastTriggerTime = ConcurrentHashMap<UUID, Long>()

    private val dBlackList = mutableSetOf<Material>()
    private val cBlackList = mutableSetOf<Material>()
    private val suppressList = mutableSetOf<Material>()
    private val customOverrides = mutableMapOf<Material, ItemStack>()
    private val worldBlackList = mutableSetOf<String>()

    init { loadConfig() }

    fun loadConfig() {
        dBlackList.clear()
        cBlackList.clear()
        suppressList.clear()
        customOverrides.clear()
        worldBlackList.clear()

        plugin.config.getStringList("mechanics.drill.blacklist")
            .mapNotNull { Material.matchMaterial(it) }
            .forEach { dBlackList.add(it) }

        plugin.config.getStringList("mechanics.tree_chopper.blacklist")
            .mapNotNull { Material.matchMaterial(it) }
            .forEach { cBlackList.add(it) }

        plugin.config.getStringList("drops.suppress-list")
            .mapNotNull { Material.matchMaterial(it) }
            .forEach { suppressList.add(it) }

        plugin.config.getStringList("world-blacklist")
            .filterNotNull()
            .map { it.lowercase() }
            .forEach { worldBlackList.add(it) }

        plugin.config.getConfigurationSection("drops.custom-overrides")?.getKeys(false)?.forEach { k ->
            Material.matchMaterial(k)?.let { sm ->
                val targetMaterialName = plugin.config.getString("drops.custom-overrides.$k.material")
                val amount = plugin.config.getInt("drops.custom-overrides.$k.amount", 1)
                targetMaterialName?.let { Material.matchMaterial(it) }?.let {
                    customOverrides[sm] = ItemStack(it, amount)
                }
            }
        }
    }

    @EventHandler
    fun onPlayerInteract(e: PlayerInteractEvent) {
        if(e.action == Action.LEFT_CLICK_BLOCK) {
            lastClickedFace[e.player.uniqueId] = e.blockFace
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(e: BlockBreakEvent) {
        val p = e.player
        val b = e.block
        val t = p.inventory.itemInMainHand
        if(t.type.isAir || !t.hasItemMeta()) return
        val tt = t.itemMeta.persistentDataContainer.get(plugin.toolKey, PersistentDataType.STRING) ?: return
        if(worldBlackList.contains(p.location.world.name.lowercase())) {
            e.isCancelled = true
            return
        }

        val mm = b.type
        when {
            suppressList.contains(mm) -> e.isDropItems = false
            customOverrides.contains(mm) -> {
                e.isDropItems = false
                val d = customOverrides[mm]?.clone() as? ItemStack
                d?.let { b.world.dropItemNaturally(b.location, it) }
            }
        }
    }
}