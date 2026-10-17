package com.bettercontent.betteroregeology.exposureharness;

import com.bettercontent.betteroregeology.block.GeologicalOreBlock;
import com.bettercontent.betteroregeology.visual.ExposureField;
import com.bettercontent.betteroregeology.visual.ExposureState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.lukebemish.excavatedvariants.impl.ModifiedOreBlock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/** Nonshipping companion mod: native server evidence only, never part of the runtime artifact. */
@Mod(ExposureServerProbe.MOD_ID)
public final class ExposureServerProbe {
    public static final String MOD_ID = "better_ore_exposure_probe";
    private static final String SCHEMA = "better_ore_exposure_probe.server.v1";
    private static final String WALL_SCHEMA = "better_ore_exposure_probe.wall.v1";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path REPORT = Path.of("exposure-probe", "server.json");
    private static final BlockPos MINE = new BlockPos(0, 101, -4);
    private final JsonObject report = new JsonObject();
    private final List<Block> miningBlocks = new ArrayList<>();
    private final List<Panel> panels = new ArrayList<>();
    private final Set<java.util.UUID> joined = new HashSet<>();
    private MinecraftServer server;
    private String sceneFamily;
    private boolean prepared;
    private boolean failed;

    public ExposureServerProbe() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void serverStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("better_ore_geology.exposureProbe")) return;
        server = event.getServer();
        try {
            report.addProperty("schema", SCHEMA);
            report.addProperty("ready", false);
            report.addProperty("started_utc", Instant.now().toString());
            report.addProperty("dimension", "minecraft:overworld");
            report.addProperty("phase", "registry");
            sceneFamily = System.getProperty("better_ore_geology.exposureProbe.family", "copper_bloom");
            report.addProperty("family", sceneFamily);
            require(ExposureField.FAMILIES.contains(sceneFamily), "Unknown exposure probe family: " + sceneFamily);
            registryAssertions(server.overworld());
            prepareWall(server.overworld());
            report.addProperty("phase", "awaiting_player");
            prepared = true;
            writeReport();
            LOGGER.info("EXPOSURE_PROBE_SERVER_READY registry/conversion/placement/wall checks passed; awaiting player");
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void registryAssertions(ServerLevel level) {
        JsonArray own = new JsonArray();
        JsonArray unrelated = new JsonArray();
        Set<String> families = new HashSet<>();
        Map<String, Block> representative = new TreeMap<>();
        List<Block> all = ForgeRegistries.BLOCKS.getValues().stream()
                .sorted(Comparator.comparing(ExposureServerProbe::id)).toList();
        for (Block block : all) {
            if (block instanceof GeologicalOreBlock nativeOre) {
                require(nativeOre.defaultBlockState().hasProperty(ExposureState.STYLE), "Native STYLE missing: " + id(block));
                miningBlocks.add(block);
            }
            if (!(block instanceof ModifiedOreBlock hosted)) continue;
            Block original = ForgeRegistries.BLOCKS.getValue(hosted.ore.blockId.get(0));
            String family = original instanceof GeologicalOreBlock ore ? ore.family() : null;
            if (family == null) {
                require(!block.defaultBlockState().hasProperty(ExposureState.STYLE),
                        "Unrelated EV ore acquired STYLE: " + id(block));
                unrelated.add(id(block));
                continue;
            }
            families.add(family);
            representative.putIfAbsent(family, block);
            BlockState def = block.defaultBlockState();
            require(def.hasProperty(ExposureState.STYLE), "Hosted STYLE missing: " + id(block));
            require(ExposureState.STYLE.getPossibleValues().size() == 64, "STYLE must have64values");
            int conversions = 0;
            for (int style = 0; style < 64; style++) {
                BlockState converted = block.withPropertiesOf(original.defaultBlockState().setValue(ExposureState.STYLE, style));
                require(converted.is(block) && converted.getValue(ExposureState.STYLE) == style,
                        "withPropertiesOf lost STYLE " + id(block) + ":" + style);
                BlockState roundTrip = original.withPropertiesOf(converted);
                require(roundTrip.getValue(ExposureState.STYLE) == style,
                        "Hosted-to-native conversion lost STYLE " + id(block) + ":" + style);
                conversions++;
            }
            JsonArray placements = new JsonArray();
            for (BlockPos pos : List.of(new BlockPos(1, 101, -4), new BlockPos(-17, 101, -4),
                    new BlockPos(33, 102, -4))) {
                var context = new DirectionalPlaceContext(level, pos, Direction.DOWN,
                        new ItemStack(block), Direction.NORTH);
                BlockState placed = block.getStateForPlacement(context);
                int expected = ExposureState.placedStyle(family, context.getClickedPos());
                require(placed != null && placed.hasProperty(ExposureState.STYLE)
                                && placed.getValue(ExposureState.STYLE) == expected,
                        "Placement STYLE mismatch: " + id(block));
                JsonObject placement = coords(pos);
                placement.addProperty("style", expected);
                placements.add(placement);
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id(block));
            entry.addProperty("family", family);
            entry.addProperty("native_id", id(original));
            entry.addProperty("host_id", hosted.stone.blockId.toString());
            entry.addProperty("style_values", 64);
            entry.addProperty("conversion_assertions_passed", conversions);
            entry.addProperty("round_trip_assertions_passed", conversions);
            entry.add("placement_assertions_passed", placements);
            own.add(entry);
        }
        require(families.equals(new HashSet<>(ExposureField.FAMILIES)), "Missing hosted families: " + families);
        require(!unrelated.isEmpty(), "No unrelated EV negative controls registered");
        require(miningBlocks.size() == 16, "Expected16native ore types; observed " + miningBlocks.size());
        miningBlocks.addAll(representative.values());
        report.add("hosted_assertions", own);
        report.add("unrelated_without_style", unrelated);
        report.addProperty("family_count", families.size());
        report.addProperty("native_ore_types", 16);
        Block nativeSceneOre = requiredBlock("better_ore_geology:" + sceneFamily);
        Block deepslate = requiredBlock("better_ore_geology:deepslate_" + sceneFamily);
        Block unearthed = all.stream().filter(block -> block instanceof ModifiedOreBlock)
                .filter(block -> {
                    ModifiedOreBlock ore = (ModifiedOreBlock) block;
                    Block nativeBlock = ForgeRegistries.BLOCKS.getValue(ore.ore.blockId.get(0));
                    return nativeBlock instanceof GeologicalOreBlock nativeOre
                            && nativeOre.family().equals(sceneFamily)
                            && ore.stone.blockId.getNamespace().equals("unearthed");
                }).findFirst().orElseThrow(() -> new IllegalStateException("No Unearthed-hosted " + sceneFamily + " registered"));
        panels.add(new Panel(0, nativeSceneOre, "minecraft:stone"));
        panels.add(new Panel(14, deepslate, "minecraft:deepslate"));
        panels.add(new Panel(28, unearthed, ((ModifiedOreBlock) unearthed).stone.blockId.toString()));
        if (!miningBlocks.contains(unearthed)) miningBlocks.add(unearthed);
    }

    private void prepareWall(ServerLevel level) throws IOException {
        Path marker = server.getWorldPath(LevelResource.ROOT).resolve("exposure-probe-wall.json");
        JsonObject expected = wallManifest();
        boolean restart = Files.exists(marker);
        report.addProperty("restart", restart);
        report.addProperty("wall_marker", marker.toAbsolutePath().toString());
        if (restart) {
            JsonObject persisted = JsonParser.parseString(Files.readString(marker)).getAsJsonObject();
            require(expected.equals(persisted), "Persisted wall manifest differs from recomputed expected schema/states");
            verifyWall(level);
            report.addProperty("restart_states_verified_before_writes", 288);
            LOGGER.info("EXPOSURE_PROBE_RESTART_PASS all288native persisted block states match before any writes");
        } else {
            // This mod is only launched against the coordinator's fresh disposable fixture.
            for (BlockPos pos : BlockPos.betweenClosed(-4, 100, -5, 44, 112, 36)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            for (BlockPos pos : BlockPos.betweenClosed(-4, 99, -5, 44, 99, 36)) {
                level.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            }
            for (Panel panel : panels) for (int row = 0; row < 8; row++) for (int col = 0; col < 12; col++) {
                level.setBlock(new BlockPos(panel.x + col, 100 + row, 0),
                        panel.block.defaultBlockState().setValue(ExposureState.STYLE, style(row)), 3);
            }
            verifyWall(level);
            require(server.saveEverything(false, true, true), "Native initial world save failed");
            atomicJson(marker, expected);
            report.addProperty("initial_states_written_and_verified", 288);
        }
        level.setDayTime(6000);
        level.setWeatherParameters(0, 240000, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        report.addProperty("wall_expected_schema", WALL_SCHEMA);
        report.add("expected_states", expected.get("expected_states").deepCopy());
        report.add("panels", expected.get("panels").deepCopy());
        JsonObject camera = new JsonObject();
        camera.addProperty("x", 19.5);
        camera.addProperty("y", 102);
        camera.addProperty("z", 33);
        camera.addProperty("yaw", 180);
        camera.addProperty("pitch", 0);
        report.add("camera", camera);
    }

    private JsonObject wallManifest() {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("schema", WALL_SCHEMA);
        manifest.addProperty("dimension", "minecraft:overworld");
        JsonArray descriptions = new JsonArray();
        JsonArray states = new JsonArray();
        for (Panel panel : panels) {
            JsonObject description = coords(new BlockPos(panel.x, 100, 0));
            description.addProperty("id", id(panel.block));
            description.addProperty("host_id", panel.host);
            description.addProperty("width", 12);
            description.addProperty("height", 8);
            JsonArray styles = new JsonArray();
            styles.add(style(0));
            styles.add(style(4));
            description.add("styles", styles);
            descriptions.add(description);
            for (int row = 0; row < 8; row++) for (int col = 0; col < 12; col++) {
                JsonObject state = coords(new BlockPos(panel.x + col, 100 + row, 0));
                state.addProperty("block", id(panel.block));
                state.addProperty("geological_style", style(row));
                states.add(state);
            }
        }
        manifest.add("panels", descriptions);
        manifest.add("expected_states", states);
        return manifest;
    }

    private void verifyWall(ServerLevel level) {
        for (Panel panel : panels) for (int row = 0; row < 8; row++) for (int col = 0; col < 12; col++) {
            BlockPos pos = new BlockPos(panel.x + col, 100 + row, 0);
            BlockState state = level.getBlockState(pos);
            require(state.is(panel.block) && state.hasProperty(ExposureState.STYLE)
                            && state.getValue(ExposureState.STYLE) == style(row),
                    "Persisted wall state mismatch at " + pos + " actual=" + state);
        }
    }

    @SubscribeEvent
    public void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!prepared || failed || !(event.getEntity() instanceof ServerPlayer player)
                || !joined.add(player.getUUID())) return;
        // The login event runs on the server thread; enqueue after native join completes.
        server.execute(() -> {
            try {
                miningAssertions(player);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(server.overworld(), 19.5, 102, 33, 180, 0);
                verifyWall(server.overworld());
                require(server.saveEverything(false, true, true), "Native post-probe save failed");
                report.addProperty("ready", true);
                report.addProperty("phase", "capture_ready");
                report.addProperty("player_uuid", player.getStringUUID());
                report.addProperty("completed_utc", Instant.now().toString());
                writeReport();
                player.sendSystemMessage(Component.literal("EXPOSURE_PROBE_CAPTURE_READY"));
                LOGGER.info("EXPOSURE_PROBE_CAPTURE_READY native mining, conversion, placement and persistence checks passed");
            } catch (Throwable error) {
                fail(error);
            }
        });
    }

    private void miningAssertions(ServerPlayer player) {
        ServerLevel level = server.overworld();
        require(level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS), "Native block drops disabled");
        ItemStack originalHand = player.getMainHandItem().copy();
        JsonArray results = new JsonArray();
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, 0.5, 101, -2.5, 180, 0);
        try {
            for (Block block : miningBlocks) for (boolean silk : List.of(false, true)) {
                Map<String, Integer> baseline = null;
                for (int style : new int[] {0, 63}) {
                    AABB bounds = new AABB(MINE).inflate(1.5);
                    level.getEntitiesOfClass(ItemEntity.class, bounds).forEach(ItemEntity::discard);
                    ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
                    if (silk) tool.enchant(Enchantments.SILK_TOUCH, 1);
                    player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                    BlockState state = block.defaultBlockState().setValue(ExposureState.STYLE, style);
                    level.setBlock(MINE, state, 3);
                    require(level.getBlockState(MINE).equals(state), "Mining fixture state was not installed");
                    require(player.hasCorrectToolForDrops(state), "Incorrect native mining tool for " + id(block));
                    Map<String, Integer> expected = stacks(Block.getDrops(state, level, MINE, null, player, tool));
                    require(!expected.isEmpty(), "Expected native loot is empty: " + id(block));
                    require(player.gameMode.destroyBlock(MINE), "Native destroyBlock rejected " + id(block));
                    require(level.getBlockState(MINE).isAir(), "Native mining did not remove " + id(block));
                    List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, bounds);
                    Map<String, Integer> observed = stacks(entities.stream().map(ItemEntity::getItem).toList());
                    require(expected.equals(observed), "Actual native loot mismatch " + id(block)
                            + " expected=" + expected + " observed=" + observed);
                    if (baseline == null) baseline = observed;
                    else require(baseline.equals(observed), "STYLE changed native drops for " + id(block));
                    JsonObject result = new JsonObject();
                    result.addProperty("block", id(block));
                    result.addProperty("style", style);
                    result.addProperty("silk_touch", silk);
                    result.addProperty("native_destroy_block", true);
                    result.add("expected_drops", GSON.toJsonTree(expected));
                    result.add("observed_drops", GSON.toJsonTree(observed));
                    results.add(result);
                    entities.forEach(ItemEntity::discard);
                }
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, originalHand);
            player.setGameMode(GameType.SPECTATOR);
            level.setBlock(MINE, Blocks.AIR.defaultBlockState(), 3);
        }
        report.add("mining_assertions", results);
        report.addProperty("mining_assertion_count", results.size());
    }

    private static Map<String, Integer> stacks(List<ItemStack> items) {
        Map<String, Integer> result = new TreeMap<>();
        for (ItemStack item : items) {
            if (!item.isEmpty()) result.merge(ForgeRegistries.ITEMS.getKey(item.getItem()).toString(),
                    item.getCount(), Integer::sum);
        }
        return result;
    }

    private void fail(Throwable error) {
        failed = true;
        report.addProperty("ready", false);
        report.addProperty("phase", "failed");
        report.addProperty("failure", error.toString());
        LOGGER.error("EXPOSURE_PROBE_ERROR native assertion failed", error);
        try {
            writeReport();
        } catch (IOException writeError) {
            LOGGER.error("EXPOSURE_PROBE_ERROR cannot write failure report", writeError);
        }
        if (server != null) server.halt(false);
        throw new IllegalStateException("EXPOSURE_PROBE_ERROR " + error.getMessage(), error);
    }

    private void writeReport() throws IOException {
        atomicJson(REPORT, report);
    }

    private static void atomicJson(Path path, JsonObject value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path staging = path.resolveSibling(path.getFileName() + ".new");
        Files.writeString(staging, GSON.toJson(value));
        Files.move(staging, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static int style(int row) {
        return ExposureField.style(0, 0x455850L + row / 4);
    }

    private static JsonObject coords(BlockPos pos) {
        JsonObject result = new JsonObject();
        result.addProperty("x", pos.getX());
        result.addProperty("y", pos.getY());
        result.addProperty("z", pos.getZ());
        return result;
    }

    private static Block requiredBlock(String key) {
        Block block = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(key));
        require(block != null && block != Blocks.AIR, "Missing required block " + key);
        return block;
    }

    private static String id(Block block) {
        return ForgeRegistries.BLOCKS.getKey(block).toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Panel(int x, Block block, String host) {}
}
