package com.bettercontent.betteroregeology.registry;

import com.bettercontent.betteroregeology.RealisticOresMod;
import com.bettercontent.betteroregeology.block.SurfaceSampleBlock;
import com.bettercontent.betteroregeology.block.HotstoneBlock;
import com.bettercontent.betteroregeology.block.GeologicalOreBlock;
import com.bettercontent.betteroregeology.ore.OreDefinition;
import com.bettercontent.betteroregeology.ore.OreDefinitionLoader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RealisticOresMod.MOD_ID);
    private static final List<OreDefinition> ORE_DEFINITIONS = OreDefinitionLoader.loadAll();
    private static final Map<String, RegistryObject<Block>> BLOCKS_BY_ID = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<Block>> BLOCKS_BY_ORE_AND_HOST = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<Block>> SURFACE_SAMPLES_BY_ID = new LinkedHashMap<>();
    public static RegistryObject<Block> OIL_SEEP;
    private static boolean initialized;

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        if (!initialized) {
            initialized = true;
            for (OreDefinition definition : ORE_DEFINITIONS) {
                for (OreDefinition.VariantDefinition variant : definition.variants()) {
                    RegistryObject<Block> block = BLOCKS.register(
                            variant.blockId(),
                            () -> newDepositBlock(definition.id(), variant.copyPropertiesFrom()));
                    BLOCKS_BY_ID.put(variant.blockId(), block);
                    BLOCKS_BY_ORE_AND_HOST.put(key(definition.id(), variant.host()), block);
                }
                String sampleId = definition.surfaceSampleBlockId();
                ResourceLocation collectedItemId = ResourceLocation.fromNamespaceAndPath(
                        RealisticOresMod.MOD_ID,
                        definition.smallOreChunkItemId());
                SURFACE_SAMPLES_BY_ID.put(sampleId, BLOCKS.register(
                        sampleId,
                        () -> newSurfaceSample(collectedItemId, definition.id())));
            }
            ResourceLocation oilSeepItemId = ResourceLocation.fromNamespaceAndPath(
                    RealisticOresMod.MOD_ID,
                    "oil_seep");
            OIL_SEEP = BLOCKS.register("oil_seep", () -> newSurfaceSample(oilSeepItemId, null));
        }
        BLOCKS.register(modBus);
    }

    public static List<OreDefinition> oreDefinitions() {
        return ORE_DEFINITIONS;
    }

    public static RegistryObject<Block> getBlock(String oreId, String host) {
        RegistryObject<Block> block = BLOCKS_BY_ORE_AND_HOST.get(key(oreId, host));
        if (block == null) {
            throw new IllegalArgumentException("Unknown ore variant " + oreId + ":" + host);
        }
        return block;
    }

    public static Collection<Map.Entry<String, RegistryObject<Block>>> blockEntries() {
        return BLOCKS_BY_ID.entrySet();
    }

    public static Collection<Map.Entry<String, RegistryObject<Block>>> surfaceSampleEntries() {
        return SURFACE_SAMPLES_BY_ID.entrySet();
    }


    private static SurfaceSampleBlock newSurfaceSample(ResourceLocation collectedItemId, String depositFamily) {
        return new SurfaceSampleBlock(BlockBehaviour.Properties.copy(Blocks.MOSS_CARPET)
                .noCollission()
                .instabreak()
                .sound(SoundType.GRAVEL)
                .offsetType(BlockBehaviour.OffsetType.XZ), collectedItemId, depositFamily);
    }

    private static Block newDepositBlock(String family, String copyPropertiesFrom) {
        BlockBehaviour.Properties properties = copyProperties(copyPropertiesFrom);
        return family.equals("hotstone") ? new HotstoneBlock(properties) : new GeologicalOreBlock(family, properties);
    }

    private static String key(String oreId, String host) {
        return oreId + ":" + host;
    }

    private static BlockBehaviour.Properties copyProperties(String blockId) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(blockId);
        if (resourceLocation == null) {
            throw new IllegalArgumentException("Invalid copy_properties_from block id: " + blockId);
        }

        Block source = ForgeRegistries.BLOCKS.getValue(resourceLocation);
        if (source == null || source == Blocks.AIR) {
            throw new IllegalArgumentException("Unknown source block " + blockId);
        }
        return BlockBehaviour.Properties.copy(source);
    }
}
