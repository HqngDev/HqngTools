package tech.qhuyy.hqngTools

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class MessageManager(private val plugin: HqngTools) {
    private val fileName: String = "messages.yml"
    private val miniMessage: MiniMessage = MiniMessage.miniMessage()
    private val cache = mutableMapOf<String, String>()

    init { load() }

    fun load() {
        cache.clear()
        val f = File(plugin.dataFolder, fileName)
        if (!f.exists()) plugin.saveResource(fileName, false)
        val c = YamlConfiguration.loadConfiguration(f)
        // Flatten all keys (including nested ones) so "admin.reload" etc. also work
        flattenKeys(c, "", cache)
    }

    /**
     * Recursively flatten a YamlConfiguration into a flat Map<String, String>.
     * Nested sections are joined with ".".
     */
    private fun flattenKeys(
        section: org.bukkit.configuration.ConfigurationSection,
        prefix: String,
        out: MutableMap<String, String>
    ) {
        for (key in section.getKeys(false)) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            val value = section.get(key)
            when {
                value is org.bukkit.configuration.ConfigurationSection ->
                    flattenKeys(value, fullKey, out)
                value is String ->
                    out[fullKey] = value
                value != null ->
                    out[fullKey] = value.toString()
            }
        }
    }

    fun reload() = load()

    fun getMessage(m: Messages, vararg p: Pair<String, String>): Component =
        getMessage(m.key, *p)

    fun getMessage(k: String, vararg p: Pair<String, String>): Component {
        val raw = cache[k] ?: "<red>Missing message key: $k</red>"
        val resolvers = p.map { (tag, value) ->
            Placeholder.component(tag, Component.text(value))
        }.toTypedArray()
        return miniMessage.deserialize(raw, *resolvers)
    }

    /** Raw string (for replacing placeholders manually or building item names). */
    fun getRaw(k: String): String = cache[k] ?: ""

    /** Raw string list from config (not messages) – convenience wrapper. */
    fun getConfigStringList(k: String): List<String> =
        plugin.config.getStringList(k)
}