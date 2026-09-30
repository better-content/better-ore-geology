package com.bettercontent.betteroregeology.registry;

import com.bettercontent.betteroregeology.RealisticOresMod;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModFluids {
    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, RealisticOresMod.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, RealisticOresMod.MOD_ID);
    private static final ResourceLocation STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation FLOWING = ResourceLocation.withDefaultNamespace("block/water_flow");

    public static final MoltenForm TITANIUM = molten("titanium", 0xff8e969c, 950);
    public static final MoltenForm THORIUM = molten("thorium", 0xff8f9c68, 950);

    private ModFluids() {
    }

    /** Forces registration suppliers to be installed before the shared block/item registers attach. */
    public static void bootstrap() {
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }

    private static MoltenForm molten(String material, int tint, int temperature) {
        String name = "molten_" + material;
        RegistryObject<FluidType> type = FLUID_TYPES.register(name, () -> new FluidType(
                FluidType.Properties.create()
                        .density(3000)
                        .viscosity(6000)
                        .temperature(temperature)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)) {
            @Override
            public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return STILL;
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return FLOWING;
                    }

                    @Override
                    public int getTintColor() {
                        return tint;
                    }
                });
            }
        });
        References references = new References();
        ForgeFlowingFluid.Properties properties = new ForgeFlowingFluid.Properties(
                type, () -> references.source.get(), () -> references.flowing.get())
                .block(() -> references.block.get())
                .bucket(() -> references.bucket.get())
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2);
        references.source = FLUIDS.register(name, () -> new ForgeFlowingFluid.Source(properties));
        references.flowing = FLUIDS.register("flowing_" + name, () -> new ForgeFlowingFluid.Flowing(properties));
        references.block = ModBlocks.BLOCKS.register(name, () -> new LiquidBlock(
                () -> references.source.get(), BlockBehaviour.Properties.of().noCollission().strength(100.0F).noLootTable()));
        references.bucket = ModItems.ITEMS.register(name + "_bucket", () -> new BucketItem(
                () -> references.source.get(), new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1)));
        return new MoltenForm(references.source, references.flowing, references.block, references.bucket);
    }

    public record MoltenForm(
            RegistryObject<FlowingFluid> source,
            RegistryObject<FlowingFluid> flowing,
            RegistryObject<LiquidBlock> block,
            RegistryObject<Item> bucket) {
    }

    private static final class References {
        private RegistryObject<FlowingFluid> source;
        private RegistryObject<FlowingFluid> flowing;
        private RegistryObject<LiquidBlock> block;
        private RegistryObject<Item> bucket;
    }
}
