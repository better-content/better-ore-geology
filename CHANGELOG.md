# Changelog

## Unreleased

### Changed

- Surface samples and oil seep now pop off into their collected item like a flower: breaking
  one drops the item, right-clicking one pops it off into the world instead of silently adding
  it to the inventory, and losing the supporting block pops it off instead of vanishing.

- Replaced symbolic six-face deposit art with the approved geology-v7 standalone-first suite:
  three edge-crossing ImageGen textures per family, direct nearest-neighbor reduction, canonical
  stone/deepslate compositing, and vanilla-style all-face reuse.
- Added the living ore-texture authoring, alpha, morphology, review, Excavated Variants, and shader
  contract in `docs/ORE_TEXTURE_WORKFLOW.md`.

## 0.2.0 - 2026-08-25

### Changed

- Replaced the 23 taxonomic processing and worldgen families with ten player-facing
  deposits: Coal Measures, Ironstone, Copper Bloom, Tin Quartz, Brassroot, Redbed,
  Evaporite Beds, Gem Pipe, Hotstone, and Black Shale. Oil Seep remains independent.
- Rebuilt processing around 24 proven assay outputs plus rock salt, sodium chloride,
  and saltpeter. Gem Pipe and Hotstone expose multiple assay profiles through routes.
- Made first-contact promises mechanical: coal chunks burn, Evaporite yields salt,
  Gem Pipe yields rough chips, Black Shale supports soul fire, and Hotstone warms,
  hurts, and consolidates into magma.
- Capped Create separation recipes at four item outputs. The richest assay routes now
  consume their grinding medium, while Hotstone's structural route yields its four
  structural metals and leaves uranium recovery to the other Hotstone routes.
- Added the client blockstate and particle models required by molten titanium and
  molten thorium at every fluid level.

### Removed

- Removed inert beryl, beryllium, calcium, carbon, chromium, gallium, iridium,
  magnesium, phosphate, platinum, silicon, sodium, tantalum, and tungsten concentrates.
- Removed the superseded family IDs and their blocks, items, worldgen, processing,
  Excavated Variants entries, language keys, and art without compatibility aliases.

## 0.1.0

### Changed

- Rebalanced every family to two primary units per full chunk and one per crushed feed;
  millstones now produce two crushed feeds and crushing wheels produce three.
- Added immediate low-yield bauxite recovery and restricted TConstruct ore processing to
  metal primary products and metal coproducts.
- Made oil seep collectible by right-click like the other surface indicators.
- Added a canonical family art manifest for palette- and morphology-consistent chunk and
  crushed-feed sprites.
- Surface samples now place and collect through the matching `small_ore_chunk_<family>` BlockItem.
- Moved the complete 23-family chunk, crushing, separation, concentrate, thermal-exit,
  grinding-media, gem-chip, and Excavated Variants lifecycle into Realistic Ores.
- Renamed the pre-normalization amethyst family to Amethyst-Beryl Pegmatite and added the
  uncommon Gold-Quartz Vein family without compatibility aliases.
- Standardized the project as **Realistic Ores** with mod ID `better_ore_geology`, artifact `better-ore-geology`, and package `com.bettercontent.betteroregeology`.
- Adopted Java 17 and Forge 1.20.1-47.4.13 as the build baseline without changing the project version.
- This is a clean break; legacy worlds, configurations, and integrations are not migrated.
