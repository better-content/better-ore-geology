Realistic Ores

Forge 1.20.1 mod that adds JSON-defined ore blocks, optional Create crushing outputs,
and data-driven world generation overrides.

Project layout
- `src/main/java/com/bettercontent/betteroregeology/registry`: block and item registration
- `src/main/java/com/bettercontent/betteroregeology/ore`: JSON definition models and loaders
- `src/main/java/com/bettercontent/betteroregeology/worldgen`: biome modifier hooks for data-driven feature removal
- `src/main/resources/data/better_ore_geology/better_ore_geology`: ore block definitions
- `src/main/resources/data/better_ore_geology/disabled_placed_features`: placed feature removal rules for vanilla or modded ore generation
- `src/main/resources/data/better_ore_geology/worldgen`: configured and placed features
- `src/main/resources/data/better_ore_geology/forge/biome_modifier`: biome modifiers for adding the features and applying removal rules

Notes
- Block and item registration is driven from the ore definition JSON files.
- Ore addition is driven by resource JSON biome modifiers and worldgen definitions.
- Placed feature removal is driven by `disabled_placed_features` JSON files, including arbitrary modded feature ids.
- Create crushing recipes are guarded with `forge:mod_loaded` conditions.

Disabled placed feature rule format
- `biomes`: exact biome id or biome tag, such as `minecraft:plains` or `#minecraft:is_overworld`
- `steps`: generation steps to scan, usually `underground_ores`
- `features`: placed feature ids to remove, including modded namespaces such as `modid:ore_whatever`

Local build
- Use Java 17.
- Run `./gradlew build`.

Repository hygiene
- `tools/generate_resources.py` is a legacy helper and does not participate in the Gradle build.
- Ignore `.codex` and Python cache artifacts when working locally.
