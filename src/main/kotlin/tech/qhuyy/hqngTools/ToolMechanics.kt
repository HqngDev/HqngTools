package tech.qhuyy.hqngTools

import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
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
}