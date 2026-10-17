package com.bettercontent.betteroregeology.compat.exposure;

import com.bettercontent.betteroregeology.visual.ExposureState;
import com.mojang.datafixers.util.Pair;
import dev.lukebemish.dynamicassetgenerator.api.client.generators.TexSource;
import dev.lukebemish.dynamicassetgenerator.api.client.generators.texsources.AnimationFrameCapture;
import dev.lukebemish.dynamicassetgenerator.api.client.generators.texsources.AnimationSplittingSource;
import dev.lukebemish.dynamicassetgenerator.api.client.generators.texsources.TextureReaderSource;
import dev.lukebemish.excavatedvariants.api.ExcavatedVariantsListener;
import dev.lukebemish.excavatedvariants.api.client.Face;
import dev.lukebemish.excavatedvariants.api.client.ResourceProvider;
import dev.lukebemish.excavatedvariants.api.client.TexFaceProvider;
import dev.lukebemish.excavatedvariants.api.client.TextureProducer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

/** Loaded by EV's client listener discovery only when that optional mod is installed. */
@ExcavatedVariantsListener
public final class ExcavatedExposureProvider implements ResourceProvider {
    private static final List<String> ROLES = List.of("quiet", "fragment", "richer", "original");

    @Override
    public int priority() {
        // EV sorts descending and uses Map.put: run after its default provider (-10).
        return -20;
    }

    @Override
    public void provideOreTextures(List<ResourceLocation> ores,
            BiConsumer<ResourceLocation, List<TexFaceProvider>> consumer) {
        for (ResourceLocation ore : ores) {
            Block block = ForgeRegistries.BLOCKS.getValue(ore);
            String family = block == null ? null : ExposureState.family(block);
            if (family == null) continue;
            List<TexFaceProvider> faces = new ArrayList<>(12);
            for (String role : ROLES) {
                for (int variant = 0; variant < 3; variant++) {
                    ResourceLocation overlay = new ResourceLocation("better_ore_geology",
                            "block/exposure/overlays/" + family + "/" + role + "_" + variant);
                    faces.add(new ExposureFaces(family, overlay));
                }
            }
            consumer.accept(ore, List.copyOf(faces));
        }
    }

    /** Also marks precisely those generated models that need the exposure loader. */
    public record ExposureFaces(String family, ResourceLocation overlay) implements TexFaceProvider {
        @Override
        public TextureProducer get(Face face) {
            return (newHost, oldHost) -> {
                Map<String, AnimationSplittingSource.TimeAwareSource> frames = new LinkedHashMap<>();
                TexSource host = newHost.apply(source -> {
                    String name = "host" + frames.size();
                    frames.put(name, new AnimationSplittingSource.TimeAwareSource(source.cached(), 1));
                    return new AnimationFrameCapture.Builder().setCapture(name).build();
                });
                TexSource composite = new ExposureOverlaySource(List.of(host,
                        new TextureReaderSource.Builder().setPath(overlay).build()));
                return Pair.of(new AnimationSplittingSource.Builder().setSources(frames)
                        .setGenerator(composite).build(), List.of(overlay));
            };
        }
    }
}
