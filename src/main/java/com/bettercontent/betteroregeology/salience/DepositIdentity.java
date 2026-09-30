package com.bettercontent.betteroregeology.salience;

import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The exact one-deposit-to-one-aspect identity contract. */
public enum DepositIdentity {
    IMPACT("hotstone"),
    TEMPO("copper_bloom"),
    WORK("tin_quartz"),
    MOBILITY("brassroot"),
    ENDURANCE("coal_measures"),
    ROBUSTNESS("ironstone"),
    RENEWAL("evaporite_beds"),
    CONTROL("black_shale");

    private final String family;

    DepositIdentity(String family) { this.family = family; }

    public String family() { return family; }
    public String soundPath() { return "aspect." + name().toLowerCase(Locale.ROOT); }

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
