package com.bettercontent.betteroregeology.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Placement profile: common home-band bodies or rarer, more expressive echo bodies. */
public enum DepositClass implements StringRepresentable {
    HOME("home"),
    ECHO("echo");

    public static final Codec<DepositClass> CODEC = StringRepresentable.fromEnum(DepositClass::values);

    private final String serializedName;

    DepositClass(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
