package com.bettercontent.betteroregeology.compat;

import com.bettercontent.betteroregeology.RealisticOresMod;
import com.bettercontent.betteroregeology.registry.ModBlocks;
import dev.lukebemish.excavatedvariants.impl.ModifiedOreBlock;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Optional typed bridge guarded by the loaded mod and its declared compatible version. */
public final class ExcavatedVariantSupport {
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile Map<String, Variant> variantsByCycle;

    private ExcavatedVariantSupport() {
    }

    public static @Nullable Variant identify(Item item) {
        if (!ModList.get().isLoaded("excavated_variants")) return null;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        Block block = itemId == null ? null : ForgeRegistries.BLOCKS.getValue(itemId);
        if (!(block instanceof ModifiedOreBlock modifiedOre)) {
            return null;
        }
        String family = modifiedOre.ore.id;
        ResourceLocation substrateId = modifiedOre.stone.blockId;
        boolean known = ModBlocks.oreDefinitions().stream().anyMatch(definition -> definition.id().equals(family));
        if (!known) {
            if (isOwnedOre(modifiedOre)) {
                warnOnce(block, "unknown deposit family " + family);
            }
            return null;
        }
        Block substrate = ForgeRegistries.BLOCKS.getValue(substrateId);
        if (substrate == null) {
            warnOnce(block, "unknown substrate " + substrateId);
            return null;
        }
        return new Variant(family, substrate, block);
    }

    public static @Nullable Variant find(String family, Item substrate) {
        Map<String, Variant> variants = variantsByCycle;
        if (variants == null) {
            synchronized (ExcavatedVariantSupport.class) {
                variants = variantsByCycle;
                if (variants == null) {
                    Map<String, Variant> discovered = new LinkedHashMap<>();
                    for (Block block : ForgeRegistries.BLOCKS.getValues()) {
                        Variant variant = identify(block.asItem());
                        if (variant != null) {
                            discovered.put(key(variant.family(), variant.substrate().asItem()), variant);
                        }
                    }
                    variants = Map.copyOf(discovered);
                    variantsByCycle = variants;
                }
            }
        }
        return variants.get(key(family, substrate));
    }

    private static String key(String family, Item substrate) {
        return family + ":" + ForgeRegistries.ITEMS.getKey(substrate);
    }

    private static boolean isOwnedOre(ModifiedOreBlock ore) {
        return ore.ore.blockId.stream().anyMatch(id -> id.getNamespace().equals(RealisticOresMod.MOD_ID));
    }

    private static void warnOnce(Block block, String reason) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        String key = id + ":" + reason;
        if (WARNED.add(key)) {
            RealisticOresMod.LOGGER.warn("Skipping Excavated Variants ore {}: {}", id, reason);
        }
    }

    public record Variant(String family, Block substrate, Block hostedOre) {
    }
}
