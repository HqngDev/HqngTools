package tech.qhuyy.hqngTools

enum class ToolType(val tag: String, val aliases: List<String>) {
    DRILL("drill", listOf("drill")),
    TREE_CHOPPER("tree_chopper", listOf("tree_chopper", "chopper")),
    MULTI_TOOL("multi_tool", listOf("multi_tool", "multitool"));

    companion object {
        fun fromString(input: String): ToolType? {
            val lower = input.lowercase()
            return entries.find { type -> type.aliases.any { it.equals(lower, ignoreCase = true) } }
        }

        val tabCompleteNames: List<String> = entries.map { it.aliases[0] }
    }
}
