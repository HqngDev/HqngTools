package tech.qhuyy.hqngTools

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

class HqngTools : JavaPlugin() {
    lateinit var audience: BukkitAudiences
    lateinit var toolKey: NamespacedKey

    override fun onEnable() {
        this.audience = BukkitAudiences.create(this)
        this.toolKey = NamespacedKey(this, "hqngtool_type")
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
