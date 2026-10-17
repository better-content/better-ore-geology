package com.bettercontent.betteroregeology.client;

import com.bettercontent.betteroregeology.visual.ExposureField;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.model.ElementsModel;
import net.minecraftforge.client.model.geometry.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Same geometry contract for canonical hosts and EV's generated host composites. */
@Mod.EventBusSubscriber(modid = "better_ore_geology", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ExposureGeometryLoader implements IGeometryLoader<ExposureGeometryLoader.Geometry>, ResourceManagerReloadListener {
    public static final ExposureGeometryLoader INSTANCE = new ExposureGeometryLoader();
    private final Map<BakeKey, BakedModel> baked = new HashMap<>();
    private record BakeKey(String geometry, TextureAtlasSprite atlasEpoch, List<BlockElement> elements) {}
    private static final JsonObject MASKS = masks();

    @SubscribeEvent public static void loaders(ModelEvent.RegisterGeometryLoaders event) { event.register("exposure", INSTANCE); }
    @SubscribeEvent public static void reloaders(RegisterClientReloadListenersEvent event) { event.registerReloadListener(INSTANCE); }
    /** EV's weighted parent does not forward getModelData to its child geometry. */
    @SubscribeEvent public static void positions(ModelEvent.ModifyBakingResult event) {
        Map<BakedModel, BakedModel> wrappers = new IdentityHashMap<>();
        event.getModels().replaceAll((location, model) -> {
            if (!(location instanceof ModelResourceLocation state)
                    || !state.getVariant().contains("geological_style=")) return model;
            return wrappers.computeIfAbsent(model, original -> new net.minecraftforge.client.model.BakedModelWrapper<BakedModel>(original) {
                @Override public net.minecraftforge.client.model.data.ModelData getModelData(
                        net.minecraft.world.level.BlockAndTintGetter level, net.minecraft.core.BlockPos pos,
                        net.minecraft.world.level.block.state.BlockState blockState,
                        net.minecraftforge.client.model.data.ModelData data) {
                    return originalModel.getModelData(level, pos, blockState, data).derive()
                            .with(ExposureBakedModel.POSITION, pos.immutable()).build();
                }
            });
        });
    }
    @Override public synchronized void onResourceManagerReload(ResourceManager manager) {
        baked.clear();
        ExposureBakedModel.clearCache();
    }
    private static JsonObject masks() {
        try (var stream = ExposureGeometryLoader.class.getResourceAsStream("/assets/better_ore_geology/exposure_masks.json")) {
            if (stream == null) throw new IllegalStateException("Missing approved exposure masks");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    @Override public Geometry read(JsonObject json, JsonDeserializationContext parser) {
        String family = json.get("family").getAsString();
        if (!ExposureField.FAMILIES.contains(family)) throw new JsonParseException("Unknown exposure family " + family);
        // EV emits parent-only models (for example minecraft:block/cube_all). Resolve
        // them through the same bakery as vanilla rather than inventing host geometry.
        JsonObject vanilla = json.deepCopy();
        vanilla.remove("loader");
        BlockModel base = parser.deserialize(vanilla, BlockModel.class);
        JsonObject slots = new JsonObject();
        for (var entry : json.getAsJsonObject("textures").entrySet())
            if (entry.getKey().startsWith("slot")) slots.add(entry.getKey(), entry.getValue());
        return new Geometry(family, base, slots.toString());
    }

    public record Geometry(String family, BlockModel base, String textureKey) implements IUnbakedGeometry<Geometry> {
        @Override public void resolveParents(Function<ResourceLocation, UnbakedModel> models, IGeometryBakingContext context) {
            base.resolveParents(models);
        }
        @Override public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides, ResourceLocation location) {
            List<BlockElement> elements = base.getElements();
            if (elements.size() != 1) throw new JsonParseException("Exposure requires a full-cube host model");
            BlockElement host = elements.get(0);
            if (!host.from.equals(new org.joml.Vector3f(0, 0, 0)) || !host.to.equals(new org.joml.Vector3f(16, 16, 16))
                    || host.rotation != null || host.faces.size() != 6)
                throw new JsonParseException("Exposure requires six full-cube host faces");
            Set<String> slotNames = new LinkedHashSet<>();
            JsonParser.parseString(textureKey).getAsJsonObject().keySet().stream()
                    .filter(name -> name.startsWith("slot0_")).forEach(name -> slotNames.add(name.substring(6)));
            String textureName = slotName(base, host.faces.values().iterator().next().texture, slotNames);
            // Reload listeners may apply after ModelManager. A new atlas sprite is an identity
            // boundary: never return quads retaining sprites from the previous resource atlas.
            var epoch = sprites.apply(context.getMaterial("#slot0_" + textureName));
            BakeKey key = new BakeKey(family + textureKey + state.getRotation() + state.isUvLocked() + context.getRootTransform()
                    + context.getRenderTypeHint() + context.useAmbientOcclusion() + context.getTransforms().hashCode(), epoch, elements);
            synchronized (INSTANCE) {
                BakedModel existing = INSTANCE.baked.get(key);
                if (existing != null) return existing;
                BakedModel[] variants = new BakedModel[12];
                for (int slot = 0; slot < 12; slot++) {
                    List<BlockElement> copies = new ArrayList<>();
                    for (BlockElement element : elements) {
                        Map<Direction, BlockElementFace> faces = new EnumMap<>(Direction.class);
                        for (var entry : element.faces.entrySet()) {
                            var face = entry.getValue();
                            String name = slotName(base, face.texture, slotNames);
                            faces.put(entry.getKey(), new BlockElementFace(face.cullForDirection, face.tintIndex,
                                    "#slot" + slot + "_" + name, face.uv, face.getFaceData()));
                        }
                        copies.add(new BlockElement(element.from, element.to, faces, element.rotation, element.shade, element.getFaceData()));
                    }
                    variants[slot] = new ElementsModel(copies).bake(context, baker, sprites, state, overrides, location);
                }
                JsonArray masks = MASKS.getAsJsonArray(family);
                String[] originalMasks = new String[3];
                for (int i = 0; i < 3; i++) {
                    originalMasks[i] = masks.get(i).getAsString();
                    if (originalMasks[i].length() != 256) throw new IllegalStateException("Invalid original mask " + family);
                }
                BakedModel result = new ExposureBakedModel(family, variants, originalMasks);
                INSTANCE.baked.put(key, result);
                return result;
            }
        }

        private static String slotName(BlockModel base, String texture, Set<String> slots) {
            String name = texture.startsWith("#") ? texture.substring(1) : texture;
            if (slots.contains(name)) return name;
            // cube_bottom_top inherits cube's #north/#down faces via #side/#bottom
            // aliases. Resolve those aliases BEFORE prefixing the exposure slot.
            Material resolved = base.getMaterial(texture);
            for (String candidate : slots)
                if (base.getMaterial("#" + candidate).equals(resolved)) return candidate;
            throw new JsonParseException("No exposure slot binding for inherited face " + texture);
        }
    }
}
