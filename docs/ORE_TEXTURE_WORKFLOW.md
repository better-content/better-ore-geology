# Ore texture workflow

Status: living art and implementation contract. Update this document whenever the accepted source,
prompting method, reducer, host treatment, review gate, or shader/Excavated Variants contract changes.

Last accepted suite: geology-v7, 2026-09-10. It contains three textures for each of eight families.

## What the texture must communicate

An ore block is a one-block window into a deposit larger than the block. It is not a stone cube
containing a nugget, badge, gem, or centered object. Each texture must satisfy two views at once:

1. Alone, one block has a deliberate, attractive composition with enough structure to identify it.
2. In a wall of random variants, edge-crossing structures imply a larger seam, lens, lode,
   stockwork, replacement body, or breccia zone without requiring connected textures.

Geological realism here means realistic morphology translated into stylized Minecraft pixel art.
It does not mean photorealistic rendering or high-frequency mineral photography.

## Canonical architecture

- Each family has exactly three independently generated variants.
- Each variant is one square transparent mineral-overlay master under
  `art/block-masters/geology-v7/<family>/variant_<0..2>.png`.
- The same reduced mineral texture is used on all six cube faces, matching vanilla ore blocks.
  This makes every face suitable when it is the only visible face and avoids hidden weak panels.
- Vanilla random model selection supplies patch variation. Exact edge-to-edge CTM matching is not
  required; the edge crossings create a continuation illusion.
- Stone uses the canonical 1.20.1 stone host on every face. Deepslate uses its lateral host on the
  four sides and `deepslate_top` on up/down, so host sidedness remains correct.
- Runtime textures are ordinary opaque 16×16 PNGs. Excavated Variants and shaders consume normal
  block definitions; neither integration depends on the high-resolution art representation.

## What “reproduce” means

There are two different reproducibility guarantees:

- **Reproduce the shipped suite exactly:** the 24 accepted transparent masters and three host
  textures are checked in. The Java generator deterministically recreates all 288 runtime PNGs,
  pixel-for-pixel, without calling ImageGen.
- **Create a replacement suite by the same method:** ImageGen is stochastic. Reusing an exact
  prompt reproduces the art direction and constraints, not identical source pixels. Preserve the
  generated master and exact prompt, then require a new visual approval. Once a master is accepted
  and checked in, all reduction and compositing after it are deterministic.

Do not claim that a fresh model generation reproduces geology-v7 exactly. The checked-in masters,
not a model seed or an old generated-image path, are the durable source of the accepted art.

## Begin with geology, not a picture prompt

Write a short morphology rule before generation. It must name:

- the deposit process and analogue;
- its dominant structure;
- two or three subordinate structures;
- edge behavior;
- transparent-space target;
- material zoning and restrained accents;
- forbidden shapes, especially the nearest confusing family.

Current identity anchors:

| Family | Dominant morphology | Must not become |
| --- | --- | --- |
| Coal Measures | broad persistent carbonaceous benches with pale partings | Black Shale fissility or isolated coal chunks |
| Ironstone | flattened oolitic lenses and linked sedimentary pods | coins, bars, or one round rust blob |
| Copper Bloom | multi-generation crosscutting stockwork | a central X, radial hub, or green confetti |
| Tin Quartz | steep quartz-cassiterite lode, offsets, and splays | a lightning bolt or centered crystal |
| Brassroot | joint fill with short bedding-replacement branches | a literal root, tree, or gold outline |
| Evaporite Beds | imperfect persistent beds, mud breaks, and nodules | perfect stripes, ladders, or icicles |
| Hotstone | asymmetric crackle breccia and sparse fluid conduits | lava wallpaper, glowing rings, or stars |
| Black Shale | thin tapered fissility, cleavage, and crenulated wisps | Coal's broad continuous benches |

`art/GEOLOGICAL_MORPHOLOGY.md` owns the detailed geological rules. World-generation geometry is a
separate contract: it should echo the family process at deposit scale, but it does not determine
the pixels in one block face.

## ImageGen workflow

Use the built-in ImageGen tool, one call per variant. Generate the mineral layer only. Do not ask
for a finished ore-on-stone texture: the accepted host is composited later.

Use this prompt scaffold and replace only the bracketed family/variant material:

```text
Use case: stylized-concept
Asset type: one transparent Minecraft-style ore-overlay texture master designed to become exactly
one 16x16 all-face ore-block texture
Primary request: Create one square pixel-art overlay for [family], variant [0..2]: [specific
morphology]. Geological identity: [process and dominant structure]. It must read as a deposit
passing through a volume of rock and continuing beyond the block, never as an object placed inside
one block.
Scene/backdrop: genuinely transparent RGBA canvas; every empty region is alpha zero.
Style/medium: polished Minecraft Java Edition 16x16-style pixel art authored at high resolution
using large hard-edged square pixel clusters, deliberate stepped silhouettes, restrained flat color
groups, and minimal shading; stylized geological morphology, not photorealism.
Composition/framing: one square texture reused on every cube face; mineral coverage roughly 20% to
46% after reduction; the coherent formation touches at least two canvas edges; asymmetrical
whole-face balance; attractive and legible when this is the only exposed ore block; this variant
must remain visibly distinct from its siblings.
Materials/textures: [family palette and zoning].
Constraints: actual transparent alpha; exactly one complete standalone texture; hard pixel
boundaries; no host stone or deepslate pixels; substantial open alpha; no frame, panel, text,
label, or watermark.
Avoid: [family confusions]; presentation board; drawn checkerboard; colored or gray backdrop;
haze; vignette; external shadow; glow; gradients; anti-aliased fringe; smooth painting;
high-resolution realism; singular gemstone; centered specimen; uniform speckles.
```

Do not generate a 3×2 panel atlas. In practice, multi-panel prompts encouraged ImageGen to paint a
smoky presentation board and made individual panels weaker. Background-extraction retries also
painted checkerboards instead of reliable alpha. Reject these outputs; do not threshold or repair
them algorithmically.

## Alpha-master acceptance gate

Inspect the generated master before copying it into the repository:

- real alpha channel, with genuinely clear regions;
- square and at least 1024×1024;
- no matte, checkerboard pixels, host rock, haze, vignette, external shadow, or halo;
- one coherent formation rather than disconnected decorative fragments;
- at least two edge contacts after reduction;
- asymmetrical but balanced composition when isolated;
- variant changes structure, not only rotation, mirroring, or color;
- no family-specific forbidden form.

The current reducer accepts 50–120 materially visible pixels of 256 (19.5–46.9%). That is a guard
rail around the accepted suite, not a target to fill. Negative space and recognizable structure
matter more than maximizing coverage.

## Complete reproduction runbook

Run all repository commands from `/home/dev/mod_source/better-ore-geology`. Java 17 is required. On a
Better Content lane, activate the recorded SDK when the current shell is not already using it:

```sh
cd /home/dev/mod_source/better-ore-geology
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 17.0.16-tem
java -version
git status --short
```

Stop if `git status --short` reports work that is not yours. Read the workspace and repository
`AGENTS.md` files, inspect current agent claims, and coordinate before overlapping another change.
Do not mutate the modpack as part of this source-art workflow.

### A. Reproduce the current accepted textures exactly

This is the shortest and fully deterministic path. It does not need ImageGen, GraphicsMagick, or
the retained review bundle.

1. Verify the immutable source inputs. The expected Minecraft 1.20.1 host hashes are:

   ```text
   0096954ee757a2184f96e5e300953a6c41855ee49e8e5ad90aeeb0444953fd9d  stone.png
   fcba18a824b979157ccce4075b5bc26fd5c4dda6bc0003fc691ae7012856ba22  deepslate.png
   6756166657ca891a1463c60123de3e4bc23c21d78c61471dea0594250a4119b2  deepslate_top.png
   ```

   Check them with:

   ```sh
   sha256sum art/host-textures/minecraft-1.20.1/*.png
   ```

2. Validate all 24 geology-v7 alpha masters. This checks square dimensions of at least 1024px,
   genuine transparent and solid areas, no opaque perimeter matte, 50–120 visible sampled pixels,
   at least two contacted edges, and a unique 16×16 silhouette for every variant:

   ```sh
   java tools/GenerateDepositTextures.java --validate-masters
   ```

3. Regenerate 8 families × 3 variants × 2 hosts × 6 faces = 288 runtime textures:

   ```sh
   java tools/GenerateDepositTextures.java --write
   ```

4. Prove every generated pixel agrees with the masters and hosts, then run the complete repository
   gate:

   ```sh
   java tools/GenerateDepositTextures.java --check
   ./gradlew verifyFull --rerun-tasks
   git diff --check
   git status --short
   ```

   On an unchanged checkout, `--write` must leave `git status --short` empty. A diff means an input,
   generator, or committed runtime texture has changed and must be investigated; do not normalize
   or automatically accept it.

### B. Author and review a replacement master or suite

The following procedure is the full human-in-the-loop path. Repeat steps 3–7 independently for
each family and variant; do not generate an atlas.

1. **Create an external candidate area.** Candidates remain outside the source repository until
   approved. Use a unique persistent review directory and a disposable lane-local working area:

   ```sh
   REVIEW_ROOT="/home/dev/workspace_artifacts/reviews/better-ore-geology/$(date -u +%Y%m%dT%H%M%SZ)-geology-candidate"
   WORK_ROOT="/home/dev/.tmp/better-ore-geology-texture-review"
   mkdir -p "$REVIEW_ROOT/masters" "$REVIEW_ROOT/composites" \
     "$REVIEW_ROOT/sheets" "$REVIEW_ROOT/provenance" "$WORK_ROOT"
   ```

   Do not use `/tmp`, and do not put unapproved binaries under `art/block-masters/geology-v7/`.

2. **Write the geology brief.** Choose one family and variant from the table above, then expand its
   process, dominant structure, subordinate structures, edge behavior, material zoning, and nearest
   forbidden family using `art/GEOLOGICAL_MORPHOLOGY.md`. Three siblings must be independently
   composed sections through the same deposit type, not rotations or recolors.

3. **Generate one alpha master.** Ask an image-capable Codex agent to call the built-in ImageGen
   tool with use case `stylized-concept`, no reference image, and the exact scaffold in the
   [ImageGen workflow](#imagegen-workflow). Make one call for one variant. Do not use a CLI, request
   a finished ore-on-stone texture, or ask for multiple panels. The tool returns the generated PNG
   path; copy that untouched PNG into the external review area:

   ```sh
   FAMILY=ironstone
   VARIANT=0
   GENERATED_PNG=/absolute/path/returned/by/imagegen.png
   MASTER="$REVIEW_ROOT/masters/$FAMILY-v$VARIANT.png"
   cp "$GENERATED_PNG" "$MASTER"
   ```

   Record `family`, `variant`, returned generated path, review-relative master path, the complete
   expanded prompt, UTC generation time, and disposition in a TSV or Markdown provenance file.
   Generated-image cache paths are useful provenance but are not durable inputs; the copied PNG is.

4. **Inspect the real alpha before judging the picture.** GraphicsMagick is acceptable for
   read-only inspection and review rendering, but the Java generator is the acceptance authority:

   ```sh
   gm identify -verbose "$MASTER" | sed -n '1,55p'
   java tools/GenerateDepositTextures.java \
     --preview "$FAMILY" "$VARIANT" "$MASTER" "$WORK_ROOT/$FAMILY-v$VARIANT"
   ```

   `--preview` validates the source, performs the production point sample, reports visible coverage,
   and writes 12 temporary host/face composites without changing repository resources. In
   GraphicsMagick output, the `Opacity` channel uses the opposite wording from alpha: maximum
   opacity values describe transparent pixels and zero describes opaque pixels. Do not reject a
   valid master by reading that field backwards.

5. **Reject or retry without repairing.** Reject mattes, painted checkerboards, halos, host-rock
   pixels, weak edge contact, centered specimens, confetti, confusing family morphology, and
   variants that differ only by transform. Do not threshold, erase a background, trace a vein,
   force edge pixels, quantize the palette, or paint missing morphology. A failed master gets a new
   ImageGen call and a recorded rejected disposition.

6. **Construct the visual review.** Use only nearest-neighbor enlargement. For each candidate,
   make one review image containing the reduced alpha on a checkerboard, its isolated stone and
   deepslate composites from the preview directory, a correctly aligned isometric cube, and a
   square-block family wall using all three variants. The review renderer is non-authoritative: its
   sampled overlay must use the same center coordinate as the generator,
   `floor((2 * output + 1) * sourceSize / 32)`, and its preview blocks must remain square and
   unstretched. It must never write production textures.

   Present every candidate in its own native graphics tab. On a Worklane/Ghostty lane, a single
   sheet can be checked with:

   ```sh
   worklane-show-image --session "$HERDR_SESSION" \
     --label "$FAMILY variant $VARIANT review" \
     "$REVIEW_ROOT/sheets/$FAMILY-v$VARIANT-review.png"
   ```

   For a whole suite, create one labeled Herdr workspace and 24 tabs, one sheet per tab, and retain
   a mapping of family, variant, tab ID, pane ID, and absolute image path. Do not substitute one
   tiny overview for per-texture inspection. During the active review, identify the mapping in
   a concise task-local README/checksum manifest; no historical bundle is a standing dependency.
   After promoting only approved current authored inputs, dispose of review candidates, sheets
   and manifests at handoff under the [shared policy](../../../better-content-modpack/docs/policies/generated-data.md).
   Do not add a historical retention catalog to `workspace_artifacts/README.md`.

   For checksums during the active review:

   ```sh
   (
     cd "$REVIEW_ROOT"
     find . -type f ! -name SHA256SUMS -print0 \
       | sort -z \
       | xargs -0 sha256sum > SHA256SUMS
   )
   ```

7. **Obtain explicit human approval.** Apply every question in the [review gate](#review-gate),
   including isolated-block quality, deposit-scale reading, family identity, sibling variety, host
   legibility, and the Coal/Black Shale distinction. Candidate generation and previewing do not
   authorize promotion.

8. **Promote only the approved untouched master.** Recheck coordination and repository status,
   then copy the accepted review source into its canonical slot:

   ```sh
   mkdir -p "art/block-masters/geology-v7/$FAMILY"
   cp "$MASTER" "art/block-masters/geology-v7/$FAMILY/variant_$VARIANT.png"
   java tools/GenerateDepositTextures.java --validate-masters
   java tools/GenerateDepositTextures.java --write
   java tools/GenerateDepositTextures.java --check
   ```

9. **Review the complete generated diff.** All six faces of one host intentionally receive the same
   sampled mineral overlay; deepslate `up` and `down` differ only because they use
   `deepslate_top.png`. Confirm the expected changed family/variant paths and no unrelated files:

   ```sh
   git diff --stat
   git status --short
   git diff --check
   ```

10. **Update canonical spot-check hashes deliberately.**
    `src/test/resources/canonical_ore_texture_hashes.json` records variant-0 south stone,
    variant-0 south deepslate, and variant-0 up deepslate for each family. If and only if one of
    those approved images changed, print the new values and edit the corresponding family entry:

    ```sh
    sha256sum \
      "src/main/resources/assets/better_ore_geology/textures/block/${FAMILY}_0_south.png" \
      "src/main/resources/assets/better_ore_geology/textures/block/deepslate_${FAMILY}_0_south.png" \
      "src/main/resources/assets/better_ore_geology/textures/block/deepslate_${FAMILY}_0_up.png"
    ```

    Do not update hashes simply to make a failing test green. Variants 1 and 2 are still fully
    protected by `--check` and the resource tests even though the JSON is a compact variant-0
    visual spot check.

11. **Run the required source gate and commit only owned paths:**

    ```sh
    ./gradlew verifyFull --rerun-tasks
    git diff --check
    git status --short
    git add docs/ORE_TEXTURE_WORKFLOW.md \
      "art/block-masters/geology-v7/$FAMILY/variant_$VARIANT.png" \
      src/main/resources/assets/better_ore_geology/textures/block \
      src/test/resources/canonical_ore_texture_hashes.json
    git diff --cached --stat
    git commit -m "Refresh approved ore texture art"
    git push
    ```

    Adjust the explicit `git add` list to the actually approved scope. Never use `git add -A` in a
    shared workspace.

12. **Keep deployment a separate authorization boundary.** A clean, pushed source commit is not a
    modpack deployment. Change the modpack, deploy a JAR, run packwiz, or build a fresh distribution
    only when the user explicitly orders that work and after coordinating a clean handoff. When a
    fresh distribution is ordered, follow the modpack's documented `release.main.kts` workflow
    instead of hand-copying one development artifact.

## Reduction and host compositing

The high-resolution master is authoritative. `tools/GenerateDepositTextures.java` performs one
center-point nearest-neighbor sample per output pixel and preserves that sampled RGBA. It does not:

- threshold or reconstruct alpha;
- trace paths or force edge contacts;
- quantize the mineral palette;
- prune details;
- paint missing pixels;
- generate new morphology.

The sampled overlay is then composited onto the checked-in canonical Minecraft 1.20.1 hosts under
`art/host-textures/minecraft-1.20.1/`. Runtime PNGs are fully opaque.

Commands:

```sh
java tools/GenerateDepositTextures.java --validate-masters
java tools/GenerateDepositTextures.java --preview FAMILY VARIANT /path/to/master.png build/texture-preview
java tools/GenerateDepositTextures.java --write
java tools/GenerateDepositTextures.java --check
./gradlew verifyFull
```

`--preview` never promotes a candidate. Copy an approved master into geology-v7, run `--write`,
update canonical hashes, and run the full gate only after visual approval.

## Review gate

Review every variant in its own tab. Each sheet must show:

- the 16×16 alpha over a checkerboard;
- the isolated stone face;
- the isolated deepslate face;
- a correctly aligned isometric block using the same texture on all faces;
- a wall made from square, unstretched blocks using all three family variants.

Inspect at native size and with nearest-neighbor enlargement. Reject stretched block previews,
misaligned cube projections, bilinear filtering, or a wall that hides the single-block result.

Ask, in order:

1. Does a single block look intentionally composed?
2. Does it read as a cut through a larger deposit rather than an object?
3. Is the family identifiable without hue alone?
4. Do three variants produce an immersive patch without obvious repeated stamps?
5. Do stone and deepslate retain their host identity?
6. Are Black Shale and Coal unmistakably different?

## Excavated Variants and shaders

Every family must remain listed in the Excavated Variants provider and modifier resources. Derived
host ores should inherit the normal block/model identity; do not create a hand-authored texture
matrix for every rock mod.

Shader support is block classification, not painted emission. Keep runtime textures opaque and do
not add full-face emissive masks. Every canonical and derived ore block must remain in the modded-ore
classification used by the pack's shader integration. Hotstone may have restrained bright mineral
junctions and block light, but it must not become a glowing lava web.

## Maintaining this document

When the workflow changes, update this file in the same commit as the generator or art change.
Record why the old method failed, what visual invariant the replacement protects, and which test or
review catches regression. Do not rewrite history by deleting superseded candidate directories;
label them as superseded and keep the accepted suite unambiguous.
