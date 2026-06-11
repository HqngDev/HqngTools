package tech.qhuyy.hqngTools

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class HqngToolsCommand(private val plugin: HqngTools) : CommandExecutor, TabCompleter {

    // Sub-commands exposed to admins
    private val subCommands = listOf("give", "set", "reload")

    // Names shown in tab-complete for tool type argument
    private val toolNames = listOf("drill", "chopper", "multitool")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        // Permission gate
        if (!sender.hasPermission("hqngtools.admin")) {
            sender.sendMessage(plugin.messageManager.getMessage(Messages.NO_PERMISSION))
            return true
        }

        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        return when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            "give"   -> handleGive(sender, args)
            "set"    -> handleSet(sender, args)
            else     -> { sendHelp(sender); true }
        }
    }

    // -----------------------------------------------------------------------
    // /htools reload
    // -----------------------------------------------------------------------
    private fun handleReload(sender: CommandSender): Boolean {
        plugin.reloadAll()
        sender.sendMessage(plugin.messageManager.getMessage(Messages.RELOAD))
        return true
    }

    // -----------------------------------------------------------------------
    // /htools give <player> <drill|chopper|multitool>
    // -----------------------------------------------------------------------
    private fun handleGive(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 3) {
            sender.sendMessage(plugin.messageManager.getMessage(Messages.QUICK_HELP))
            return true
        }

        val target = Bukkit.getPlayer(args[1])
        if (target == null) {
            sender.sendMessage(plugin.messageManager.getMessage(Messages.PLAYER_NOT_FOUND))
            return true
        }

        val toolType = ToolType.fromString(args[2])
        if (toolType == null) {
            sender.sendMessage(plugin.messageManager.getMessage(Messages.INVALID_TOOL))
            return true
        }

        val tool = plugin.createTool(toolType)
        if (tool == null) {
            sender.sendMessage(plugin.messageManager.getMessage(Messages.TOOL_CREATE_ERROR))
            return true
        }

        target.inventory.addItem(tool)

        val itemName = plugin.config.getString("items.${toolType.tag}.name", toolType.tag) ?: toolType.tag
        sender.sendMessage(
            plugin.messageManager.getMessage(
                Messages.GIVE_SUCCESS,
                "item" to itemName,
                "player" to target.name
            )
        )
        return true
    }

    // -----------------------------------------------------------------------
    // /htools set <drill|chopper|multitool>
    // Sets (tags) the item currently held in the executor's main hand.
    // -----------------------------------------------------------------------
    private fun handleSet(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(plugin.messageManager.getMessage(Messages.PLAYER_ONLY))
            return true
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.messageManager.getMessage(Messages.QUICK_HELP))
            return true
        }

        val item: ItemStack = sender.inventory.itemInMainHand
        if (item.type.isAir) {
            sender.sendMessage(plugin.messageManager.getMessage(Messages.EMPTY_HAND))
            return true
        }

        val toolType = ToolType.fromString(args[1])
        if (toolType == null) {
            sender.sendMessage(plugin.messageManager.getMessage(Messages.INVALID_TOOL))
            return true
        }

        // Tag the held item
        val meta = item.itemMeta ?: run {
            sender.sendMessage(plugin.messageManager.getMessage(Messages.TOOL_CREATE_ERROR))
            return true
        }
        meta.persistentDataContainer.set(plugin.toolKey, PersistentDataType.STRING, toolType.tag)
        item.itemMeta = meta

        sender.sendMessage(plugin.messageManager.getMessage(Messages.SET_SUCCESS))
        return true
    }

    // -----------------------------------------------------------------------
    // Help message
    // -----------------------------------------------------------------------
    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(plugin.messageManager.getMessage(Messages.QUICK_HELP))
    }

    // -----------------------------------------------------------------------
    // Tab completion
    // -----------------------------------------------------------------------
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!sender.hasPermission("hqngtools.admin")) return emptyList()

        return when (args.size) {
            1 -> subCommands.filter { it.startsWith(args[0].lowercase()) }

            2 -> when (args[0].lowercase()) {
                "give" -> Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[1].lowercase()) }
                "set"  -> toolNames.filter { it.startsWith(args[1].lowercase()) }
                else   -> emptyList()
            }

            3 -> when (args[0].lowercase()) {
                "give" -> toolNames.filter { it.startsWith(args[2].lowercase()) }
                else   -> emptyList()
            }

            else -> emptyList()
        }
    }
}