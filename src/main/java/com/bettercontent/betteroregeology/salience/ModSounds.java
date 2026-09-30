package com.bettercontent.betteroregeology.salience;

import com.bettercontent.betteroregeology.RealisticOresMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RealisticOresMod.MOD_ID);
    private static final Map<DepositIdentity, RegistryObject<SoundEvent>> ASPECTS =
            new EnumMap<>(DepositIdentity.class);

    static {
        for (DepositIdentity identity : DepositIdentity.values()) {
            String path = identity.soundPath();
            ASPECTS.put(identity, SOUNDS.register(path,
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RealisticOresMod.MOD_ID, path))));
        }
    }

    private ModSounds() {}
    public static void register(IEventBus bus) { SOUNDS.register(bus); }
    public static SoundEvent get(DepositIdentity identity) { return ASPECTS.get(identity).get(); }
}
