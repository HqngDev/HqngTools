# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**HqngTools** is a Minecraft server plugin for Paper 1.21.8 (Folia-compatible) written in Kotlin. It provides three custom tools — **Drill**, **Tree Chopper**, and **Multi-Tool** — that enhance block breaking with area-of-effect mechanics. Built with Gradle, uses Shadow to produce a fat JAR.

- **Group:** `tech.qhuyy`
- **Package:** `tech.qhuyy.hqngTools`
- **Main class:** `tech.qhuyy.hqngTools.HqngTools`
- **Paper API:** `1.21.8-R0.1-SNAPSHOT` (compileOnly — not bundled)
- **Kotlin:** 2.3.10, JVM toolchain 21

## Build & Run

```bash
./gradlew build          # Build the shadow JAR (output: build/libs/HqngTools-1.0.0.jar)
./gradlew shadowJar      # Just the shadow JAR
./gradlew clean          # Clean build artifacts
```

To test, copy the JAR from `build/libs/` into the server's `plugins/` folder. A `run/` directory is gitignored for local testing.

## Architecture

### Plugin Lifecycle (`HqngTools.kt`)
- `onEnable()` saves default configs, registers the `toolKey` (a `NamespacedKey` for PDC tagging), creates `MessageManager` and `ToolMechanics`, registers events, and registers the `/htools` command.
- `createTool(type: ToolType)` builds an `ItemStack` from the `items.<type>` config section (material, MiniMessage name, lore, enchants, glow) and tags it in the **Persistent Data Container (PDC)** with `toolKey`.
- `reloadAll()` fully reloads config, messages, and mechanics.

### Core Systems

| Class | File | Responsibility |
|---|---|---|
| `HqngTools` | `HqngTools.kt` | Main plugin class, item creation, lifecycle |
| `ToolMechanics` | `ToolMechanics.kt` | Event listener: block break logic, drill/chopper/multi-tool handlers, drop management, batch breaking |
| `HqngToolsCommand` | `HqngToolsCommand.kt` | Command executor + tab completer for `/htools give|set|reload` |
| `MessageManager` | `MessageManager.kt` | Loads `messages.yml` into a flat cache, resolves MiniMessage strings with placeholders |
| `Messages` | `Messages.kt` | Enum mapping friendly keys → message config paths |
| `ToolType` | `ToolType.kt` | Enum with `tag` + `aliases` for drill/tree_chopper/multi_tool; `fromString()` lookup |

### Tool Mechanics

All three tools work by intercepting `BlockBreakEvent`, reading the tool type from the held item's PDC, then breaking additional blocks in batch:

1. **Drill** — Breaks a 3×3 or CROSS pattern of blocks perpendicular to the clicked face. Respects `drill.blacklist` and `drill.cooldown-ms`.
2. **Tree Chopper** — BFS-finds connected logs (matching `_LOG`, `_WOOD`, `_STEM`, `MANGROVE_ROOTS`) and leaves (matching `_LEAVES`, nether wart blocks), then breaks them. Respects `max-blocks` cap and `chopper.blacklist`.
3. **Multi-Tool** — Auto-detects mode: acts as Tree Chopper on logs, Drill on everything else.

**Drop management** is applied to every block broken (including the primary block):
- `suppress-list` → block becomes AIR with no drops.
- `custom-overrides` → replaces normal drops with a specified `ItemStack`.

**Batch breaking** uses Folia's `RegionScheduler.runAtFixedRate` to break blocks at `performance.batch-size` per tick. A `ThreadLocal<Boolean>` re-entrancy guard prevents the internal `breakNaturally` calls from re-triggering the event handler.

### Command System (`/htools`)
Requires `hqngtools.admin` permission (default: op).
- `give <player> <type>` — Creates a tool from config and adds it to the target's inventory.
- `set <type>` — Tags the item in the sender's main hand with the tool type via PDC.
- `reload` — Full config + mechanics reload.

### Configuration Files
- **`config.yml`** — Tool mechanics (drill pattern, cooldowns, blacklists, max-blocks), drop management, world blacklist, performance settings, item definitions.
- **`messages.yml`** — All user-facing messages in Vietnamese using MiniMessage format.
- **`plugin.yml`** — Plugin metadata, `folia-supported: true`, command + permission registration.

## Key Patterns
- **PDC tagging**: Tools are identified by a `NamespacedKey("hqngtool_type")` stored as `PersistentDataType.STRING` in the item's PDC. This persists across inventory moves and server restarts.
- **MiniMessage**: All user-facing text uses Adventure's MiniMessage format. Display names and lore on created items are deserialized from config strings.
- **Folia compatibility**: Task scheduling uses `Bukkit.getRegionScheduler().runAtFixedRate()` instead of `Bukkit.getScheduler()`.
- **Config-driven**: Almost all behavior (blacklists, cooldowns, patterns, drops, item definitions) is driven by `config.yml`. The `loadConfig()` method in `ToolMechanics` rebuilds in-memory sets/maps on reload.
