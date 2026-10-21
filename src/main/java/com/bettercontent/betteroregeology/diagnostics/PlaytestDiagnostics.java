package com.bettercontent.betteroregeology.diagnostics;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Native lifecycle adapter only; all rate/focus/encoding policy is the reviewed PlaytestLog. */
@Mod.EventBusSubscriber(modid = "better_ore_geology")
public final class PlaytestDiagnostics {
    public static final PlaytestLog LOG = new PlaytestLog("better_ore_geology");
    private PlaytestDiagnostics() {}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        if (LOG.enabled("summary")) LOG.summary("server");
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        
        LOG.reset("server");
    }
    @Mod.EventBusSubscriber(modid = "better_ore_geology", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Startup {
        private Startup() {}
        @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
            var mods = ModList.get();
            String version = mods.getModContainerById("better_ore_geology").map(c -> c.getModInfo().getVersion().toString()).orElse("UNKNOWN");
            var own = mods.getModFileById("better_ore_geology");
            String source = own == null ? "UNKNOWN" : LOG.sourceIdentity(own.getFile().findResource("META-INF", "better-content-source.properties"));
            LOG.status("common", version, source, () -> PlaytestLog.fields("enabled", true, "excavated_variants_loaded", mods.isLoaded("excavated_variants")));
        }
    }
}
