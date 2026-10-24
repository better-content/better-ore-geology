# Better Ore Geology

## Playtest diagnostics

`bc.playtest.better_ore_geology.worldgen` aggregates candidates, bounds/write vetoes,
host-or-exposure vetoes, placement calls and native returned-true counts per occurrence.
The existing sampler, target short-circuit checks and RNG sequence are unchanged; no
seed/salt or per-block details are logged. `salience` reports only the native miner sound
packet dispatch after the existing operation; packet send is not observed hearing or
client playback. No player names, inventories or NBT are emitted.

INFO startup supplies enabled/version/EV presence and exact own-artifact source metadata
when available (UNKNOWN otherwise). The repository-local Java17 helper shares120 DEBUG/
TRACE details/minute, burst20,256 keys and2048-character records. Native END ticks summarize
30-second windows; server stop clears aggregation without rearming TRACE or the bucket.
Standalone remains native INFO; pack profile chooses scoped DEBUG/`bc.playtest.level`.
Common six-case and placement-call-versus-native-return projection tests run alongside
existing deterministic/resource/source gates. Logging is not rendering/worldgen acceptance.

## Scope

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

- Depict geological deposits through each family's seams, branching veins, grains, nodules,
  or sparse crystals. Keep the host rock dominant and the family recognizable by silhouette
  and value structure as well as hue.
- Each family has four visual roles: quiet, fragment, richer, and original. Each role has
  three independently authored patterns; rotation, mirroring, or recoloring alone is not a
  new pattern. Sparse roles intentionally expose less mineral than the original artwork.
- Direct point sampling reduces approved transparent masters to crisp 16×16 overlays.
  Preserve the sampled RGBA and composite onto the host with the authored integer blend.

### World appearance and host treatment

Ore blocks persist one `geological_style` property with 64 values: eight quantized
structural directions × eight occurrence phases. A globally continuous family field uses
that style and world position to choose exposure within each face. Pattern selection is
patch coherent and independent for each of the four roles, with three patterns per role;
it does not roll a complete random texture independently for every block or face.

The renderer crops opaque source quads into non-overlapping rectangles, preserving their
UVs and the original rich mineral pixels wherever the field exposes them. Stone and
deepslate share mineral geometry while retaining the host's own face treatment, including
directional deepslate sides and tops. The shared geometry cache is bounded at 32,768 quads.
Hotstone retains its light and contact behavior.

World generation supplies the compact style without changing ore positions or budgets.
Placement chooses a stable appearance from family and position. The design uses no ore
block entities, custom chunk NBT, custom network payloads, or item NBT. Inventory models
and surface samples intentionally retain their legacy canonical artwork. Already generated
chunks whose states lack `geological_style` use its default value, `0`; no regeneration or
migration is implemented. Drops, recipes, and other ore mechanics remain unchanged.

### Excavated Variants contract

Hyle/Unearthed supplies the rock strata; Excavated Variants synthesizes host-specific ore
textures. Its public resource-provider API receives our explicit-alpha overlays in an
ordered set of 12: four roles × three patterns. Narrow, version-pinned property and model
mixins preserve `geological_style` through host conversion and connect generated models
to the same exposure renderer. The integration targets EV 3.0.9 and DAG 4.2.2.

Keep `defaultresources/excavated_variants/excavated_variants/variants/better_ore_geology.json5`
as the declarative host mapping boundary. Host synthesis does not require an authored
matrix of composites for every Unearthed stone. Review representative light, dark, coarse,
and directional hosts alongside native stone and deepslate.

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
2. Coverage fits the selected quiet, fragment, richer, or original role, while palette
   and morphology remain recognizable.
3. The arrangement is genuinely new and does not create obvious tiling or face-edge
   artifacts in a cluster of blocks.
4. All six faces preserve their host treatment and the world-position exposure field
   remains coherent across adjacent blocks.
5. No variant can be confused with another family, vanilla ore, or exposed surface-sample
   rubble.
6. Inventory/JEI and surface samples retain their canonical appearance while world
   faces combine the four roles and three patterns through the shared field.

## Common commands

```bash
./gradlew verifyFast
./gradlew verifyFull
./gradlew stageRuntimeJar
```

`verifyFull` runs the fast checks plus item-texture, original/exposure-master, block-texture,
exposure model/blockstate reproducibility, optional-data guard, and pure-logic coverage gates. `stageRuntimeJar` stages the reobfuscated production artifact.

The separate `exposureHarness` source set contains the nonshipping native server/client
probe. With runtime testing authorized, prepare its pinned dependencies and launch
descriptors, then run the bounded controller:

```bash
./gradlew prepareExposureLaunch
python3 -B tools/run_exposure_probe.py
# Each selected family gets an independent two-launch fixture:
python3 -B tools/run_exposure_probe.py --family coal_measures --fixture-id fresh-scene
# Separate dedicated-server classloading/startup smoke with no optional integrations:
python3 -B tools/run_standalone_smoke.py
```

The controller uses disposable `run-exposure/` fixtures, a real client under Xvfb/software
rendering, and a second launch of the same world to check persistence. It checks all 288
scene blocks across six faces before and after resource reload; selected-family fixtures live
under `run-exposure/families/<family>/`. Run each of the eight supported family selectors for
full renderer coverage. `--fixture-id` requires an absent named directory, preventing accidental
reuse of an old scene. Registry/conversion/placement and native mining assertions cover all
families on every server launch. Each launch records pre-start config absences and verified pinned
asset inputs. The strict diagnostic classifier rejects unknown warnings/errors, and its negative
contracts run in `verifyFull`; it does not change the full-pack diagnostic policy. The standalone
smoke checks dedicated-server startup only, not client rendering. Reports/screenshots apply only
to the current invocation;
compilation and focused contracts do not establish native acceptance or gameplay performance.
The production JAR excludes the entire companion fixture source set.

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
