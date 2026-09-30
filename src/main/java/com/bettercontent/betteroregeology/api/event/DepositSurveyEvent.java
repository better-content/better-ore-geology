package com.bettercontent.betteroregeology.api.event;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/** A sample-to-extraction episode published by the ore provider for any interested consumer. */
public final class DepositSurveyEvent extends Event {
    public enum Kind { SAMPLE_READ, DEPOSIT_EXTRACTED }

    private final ServerPlayer player;
    private final Kind kind;
    private final String family;
    private final String episodeId;

    public DepositSurveyEvent(ServerPlayer player, Kind kind, String family, String episodeId) {
        this.player = Objects.requireNonNull(player, "player");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.family = Objects.requireNonNull(family, "family");
        this.episodeId = Objects.requireNonNull(episodeId, "episodeId");
    }

    public ServerPlayer player() { return player; }
    public Kind kind() { return kind; }
    public String family() { return family; }
    public String episodeId() { return episodeId; }
}
