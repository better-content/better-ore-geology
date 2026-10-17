package com.bettercontent.betteroregeology.visual;

import java.util.List;

/** Pure, world-continuous visual field. It never changes ore placement or resource grade. */
public final class ExposureField {
    public static final List<String> FAMILIES = List.of("coal_measures", "ironstone", "copper_bloom",
            "tin_quartz", "brassroot", "evaporite_beds", "hotstone", "black_shale");
    public static final List<String> ROLES = List.of("quiet", "fragment", "richer", "original");
    private ExposureField() {}

    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    /** Eight structural directions times eight occurrence phases: only 64 persistent states. */
    public static int style(int structuralStrike, long occurrenceSalt) {
        int direction = Math.floorMod((structuralStrike + 2) / 4, 8);
        return direction * 8 + (int) (mix(occurrenceSalt) & 7);
    }

    public static int placedStyle(String family, int x, int y, int z) {
        return (int) (mix(((long) x * 73428767L) ^ ((long) y * 912931L)
                ^ ((long) z * 438289L) ^ family.hashCode()) & 63);
    }

    /** Spatially coherent masks, evaluated at face texel centers in world coordinates. */
    public static int role(String family, int style, double x, double y, double z) {
        double angle = (style >>> 3) * Math.PI / 4.0;
        double u = Math.cos(angle) * x + Math.sin(angle) * z;
        double v = -Math.sin(angle) * x + Math.cos(angle) * z;
        double phase = (style & 7) * 1.731;
        double pocket = 0.5 + 0.5 * Math.sin(u * 0.21 + phase + 0.7 * Math.sin(v * 0.17));
        double strength = switch (family) {
            case "coal_measures" -> 0.72 * (1 - Math.abs(Math.sin(y * 1.17 + 0.18 * Math.sin(u * 0.22) + phase))) + 0.28 * pocket;
            case "ironstone" -> {
                double a = Math.sin(u * 0.29 + phase), b = Math.sin(v * 0.38 - phase);
                yield 0.78 * Math.max(0, 1 - Math.sqrt(a * a + b * b)) + 0.22 * (0.5 + 0.5 * Math.cos(y * 0.8));
            }
            case "copper_bloom" -> 0.82 * (1 - Math.min(Math.abs(Math.sin(u * 0.53 + v * 0.13 + phase)),
                    Math.abs(Math.sin(v * 0.41 - y * 0.25 - phase)))) + 0.18 * pocket;
            case "tin_quartz" -> 0.82 * (1 - Math.abs(Math.sin(v * 0.77 - y * 0.12 + 0.22 * Math.sin(u * 0.3) + phase))) + 0.18 * pocket;
            case "brassroot" -> 0.8 * (1 - Math.min(Math.abs(Math.sin(v * 0.63 + 0.45 * Math.sin(y * 0.47) + phase)),
                    Math.abs(Math.sin(y * 0.93 + u * 0.18 - phase)))) + 0.2 * pocket;
            case "evaporite_beds" -> 0.77 * (1 - Math.abs(Math.sin(y * 1.45 + 0.16 * Math.sin(v * 0.2) + phase))) + 0.23 * pocket;
            case "hotstone" -> {
                double a = Math.sin(u * 0.47 + phase), b = Math.sin(v * 0.51 - phase);
                double matrix = 1 - Math.min(1, Math.sqrt(a * a + b * b));
                yield 0.75 * matrix + 0.25 * (0.5 + 0.5 * Math.sin(y * 0.38 + phase));
            }
            case "black_shale" -> 0.78 * (1 - Math.abs(Math.sin(y * 1.87 + v * 0.27 + 0.3 * Math.sin(u * 0.31) + phase))) + 0.22 * pocket;
            default -> throw new IllegalArgumentException("Unknown deposit family " + family);
        };
        return strength >= 0.94 ? 3 : strength >= 0.79 ? 2 : strength >= 0.60 ? 1 : 0;
    }

    /** Independent pattern identities per role and geological patch, not per-block noise. */
    public static int variant(String family, int style, int role, double x, double y, double z) {
        long px = (long) Math.floor(x / 4), py = (long) Math.floor(y / 4), pz = (long) Math.floor(z / 4);
        long seed = px * 73428767L ^ py * 912931L ^ pz * 438289L ^ family.hashCode()
                ^ style * 0x9e3779b97f4a7c15L ^ role * 0xd1b54a32d192ed03L;
        return (int) Math.floorMod(mix(seed), 3L);
    }
}
