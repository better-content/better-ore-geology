package com.bettercontent.betteroregeology.salience;

import net.minecraft.nbt.CompoundTag;

/** Real-time, offline-aware familiarity for one aspect. */
public record FamiliarityState(int level, long lastCueMillis, long decayAnchorMillis) {
    public static final int MAX_LEVEL = 5;
    public static final long DECAY_STEP_MILLIS = 4L * 60L * 60L * 1000L + 48L * 60L * 1000L;
    private static final long[] COOLDOWNS = {
            0L, 30_000L, 120_000L, 480_000L, 1_920_000L, 7_200_000L
    };

    public FamiliarityState {
        level = Math.max(0, Math.min(MAX_LEVEL, level));
    }

    public static FamiliarityState fresh(long now) { return new FamiliarityState(0, 0, now); }

    public FamiliarityState decay(long now) {
        if (now < decayAnchorMillis || now < lastCueMillis) {
            // A wall-clock correction must never create a multi-hour lockout.
            return new FamiliarityState(level, now - COOLDOWNS[level], now);
        }
        long steps = (now - decayAnchorMillis) / DECAY_STEP_MILLIS;
        if (steps <= 0 || level == 0) return this;
        int next = Math.max(0, level - (int) Math.min(Integer.MAX_VALUE, steps));
        return new FamiliarityState(next, lastCueMillis,
                next == 0 ? now : decayAnchorMillis + steps * DECAY_STEP_MILLIS);
    }

    public boolean mayCue(long now) {
        return now >= lastCueMillis && now - lastCueMillis >= COOLDOWNS[level];
    }

    public FamiliarityState afterCue(long now) {
        return new FamiliarityState(Math.min(MAX_LEVEL, level + 1), now, now);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Level", level);
        tag.putLong("LastCue", lastCueMillis);
        tag.putLong("DecayAnchor", decayAnchorMillis);
        return tag;
    }

    public static FamiliarityState load(CompoundTag tag, long now) {
        if (tag.isEmpty()) return fresh(now);
        return new FamiliarityState(tag.getInt("Level"), tag.getLong("LastCue"),
                tag.contains("DecayAnchor") ? tag.getLong("DecayAnchor") : now).decay(now);
    }
}
