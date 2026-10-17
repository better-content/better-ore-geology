package com.bettercontent.betteroregeology.client;

import com.bettercontent.betteroregeology.visual.ExposureField;
import com.bettercontent.betteroregeology.visual.ExposureState;
import java.util.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/** Opaque, non-overlapping face rectangles; source UVs and original mineral colors are untouched. */
public final class ExposureBakedModel extends BakedModelWrapper<BakedModel> {
    static final ModelProperty<BlockPos> POSITION = new ModelProperty<>();
    private static final int MAX_CACHED_QUADS = 32768;
    private static int cachedQuads;
    private static final Map<CacheKey, List<BakedQuad>> CACHE = new LinkedHashMap<>(256, 0.75f, true);
    private final String family;
    private final BakedModel[] variants;
    private final String[] originalMasks;

    public ExposureBakedModel(String family, BakedModel[] variants, String[] originalMasks) {
        super(variants[9]);
        this.family = family;
        this.variants = variants.clone();
        this.originalMasks = originalMasks.clone();
    }
    public static synchronized void clearCache() { synchronized (CACHE) { CACHE.clear(); cachedQuads = 0; } }
    public static int cachedQuadCount() { synchronized (CACHE) { return cachedQuads; } }
    @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData input) {
        return input.derive().with(POSITION, pos.immutable()).build();
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType renderType) {
        BlockPos pos = data.get(POSITION);
        if (pos == null || state == null || !state.hasProperty(ExposureState.STYLE) || side == null)
            return originalModel.getQuads(state, side, random, data, renderType);
        BakedQuad[] sources = new BakedQuad[12];
        for (int i = 0; i < 12; i++) {
            List<BakedQuad> quads = variants[i].getQuads(state, side, RandomSource.create(0), ModelData.EMPTY, renderType);
            if (quads.isEmpty()) return List.of();
            if (quads.size() != 1) throw new IllegalStateException("Exposure host must provide one quad per cube face: " + family);
            sources[i] = quads.get(0);
        }
        byte[] slots = new byte[256];
        int style = state.getValue(ExposureState.STYLE);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            float s = (x + 0.5f) / 16, t = (y + 0.5f) / 16;
            double wx = pos.getX() + interpolate(sources[0], 0, s, t);
            double wy = pos.getY() + interpolate(sources[0], 1, s, t);
            double wz = pos.getZ() + interpolate(sources[0], 2, s, t);
            int role = ExposureField.role(family, style, wx, wy, wz);
            int variant = ExposureField.variant(family, style, role, wx, wy, wz);
            if (role == 3) {
                var sprite = sources[9 + variant].getSprite();
                float u = interpolate(sources[9 + variant], 4, s, t);
                float v = interpolate(sources[9 + variant], 5, s, t);
                int px = Math.max(0, Math.min(15, (int) ((u - sprite.getU0()) / (sprite.getU1() - sprite.getU0()) * 16)));
                int py = Math.max(0, Math.min(15, (int) ((v - sprite.getV0()) / (sprite.getV1() - sprite.getV0()) * 16)));
                if (originalMasks[variant].charAt(py * 16 + px) == '0') {
                    role = 0;
                    variant = ExposureField.variant(family, style, role, wx, wy, wz);
                }
            }
            slots[y * 16 + x] = (byte) (role * 3 + variant);
        }
        CacheKey key = new CacheKey(this, side, new String(slots, StandardCharsets.ISO_8859_1));
        synchronized (CACHE) {
            List<BakedQuad> cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        List<BakedQuad> result = new ArrayList<>();
        boolean[] used = new boolean[256];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int index = y * 16 + x;
            if (used[index]) continue;
            byte slot = slots[index];
            int width = 1;
            while (x + width < 16 && !used[index + width] && slots[index + width] == slot) width++;
            int height = 1;
            outer: while (y + height < 16) {
                for (int dx = 0; dx < width; dx++) {
                    int at = (y + height) * 16 + x + dx;
                    if (used[at] || slots[at] != slot) break outer;
                }
                height++;
            }
            for (int dy = 0; dy < height; dy++) for (int dx = 0; dx < width; dx++) used[(y + dy) * 16 + x + dx] = true;
            result.add(crop(sources[slot], x, y, x + width, y + height));
        }
        List<BakedQuad> immutable = List.copyOf(result);
        synchronized (CACHE) {
            List<BakedQuad> old = CACHE.put(key, immutable);
            cachedQuads += immutable.size() - (old == null ? 0 : old.size());
            var iterator = CACHE.entrySet().iterator();
            while (cachedQuads > MAX_CACHED_QUADS && iterator.hasNext()) {
                cachedQuads -= iterator.next().getValue().size();
                iterator.remove();
            }
        }
        return immutable;
    }
    private static float interpolate(BakedQuad quad, int component, float s, float t) {
        int[] data = quad.getVertices();
        int stride = data.length / 4;
        float a = Float.intBitsToFloat(data[component]);
        float b = Float.intBitsToFloat(data[stride + component]);
        float c = Float.intBitsToFloat(data[2 * stride + component]);
        float d = Float.intBitsToFloat(data[3 * stride + component]);
        return (1 - t) * ((1 - s) * a + s * d) + t * ((1 - s) * b + s * c);
    }
    private static BakedQuad crop(BakedQuad source, int x0, int y0, int x1, int y1) {
        int[] vertices = source.getVertices().clone();
        int stride = vertices.length / 4;
        float[][] corners = {{x0 / 16f, y0 / 16f}, {x0 / 16f, y1 / 16f}, {x1 / 16f, y1 / 16f}, {x1 / 16f, y0 / 16f}};
        for (int v = 0; v < 4; v++) for (int component : new int[]{0, 1, 2, 4, 5})
            vertices[v * stride + component] = Float.floatToRawIntBits(interpolate(source, component, corners[v][0], corners[v][1]));
        return new BakedQuad(vertices, source.getTintIndex(), source.getDirection(), source.getSprite(), source.isShade());
    }
    private record CacheKey(ExposureBakedModel model, Direction side, String slots) {}
}
