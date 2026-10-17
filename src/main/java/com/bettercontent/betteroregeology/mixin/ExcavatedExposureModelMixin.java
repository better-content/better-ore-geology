package com.bettercontent.betteroregeology.mixin;

import com.bettercontent.betteroregeology.compat.exposure.ExcavatedExposureModels;
import com.bettercontent.betteroregeology.compat.exposure.ExcavatedExposureProvider.ExposureFaces;
import com.google.gson.JsonElement;
import dev.lukebemish.excavatedvariants.api.client.ModelData;
import dev.lukebemish.excavatedvariants.api.client.TexFaceProvider;
import dev.lukebemish.excavatedvariants.impl.data.BaseStone;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Pinned to EV 3.0.9's single JsonElement model local immediately after host assembly. */
// Reviewed upstream source: ExcavatedVariants c9b3ac5c61b1defaf7485b91828dc954c4a0dfe0 (3.0.9).
@Pseudo
@Mixin(targets = "dev.lukebemish.excavatedvariants.impl.client.ResourceAssembler", remap = false)
public abstract class ExcavatedExposureModelMixin {
    @ModifyVariable(method = "assembleModel(Lnet/minecraft/resources/ResourceLocation;Ldev/lukebemish/excavatedvariants/api/client/TexFaceProvider;Ldev/lukebemish/excavatedvariants/api/client/ModelData;Ldev/lukebemish/excavatedvariants/api/client/ModelData;Ldev/lukebemish/excavatedvariants/impl/data/BaseStone;)V",
            at = @At("STORE"), ordinal = 0, require = 1, expect = 1, allow = 1, remap = false)
    private JsonElement better_ore_geology$decorateExposureModel(JsonElement model,
            ResourceLocation modelLocation, TexFaceProvider ore, ModelData oldStone,
            ModelData newStone, BaseStone oldStoneData) {
        if (!(ore instanceof ExposureFaces exposure)) return model;
        return ExcavatedExposureModels.decorate(model, modelLocation.toString(), exposure.family());
    }
}
