package com.bettercontent.betteroregeology.exposureharness;

import com.bettercontent.betteroregeology.client.ExposureBakedModel;
import com.bettercontent.betteroregeology.visual.ExposureField;
import com.bettercontent.betteroregeology.visual.ExposureState;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Nonshipping, fixed-scene client proof using real loaded models and Minecraft framebuffer captures. */
@Mod.EventBusSubscriber(modid = "better_ore_exposure_probe", value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExposureClientProbe {
    private static final int READY_LIMIT = 2400;
    private static final int RELOAD_LIMIT = 1200;
    private static final int CAPTURE_LIMIT = 200;
    private static final int SETTLE_TICKS = 120;
    private static final int[] PANEL_X = {0, 14, 28};
    private static final String[] PANEL_NAMES = {"stone", "deepslate", "unearthed_ev"};
    private static final String FAMILY = System.getProperty("better_ore_geology.exposureProbe.family", "copper_bloom");
    private static Stage stage = Stage.WAIT_FOR_FIXTURE;
    private static int ticks;
    private static JsonObject before;
    private static JsonObject after;
    private static JsonObject beforeCapture;
    private static JsonObject afterCapture;
    private static JsonObject activeInspection;
    private static String activePosition;
    private static String fixtureEvBlock;

    private ExposureClientProbe() {}

    private enum Stage {
        WAIT_FOR_FIXTURE, SETTLE_BEFORE, CAPTURE_BEFORE, RELOAD, SETTLE_AFTER,
        CAPTURE_AFTER, DONE, FAILED
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE || stage == Stage.FAILED) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!ModList.get().isLoaded("better_ore_exposure_probe")) return;
        try {
            if (!ExposureField.FAMILIES.contains(FAMILY))
                throw new IllegalArgumentException("unknown exposure probe family: " + FAMILY);
            switch (stage) {
                case WAIT_FOR_FIXTURE -> {
                    if (fixtureReady(minecraft)) {
                        minecraft.options.hideGui = true;
                        move(Stage.SETTLE_BEFORE);
                    }
                    else if (++ticks > READY_LIMIT) throw new IllegalStateException("fixed " + FAMILY + " wall or spectator camera was not ready");
                }
                case SETTLE_BEFORE -> {
                    if (!fixtureReady(minecraft)) throw new IllegalStateException("fixture changed before first capture");
                    if (++ticks >= SETTLE_TICKS) {
                        before = inspectModels(minecraft);
                        move(Stage.CAPTURE_BEFORE);
                        capture(minecraft, "exposure-before.png", Stage.CAPTURE_BEFORE);
                    }
                }
                case CAPTURE_BEFORE, CAPTURE_AFTER -> {
                    if (++ticks > CAPTURE_LIMIT) throw new IllegalStateException("native framebuffer capture timed out: " + stage);
                }
                case RELOAD -> {
                    if (++ticks > RELOAD_LIMIT) throw new IllegalStateException("client resource reload timed out");
                }
                case SETTLE_AFTER -> {
                    if (!fixtureReady(minecraft)) throw new IllegalStateException("fixture changed after resource reload");
                    if (++ticks >= SETTLE_TICKS) {
                        after = inspectModels(minecraft);
                        move(Stage.CAPTURE_AFTER);
                        capture(minecraft, "exposure-after.png", Stage.CAPTURE_AFTER);
                    }
                }
                default -> throw new IllegalStateException("unexpected probe state " + stage);
            }
        } catch (Throwable failure) {
            fail(minecraft, failure);
        }
    }

    private static boolean fixtureReady(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || !minecraft.player.isSpectator()
                || !minecraft.level.dimension().location().equals(new ResourceLocation("minecraft", "overworld"))) {
            return false;
        }
        BlockPos camera = minecraft.player.blockPosition();
        if (camera.getX() < 18 || camera.getX() > 21 || camera.getY() < 101 || camera.getY() > 103
                || camera.getZ() < 31 || camera.getZ() > 35) return false;
        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(minecraft.player.getYRot() - 180)) > 2
                || Math.abs(minecraft.player.getXRot()) > 2) return false;
        String observedEvBlock = null;
        for (int panel = 0; panel < 3; panel++) for (int y = 100; y <= 107; y++)
            for (int x = PANEL_X[panel]; x < PANEL_X[panel] + 12; x++) {
                BlockPos pos = new BlockPos(x, y, 0);
                if (!minecraft.level.hasChunkAt(pos)) return false;
                BlockState state = minecraft.level.getBlockState(pos);
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                if (id == null || !state.hasProperty(ExposureState.STYLE)
                        || !FAMILY.equals(ExposureState.family(state.getBlock()))
                        || state.getValue(ExposureState.STYLE)
                        != ExposureField.style(0, 0x455850L + (y - 100) / 4)) return false;
                if (panel == 0 && !id.toString().equals("better_ore_geology:" + FAMILY)) return false;
                if (panel == 1 && !id.toString().equals("better_ore_geology:deepslate_" + FAMILY)) return false;
                if (panel == 2) {
                    if (id.getNamespace().equals("better_ore_geology")) return false;
                    if (observedEvBlock == null) observedEvBlock = id.toString();
                    else if (!observedEvBlock.equals(id.toString())) return false;
                }
            }
        if (fixtureEvBlock != null && !fixtureEvBlock.equals(observedEvBlock)) return false;
        fixtureEvBlock = observedEvBlock;
        return true;
    }

    private static JsonObject inspectModels(Minecraft minecraft) throws Exception {
        JsonObject result = new JsonObject();
        result.addProperty("family", FAMILY);
        JsonArray panels = new JsonArray();
        result.add("panels", panels);
        activeInspection = result;
        activePosition = null;
        for (int panel = 0; panel < 3; panel++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("host", PANEL_NAMES[panel]);
            JsonArray samples = new JsonArray();
            entry.add("samples", samples);
            panels.add(entry);
            int mixedFaces = 0;
            Set<String> panelSprites = new HashSet<>();
            for (int y = 100; y <= 107; y++) for (int offset = 0; offset < 12; offset++) {
                BlockPos pos = new BlockPos(PANEL_X[panel] + offset, y, 0);
                activePosition = pos.toShortString();
                BlockState state = minecraft.level.getBlockState(pos);
                BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
                JsonObject sample = new JsonObject();
                sample.addProperty("pos", activePosition);
                sample.addProperty("block", ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString());
                sample.addProperty("block_class", state.getBlock().getClass().getName());
                sample.addProperty("model_class", model.getClass().getName());
                JsonArray faces = new JsonArray();
                sample.add("faces", faces);
                samples.add(sample);
                if (!state.hasProperty(ExposureState.STYLE)) throw new IllegalStateException("missing STYLE at " + pos);
                if (!FAMILY.equals(ExposureState.family(state.getBlock())))
                    throw new IllegalStateException("wrong family at " + pos + ": " + state.getBlock());
                sample.addProperty("style", state.getValue(ExposureState.STYLE));
                ModelData data = model.getModelData(minecraft.level, pos, state, ModelData.EMPTY);
                sample.addProperty("model_data_empty", data == null || data == ModelData.EMPTY);
                if (data == null || data == ModelData.EMPTY) {
                    throw new IllegalStateException("position-aware model data absent at " + pos + " (" + model.getClass() + ")");
                }
                for (Direction side : Direction.values()) {
                    List<BakedQuad> quads = model.getQuads(state, side, RandomSource.create(0), data, RenderType.solid());
                    JsonObject face = new JsonObject();
                    face.addProperty("side", side.getName());
                    face.addProperty("quad_count", quads.size());
                    JsonArray names = new JsonArray();
                    rawSpriteNames(quads).stream().sorted().forEach(names::add);
                    face.add("source_sprites", names);
                    faces.add(face);
                    if (quads.isEmpty()) throw new IllegalStateException("no rendered quads at " + pos + " " + side);
                    Set<String> sprites = checkFullFace(quads, side, pos);
                    face.addProperty("geometry_sha256", geometryDigest(quads));
                    panelSprites.addAll(sprites);
                    if (sprites.size() > 1) mixedFaces++;
                    face.addProperty("geometry_valid", true);
                }
            }
            entry.addProperty("mixed_faces", mixedFaces);
            JsonArray panelNames = new JsonArray();
            panelSprites.stream().sorted().forEach(panelNames::add);
            entry.add("source_sprites", panelNames);
            if (mixedFaces == 0 || panelSprites.size() < 2) {
                throw new IllegalStateException("no mixed-source face in fixed " + PANEL_NAMES[panel] + " panel");
            }
        }
        int cached = ExposureBakedModel.cachedQuadCount();
        if (cached < 1 || cached > 32768) throw new IllegalStateException("quad cache bound failed: " + cached);
        result.addProperty("cached_quad_count", cached);
        result.addProperty("ev_block", fixtureEvBlock);
        result.addProperty("blocks_inspected", 3 * 12 * 8);
        activePosition = null;
        return result;
    }

    private static Set<String> rawSpriteNames(List<BakedQuad> quads) {
        Set<String> names = new HashSet<>();
        for (BakedQuad quad : quads) {
            try {
                names.add(quad.getSprite() == null ? "<missing>" : quad.getSprite().contents().name().toString());
            } catch (RuntimeException error) {
                names.add("<unavailable:" + error.getClass().getSimpleName() + ">");
            }
        }
        return names;
    }

    private static Set<String> checkFullFace(List<BakedQuad> quads, Direction side, BlockPos pos) {
        boolean[] covered = new boolean[256];
        Set<String> sprites = new HashSet<>();
        for (BakedQuad quad : quads) {
            if (quad.getDirection() != side || quad.getSprite() == null)
                throw new IllegalStateException("wrong face/sprite at " + pos + " " + side);
            String sprite = quad.getSprite().contents().name().toString();
            if (sprite.equals("minecraft:missingno"))
                throw new IllegalStateException("missing source sprite at " + pos + " " + side);
            sprites.add(sprite);
            int[] vertices = quad.getVertices();
            if (vertices.length % 4 != 0 || vertices.length / 4 < 6)
                throw new IllegalStateException("invalid vertex format at " + pos + " " + side);
            int stride = vertices.length / 4;
            int axisA = side.getAxis() == Direction.Axis.X ? 2 : 0;
            int axisB = side.getAxis() == Direction.Axis.Y ? 2 : 1;
            double minA = Double.POSITIVE_INFINITY, maxA = Double.NEGATIVE_INFINITY;
            double minB = Double.POSITIVE_INFINITY, maxB = Double.NEGATIVE_INFINITY;
            double plane = side.getAxis() == Direction.Axis.X ? Float.intBitsToFloat(vertices[0])
                    : side.getAxis() == Direction.Axis.Y ? Float.intBitsToFloat(vertices[1])
                    : Float.intBitsToFloat(vertices[2]);
            for (int v = 0; v < 4; v++) {
                int at = v * stride;
                for (int component : new int[] {0, 1, 2, 4, 5}) {
                    float value = Float.intBitsToFloat(vertices[at + component]);
                    if (!Float.isFinite(value)) throw new IllegalStateException("nonfinite vertex/UV at " + pos);
                }
                double a = Float.intBitsToFloat(vertices[at + axisA]);
                double b = Float.intBitsToFloat(vertices[at + axisB]);
                double p = Float.intBitsToFloat(vertices[at + side.getAxis().ordinal()]);
                if (Math.abs(p - plane) > 1e-4 || Math.abs(p - (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0)) > 1e-4)
                    throw new IllegalStateException("quad left cube face at " + pos + " " + side);
                minA = Math.min(minA, a); maxA = Math.max(maxA, a);
                minB = Math.min(minB, b); maxB = Math.max(maxB, b);
                float u = Float.intBitsToFloat(vertices[at + 4]);
                float textureV = Float.intBitsToFloat(vertices[at + 5]);
                if (u < Math.min(quad.getSprite().getU0(), quad.getSprite().getU1()) - 1e-3
                        || u > Math.max(quad.getSprite().getU0(), quad.getSprite().getU1()) + 1e-3
                        || textureV < Math.min(quad.getSprite().getV0(), quad.getSprite().getV1()) - 1e-3
                        || textureV > Math.max(quad.getSprite().getV0(), quad.getSprite().getV1()) + 1e-3)
                    throw new IllegalStateException("UV outside source sprite at " + pos + " " + side);
            }
            int x0 = grid(minA, pos), x1 = grid(maxA, pos);
            int y0 = grid(minB, pos), y1 = grid(maxB, pos);
            if (x0 >= x1 || y0 >= y1) throw new IllegalStateException("degenerate quad at " + pos);
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                int index = y * 16 + x;
                if (covered[index]) throw new IllegalStateException("overlapping face quads at " + pos + " " + side);
                covered[index] = true;
            }
        }
        for (boolean cell : covered) if (!cell) throw new IllegalStateException("incomplete face coverage at " + pos + " " + side);
        return sprites;
    }

    private static String geometryDigest(List<BakedQuad> quads) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (BakedQuad quad : quads) {
            byte[] sprite = quad.getSprite().contents().name().toString().getBytes(StandardCharsets.UTF_8);
            digestWord(digest, sprite.length);
            digest.update(sprite);
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            float u0 = quad.getSprite().getU0(), uSpan = quad.getSprite().getU1() - u0;
            float v0 = quad.getSprite().getV0(), vSpan = quad.getSprite().getV1() - v0;
            if (uSpan == 0 || vSpan == 0) throw new IllegalStateException("zero-size atlas sprite in reload oracle");
            for (int vertex = 0; vertex < 4; vertex++) for (int component = 0; component < stride; component++) {
                int word = vertices[vertex * stride + component];
                if (component == 4 || component == 5) {
                    float atlas = Float.intBitsToFloat(word);
                    float local = component == 4 ? (atlas - u0) / uSpan : (atlas - v0) / vSpan;
                    word = Math.round(local * 4096);
                }
                digestWord(digest, word);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void digestWord(MessageDigest digest, int word) {
        digest.update((byte) (word >>> 24));
        digest.update((byte) (word >>> 16));
        digest.update((byte) (word >>> 8));
        digest.update((byte) word);
    }

    private static int grid(double coordinate, BlockPos pos) {
        int cell = (int) Math.round(coordinate * 16);
        if (cell < 0 || cell > 16 || Math.abs(coordinate * 16 - cell) > 1e-3)
            throw new IllegalStateException("quad not aligned to logical texel grid at " + pos + ": " + coordinate);
        return cell;
    }

    private static void capture(Minecraft minecraft, String file, Stage expected) {
        Screenshot.grab(minecraft.gameDirectory, file, minecraft.getMainRenderTarget(), message ->
                minecraft.execute(() -> {
                    if (stage != expected) return;
                    try {
                        JsonObject record = checkScreenshot(minecraft.gameDirectory.toPath().resolve("screenshots").resolve(file));
                        if (expected == Stage.CAPTURE_BEFORE) {
                            beforeCapture = record;
                            move(Stage.RELOAD);
                            minecraft.reloadResourcePacks().whenComplete((ignored, error) ->
                                    minecraft.execute(() -> {
                                        if (stage != Stage.RELOAD) return;
                                        if (error != null) fail(minecraft, error);
                                        else move(Stage.SETTLE_AFTER);
                                    }));
                        } else {
                            afterCapture = record;
                            finish(minecraft);
                        }
                    } catch (Throwable failure) {
                        fail(minecraft, failure);
                    }
                }));
    }

    private static JsonObject checkScreenshot(Path screenshot) throws Exception {
        if (!Files.isRegularFile(screenshot) || Files.size(screenshot) < 10000)
            throw new IllegalStateException("missing or empty native screenshot: " + screenshot);
        BufferedImage image = ImageIO.read(screenshot.toFile());
        if (image == null || image.getWidth() < 640 || image.getHeight() < 360)
            throw new IllegalStateException("invalid native framebuffer: " + screenshot);
        Set<Integer> colors = new HashSet<>();
        for (int y = image.getHeight() / 5; y < image.getHeight() * 4 / 5; y += Math.max(1, image.getHeight() / 48))
            for (int x = image.getWidth() / 5; x < image.getWidth() * 4 / 5; x += Math.max(1, image.getWidth() / 64))
                colors.add(image.getRGB(x, y) & 0xffffff);
        if (colors.size() < 16) throw new IllegalStateException("framebuffer lacks rendered scene detail: " + screenshot);
        JsonObject record = new JsonObject();
        record.addProperty("path", screenshot.toAbsolutePath().toString());
        record.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(screenshot))));
        record.addProperty("width", image.getWidth());
        record.addProperty("height", image.getHeight());
        record.addProperty("sampled_colors", colors.size());
        return record;
    }

    private static void finish(Minecraft minecraft) throws IOException {
        if (before == null || after == null || !before.get("panels").equals(after.get("panels")))
            throw new IllegalStateException("face sources or geometry changed across client resource reload");
        JsonObject report = new JsonObject();
        report.addProperty("schema", "better_ore_exposure_probe.client.v1");
        report.addProperty("status", "pass");
        report.addProperty("family", FAMILY);
        report.addProperty("dimension", minecraft.level.dimension().location().toString());
        report.addProperty("fixture_ev_block", fixtureEvBlock);
        report.addProperty("before_after_faces_equal", true);
        report.add("before", before);
        report.add("after", after);
        report.add("screenshot_before", beforeCapture);
        report.add("screenshot_after", afterCapture);
        writeReport(minecraft, report);
        System.out.println("EXPOSURE_PROBE_CLIENT_PASS " + report);
        move(Stage.DONE);
        minecraft.stop();
    }

    private static void fail(Minecraft minecraft, Throwable failure) {
        if (stage == Stage.FAILED || stage == Stage.DONE) return;
        Stage failedStage = stage;
        move(Stage.FAILED);
        JsonObject report = new JsonObject();
        report.addProperty("schema", "better_ore_exposure_probe.client.v1");
        report.addProperty("status", "fail");
        report.addProperty("family", FAMILY);
        report.addProperty("stage", failedStage.toString());
        report.addProperty("error", failure.toString());
        if (activePosition != null) report.addProperty("active_position", activePosition);
        if (activeInspection != null) report.add("partial_model_inspection", activeInspection);
        report.addProperty("cached_quad_count", ExposureBakedModel.cachedQuadCount());
        if (before != null) report.add("before", before);
        if (after != null) report.add("after", after);
        if (beforeCapture != null) report.add("screenshot_before", beforeCapture);
        if (afterCapture != null) report.add("screenshot_after", afterCapture);
        report.add("ev_model_resources", generatedEvModels(minecraft));
        report.add("ev_texture_and_atlas", generatedEvTextures(minecraft));
        try {
            if (minecraft.getMainRenderTarget() != null && minecraft.getWindow() != null
                    && minecraft.getWindow().getWidth() > 0 && minecraft.getWindow().getHeight() > 0) {
                Path screenshot = minecraft.gameDirectory.toPath().resolve("screenshots/failure.png");
                Files.createDirectories(screenshot.getParent());
                try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                    image.writeToFile(screenshot);
                    JsonObject frame = new JsonObject();
                    frame.addProperty("path", screenshot.toAbsolutePath().toString());
                    frame.addProperty("width", image.getWidth());
                    frame.addProperty("height", image.getHeight());
                    frame.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(Files.readAllBytes(screenshot))));
                    report.add("failure_framebuffer", frame);
                }
            }
        } catch (Throwable captureFailure) {
            report.addProperty("failure_framebuffer_error", captureFailure.toString());
            failure.addSuppressed(captureFailure);
        }
        try { writeReport(minecraft, report); }
        catch (IOException writeFailure) { failure.addSuppressed(writeFailure); }
        failure.printStackTrace();
        minecraft.stop();
    }

    private static JsonArray generatedEvModels(Minecraft minecraft) {
        JsonArray models = new JsonArray();
        String evPath = diagnosticEvPath();
        for (int variant : new int[] {0, 1, 11}) {
            ResourceLocation id = new ResourceLocation("excavated_variants",
                    "models/block/" + evPath + "_" + variant + ".json");
            JsonObject entry = new JsonObject();
            entry.addProperty("resource", id.toString());
            models.add(entry);
            try {
                var resource = minecraft.getResourceManager().getResource(id);
                entry.addProperty("found", resource.isPresent());
                if (resource.isEmpty()) continue;
                try (var stream = resource.orElseThrow().open()) {
                    byte[] bytes = stream.readNBytes(131073);
                    if (bytes.length > 131072) {
                        entry.addProperty("error", "generated model exceeds 128 KiB report limit");
                        continue;
                    }
                    String source = new String(bytes, StandardCharsets.UTF_8);
                    entry.addProperty("byte_count", bytes.length);
                    entry.addProperty("sha256", HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest(bytes)));
                    entry.addProperty("raw_json", source);
                    try {
                        JsonObject parsed = JsonParser.parseString(source).getAsJsonObject();
                        if (parsed.has("loader")) entry.add("loader", parsed.get("loader"));
                        if (parsed.has("family")) entry.add("family", parsed.get("family"));
                        if (parsed.has("textures")) entry.add("textures", parsed.get("textures"));
                    } catch (RuntimeException parseError) {
                        entry.addProperty("parse_error", parseError.toString());
                    }
                }
            } catch (Exception resourceError) {
                entry.addProperty("error", resourceError.toString());
            }
        }
        return models;
    }

    private static JsonObject generatedEvTextures(Minecraft minecraft) {
        JsonObject result = new JsonObject();
        String evPath = diagnosticEvPath();
        JsonArray samples = new JsonArray();
        result.add("samples", samples);
        TextureAtlas atlas = null;
        try {
            atlas = minecraft.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
            result.addProperty("atlas_location", TextureAtlas.LOCATION_BLOCKS.toString());
            result.addProperty("atlas_sprite_count", atlas.getTextureLocations().size());
        } catch (RuntimeException atlasError) {
            result.addProperty("atlas_error", atlasError.toString());
        }
        for (int[] pair : new int[][] {{0, 1}, {1, 1}, {11, 1}}) {
            ResourceLocation spriteId = new ResourceLocation("excavated_variants",
                    "block/" + evPath + "_" + pair[0] + "_" + pair[1]);
            ResourceLocation pngId = new ResourceLocation(spriteId.getNamespace(),
                    "textures/" + spriteId.getPath() + ".png");
            JsonObject sample = new JsonObject();
            sample.addProperty("sprite", spriteId.toString());
            sample.addProperty("png_resource", pngId.toString());
            samples.add(sample);
            try {
                var resource = minecraft.getResourceManager().getResource(pngId);
                sample.addProperty("png_found", resource.isPresent());
                if (resource.isPresent()) try (var stream = resource.orElseThrow().open()) {
                    byte[] bytes = stream.readNBytes(1048577);
                    if (bytes.length > 1048576) {
                        sample.addProperty("png_error", "generated PNG exceeds 1 MiB inspection limit");
                    } else {
                        sample.addProperty("png_byte_count", bytes.length);
                        sample.addProperty("png_sha256", HexFormat.of().formatHex(
                                MessageDigest.getInstance("SHA-256").digest(bytes)));
                        try (NativeImage image = NativeImage.read(bytes)) {
                            sample.addProperty("png_width", image.getWidth());
                            sample.addProperty("png_height", image.getHeight());
                            Set<Integer> pixelValues = new HashSet<>();
                            int opaque = 0;
                            int transparent = 0;
                            int translucent = 0;
                            for (int y = 0; y < image.getHeight(); y++) {
                                for (int x = 0; x < image.getWidth(); x++) {
                                    int pixel = image.getPixelRGBA(x, y);
                                    pixelValues.add(pixel);
                                    int alpha = pixel >>> 24;
                                    if (alpha == 255) opaque++;
                                    else if (alpha == 0) transparent++;
                                    else translucent++;
                                }
                            }
                            sample.addProperty("png_distinct_pixel_values", pixelValues.size());
                            sample.addProperty("png_opaque_pixels", opaque);
                            sample.addProperty("png_transparent_pixels", transparent);
                            sample.addProperty("png_translucent_pixels", translucent);
                        }
                    }
                }
            } catch (Exception textureError) {
                sample.addProperty("png_error", textureError.toString());
            }
            if (atlas != null) try {
                sample.addProperty("atlas_contains_sprite", atlas.getTextureLocations().contains(spriteId));
                sample.addProperty("atlas_lookup", atlas.getSprite(spriteId).contents().name().toString());
            } catch (RuntimeException atlasError) {
                sample.addProperty("atlas_error", atlasError.toString());
            }
        }
        return result;
    }

    private static String diagnosticEvPath() {
        return fixtureEvBlock == null ? "unearthed_beige_limestone_" + FAMILY
                : new ResourceLocation(fixtureEvBlock).getPath();
    }

    private static void writeReport(Minecraft minecraft, JsonObject report) throws IOException {
        Path directory = minecraft.gameDirectory.toPath().resolve("exposure-probe");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("client.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }

    private static void move(Stage next) { stage = next; ticks = 0; }
}
