package com.bettercontent.betteroregeology.salience;

import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Optional;

/** Eight deposit families retain distinct extraction/material episodes; each cues one of five aspects. */
public enum DepositIdentity {
    HOTSTONE("hotstone", SalienceAspect.POWER, "IMPACT"),
    COPPER_BLOOM("copper_bloom", SalienceAspect.MOTION, "TEMPO"),
    TIN_QUARTZ("tin_quartz", SalienceAspect.POWER, "WORK"),
    BRASSROOT("brassroot", SalienceAspect.MOTION, "MOBILITY"),
    COAL_MEASURES("coal_measures", SalienceAspect.ENDURANCE, "ENDURANCE"),
    IRONSTONE("ironstone", SalienceAspect.ENDURANCE, "ROBUSTNESS"),
    EVAPORITE_BEDS("evaporite_beds", SalienceAspect.RENEWAL, "RENEWAL"),
    BLACK_SHALE("black_shale", SalienceAspect.CONTROL, "CONTROL");

    private final String family;
    private final SalienceAspect aspect;
    private final String familiarityKey;

    DepositIdentity(String family, SalienceAspect aspect, String familiarityKey) {
        this.family = family;
        this.aspect = aspect;
        this.familiarityKey = familiarityKey;
    }

    public String family() { return family; }
    public SalienceAspect aspect() { return aspect; }
    /** Keep the existing per-deposit cooldown key in player data; this is not an aspect ID. */
    public String familiarityKey() { return familiarityKey; }

    public static Optional<DepositIdentity> fromBlockId(ResourceLocation id) {
        String path = id.getPath();
        return Arrays.stream(values()).filter(identity -> {
            if (id.getNamespace().equals("better_ore_geology")) {
                return path.equals(identity.family) || path.equals("deepslate_" + identity.family);
            }
            return id.getNamespace().equals("excavated_variants") && path.endsWith("_" + identity.family);
        }).findFirst();
    }
}
