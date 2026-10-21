package com.bettercontent.betteroregeology.salience;

import com.bettercontent.betteroregeology.RealisticOresMod;
import com.bettercontent.betteroregeology.diagnostics.PlaytestDiagnostics;
import com.bettercontent.betteroregeology.diagnostics.PlaytestLog;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = RealisticOresMod.MOD_ID)
public final class OreSalienceEvents {
    static final String ROOT_KEY = "RealisticOresSalienceFamiliarity";

    private OreSalienceEvents() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock());
        if (blockId == null) return;
        DepositIdentity.fromBlockId(blockId).ifPresent(identity -> {
            DepositSurveyEpisodes.depositExtracted(player, identity.family());
            cue(player, identity, event.getPos().getX() + .5, event.getPos().getY() + .5, event.getPos().getZ() + .5);
        });
    }

    private static void cue(ServerPlayer player, DepositIdentity identity, double x, double y, double z) {
        long now = System.currentTimeMillis();
        CompoundTag persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag root = persistent.getCompound(ROOT_KEY);
        String key = identity.name();
        FamiliarityState state = FamiliarityState.load(root.getCompound(key), now);
        if (!state.mayCue(now)) {
            root.put(key, state.save());
            persistent.put(ROOT_KEY, root);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
            return;
        }
        root.put(key, state.afterCue(now).save());
        persistent.put(ROOT_KEY, root);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
        player.connection.send(new ClientboundSoundPacket(Holder.direct(ModSounds.get(identity)), SoundSource.BLOCKS,
                x, y, z, .18f, 1.0f, player.getRandom().nextLong()));
        if (PlaytestDiagnostics.LOG.enabled("salience")) PlaytestDiagnostics.LOG.debug("salience", "geology.cue",
            PlaytestLog.Context.server(player.serverLevel().dimension().location().toString(), player.serverLevel().getGameTime()),
            "dispatched", "miner_packet_sent", () -> PlaytestLog.fields("family", identity.family(), "cue", identity.name(),
                "client_playback", "not_observed"));
    }
}
