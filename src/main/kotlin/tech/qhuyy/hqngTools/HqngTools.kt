package tech.qhuyy.hqngTools

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class HqngTools : JavaPlugin() {
    lateinit var toolKey: NamespacedKey
    lateinit var messageManager: MessageManager
    lateinit var toolMechanics: ToolMechanics

    private val miniMessage = MiniMessage.miniMessage()

    override fun onEnable() {
        // Save default configs
        saveDefaultConfig()
        saveResourceIfAbsent("messages.yml")

        toolKey = NamespacedKey(this, "hqngtool_type")
        messageManager = MessageManager(this)
        toolMechanics = ToolMechanics(this)

        // Register listener
        server.pluginManager.registerEvents(toolMechanics, this)

        // Register command + tab completer
        val htoolsCommand = HqngToolsCommand(this)
        getCommand("htools")?.let { cmd ->
            cmd.setExecutor(htoolsCommand)
            cmd.tabCompleter = htoolsCommand
        }

        logger.info("HqngTools enabled!")
    }

    override fun onDisable() {
        logger.info("HqngTools disabled!")
    }

    // -----------------------------------------------------------------------
    // Full config reload (config.yml + messages.yml + mechanics)
    // -----------------------------------------------------------------------
    fun reloadAll() {
        reloadConfig()
        messageManager.reload()
        toolMechanics.loadConfig()
    }

    // -----------------------------------------------------------------------
    // Item creation from config (items.<type> section in config.yml)
    // -----------------------------------------------------------------------
    fun createTool(type: ToolType): ItemStack? {
        val path = "items.${type.tag}"
        if (!config.contains(path)) return null

        val matName = config.getString("$path.material", "NETHERITE_PICKAXE") ?: "NETHERITE_PICKAXE"
        val material = Material.matchMaterial(matName) ?: Material.NETHERITE_PICKAXE
        val glow = config.getBoolean("$path.glow", true)

        val item = ItemStack(material)
        val meta: ItemMeta = item.itemMeta ?: return item

        // Tag the item with its tool type
        meta.persistentDataContainer.set(toolKey, PersistentDataType.STRING, type.tag)

        // Display name (from config)
        val rawName = config.getString("$path.name", type.tag)!!
        meta.displayName(miniMessage.deserialize(rawName))

        // Lore (from config)
        val loreLines = config.getStringList("$path.lore")
        meta.lore(loreLines.map { miniMessage.deserialize(it) })

        // Enchantments
        config.getStringList("$path.enchants").forEach { enchantStr ->
            val parts = enchantStr.split(":")
            if (parts.size < 2) return@forEach
            val enchantName = parts[0].lowercase()
            val level = parts[1].toIntOrNull() ?: return@forEach
            val key = NamespacedKey.minecraft(enchantName)
            val enchantment = Registry.ENCHANTMENT.get(key)
            enchantment?.let { meta.addEnchant(it, level, true) }
        }

        // Glow override (enchantment glint without enchant)
        if (glow) meta.setEnchantmentGlintOverride(true)

        meta.isUnbreakable = true
        item.itemMeta = meta
        return item
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------
    private fun saveResourceIfAbsent(name: String) {
        val f = java.io.File(dataFolder, name)
        if (!f.exists()) saveResource(name, false)
    }
}
