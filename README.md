# HqngTools

> Custom tools for Paper 1.21.8 (Folia-compatible) that enhance block breaking with area-of-effect mechanics.

## Features

- **Drill** — Breaks a 3×3 or CROSS pattern of blocks perpendicular to the clicked face.
- **Tree Chopper** — BFS-finds connected logs and leaves, then breaks the entire tree in one swing.
- **Multi-Tool** — Auto-detects mode: acts as Tree Chopper on logs, Drill on everything else.
- **Drop management** — Suppress drops for specified blocks, or override them with custom items (e.g. ores drop ingots directly).
- **Auto-collect** — Broken blocks go straight into the player's inventory.
- **Folia-compatible** — Uses `RegionScheduler` for batch breaking; works on Folia without breaking region threading.
- **Config-driven** — Blacklists, cooldowns, patterns, drops, and item definitions are all in `config.yml`.
- **MiniMessage** — All user-facing text uses Adventure's MiniMessage format (Vietnamese by default).

## Requirements

- Paper 1.21.8 or a compatible fork (Folia supported)
- Java 21

## Installation

1. Build the plugin:

   ```bash
   ./gradlew shadowJar
   ```

2. Copy the JAR from `build/libs/HqngTools-1.0.0.jar` into your server's `plugins/` folder.
3. Start the server. The plugin generates `config.yml` and `messages.yml` on first run.
4. Edit the configs to your liking, then run `/htools reload`.

## Usage

### Commands

All commands require `hqngtools.admin` (default: op).

| Command | Description |
|---|---|
| `/htools give <player> <drill\|chopper\|multitool>` | Create a tool from config and give it to a player |
| `/htools set <drill\|chopper\|multitool>` | Tag the item in your main hand as a tool |
| `/htools reload` | Reload config, messages, and mechanics |

### Tool behavior

- **Sneak to bypass** — Holding shift cancels all custom mechanics and lets vanilla handle the break normally (single block, normal drops).
- **Drill** — Left-click a block face to set the orientation. The pattern (3×3 or CROSS) is configured in `config.yml`.
- **Tree Chopper** — Break any log in a tree; connected logs and leaves are found via BFS up to `max-blocks` (default 1000).
- **Multi-Tool** — Detects the block type automatically: logs trigger tree chopping, everything else triggers drilling.

### Configuration

`config.yml` is the single source of truth for almost all behavior:

- `mechanics.drill.pattern` — `3X3` or `CROSS`
- `mechanics.drill.cooldown-ms` — Cooldown between drill triggers
- `mechanics.tree_chopper.max-blocks` — BFS cap to prevent lag
- `drops.suppress-list` — Blocks that drop nothing
- `drops.custom-overrides` — Blocks with custom drop items (e.g. `IRON_ORE` → `IRON_INGOT`)
- `world-blacklist` — Worlds where tools are disabled
- `performance.batch-size` — Blocks broken per tick in the batch scheduler
- `items.<type>` — Material, name, lore, enchants, and glow for each tool

`messages.yml` contains all user-facing strings in MiniMessage format.

## Project Structure

```
src/main/kotlin/tech/qhuyy/hqngTools/
├── HqngTools.kt           # Main plugin class, item creation, lifecycle
├── ToolMechanics.kt       # Event listener: block break logic, batch breaking
├── HqngToolsCommand.kt    # /htools command executor + tab completer
├── MessageManager.kt      # Loads messages.yml, resolves MiniMessage
├── Messages.kt            # Enum mapping keys → message config paths
└── ToolType.kt            # Tool type enum with aliases

src/main/resources/
├── config.yml             # Mechanics, drops, performance, item definitions
├── messages.yml           # User-facing messages (MiniMessage, Vietnamese)
└── plugin.yml             # Plugin metadata, commands, permissions
```

## How It Works

### Tool identification

Tools are identified by a `NamespacedKey("hqngtool_type")` stored as `PersistentDataType.STRING` in the item's Persistent Data Container (PDC). This persists across inventory moves and server restarts — no NBT wrappers needed.

### Batch breaking

When a tool triggers, the plugin collects the list of blocks to break, then schedules a Folia `RegionScheduler.runAtFixedRate` task at the anchor block's location. Each tick it processes `performance.batch-size` blocks. A `ThreadLocal<Boolean>` re-entrancy guard prevents internal `breakNaturally` calls from re-triggering the event handler.

### Drop flow

For every block broken (including the primary block the player hit):

1. If the block is in `suppress-list` → set to AIR, no drops.
2. If the block has a `custom-overrides` entry → suppress vanilla drops, spawn the override item.
3. Otherwise → if `auto-collect` is true, suppress vanilla drops and give items directly to the player's inventory (overflow drops on the ground).

## License

This project is licensed under the [MIT License](LICENSE).
