package com.bettercontent.betteroregeology.salience;

import com.bettercontent.betteroregeology.api.event.DepositSurveyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;

/** Owns the persisted identity of each sampled deposit-family survey. */
public final class DepositSurveyEpisodes {
    private static final String ROOT = "RealisticOresThreadEpisodes";

    private DepositSurveyEpisodes() {}

    public static void sampleRead(ServerPlayer player, String family) {
        String episodeId = player.getUUID() + ":deposit:" + family + ":" + player.server.getTickCount();
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag episodes = persisted.getCompound(ROOT);
        if (!shouldStart(episodes.getString(family))) return;
        episodes.putString(family, episodeId);
        persisted.put(ROOT, episodes);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        post(player, DepositSurveyEvent.Kind.SAMPLE_READ, family, episodeId);
    }

    public static void depositExtracted(ServerPlayer player, String family) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag episodes = persisted.getCompound(ROOT);
        String episodeId = episodes.getString(family);
        if (!validEpisodeId(episodeId)) return;
        post(player, DepositSurveyEvent.Kind.DEPOSIT_EXTRACTED, family, episodeId);
        episodes.remove(family);
        persisted.put(ROOT, episodes);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static void post(ServerPlayer player, DepositSurveyEvent.Kind kind, String family, String episodeId) {
        MinecraftForge.EVENT_BUS.post(new DepositSurveyEvent(player, kind, family, episodeId));
    }

    static boolean validEpisodeId(String value) {
        if (value == null || value.isBlank() || value.length() > 128) return false;
        return value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e);
    }

    static boolean shouldStart(String existingEpisodeId) {
        return !validEpisodeId(existingEpisodeId);
    }
}
