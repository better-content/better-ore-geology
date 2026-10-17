package com.bettercontent.betteroregeology.compat.exposure;

import com.mojang.serialization.Codec;
import dev.lukebemish.dynamicassetgenerator.api.client.generators.TexSource;
import dev.lukebemish.dynamicassetgenerator.api.client.generators.texsources.AbstractManyOperationSource;
import dev.lukebemish.dynamicassetgenerator.api.colors.operations.PointwiseOperation;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Client-only DAG source, preserving the accepted integer blend instead of DAG's staged rounding. */
public final class ExposureOverlaySource extends AbstractManyOperationSource {
    private static final Codec<ExposureOverlaySource> CODEC = makeCodec(ExposureOverlaySource::new);

    static {
        TexSource.register(new ResourceLocation("better_ore_geology", "exposure_overlay"), CODEC);
    }

    public ExposureOverlaySource(List<TexSource> sources) {
        super(sources);
        if (sources.size() != 2) throw new IllegalArgumentException("Exposure overlay requires host and overlay");
    }

    @Override
    public Codec<? extends TexSource> codec() {
        return CODEC;
    }

    @Override
    public PointwiseOperation.Any<Integer> getOperation() {
        return (colors, inBounds) -> {
            if (!inBounds[0] || !inBounds[1]) {
                throw new IllegalStateException("Exposure host and overlay must cover the same face");
            }
            return ExposureComposite.over(colors[0], colors[1]);
        };
    }
}
