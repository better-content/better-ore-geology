# Processing item art

## Geological morphology contract

The eight player-facing families use recognizable geology rather than symbols. Coal Measures
forms broken uneven carbon seams; Ironstone forms rusty lenticular and oolitic beds; Copper
Bloom forms crosscutting stockwork with oxidation halos; Tin Quartz forms steep quartz lodes
with cassiterite splays; Brassroot follows an asymmetric dendritic fracture; Evaporite Beds
stacks salt and gypsum beds with crystalline pockets; Hotstone forms a lopsided breccia pipe
with sparse radial fissures; Black Shale carries dark laminations with violet stringers.

The canonical 144×18 badge strip is rendered only on interaction surfaces. World blocks remain host-rock dominant and communicate through morphology, value, and physical behavior before their tooltip names the aspect.

## Deposit block art

`tools/GenerateDepositTextures.java` reproduces the 288 existing geology-v7 faces and the 1,248
exposure-v1 PNGs (96 transparent overlays and 1,152 opaque host composites). One square
transparent ImageGen master supplies each pattern; the same directly reduced morphology is reused
on all cube faces like a vanilla ore block. Stone uses one host on all faces, while deepslate
preserves its lateral/top host direction.

The complete 24-master suite lives under
`art/block-masters/geology-v7/<family>/variant_<n>.png`. The validator requires every family and all
three variants; partial suites cannot pass. Candidate art stays outside this accepted directory
until its isolated-block and family-patch review is approved.

The approved 96-master exposure suite and each exact prompt/provenance JSON live under
`art/block-masters/exposure-v1/<family>/<quiet|fragment|richer|original>/variant_<n>.{png,json}`.
The original-role PNGs match geology-v7 byte for byte. Sparse roles are partial deposit windows,
so their sampled clusters use category-aware survival checks instead of the old 50-pixel and
two-edge requirements. The generator also writes original-role sampled alpha masks to
`assets/better_ore_geology/exposure_masks.json` for runtime exposure composition.

```sh
java tools/GenerateDepositTextures.java --write
java tools/GenerateDepositTextures.java --check
java tools/GenerateDepositTextures.java --preview coal_measures 0 /path/to/master.png build/preview
java tools/GenerateDepositTextures.java --validate-masters
java tools/GenerateDepositTextures.java --validate-exposure-masters
java tools/GenerateDepositTextures.java --preview-exposure coal_measures quiet 0 /path/to/preview
```

Source masters are generated one standalone texture at a time with built-in ImageGen on transparent
alpha. The reducer center-point samples directly to 16×16 without thresholding, topology repair,
palette quantization, or detail synthesis, then composites the result over checked-in canonical
hosts. Runtime PNGs are generated artifacts and are never hand-painted.

The complete maintained process, prompt scaffold, failure modes, and visual gate live in
`docs/ORE_TEXTURE_WORKFLOW.md`.

The PNGs under `item-masters/` are accepted 1024x1024 transparent masters generated
with the built-in ImageGen workflow documented below. Runtime sprites are deterministic
16x16 reductions; do not paint or regenerate them directly.

## Rebuild and verify

```sh
java tools/DownsampleItemTextures.java --write
java tools/DownsampleItemTextures.java --check
```

Import an accepted ImageGen candidate through the same alpha-aware normalization path:

```sh
java tools/DownsampleItemTextures.java --import small_chunks copper_bloom /path/to/candidate.png
```

Valid kinds are `small_chunks`, `crushed_feeds`, and `concentrates`. Import rejects
images without a real alpha channel, crops only materially opaque pixels, fits the
subject onto a transparent square, and writes the canonical 1024x1024 master. The
source generator must not overwrite curated masters or runtime sprites.

## Prompt language

Use one built-in ImageGen call per asset. Use the repository ore-chunk preview and the
matching full-chunk sprite as references for family forms. Ask for an isolated game-item
cutout on genuinely transparent alpha with generous padding, upper-left lighting,
strong grouped values, no cast shadow, no text, no container, no watermark, and no
detached particles.

- Small chunks are single compact ore-bearing fragments, never piles or cubes.
- Crushed feeds are low, coarse, irregular multi-fragment heaps, never fine powder.
- Concentrates are clean low mounds of fine powder, never rocks, bags, or bowls.

Family prompts must name the morphology and five colors in `tools/ore_art_manifest.json`.
Concentrate prompts must name the output material and use the frozen five-color palette
in `tools/concentrate_art_manifest.json`. Accepted prompts follow this exact scaffold,
with only the item name, subject description, and palette substituted:

```text
Use case: stylized-concept
Asset type: high-resolution source master for a Minecraft 16x16 inventory icon
Primary request: <one small fragment | a coarse crushed-feed heap | a fine concentrate mound>
Scene/backdrop: genuinely transparent alpha background, not a drawn checkerboard
Subject: <material-specific morphology>
Style/medium: polished hand-painted game item icon designed to survive deterministic reduction to crisp 16x16 pixel art
Composition/framing: centered isolated subject, slight three-quarter top view, square composition, generous transparent padding
Lighting/mood: simple upper-left light, strong grouped values, no cast shadow or glow outside the subject
Color palette: anchored to <five manifest colors>
Constraints: real transparent alpha; clean silhouette; opaque subject; no text; no label; no border; no watermark
Avoid: wrong material form, background, floor, checkerboard, glow, dust cloud, fuzzy edge, detached particles
```

Nearest-neighbor inspection previews are written under ignored `build/texture-previews/`.
