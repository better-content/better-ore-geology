package com.bettercontent.betteroregeology.compat.exposure;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the optional-loading boundary and reviewed EV 3.0.9 injection descriptors. */
final class ExcavatedExposureInjectionContractTest {
    private static final Path JAVA = Path.of("src/main/java/com/bettercontent/betteroregeology");

    @Test
    void optionalTargetsHaveExplicitProductionSelectorsAndRequiredMatchCounts() throws IOException {
        String block = Files.readString(JAVA.resolve("mixin/ExcavatedExposureBlockMixin.java"));
        String model = Files.readString(JAVA.resolve("mixin/ExcavatedExposureModelMixin.java"));
        for (String source : new String[] {block, model}) {
            assertTrue(source.contains("@Pseudo"));
            assertTrue(source.contains("remap = false"));
            assertTrue(source.contains("require = 1, expect = 1, allow = 1"));
        }
        assertTrue(block.contains("setupStaticWrapper(Ldev/lukebemish/excavatedvariants/impl/data/BaseOre;Ldev/lukebemish/excavatedvariants/impl/data/BaseStone;)V"));
        assertTrue(block.contains("getStateForPlacement(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"));
        assertTrue(block.contains("m_5573_(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"));
        assertTrue(model.contains("assembleModel(Lnet/minecraft/resources/ResourceLocation;Ldev/lukebemish/excavatedvariants/api/client/TexFaceProvider;Ldev/lukebemish/excavatedvariants/api/client/ModelData;Ldev/lukebemish/excavatedvariants/api/client/ModelData;Ldev/lukebemish/excavatedvariants/impl/data/BaseStone;)V"));
        assertTrue(model.contains("at = @At(\"STORE\"), ordinal = 0"));
    }

    @Test
    void commonMixinDoesNotReferenceClientTextureClasses() throws IOException {
        String source = Files.readString(JAVA.resolve("mixin/ExcavatedExposureBlockMixin.java"));
        assertFalse(source.contains("api.client"));
        assertFalse(source.contains("dynamicassetgenerator"));
        assertFalse(source.contains("net.minecraft.client"));
        String provider = Files.readString(JAVA.resolve("compat/exposure/ExcavatedExposureProvider.java"));
        assertTrue(provider.contains("@ExcavatedVariantsListener"));
        assertFalse(provider.contains("@Mod.EventBusSubscriber"), "EV owns optional client discovery");
    }
}
