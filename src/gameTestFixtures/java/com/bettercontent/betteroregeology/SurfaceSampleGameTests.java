package com.bettercontent.betteroregeology;

import com.bettercontent.betteroregeology.api.event.DepositSurveyEvent;
import com.bettercontent.betteroregeology.block.SurfaceSampleBlock;
import com.bettercontent.betteroregeology.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.UUID;

@GameTestHolder("better_ore_geology")
@PrefixGameTestTemplate(false)
public final class SurfaceSampleGameTests {
    public static final class Recorder {
        final ArrayList<DepositSurveyEvent> events = new ArrayList<>();
        @SubscribeEvent public void survey(DepositSurveyEvent event) { events.add(event); }
    }

    @GameTest(templateNamespace = "better_ore_geology", template = "sample", timeoutTicks = 40)
    public static void collectionHonorsPermissionAndDoesNotLoadChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        var definition = ModBlocks.oreDefinitions().get(0);
        var sample = ModBlocks.surfaceSampleEntries().stream()
                .filter(e -> e.getKey().equals(definition.surfaceSampleBlockId())).findFirst().orElseThrow().getValue().get();
        var expectedItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("better_ore_geology", definition.smallOreChunkItemId()));
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "sample-reader"));
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(pos.getX(), pos.getY(), pos.getZ());
        var recorder = new Recorder();
        MinecraftForge.EVENT_BUS.register(recorder);
        try {
            for (boolean wet : new boolean[]{false, true}) {
                var state = sample.defaultBlockState().setValue(SurfaceSampleBlock.WATERLOGGED, wet);
                level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(pos, state);
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                player.getAbilities().mayBuild = false;
                helper.assertTrue(sample.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS,
                        "without build permission collection must pass");
                helper.assertTrue(level.getBlockState(pos).equals(state) && recorder.events.isEmpty(), "denied use must preserve sample and emit no event");
                player.getAbilities().mayBuild = true;
                var beforeChunks = level.getChunkSource().getLoadedChunksCount();
                helper.assertTrue(sample.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit) == InteractionResult.CONSUME, "authorized collection consumes interaction");
                helper.assertTrue(level.getBlockState(pos).is(wet ? Blocks.WATER : Blocks.AIR), "sample must become water or air");
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
                helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(expectedItem) && drops.get(0).getItem().getCount() == 1,
                        "one matching small ore chunk must pop into the world");
                drops.forEach(ItemEntity::discard);
                helper.assertTrue(player.getInventory().isEmpty(), "collection does not silently fill inventory");
                helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == beforeChunks, "collection must not load chunks to search for a deposit");
                helper.assertTrue(recorder.events.size() == 1 && recorder.events.get(0).player() == player
                        && recorder.events.get(0).kind() == DepositSurveyEvent.Kind.SAMPLE_READ
                        && recorder.events.get(0).family().equals(definition.id()), "collection only reports the sampled family, not deposit extraction");
                recorder.events.clear();
                player.getPersistentData().remove(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            }
            helper.succeed();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(recorder);
        }
    }
}
