# Better Ore Geology

## Scope and authority

This repository owns its mod-specific behavior and authoring inputs. Read [local instructions](AGENTS.md)
and the [shared documentation/policy index](../../better-content-modpack/docs/README.md).


Pack-owned ore and deposit support mod for Forge `1.20.1`.

The eight deposit families each own exactly one Systemic Salience aspect. Mining a native
deposit or its Excavated Variants host-specific form gives only the miner a quiet spatial
physical-foley cue. Familiarity backs cues off per player and per aspect from immediate to
30 seconds, 2 minutes, 8 minutes, 32 minutes, and 2 hours; one level decays every 4 hours
48 minutes of real time, including offline time. The state persists through death and
handles backward wall-clock corrections without trapping the player in a cooldown.

## Texture visual identity

The maintained, normative authoring and review contract is
[`docs/ORE_TEXTURE_WORKFLOW.md`](docs/ORE_TEXTURE_WORKFLOW.md). This section summarizes the shipped
result; update the living document whenever the workflow changes.

### Core language

- Depict geological deposits, not vanilla-style isolated ore spots. Each family has a
  recognizable morphology: a seam, branching vein, disseminated grains, nodules, or
  sparse crystals.
- Keep the authored stone and deepslate ores as ordinary, finished Minecraft blocks.
  Realistic Ores establishes their morphology, coverage, variants, and host treatment;
  Hyle and Unearthed provide the broader rock strata; Excavated Variants reads those
  normal block definitions and derives the matching host-specific ore blocks.
- Express that contract through ordinary vanilla-format blockstates, models, and
  textures. Do not hand-author a matrix of Realistic Ores composites for every Unearthed
  stone, add custom integration code when normal definitions suffice, or simplify the
  canonical ore art to compensate for hypothetical generated hosts.
- Keep the host rock visually dominant. The accepted geology-v7 overlays retain 50–120 materially
  visible pixels of 256 after direct reduction; composition and negative space matter more than a
  single target percentage.
- Use crisp 16x16 pixel art produced by direct point sampling of approved transparent ImageGen
  masters. Preserve the sampled RGBA and composite it into fully opaque runtime textures.
- Make identity depend on silhouette and value structure as well as hue. A texture must
  remain recognizable in low light and under shaders; increasing saturation is not a
  substitute for preserving its deposit shape.
- Preserve unequal clustering and negative space. Do not distribute mineral pixels
  uniformly, add one-pixel confetti, or recolor a shared generic mineral layout.
- Variants are alternate arrangements of the same geology. They may move, fork, shorten,
  or thicken clusters while retaining the family's morphology, coverage, palette, and
  overall light/dark balance. Rotation or mirroring alone does not count as a texture
  variant.
- Each visual family has exactly three equally weighted model variants: canonical
  variant `0` and alternate variants `1` and `2`.

### Host treatment

- Both stone and deepslate variants are complete opaque runtime textures derived from one approved
  transparent mineral master per variant. Reuse the same morphology on all cube faces, matching
  vanilla ore blocks and guaranteeing that every exposed face works alone.
- Edge crossings imply continuation under random block variants; exact CTM joins are not required.
- Corresponding stone and deepslate variants share pixel-identical mineral geometry.
- Stone faces are finished opaque textures using the normal stone host, with the mineral
  embedded in the rock rather than drawn as a floating outline or decal.
- Deepslate faces are finished opaque textures retaining the directional deepslate side
  treatment on lateral faces and the appropriate top or bottom treatment vertically.
  Preserve that host directionality while allowing all six mineral arrangements to differ.
- Balance all three variants as a set; structure must differ, not merely rotation or recoloring.
- Host contrast may shift naturally between stone and deepslate, but the mineral palette
  and family silhouette stay recognizable. Do not brighten deep variants merely to make
  them as luminous as their stone versions.
- Keep all block textures fully opaque. Shader integration classifies every canonical and
  Excavated Variants deposit as a modded ore, allowing mineral pixels to glow without
  painting transparent or full-face emissive pixels into the texture. Hotstone additionally
  communicates heat through block light and contact behaviour.

### Excavated Variants contract

The authored stone and deepslate blocks are canonical reference implementations, not the
complete host catalogue. During world generation, Hyle/Unearthed establishes the local
rock type and Excavated Variants derives the corresponding ore block by inspecting its
registered stone and ore definitions. Realistic Ores must therefore expose conventional,
finished models and textures rather than a private intermediate art representation.

Keep `defaultresources/excavated_variants/excavated_variants/variants/better_ore_geology.json5`
as the declarative mapping boundary. New art should require only normal Minecraft asset
definitions plus that existing mapping; it should not require bespoke generated textures
for each Unearthed stone. Validation should sample several light, dark, coarse, and
directional Unearthed hosts to confirm that all three canonical variants remain legible
after synthesis.

### Deposit families

| Family | First-contact promise | Required morphology |
| --- | --- | --- |
| Coal Measures | fuel | broad broken black strata |
| Ironstone | iron and tools | thick rusty bedded bands |
| Copper Bloom | copper | oxidized green/brassy mineralization |
| Tin Quartz | bronze and quartz | bright crystal ribbons with dark inclusions |
| Brassroot | zinc and brass | yellow-grey branching roots |
| Evaporite Beds | salt and preservation | pale crystalline sedimentary beds |
| Hotstone | dangerous usable heat and heavy matter | dark energetic mineral body |
| Black Shale | redstone and supernatural material | black strata with sparse violet contamination |

Each family has four deposit-scale archetypes rather than a single stretched blob:

| Family | In-world arrangements |
| --- | --- |
| Coal Measures | channel-broken seam; split benches; fault-stepped seam; folded pinch-out |
| Ironstone | single oolitic lens; en-echelon lenses; shoal lens chain; erosional pod bed |
| Copper Bloom | crosscut stockwork; sheeted veinlets; arcuate stockwork; breccia-margin veinlets |
| Tin Quartz | faulted steep lode; en-echelon twin lodes; greisen-splay lode; ladder-vein corridor |
| Brassroot | joint-and-bedding fill; stair-step fracture; collapse-pocket feeders; en-echelon replacement |
| Evaporite Beds | paired beds; rhythmic triple beds; nodular lens chain; dissolution-broken bed |
| Hotstone | narrow crackle pipe; asymmetric breccia corridor; en-echelon breccia pods; late-fissure pipe |
| Black Shale | anastomosing fissility; steep slaty cleavage; crenulated carbon wisps; compaction drapes |

These eight are the complete player-facing worldgen families. Oil Seep remains a separate
fluid surface feature. Exact primary and coproduct materials belong to processing depth,
not additional worldgen identities. The retained assay catalogue is the 24 useful outputs
plus rock salt, sodium chloride, and saltpeter; inert technical concentrates are removed.

`tools/ore_art_manifest.json` remains the canonical morphology list and the five-colour palette
source for processing-item art. Block masters preserve their generated palette through reduction.
Tin Quartz folds gem-bearing pegmatite depth into its later assay routes, while Black Shale
folds redstone and precious-metal depth into its controlled soul-bearing geology.

World generation uses one dual-host `better_ore_geology:geological_deposit` configured feature
per family. `tools/geological_worldgen.json` freezes each family's dominant trapezoidal home
band and its smaller uniform echo band across Tectonic's `-128..512` Overworld. The home band
carries the family identity and most of its supply; the distant echo is larger per encounter
but contributes only a minority of the expected blocks. Counts were reduced while body budgets
grew, retaining each family's previous expected supply within four percent.

The sampler is stateless and seeded only by world seed, occurrence, position, family, and home/echo
class. Jittered geological provinces make nearby deposits favor a coherent structural strike and
weighted archetype order without keeping mutable region state, so parallel/C2ME generation produces
the same blocks as serial generation. Every occurrence still varies its dip, offsets, branching,
gaps, thickness, and exact block budget. Bodies remain within 12 horizontal and 16 vertical blocks
of their feature origin, preventing cross-region writes. Home occurrences weight the province's
primary forms most strongly (`40/30/20/10`); echoes weight rarer forms more strongly
(`15/25/30/30`). Regenerate the configured features, placements, and biome modifiers with
`python3 tools/generate_geological_worldgen.py`; render all 32 review sheets with
`./gradlew renderWorldgenGallery`.

### Crushed material and surface samples

### Dimension and host-rock boundary

This source owns ordinary mineral deposits only in `#minecraft:is_overworld`, using the native stone and deepslate replaceable tags. A destination needs its own approved host mapping before it can receive these deposits: placing a stone-host deposit into Nether, End, Aether, or magical rock would return the wrong substrate. Existing chunks remain untouched.

Every native host block separates into exactly one deposit chunk and one existing host block: stone deposits return `minecraft:stone` and deep deposits return `minecraft:deepslate`. Excavated Variants returns its actual substrate. These are useful high-volume building materials; no host breakdown creates a mineral, concentrate, dust, or a route from renewable stone to a valuable material.

Pack integration closes vanilla raw-ore generation and loot separately. It must inventory vanilla and modded placed features, ore loot tables, structures, trades, recycling, and dimension-specific deposits. This source removes the listed Overworld placed features in `data/better_ore_geology/disabled_placed_features/vanilla.json`; it cannot close pack-owned datapacks, configs, or loot providers.

- Chunks of a deposit are the host-independent mining form. They are crisp 16x16
  non-placeable items whose silhouette and internal morphology preserve the parent
  family's geological identity; their transparent background must remain truly clear.
- Ordinary mining yields exactly one ore chunk and does not inspect Fortune. Silk Touch
  preserves the exact placeable host-rock ore block instead.
- Ore blocks separate reversibly into one chunk plus their host stone, and recombine from
  those same two ingredients. Processing a chunk is irreversible: an early-game millstone
  produces two crushed materials, while crushing wheels produce three.
- Crushed materials are compact lower-center piles, not miniature ore blocks. Existing
  sprites occupy roughly 30-40 visible pixels inside an `x=3..12`, `y=6..11` envelope
  (the narrowest families use `x=4..11`).
- Crushed-material sprites use the same five family mineral colors as chunks and deposits.
  Visible pixels are fully opaque against a transparent background.
- Small chunks use dedicated flat 16x16 inventory sprites rather than rendering the
  surface-sample block model. Each is one compact ore-bearing fragment occupying roughly
  16-30 visible pixels inside an `x=4..11`, `y=5..12` envelope. It retains the family
  morphology and five-color palette while remaining visibly smaller than a full chunk.
- Concentrates are clean low mounds of fine powder, not recolored chunks or coarse crushed
  material. They use five frozen output-material colors and a compact `x=3..12`, `y=7..12`
  silhouette so shared coproducts remain identifiable independently of their source deposit.
- Crushed material and surface samples have separate processing-item and block identities.
  `crushed_*` is never placeable. Each `surface_sample_*` block deliberately has no item
  identity of its own; its BlockItem is registered only as `small_ore_chunk_<family>`, so
  EMI/JEI exposes the small chunk while the placed surface block remains hidden.
- Nine identical small chunks irreversibly combine into one full chunk. A full chunk cooks
  to two primary nuggets, gem chips, or bulk items; each crushed material cooks to one. Four matching
  crushed materials, one route-specific grinding ball, and exactly 500 mB of the declared
  water/acid route usually produce four primary concentrates plus independently rolled
  coproducts. Explicit assay variants may move all guaranteed yield into route-specific
  concentrates: Hotstone's structural assay intentionally has zero primary output and guarantees
  titanium with nickel, cobalt, and iron coproducts. Separation never emits washed forms or
  generic tailings.
- TConstruct melting provides one fixed 40 mB exit for metal primaries and metal
  coproduct concentrates, independent of machine ore-rate bonuses. Titanium and
  thorium additionally cast back to their canonical nuggets and ingots. Quartz and
  gems keep item-form recovery and never become molten Realistic Ores outputs.
- Hexerei retains the ordinary four-chunk primary route. Its separate Tin Quartz
  diamond assay consumes four crushed materials and four selenite shards in 250 mB of
  heated water to recover one diamond chip.
- Coal Measures chunks are directly combustible. Evaporite chunks produce Rock Salt,
  Black Shale supports soul fire and yields soul sand, Gem Pipe yields rough gem chips,
  and Hotstone is luminous, painful to cross, and can be consolidated into magma.
- Coal measures and ironstone deliberately share a sedimentary seam vocabulary, but coal's
  broken dark carbon seam and ironstone's rusty bedded band remain distinct. Their overlapping
  height bands are preserved; their worldgen features and ADLODS deposits remain independent.
- Oil seep uses the same right-click pop-off/place behavior as surface samples, returning its
  own BlockItem without entering any ore processing tag or conversion.
- Surface samples and oil seep behave like flowers: breaking one drops its collected item,
  right-clicking one pops it off into the world as its collected item instead of silently
  pocketing it, and losing the supporting block pops it off as an item instead of vanishing.
- Surface samples use the ore family's opaque raw-deposit texture on five deterministic,
  ore-specific fragment arrangements. They must read as host-rock fragments carrying
  mineralization, not as a crushed processing pile laid on the ground.

Curated processing-item art is generated as one unique transparent high-resolution master
per item and reduced deterministically; see `art/README.md`. `tools/generate_phase3_resources.py`
owns models and recipes but must never synthesize or replace curated item PNGs.

### Variant acceptance checklist

Before accepting a block texture variant, review it at native size, nearest-neighbor
upscale, beside every other family, and in a representative stone/deepslate cave under
normal lighting and shaders. Confirm that:

1. The family is identifiable without its tooltip.
2. Mineral coverage and value balance remain close to the canonical texture.
3. The arrangement is genuinely new and does not create obvious tiling or face-edge
   artifacts in a cluster of blocks.
4. All six stone faces and all six deepslate faces form coherent sided models, with adjacent
   edge crossings aligned and no duplicated or mirrored opposite faces.
5. No variant can be confused with another family, vanilla ore, or exposed surface-sample
   rubble.
6. Inventory/JEI continues to use one stable canonical model while world blocks select
   complete sided variants through equally weighted static blockstate models. Faces
   are never selected or randomized independently.

## Common commands

```bash
./gradlew verifyFast
./gradlew verifyFull
./gradlew stageRuntimeJar
```

`verifyFull` adds `verifyItemTextures` to the fast lane, checking curated item sprites against their 1024px masters. The repository does not currently ship Forge GameTests.

## Release artifact

Deploy the reobfuscated runtime jar from:

- `build/libs/better-ore-geology-<version>.jar`

## Community and support

For modpack and mod discussion, playtest feedback, and bug reports, join the [Better Content Discord](https://discord.gg/EkRnZbzqS9).

## Canonical identity

- Repository and release artifact: `better-ore-geology`
- Mod ID and resource namespace: `better_ore_geology`
- Java package: `com.bettercontent.betteroregeology`
- Validation: `./gradlew verifyFull`

This normalization is a clean break. Worlds, configuration files, and integrations created for earlier identities are not migrated or aliased.

### Coverage gate regression

`./gradle/verify-coverage-gate.sh` checks the production coverage gate, then confirms
that an empty report scope and an unmet 100% coverage requirement fail. It restores
the normal report afterward and retains diagnostic logs under
`build/coverage-gate-regression/`. The production thresholds are unchanged.
