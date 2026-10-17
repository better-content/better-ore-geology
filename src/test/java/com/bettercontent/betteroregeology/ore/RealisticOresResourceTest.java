package com.bettercontent.betteroregeology.ore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class RealisticOresResourceTest {
    private static final Gson GSON = new Gson();
    private static final Path DATA_ROOT = Path.of("src/main/resources/data/better_ore_geology");
    private static final Path RESOURCE_ROOT = Path.of("src/main/resources");
    private static final Path ASSET_ROOT = RESOURCE_ROOT.resolve("assets/better_ore_geology");
    private static final Set<String> FACES = Set.of("north", "east", "south", "west", "up", "down");
    private static final List<String> EXPOSURE_ROLES = List.of("quiet", "fragment", "richer", "original");
    private static final Set<String> SALIENT_FAMILIES = Set.of(
            "coal_measures", "ironstone", "copper_bloom", "tin_quartz", "brassroot",
            "evaporite_beds", "hotstone", "black_shale");
    private static final Set<String> RETAINED_MATERIALS = Set.of(
            "aluminum", "cadmium", "cobalt", "copper", "gold", "iron", "lead", "nickel",
            "osmium", "silver", "thorium", "tin", "titanium", "uranium", "zinc");

    @Test
    void packagedOreDefinitionsExposeExactlyTheEightGeologicalFamilies() throws IOException {
        try (var paths = Files.list(DATA_ROOT.resolve("better_ore_geology"))) {
            Set<String> families = paths
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> read(path, OreDefinition.class))
                    .peek(OreDefinition::validate)
                    .map(OreDefinition::id)
                    .collect(Collectors.toUnmodifiableSet());
            assertEquals(SALIENT_FAMILIES, families);
        }
    }

    @Test
    void excavatedVariantsClassifiesAndTagsEveryDepositAsAnOre() {
        Path excavatedVariantsRoot = RESOURCE_ROOT.resolve(
                "defaultresources/excavated_variants/excavated_variants");
        JsonObject variants = read(
                excavatedVariantsRoot.resolve("variants/better_ore_geology.json5"), JsonObject.class);

        assertTrue(variants.getAsJsonArray("provided_stones").isEmpty(),
                "deposit families must not be registered as replacement host stones");
        JsonArray providedOres = variants.getAsJsonArray("provided_ores");
        assertEquals(SALIENT_FAMILIES, providedOres.asList().stream()
                .map(entry -> entry.getAsJsonObject().get("id").getAsString())
                .collect(Collectors.toUnmodifiableSet()));
        assertEquals(SALIENT_FAMILIES.size(), providedOres.size());

        for (var entry : providedOres) {
            JsonObject ore = entry.getAsJsonObject();
            String family = ore.get("id").getAsString();
            assertEquals(List.of("stone", "deepslate"), ore.getAsJsonArray("stone").asList().stream()
                    .map(value -> value.getAsString()).toList(), family);
            assertEquals(Set.of("better_ore_geology:" + family, "better_ore_geology:deepslate_" + family),
                    ore.getAsJsonArray("block_id").asList().stream()
                            .map(value -> value.getAsString())
                            .collect(Collectors.toUnmodifiableSet()), family);
            assertEquals(List.of("stone"), ore.getAsJsonArray("types").asList().stream()
                    .map(value -> value.getAsString()).toList(), family);
            assertTrue(ore.getAsJsonArray("orename").isEmpty(),
                    family + " must not inherit conventional material ore tags");

            Path modifierPath = excavatedVariantsRoot.resolve("modifiers/better_ore_geology/"
                    + family + ".json5");
            JsonObject modifier = read(modifierPath, JsonObject.class);
            assertEquals("ore:" + family, modifier.get("filter").getAsString(), modifierPath.toString());
            assertEquals(Set.of(
                            "minecraft:blocks/mineable/pickaxe",
                            "better_ore_geology:blocks/deposit_ore_blocks/" + family,
                            "better_ore_geology:blocks/deposit_ore_blocks"),
                    modifier.getAsJsonArray("tags").asList().stream()
                            .map(value -> value.getAsString())
                            .collect(Collectors.toUnmodifiableSet()),
                    modifierPath.toString());
        }
    }

    @Test
    void disabledPlacedFeatureResourcesValidate() throws IOException {
        try (var paths = Files.list(DATA_ROOT.resolve("disabled_placed_features"))) {
            var resources = paths.filter(path -> path.getFileName().toString().endsWith(".json")).toList();
            assertFalse(resources.isEmpty(), "expected disabled placed feature resources");
            resources.stream()
                    .map(path -> read(path, DisabledFeaturesDefinition.class))
                    .forEach(DisabledFeaturesDefinition::validate);
        }
    }

    @Test
    void everyOreBlockHasExposureModelAndRetainedCanonicalItemTextures() throws IOException {
        JsonObject canonicalHashes = read(Path.of("src/test/resources/canonical_ore_texture_hashes.json"), JsonObject.class);

        try (var paths = Files.list(DATA_ROOT.resolve("better_ore_geology"))) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                OreDefinition definition = read(path, OreDefinition.class);
                definition.validate();

                for (OreDefinition.VariantDefinition oreVariant : definition.variants()) {
                    assertEquals(OreDefinition.TextureMode.CUBE_SIDED, oreVariant.textureMode(), path.toString());
                    String blockId = oreVariant.blockId();
                    String textureBlockId = blockId;
                    assertCanonicalDefinitionTextures(oreVariant, textureBlockId);
                    assertExposureBlockstate(blockId, definition.id(), oreVariant.host());
                    assertItemUsesCanonicalModel(blockId);
                    assertFalse(Files.exists(ASSET_ROOT.resolve("models/block/" + blockId + ".json")), blockId);

                    Set<String> variantHashes = new HashSet<>();
                    for (int variant = 0; variant < 3; variant++) {
                        Path modelPath = ASSET_ROOT.resolve("models/block/" + blockId + "_" + variant + ".json");
                        JsonObject model = read(modelPath, JsonObject.class);
                        assertEquals("minecraft:block/cube", model.get("parent").getAsString(), modelPath.toString());
                        JsonObject textures = model.getAsJsonObject("textures");
                        assertEquals(textureRef(textureBlockId, variant, "south"), textures.get("particle").getAsString(), modelPath.toString());
                        Map<String, String> faceHashes = new java.util.HashMap<>();
                        for (String face : FACES) {
                            String expectedTexture = textureRef(textureBlockId, variant, face);
                            assertEquals(expectedTexture, textures.get(face).getAsString(), modelPath + " " + face);
                            Path texturePath = ASSET_ROOT.resolve("textures/block/" + textureBlockId + "_" + variant + "_" + face + ".png");
                            assertFinalTexture(texturePath);
                            faceHashes.put(face, sha256(texturePath));
                        }
                        assertAllFaceReuseContract(oreVariant.host(), faceHashes, modelPath);
                        assertTrue(variantHashes.add(faceHashes.get("south")),
                                "variants must have different approved morphology: " + modelPath);
                    }
                    assertEquals(3, variantHashes.size(), blockId);
                    assertCanonicalHashes(definition.id(), oreVariant.host(), canonicalHashes);
                }
            }
        }
    }

    private static void assertCanonicalDefinitionTextures(
            OreDefinition.VariantDefinition variant,
            String textureBlockId
    ) {
        Map<String, String> actual = Map.of(
                "north", variant.textures().north(),
                "east", variant.textures().east(),
                "south", variant.textures().south(),
                "west", variant.textures().west(),
                "up", variant.textures().up(),
                "down", variant.textures().down());
        for (String face : FACES) {
            assertEquals(textureRef(textureBlockId, 0, face), actual.get(face), variant.blockId() + " " + face);
        }
    }

    private static void assertExposureBlockstate(String blockId, String family, String host) throws IOException {
        Path statePath = ASSET_ROOT.resolve("blockstates/" + blockId + ".json");
        JsonObject state = read(statePath, JsonObject.class);
        assertEquals(Set.of("variants"), state.keySet(), statePath.toString());
        JsonObject variants = state.getAsJsonObject("variants");
        assertEquals(Set.of(""), variants.keySet(), statePath.toString());
        JsonObject entry = variants.getAsJsonObject("");
        assertEquals(Set.of("model"), entry.keySet(),
                "rotations, mirrors, and explicit weights are forbidden: " + statePath);
        assertEquals("better_ore_geology:block/" + blockId + "_exposure",
                entry.get("model").getAsString(), statePath.toString());

        Path modelPath = ASSET_ROOT.resolve("models/block/" + blockId + "_exposure.json");
        JsonObject model = read(modelPath, JsonObject.class);
        assertEquals("better_ore_geology:exposure", model.get("loader").getAsString(), modelPath.toString());
        assertEquals(family, model.get("family").getAsString(), modelPath.toString());
        assertFalse(model.has("parent"), "exposure model must supply its own cube: " + modelPath);
        JsonArray elements = model.getAsJsonArray("elements");
        assertEquals(1, elements.size(), modelPath.toString());
        JsonObject cube = elements.get(0).getAsJsonObject();
        assertEquals(Set.of("from", "to", "faces"), cube.keySet(), modelPath.toString());
        assertEquals(List.of(0, 0, 0), cube.getAsJsonArray("from").asList().stream()
                .map(JsonElement::getAsInt).toList(), modelPath.toString());
        assertEquals(List.of(16, 16, 16), cube.getAsJsonArray("to").asList().stream()
                .map(JsonElement::getAsInt).toList(), modelPath.toString());
        JsonObject faces = cube.getAsJsonObject("faces");
        assertEquals(FACES, faces.keySet(), modelPath.toString());
        for (String face : FACES) {
            JsonObject faceModel = faces.getAsJsonObject(face);
            assertEquals(Set.of("texture", "cullface"), faceModel.keySet(), modelPath + " " + face);
            assertEquals("#" + face, faceModel.get("texture").getAsString(), modelPath + " " + face);
            assertEquals(face, faceModel.get("cullface").getAsString(), modelPath + " " + face);
        }

        JsonObject textures = model.getAsJsonObject("textures");
        assertEquals(1 + EXPOSURE_ROLES.size() * 3 * FACES.size(), textures.size(), modelPath.toString());
        assertEquals(exposureTextureRef(blockId, "original", 0, "south"),
                textures.get("particle").getAsString(), modelPath.toString());
        for (int roleIndex = 0; roleIndex < EXPOSURE_ROLES.size(); roleIndex++) {
            String role = EXPOSURE_ROLES.get(roleIndex);
            for (int variant = 0; variant < 3; variant++) {
                Map<String, String> faceHashes = new java.util.HashMap<>();
                for (String face : FACES) {
                    String slot = "slot" + (roleIndex * 3 + variant) + "_" + face;
                    String expected = exposureTextureRef(blockId, role, variant, face);
                    assertEquals(expected, textures.get(slot).getAsString(), modelPath + " " + slot);
                    Path texturePath = ASSET_ROOT.resolve("textures/block/exposure/" + blockId + "/"
                            + role + "_" + variant + "_" + face + ".png");
                    assertFinalTexture(texturePath);
                    faceHashes.put(face, sha256(texturePath));
                }
                assertAllFaceReuseContract(host, faceHashes, modelPath);
            }
        }
    }

    private static String exposureTextureRef(String blockId, String role, int variant, String face) {
        return "better_ore_geology:block/exposure/" + blockId + "/" + role + "_" + variant + "_" + face;
    }

    private static void assertItemUsesCanonicalModel(String blockId) {
        Path itemPath = ASSET_ROOT.resolve("models/item/" + blockId + ".json");
        assertEquals("better_ore_geology:block/" + blockId + "_0",
                read(itemPath, JsonObject.class).get("parent").getAsString(), itemPath.toString());
    }

    private static void assertFinalTexture(Path texturePath) throws IOException {
        BufferedImage image = ImageIO.read(texturePath.toFile());
        assertTrue(image != null, texturePath.toString());
        assertEquals(16, image.getWidth(), texturePath.toString());
        assertEquals(16, image.getHeight(), texturePath.toString());
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = image.getRGB(x, y);
                assertEquals(255, argb >>> 24, texturePath + " alpha at " + x + "," + y);
            }
        }
    }

    private static void assertAllFaceReuseContract(
            String host, Map<String, String> hashes, Path modelPath
    ) {
        if (host.equals("stone")) {
            assertEquals(1, new HashSet<>(hashes.values()).size(),
                    "stone reuses one approved texture on all faces: " + modelPath);
            return;
        }
        assertEquals(1, Set.of("north", "east", "south", "west").stream()
                .map(hashes::get).collect(Collectors.toSet()).size(),
                "deepslate lateral faces reuse one approved texture: " + modelPath);
        assertEquals(hashes.get("up"), hashes.get("down"),
                "deepslate end faces share the directional top host: " + modelPath);
        assertFalse(hashes.get("south").equals(hashes.get("up")),
                "deepslate host sidedness must remain visible: " + modelPath);
    }

    private static void assertCanonicalHashes(String family, String host, JsonObject manifest) {
        JsonObject hashes = manifest.getAsJsonObject(family);
        if (host.equals("stone")) {
            assertEquals(hashes.get("stone_0_south").getAsString(),
                    sha256(ASSET_ROOT.resolve("textures/block/" + blockId(family) + "_0_south.png")), family);
        } else {
            String blockId = "deepslate_" + blockId(family);
            assertEquals(hashes.get("deepslate_0_south").getAsString(),
                    sha256(ASSET_ROOT.resolve("textures/block/" + blockId + "_0_south.png")), family);
            assertEquals(hashes.get("deepslate_0_up").getAsString(),
                    sha256(ASSET_ROOT.resolve("textures/block/" + blockId + "_0_up.png")), family);
        }
    }

    private static String textureRef(String blockId, int variant, String face) {
        return "better_ore_geology:block/" + blockId + "_" + variant + "_" + face;
    }

    private static String blockId(String family) {
        return family;
    }

    private static String sha256(Path path) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to hash " + path, exception);
        }
    }

    @Test
    void crushedItemsAndSurfaceSamplesHaveSeparateResources() throws IOException {
        Path resources = Path.of("src/main/resources");
        Path definitions = resources.resolve("data/better_ore_geology/better_ore_geology");
        try (var paths = Files.list(definitions)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                OreDefinition definition = read(path, OreDefinition.class);
                String crushed = definition.crushedItemId();
                String sample = definition.surfaceSampleBlockId();
                String small = definition.smallOreChunkItemId();
                assertFalse(Files.exists(resources.resolve("assets/better_ore_geology/blockstates/" + crushed + ".json")), crushed);
                assertFalse(Files.exists(resources.resolve("data/better_ore_geology/loot_tables/blocks/" + crushed + ".json")), crushed);
                assertTrue(Files.isRegularFile(resources.resolve("assets/better_ore_geology/blockstates/" + sample + ".json")), sample);
                assertTrue(Files.isRegularFile(resources.resolve("data/better_ore_geology/loot_tables/blocks/" + sample + ".json")), sample);
                for (int variant = 0; variant < 5; variant++) {
                    Path modelPath = resources.resolve(
                            "assets/better_ore_geology/models/block/" + sample + "_" + variant + ".json");
                    assertTrue(Files.isRegularFile(modelPath), sample);
                    assertSurfaceSampleModelUsesOpaqueOreTexture(resources, modelPath);
                }
                JsonObject itemModel = read(
                        resources.resolve("assets/better_ore_geology/models/item/" + crushed + ".json"),
                        JsonObject.class);
                String texture = itemModel.getAsJsonObject("textures").get("layer0").getAsString();
                assertTrue(texture.startsWith("better_ore_geology:item/"), crushed + " texture " + texture);
                assertTrue(Files.isRegularFile(resources.resolve(
                        "assets/better_ore_geology/textures/item/" + texture.substring("better_ore_geology:item/".length()) + ".png")));
                String rinsed = "rinsed_" + definition.id();
                JsonObject rinsedModel = read(
                        resources.resolve("assets/better_ore_geology/models/item/" + rinsed + ".json"),
                        JsonObject.class);
                assertEquals("better_ore_geology:item/" + crushed,
                        rinsedModel.getAsJsonObject("textures").get("layer0").getAsString(), rinsed);
                JsonObject sampleItemModel = read(
                        resources.resolve("assets/better_ore_geology/models/item/" + small + ".json"),
                        JsonObject.class);
                assertEquals("minecraft:item/generated",
                        sampleItemModel.get("parent").getAsString(), sample);
                assertEquals("better_ore_geology:item/" + small,
                        sampleItemModel.getAsJsonObject("textures").get("layer0").getAsString(), sample);
                Path smallTexture = resources.resolve("assets/better_ore_geology/textures/item/" + small + ".png");
                BufferedImage smallImage = ImageIO.read(smallTexture.toFile());
                assertTrue(smallImage != null, smallTexture.toString());
                assertEquals(16, smallImage.getWidth(), smallTexture.toString());
                assertEquals(16, smallImage.getHeight(), smallTexture.toString());
                assertFalse(Files.exists(resources.resolve("assets/better_ore_geology/models/item/" + sample + ".json")),
                        "surface samples have no separate item identity");
                JsonObject sampleLoot = read(resources.resolve(
                        "data/better_ore_geology/loot_tables/blocks/" + sample + ".json"), JsonObject.class);
                String serializedLoot = GSON.toJson(sampleLoot);
                assertTrue(serializedLoot.contains("better_ore_geology:" + small));
                assertFalse(serializedLoot.contains("fortune"));
                JsonObject combine = read(resources.resolve(
                        "data/better_ore_geology/recipes/crafting/small_chunks/" + definition.id() + ".json"),
                        JsonObject.class);
                assertEquals(9, combine.getAsJsonArray("ingredients").size());
                assertEquals("better_ore_geology:" + definition.oreChunkItemId(),
                        combine.getAsJsonObject("result").get("item").getAsString());
            }
        }
        assertTrue(Files.isRegularFile(resources.resolve("assets/better_ore_geology/blockstates/oil_seep.json")));
        assertTrue(Files.isRegularFile(resources.resolve("data/better_ore_geology/loot_tables/blocks/oil_seep.json")));
        for (int variant = 0; variant < 5; variant++) {
            JsonObject oilModel = read(resources.resolve(
                    "assets/better_ore_geology/models/block/oil_seep_" + variant + ".json"), JsonObject.class);
            assertEquals("better_ore_geology:block/oil_bearing_shale",
                    oilModel.getAsJsonObject("textures").get("all").getAsString());
            assertTrue(oilModel.getAsJsonArray("elements").size() >= 4);
            assertTrue(oilModel.getAsJsonArray("elements").size() <= 5);
        }
        Path oilTexture = resources.resolve("assets/better_ore_geology/textures/block/oil_bearing_shale.png");
        BufferedImage oilImage = ImageIO.read(oilTexture.toFile());
        assertTrue(oilImage != null);
        assertEquals(16, oilImage.getWidth());
        assertEquals(16, oilImage.getHeight());
    }

    @Test
    void oreChunksHaveTransparentItemsAndEnchantmentStableLoot() throws IOException {
        Path resources = Path.of("src/main/resources");
        Path definitions = resources.resolve("data/better_ore_geology/better_ore_geology");
        int definitionCount = 0;
        try (var paths = Files.list(definitions)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                definitionCount++;
                OreDefinition definition = read(path, OreDefinition.class);
                String chunk = definition.oreChunkItemId();
                String familyTag = definition.id();
                assertDepositTags(resources, definition, familyTag, chunk);
                Path texturePath = resources.resolve("assets/better_ore_geology/textures/item/" + chunk + ".png");
                BufferedImage image = ImageIO.read(texturePath.toFile());
                assertTrue(image != null, texturePath.toString());
                assertEquals(16, image.getWidth(), texturePath.toString());
                assertEquals(16, image.getHeight(), texturePath.toString());
                boolean hasTransparentPixel = false;
                boolean hasVisiblePixel = false;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int alpha = image.getRGB(x, y) >>> 24;
                        hasTransparentPixel |= alpha == 0;
                        hasVisiblePixel |= alpha > 0;
                    }
                }
                assertTrue(hasTransparentPixel, chunk + " must use a transparent background");
                assertTrue(hasVisiblePixel, chunk + " must contain visible ore pixels");
                assertTrue(visibleComponents(image) >= 2,
                        chunk + " must visibly read as multiple pieces at inventory scale");

                JsonObject itemModel = read(
                        resources.resolve("assets/better_ore_geology/models/item/" + chunk + ".json"),
                        JsonObject.class);
                assertEquals("minecraft:item/generated", itemModel.get("parent").getAsString(), chunk);
                assertEquals("better_ore_geology:item/" + chunk,
                        itemModel.getAsJsonObject("textures").get("layer0").getAsString(), chunk);

                for (OreDefinition.VariantDefinition variant : definition.variants()) {
                    JsonObject loot = read(
                            resources.resolve("data/better_ore_geology/loot_tables/blocks/" + variant.blockId() + ".json"),
                            JsonObject.class);
                    String serialized = GSON.toJson(loot);
                    assertTrue(serialized.contains("minecraft:silk_touch"), variant.blockId());
                    assertTrue(serialized.contains("better_ore_geology:" + chunk), variant.blockId());
                    assertTrue(serialized.contains("better_ore_geology:" + variant.blockId()), variant.blockId());
                    assertFalse(serialized.contains("minecraft:fortune"), variant.blockId());
                    assertFalse(serialized.contains("apply_bonus"), variant.blockId());

                    Path reassemblyPath = resources.resolve(
                            "data/better_ore_geology/recipes/crafting/ore_reassembly/" + variant.blockId() + ".json");
                    JsonObject reassembly = read(reassemblyPath, JsonObject.class);
                    assertEquals("minecraft:crafting_shapeless", reassembly.get("type").getAsString(),
                            reassemblyPath.toString());
                    assertEquals(Set.of("better_ore_geology:" + chunk, variant.copyPropertiesFrom()),
                            reassembly.getAsJsonArray("ingredients").asList().stream()
                                    .map(ingredient -> ingredient.getAsJsonObject().get("item").getAsString())
                                    .collect(Collectors.toUnmodifiableSet()),
                            reassemblyPath.toString());
                    assertEquals("better_ore_geology:" + variant.blockId(),
                            reassembly.getAsJsonObject("result").get("item").getAsString(),
                            reassemblyPath.toString());

                    Path blockCrushingPath = resources.resolve(
                            "data/better_ore_geology/recipes/compat/create/crushing/" + variant.blockId() + ".json");
                    JsonObject blockCrushing = read(blockCrushingPath, JsonObject.class);
                    assertEquals("create:crushing", blockCrushing.get("type").getAsString(),
                            blockCrushingPath.toString());
                    JsonArray blockIngredients = blockCrushing.getAsJsonArray("ingredients");
                    assertEquals(1, blockIngredients.size(), blockCrushingPath.toString());
                    JsonObject blockIngredient = blockIngredients.get(0).getAsJsonObject();
                    assertEquals("forge:nbt", blockIngredient.get("type").getAsString(),
                            blockCrushingPath.toString());
                    assertEquals("better_ore_geology:" + variant.blockId(), blockIngredient.get("item").getAsString(),
                            blockCrushingPath.toString());
                    JsonArray blockResults = blockCrushing.getAsJsonArray("results");
                    assertEquals(2, blockResults.size(), blockCrushingPath.toString());
                    assertEquals(Set.of("item"), blockResults.get(0).getAsJsonObject().keySet(),
                            blockCrushingPath.toString());
                    assertEquals("better_ore_geology:" + chunk,
                            blockResults.get(0).getAsJsonObject().get("item").getAsString(),
                            blockCrushingPath.toString());
                    assertEquals(Set.of("item"), blockResults.get(1).getAsJsonObject().keySet(),
                            blockCrushingPath.toString());
                    assertEquals(variant.copyPropertiesFrom(),
                            blockResults.get(1).getAsJsonObject().get("item").getAsString(),
                            blockCrushingPath.toString());
                    assertTrue(Set.of("minecraft:stone", "minecraft:deepslate").contains(variant.copyPropertiesFrom()),
                            "native host breakdown may only return the useful existing host");
                }

                Path chunkCrushingPath = resources.resolve(
                            "data/better_ore_geology/recipes/compat/create/crushing/ore_chunks/"
                                + definition.id() + ".json");
                JsonObject chunkCrushing = read(chunkCrushingPath, JsonObject.class);
                assertEquals("create:crushing", chunkCrushing.get("type").getAsString(),
                        chunkCrushingPath.toString());
                JsonArray chunkIngredients = chunkCrushing.getAsJsonArray("ingredients");
                assertEquals(1, chunkIngredients.size(), chunkCrushingPath.toString());
                assertEquals("better_ore_geology:" + chunk,
                        chunkIngredients.get(0).getAsJsonObject().get("item").getAsString(),
                        chunkCrushingPath.toString());
                JsonArray chunkResults = chunkCrushing.getAsJsonArray("results");
                assertEquals(1, chunkResults.size(), chunkCrushingPath.toString());
                assertEquals(Set.of("item", "count"), chunkResults.get(0).getAsJsonObject().keySet(),
                        chunkCrushingPath.toString());
                assertEquals("better_ore_geology:" + definition.crushedItemId(),
                        chunkResults.get(0).getAsJsonObject().get("item").getAsString(),
                        chunkCrushingPath.toString());
                assertEquals(3, chunkResults.get(0).getAsJsonObject().get("count").getAsInt(),
                        chunkCrushingPath.toString());

                Path chunkMillingPath = resources.resolve(
                            "data/better_ore_geology/recipes/compat/create/milling/ore_chunks/"
                                + definition.id() + ".json");
                JsonObject chunkMilling = read(chunkMillingPath, JsonObject.class);
                assertEquals("create:milling", chunkMilling.get("type").getAsString(),
                        chunkMillingPath.toString());
                assertEquals("forge:mod_loaded",
                        chunkMilling.getAsJsonArray("conditions").get(0).getAsJsonObject().get("type").getAsString(),
                        chunkMillingPath.toString());
                assertEquals("create",
                        chunkMilling.getAsJsonArray("conditions").get(0).getAsJsonObject().get("modid").getAsString(),
                        chunkMillingPath.toString());
                assertEquals(400, chunkMilling.get("processingTime").getAsInt(), chunkMillingPath.toString());
                JsonArray millingIngredients = chunkMilling.getAsJsonArray("ingredients");
                assertEquals(1, millingIngredients.size(), chunkMillingPath.toString());
                assertEquals("better_ore_geology:" + chunk,
                        millingIngredients.get(0).getAsJsonObject().get("item").getAsString(),
                        chunkMillingPath.toString());
                JsonArray millingResults = chunkMilling.getAsJsonArray("results");
                assertEquals(1, millingResults.size(), chunkMillingPath.toString());
                assertEquals(Set.of("item", "count"), millingResults.get(0).getAsJsonObject().keySet(),
                        chunkMillingPath.toString());
                assertEquals("better_ore_geology:" + definition.crushedItemId(),
                        millingResults.get(0).getAsJsonObject().get("item").getAsString(),
                        chunkMillingPath.toString());
                assertEquals(2, millingResults.get(0).getAsJsonObject().get("count").getAsInt(),
                        chunkMillingPath.toString());

                boolean metalPrimary = !Set.of("coal_measures", "evaporite_beds", "black_shale")
                        .contains(definition.id());
                for (String recipeType : List.of("furnace", "blasting")) {
                    Path chunkCookPath = resources.resolve("data/better_ore_geology/recipes/thermal/"
                            + recipeType + "/" + definition.id() + "_chunk.json");
                    Path crushedCookPath = resources.resolve("data/better_ore_geology/recipes/thermal/"
                            + recipeType + "/" + definition.id() + "_crushed.json");
                    assertEquals(metalPrimary, Files.exists(chunkCookPath), chunkCookPath.toString());
                    assertFalse(Files.exists(crushedCookPath), crushedCookPath.toString());
                    if (metalPrimary) {
                        assertEquals(4, read(chunkCookPath, JsonObject.class)
                                .getAsJsonObject("result").get("count").getAsInt(), chunkCookPath.toString());
                    }
                }
            }
        }
        assertEquals(8, definitionCount);
        Path chunkMillingDirectory = resources.resolve(
                "data/better_ore_geology/recipes/compat/create/milling/ore_chunks");
        try (var millingPaths = Files.list(chunkMillingDirectory)) {
            assertEquals(8, millingPaths.filter(path -> path.getFileName().toString().endsWith(".json")).count(),
                    chunkMillingDirectory.toString());
        }
    }

    @Test
    void exposedGemChipsHaveAcquisitionAssemblyAndLegibleShardSprites() throws IOException {
        Map<String, String> outputs = Map.of(
                "diamond", "minecraft:diamond",
                "emerald", "minecraft:emerald",
                "amethyst", "minecraft:amethyst_shard");
        for (Map.Entry<String, String> entry : outputs.entrySet()) {
            String chip = "better_ore_geology:" + entry.getKey() + "_chip";
            JsonObject assembly = read(DATA_ROOT.resolve(
                    "recipes/crafting/gem_chips/" + entry.getKey() + "_assemble.json"), JsonObject.class);
            assertEquals(9, assembly.getAsJsonArray("ingredients").size(), chip);
            assertTrue(assembly.getAsJsonArray("ingredients").asList().stream().allMatch(ingredient ->
                    chip.equals(ingredient.getAsJsonObject().get("item").getAsString())), chip);
            assertEquals(entry.getValue(), assembly.getAsJsonObject("result").get("item").getAsString(), chip);

            Path sprite = ASSET_ROOT.resolve("textures/item/" + entry.getKey() + "_chip.png");
            BufferedImage image = ImageIO.read(sprite.toFile());
            assertEquals(3, visibleComponents(image), chip + " must read as three separate gem shards");
        }
        assertRecipeProduces("recipes/compat/hexerei/diamond_assay/tin_quartz.json", "better_ore_geology:diamond_chip");
        assertRecipeProduces("recipes/compat/pneumaticcraft/separation/tin_quartz.json", "better_ore_geology:emerald_chip");
        assertRecipeProduces("recipes/compat/pneumaticcraft/separation/tin_quartz.json", "better_ore_geology:amethyst_chip");
    }

    private static void assertRecipeProduces(String relativePath, String item) throws IOException {
        assertTrue(Files.readString(DATA_ROOT.resolve(relativePath)).contains(item), relativePath + " -> " + item);
    }

    private static int visibleComponents(BufferedImage image) {
        boolean[][] seen = new boolean[image.getHeight()][image.getWidth()];
        int components = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if (seen[y][x] || image.getRGB(x, y) >>> 24 == 0) continue;
            components++;
            ArrayDeque<int[]> pending = new ArrayDeque<>();
            pending.add(new int[] {x, y});
            seen[y][x] = true;
            while (!pending.isEmpty()) {
                int[] point = pending.removeFirst();
                for (int[] delta : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nextX = point[0] + delta[0], nextY = point[1] + delta[1];
                    if (nextX < 0 || nextY < 0 || nextX >= image.getWidth() || nextY >= image.getHeight()
                            || seen[nextY][nextX] || image.getRGB(nextX, nextY) >>> 24 == 0) continue;
                    seen[nextY][nextX] = true;
                    pending.add(new int[] {nextX, nextY});
                }
            }
        }
        return components;
    }

    private static void assertDepositTags(
            Path resources,
            OreDefinition definition,
            String familyTag,
            String chunk
    ) {
        Set<String> expectedBlocks = definition.variants().stream()
                .map(variant -> "better_ore_geology:" + variant.blockId())
                .collect(Collectors.toUnmodifiableSet());
        Path path = resources.resolve("data/better_ore_geology/tags/blocks/deposit_ore_blocks/"
                + familyTag + ".json");
        JsonObject tag = read(path, JsonObject.class);
        Set<String> values = tag.getAsJsonArray("values").asList().stream()
                .map(entry -> entry.getAsString())
                .collect(Collectors.toUnmodifiableSet());
        assertEquals(expectedBlocks, values, path.toString());
        assertFalse(Files.exists(resources.resolve("data/better_ore_geology/tags/items/deposit_ore_blocks/"
                + familyTag + ".json")), "hosted ore blocks must not be exposed through processing item tags");

        Path chunkPath = resources.resolve("data/better_ore_geology/tags/items/deposit_chunks/"
                + familyTag + ".json");
        JsonObject chunkTag = read(chunkPath, JsonObject.class);
        assertEquals(List.of("better_ore_geology:" + chunk),
                chunkTag.getAsJsonArray("values").asList().stream().map(entry -> entry.getAsString()).toList(),
                chunkPath.toString());
    }

    @Test
    void phaseThreeProcessingGraphIsCompleteAndLegacyFree() throws IOException {
        Path processing = DATA_ROOT.resolve("processing_definitions");
        List<Path> definitions;
        try (var paths = Files.list(processing)) {
            definitions = paths.filter(path -> path.toString().endsWith(".json")).toList();
        }
        assertEquals(8, definitions.size());
        assertEquals(SALIENT_FAMILIES, definitions.stream()
                .map(path -> path.getFileName().toString().replace(".json", ""))
                .collect(Collectors.toUnmodifiableSet()));
        Set<String> routeIds = Set.of("tech", "hand_sifting", "dry_sifting",
                "waterlogged_zinc", "waterlogged_brass", "blood", "hexerei", "ars", "occultism");
        for (Path path : definitions) {
            JsonObject definition = read(path, JsonObject.class);
            assertEquals("bc.better_ore_geology.processing.v3", definition.get("schema").getAsString());
            assertEquals(routeIds, definition.getAsJsonObject("routes").keySet());
            assertEquals(4, definition.getAsJsonObject("routes").getAsJsonObject("tech")
                    .get("input_count").getAsInt());
            assertEquals(2.0, definition.getAsJsonObject("routes").getAsJsonObject("tech")
                    .get("pressure").getAsDouble());
            assertTrue(definition.get("primary").isJsonObject());
        }

        for (String family : SALIENT_FAMILIES) {
            Path rinsingPath = DATA_ROOT.resolve("recipes/compat/create/rinsing/" + family + ".json");
            JsonObject rinsing = read(rinsingPath, JsonObject.class);
            assertEquals("create:filling", rinsing.get("type").getAsString());
            assertEquals(250, rinsing.getAsJsonArray("ingredients").get(1).getAsJsonObject()
                    .get("amount").getAsInt());

            Path pressurePath = DATA_ROOT.resolve(
                    "recipes/compat/pneumaticcraft/separation/" + family + ".json");
            JsonObject pressure = read(pressurePath, JsonObject.class);
            assertEquals("pneumaticcraft:pressure_chamber", pressure.get("type").getAsString());
            assertEquals(4, pressure.getAsJsonArray("inputs").get(0).getAsJsonObject()
                    .get("count").getAsInt());
            assertEquals(2.0, pressure.get("pressure").getAsDouble());
            assertFalse(GSON.toJson(pressure).contains("acid"), pressurePath.toString());
            assertFalse(GSON.toJson(pressure).contains("diamond_concentrate"), pressurePath.toString());

            JsonObject blood = read(DATA_ROOT.resolve(
                    "recipes/compat/bloodmagic/separation/" + family + ".json"), JsonObject.class);
            assertEquals("bloodmagic:arc", blood.get("type").getAsString());
            assertTrue(blood.getAsJsonObject("input").get("item").getAsString().contains("ore_chunk_"));
            assertEquals("bloodmagic:arc/cuttingfluid",
                    blood.getAsJsonObject("tool").get("tag").getAsString());

            JsonObject hexerei = read(DATA_ROOT.resolve(
                    "recipes/compat/hexerei/separation/" + family + ".json"), JsonObject.class);
            assertEquals("hexerei:mixingcauldron", hexerei.get("type").getAsString());
            assertEquals(8, hexerei.getAsJsonArray("ingredients").size());
            assertFalse(GSON.toJson(hexerei).contains("grout"));

            JsonObject ars = read(DATA_ROOT.resolve(
                    "recipes/compat/ars_nouveau/separation/" + family + ".json"), JsonObject.class);
            assertEquals("ars_nouveau:crush", ars.get("type").getAsString());
            assertEquals(1.0, ars.getAsJsonArray("output").get(0).getAsJsonObject()
                    .get("chance").getAsDouble());
            assertTrue(ars.getAsJsonObject("input").get("item").getAsString().contains("ore_chunk_"));

            JsonObject occultism = read(DATA_ROOT.resolve(
                    "recipes/compat/occultism/separation/" + family + ".json"), JsonObject.class);
            assertEquals("occultism:crushing", occultism.get("type").getAsString());
            assertFalse(occultism.get("ignore_crushing_multiplier").getAsBoolean());

            Path siftingDirectory = DATA_ROOT.resolve("recipes/compat/createsifter/sifting/" + family);
            try (var paths = Files.list(siftingDirectory)) {
                assertEquals(8, paths.filter(path -> path.toString().endsWith(".json")).count());
            }
            JsonObject brassWet = read(siftingDirectory.resolve("brass_wet.json"), JsonObject.class);
            assertEquals("createsifter:sifting", brassWet.get("type").getAsString());
            assertTrue(brassWet.get("waterlogged").getAsBoolean());
            assertFalse(brassWet.getAsJsonArray("results").isEmpty());
        }

        JsonObject blackShalePressure = read(DATA_ROOT.resolve(
                "recipes/compat/pneumaticcraft/separation/black_shale.json"), JsonObject.class);
        assertTrue(blackShalePressure.getAsJsonArray("results").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(result -> "chemlib:vanadium".equals(result.get("item").getAsString())
                        && result.get("count").getAsInt() == 1));
        Path blackShaleSifting = DATA_ROOT.resolve("recipes/compat/createsifter/sifting/black_shale");
        JsonObject zincWet = read(blackShaleSifting.resolve("zinc_wet.json"), JsonObject.class);
        JsonObject blackShaleBrassWet = read(blackShaleSifting.resolve("brass_wet.json"), JsonObject.class);
        assertEquals(0.025, resultChance(zincWet, "chemlib:vanadium"));
        assertEquals(0.05, resultChance(blackShaleBrassWet, "chemlib:vanadium"));

        JsonObject diamondAssay = read(DATA_ROOT.resolve(
                "recipes/compat/hexerei/diamond_assay/tin_quartz.json"), JsonObject.class);
        assertEquals("hexerei:mixingcauldron", diamondAssay.get("type").getAsString());
        assertEquals(8, diamondAssay.getAsJsonArray("ingredients").size());
        assertEquals(4, diamondAssay.getAsJsonArray("ingredients").asList().stream()
                .filter(value -> "better_ore_geology:crushed_tin_quartz".equals(
                        value.getAsJsonObject().get("item").getAsString())).count());
        assertEquals(4, diamondAssay.getAsJsonArray("ingredients").asList().stream()
                .filter(value -> "hexerei:selenite_shard".equals(
                        value.getAsJsonObject().get("item").getAsString())).count());
        assertEquals(250, diamondAssay.get("fluidLevelsConsumed").getAsInt());
        assertEquals("heated", diamondAssay.get("heatRequirement").getAsString());
        assertEquals("better_ore_geology:diamond_chip",
                diamondAssay.getAsJsonObject("output").get("item").getAsString());
        JsonObject tinProcessing = read(DATA_ROOT.resolve(
                "processing_definitions/tin_quartz.json"), JsonObject.class);
        assertEquals("better_ore_geology:diamond_chip", tinProcessing.getAsJsonObject("routes")
                .getAsJsonObject("hexerei").getAsJsonObject("diamond_assay")
                .get("output").getAsString());

        assertTrue(Files.exists(DATA_ROOT.resolve("recipes/thermal/furnace/copper_bloom_chunk.json")));
        assertTrue(Files.exists(DATA_ROOT.resolve("recipes/compat/tconstruct/melting/copper_bloom_chunk.json")));
        Path foundryRecipes = DATA_ROOT.resolve("recipes/compat/tconstruct/foundry");
        if (Files.exists(foundryRecipes)) {
            try (var paths = Files.walk(foundryRecipes)) {
                assertEquals(0, paths.filter(path -> path.toString().endsWith(".json")).count(),
                        "fixed melting recipes must not have competing Foundry IDs");
            }
        }
        for (String material : List.of("quartz", "diamond", "emerald", "amethyst")) {
            assertFalse(Files.exists(DATA_ROOT.resolve(
                    "recipes/compat/tconstruct/melting/concentrate_" + material + ".json")), material);
            assertFalse(Files.exists(DATA_ROOT.resolve(
                    "recipes/compat/tconstruct/foundry/concentrate_" + material + ".json")), material);
        }
        assertFalse(Files.exists(DATA_ROOT.resolve("recipes/crafting/immediate/evaporite_rock_salt.json")));
        assertFalse(Files.exists(DATA_ROOT.resolve("recipes/crafting/immediate/black_shale_soul_sand.json")));
        assertTrue(Files.exists(DATA_ROOT.resolve("recipes/crafting/immediate/hotstone_magma.json")));
        for (String material : List.of("titanium", "thorium")) {
            JsonObject moltenTag = read(RESOURCE_ROOT.resolve(
                    "data/forge/tags/fluids/molten_" + material + ".json"), JsonObject.class);
            assertEquals(2, moltenTag.getAsJsonArray("values").size());
            assertTrue(Files.isRegularFile(ASSET_ROOT.resolve(
                    "models/item/molten_" + material + "_bucket.json")));
            JsonObject blockstate = read(ASSET_ROOT.resolve(
                    "blockstates/molten_" + material + ".json"), JsonObject.class);
            assertEquals("better_ore_geology:block/molten_" + material,
                    blockstate.getAsJsonObject("variants").getAsJsonObject("")
                            .get("model").getAsString());
            JsonObject blockModel = read(ASSET_ROOT.resolve(
                    "models/block/molten_" + material + ".json"), JsonObject.class);
            assertEquals("minecraft:block/water_still",
                    blockModel.getAsJsonObject("textures").get("particle").getAsString());
            for (String form : List.of("ingot", "nugget")) {
                for (String cast : List.of("multi_use", "single_use")) {
                    JsonObject casting = read(DATA_ROOT.resolve("recipes/compat/tconstruct/casting/"
                            + material + "/" + form + "_" + cast + ".json"), JsonObject.class);
                    assertEquals(form.equals("ingot") ? 90 : 10,
                            casting.getAsJsonObject("fluid").get("amount").getAsInt());
                    assertEquals("forge:molten_" + material,
                            casting.getAsJsonObject("fluid").get("tag").getAsString());
                    assertEquals("chemlib:" + material + "_" + form,
                            casting.getAsJsonObject("result").get("item").getAsString());
                    assertEquals(cast.equals("single_use"),
                            casting.has("cast_consumed") && casting.get("cast_consumed").getAsBoolean());
                }
            }
        }
        for (String material : List.of("beryl", "beryllium", "calcium", "carbon", "chromium", "gallium",
                "iridium", "magnesium", "phosphate", "platinum", "silicon", "sodium", "tantalum", "tungsten")) {
            assertFalse(Files.exists(DATA_ROOT.resolve(
                    "recipes/compat/tconstruct/melting/concentrate_" + material + ".json")),
                    "do not invent an uninstalled molten form for " + material);
            assertFalse(Files.exists(ASSET_ROOT.resolve("models/item/" + material + "_concentrate.json")),
                    "pruned concentrate remains in item resources: " + material);
        }
        try (var paths = Files.list(ASSET_ROOT.resolve("models/item"))) {
            assertEquals(RETAINED_MATERIALS, paths
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith("_concentrate.json"))
                    .map(name -> name.replace("_concentrate.json", ""))
                    .collect(Collectors.toUnmodifiableSet()));
        }

        assertFalse(Files.exists(ASSET_ROOT.resolve("models/item/diamond_concentrate.json")));
        try (var paths = Files.list(ASSET_ROOT.resolve("models/item"))) {
            assertEquals(0, paths.filter(path -> path.getFileName().toString()
                    .endsWith("_grinding_ball.json")).count());
        }

        Map<String, Integer> solidFuel = Map.of("copper", 500, "tin", 700, "zinc", 700);
        for (String material : List.of("copper", "tin", "zinc", "iron", "gold", "nickel", "lead",
                "cadmium", "silver", "aluminum", "titanium", "cobalt", "osmium", "uranium", "thorium")) {
            Path melting = DATA_ROOT.resolve("recipes/compat/tconstruct/melting/concentrate_" + material + ".json");
            if (!Files.exists(melting)) continue;
            int expected = solidFuel.getOrDefault(material, 950);
            assertEquals(expected, read(melting, JsonObject.class).get("temperature").getAsInt(), material);
            assertEquals(40, read(melting, JsonObject.class).getAsJsonObject("result")
                    .get("amount").getAsInt(), material);
            assertEquals(4, read(DATA_ROOT.resolve("recipes/thermal/furnace/concentrate_"
                    + material + ".json"), JsonObject.class).getAsJsonObject("result")
                    .get("count").getAsInt(), material);
        }

        try (var paths = Files.walk(RESOURCE_ROOT.resolve("data/better_ore_geology"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(path);
                assertFalse(content.contains("corundum_beryl"), path.toString());
                for (String obsolete : List.of("copper_sulfide", "tin_tungsten_greisen", "lead_zinc_vein",
                        "cupriferous_redbed", "phosphate_rock", "kimberlite_pipe", "uranium_ore",
                        "thorium_ore", "soul_bearing_black_shale", "gem_pipe", "redbed")) {
                    assertFalse(content.contains(obsolete), path + " retains obsolete family " + obsolete);
                }
                assertFalse(content.contains("washed_"), path.toString());
                assertFalse(content.contains("tailings"), path.toString());
                assertFalse(content.contains("_grinding_ball"), path.toString());
                if (path.toString().contains("recipes/compat")) {
                    assertFalse(content.contains("sulfuric_acid"), path.toString());
                    assertFalse(content.contains("hydrochloric_acid"), path.toString());
                    assertFalse(content.contains("nitric_acid"), path.toString());
                }
            }
        }
    }

    @Test
    void hostedOreRecipesDoNotRequireSingleItemStacks() throws IOException {
        for (String recipe : List.of("ExcavatedSeparationRecipe.java", "ExcavatedReassemblyRecipe.java")) {
            String source = Files.readString(Path.of("src/main/java/com/bettercontent/betteroregeology/compat")
                    .resolve(recipe));
            assertFalse(source.contains("stack.getCount() != 1"),
                    recipe + " must let the crafting grid consume one item from an ordinary stack");
        }
    }

    private static double resultChance(JsonObject recipe, String item) {
        return recipe.getAsJsonArray("results").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(result -> item.equals(result.get("item").getAsString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing result " + item))
                .get("chance").getAsDouble();
    }

    private static void assertSurfaceSampleModelUsesOpaqueOreTexture(Path resources, Path modelPath) throws IOException {
        JsonObject model = read(modelPath, JsonObject.class);
        assertEquals("#all", model.getAsJsonObject("textures").get("particle").getAsString(), modelPath.toString());
        String texture = model.getAsJsonObject("textures").get("all").getAsString();
        assertTrue(texture.startsWith("better_ore_geology:block/"), modelPath + " texture " + texture);
        Path texturePath = resources.resolve(
                "assets/better_ore_geology/textures/block/" + texture.substring("better_ore_geology:block/".length()) + ".png");
        BufferedImage image = ImageIO.read(texturePath.toFile());
        assertTrue(image != null, texturePath.toString());
        assertEquals(16, image.getWidth(), texturePath.toString());
        assertEquals(16, image.getHeight(), texturePath.toString());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                assertEquals(255, image.getRGB(x, y) >>> 24, texturePath + " alpha at " + x + "," + y);
            }
        }
        for (var element : model.getAsJsonArray("elements")) {
            JsonArray from = element.getAsJsonObject().getAsJsonArray("from");
            JsonArray to = element.getAsJsonObject().getAsJsonArray("to");
            assertTrue(from.get(0).getAsDouble() >= 0 && from.get(2).getAsDouble() >= 0, modelPath.toString());
            assertTrue(to.get(0).getAsDouble() <= 16 && to.get(2).getAsDouble() <= 16, modelPath.toString());
            assertTrue(from.get(1).getAsDouble() > 0, modelPath.toString());
            assertTrue(to.get(1).getAsDouble() >= 2 && to.get(1).getAsDouble() <= 4, modelPath.toString());
            for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                assertEquals("#all", face.getValue().getAsJsonObject().get("texture").getAsString(),
                        modelPath + " " + face.getKey());
                JsonArray uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                assertEquals(4, uv.size(), modelPath + " " + face.getKey());
                assertTrue(uv.get(0).getAsInt() >= 0 && uv.get(1).getAsInt() >= 0,
                        modelPath + " " + face.getKey());
                assertTrue(uv.get(2).getAsInt() <= 16 && uv.get(3).getAsInt() <= 16,
                        modelPath + " " + face.getKey());
                assertFalse(uv.get(0).getAsInt() == 0 && uv.get(1).getAsInt() == 0
                                && uv.get(2).getAsInt() == 16 && uv.get(3).getAsInt() == 16,
                        "surface chips must crop the ore texture instead of squashing a whole block face: " + modelPath);
            }
        }
        assertTrue(model.getAsJsonArray("elements").size() >= 3, modelPath.toString());
        assertTrue(model.getAsJsonArray("elements").size() <= 4, modelPath.toString());
    }

    private record OreVariant(String oreId, String variant) {
    }

    private static <T> T read(Path path, Class<T> type) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }

    private static final class GenerationDefinition {
        @com.google.gson.annotations.SerializedName("ore_id")
        private String oreId;
        private String variant;
    }
}
