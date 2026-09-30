package com.bettercontent.betteroregeology;

import com.bettercontent.betteroregeology.registry.ModBlocks;
import com.bettercontent.betteroregeology.client.ClientSetup;
import com.bettercontent.betteroregeology.registry.ModFeatures;
import com.bettercontent.betteroregeology.registry.ModItems;
import com.bettercontent.betteroregeology.registry.ModRecipeSerializers;
import com.bettercontent.betteroregeology.registry.ModFluids;
import com.bettercontent.betteroregeology.salience.ModSounds;
import com.mojang.logging.LogUtils;
import com.bettercontent.betteroregeology.worldgen.DisabledFeatureBiomeModifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RealisticOresMod.MOD_ID)
public final class RealisticOresMod {
    public static final String MOD_ID = "better_ore_geology";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealisticOresMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModFluids.bootstrap();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModFluids.register(modBus);
        ModFeatures.register(modBus);
        ModRecipeSerializers.register(modBus);
        ModSounds.register(modBus);
        DisabledFeatureBiomeModifier.register(modBus);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> modBus.addListener(ClientSetup::onClientSetup));
        modBus.addListener(this::addCreativeTabContents);
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            ModItems.getAllBlockItems().forEach(event::accept);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            ModItems.getAllOreChunkItems().forEach(event::accept);
            ModItems.getAllCrushedOreItems().forEach(event::accept);
            ModItems.getAllProcessingItems().forEach(event::accept);
            event.accept(ModFluids.TITANIUM.bucket().get());
            event.accept(ModFluids.THORIUM.bucket().get());
        }
    }

}
