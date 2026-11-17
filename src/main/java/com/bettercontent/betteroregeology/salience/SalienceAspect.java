package com.bettercontent.betteroregeology.salience;

/** Five cue identities shared by eight distinct geological deposits. Badge slots reuse the existing strip. */
public enum SalienceAspect {
    POWER(0, "✦", 0xFF4055, "impact"),
    MOTION(3, "➜", 0xE0B01F, "mobility"),
    ENDURANCE(4, "∞", 0x52606A, "endurance"),
    RENEWAL(6, "✚", 0x6CCAF0, "renewal"),
    CONTROL(7, "⊕", 0x8E5BB7, "control");

    private final int badgeSlot;
    private final String glyph;
    private final int color;
    private final String soundAsset;

    SalienceAspect(int badgeSlot, String glyph, int color, String soundAsset) {
        this.badgeSlot = badgeSlot;
        this.glyph = glyph;
        this.color = color;
        this.soundAsset = soundAsset;
    }

    public int badgeSlot() { return badgeSlot; }
    public String glyph() { return glyph; }
    public String label() { return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT); }
    public int color() { return color; }
    public String soundPath() { return "aspect." + name().toLowerCase(java.util.Locale.ROOT); }
    public String soundAsset() { return "better_ore_geology:aspect/" + soundAsset; }
}
