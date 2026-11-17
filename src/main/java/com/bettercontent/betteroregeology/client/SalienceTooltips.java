package com.bettercontent.betteroregeology.client;

import com.bettercontent.betteroregeology.RealisticOresMod;
import com.bettercontent.betteroregeology.salience.SalienceAspect;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;

@Mod.EventBusSubscriber(modid = RealisticOresMod.MOD_ID, value = Dist.CLIENT)
public final class SalienceTooltips {
    private static final ResourceLocation ASPECT_FONT = new ResourceLocation(RealisticOresMod.MOD_ID, "aspects");
    private static final ResourceLocation DEFAULT_FONT = new ResourceLocation("minecraft", "default");
    private SalienceTooltips() {}

    @SubscribeEvent
    public static void append(ItemTooltipEvent event) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(event.getItemStack().getItem());
        if (id == null || !id.getNamespace().equals(RealisticOresMod.MOD_ID)) return;
        Identity identity = Identity.fromItemPath(id.getPath());
        if (identity == null) return;

        event.getToolTip().add(Component.literal(identity.badge()).withStyle(style -> style.withFont(ASPECT_FONT))
                .append(Component.literal(" ").withStyle(style -> style.withFont(DEFAULT_FONT)))
                .append(Component.literal(identity.aspect.glyph() + " " + identity.aspect.label())
                        .withStyle(style -> style.withFont(DEFAULT_FONT).withColor(identity.aspect.color()))));
        event.getToolTip().add(Component.literal(identity.promise).withStyle(ChatFormatting.GRAY));
        if (Screen.hasShiftDown()) {
            event.getToolTip().add(Component.literal("Immediate: ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(identity.immediate).withStyle(ChatFormatting.GRAY)));
            event.getToolTip().add(Component.literal("Refines: ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(identity.refines).withStyle(ChatFormatting.GRAY)));
        } else {
            event.getToolTip().add(Component.literal("Hold Shift for assay summary").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    enum Identity {
        HOTSTONE(SalienceAspect.POWER, "hotstone", "Violent heat and heavy power.",
                "warms, burns, and yields magma.", "fissile, structural, and abyssal metals."),
        COPPER_BLOOM(SalienceAspect.MOTION, "copper_bloom", "Responsive metal for fast mechanisms.",
                "copper.", "sulfur, iron, and traces of gold."),
        TIN_QUARTZ(SalienceAspect.POWER, "tin_quartz", "Toolmaking crystal rock for productive industry.",
                "tin and quartz.", "specialized routes reveal gems and aluminum."),
        BRASSROOT(SalienceAspect.MOTION, "brassroot", "Brasswork for moving machines and transport.",
                "zinc for brass.", "lead, cadmium, and traces of silver."),
        COAL_MEASURES(SalienceAspect.ENDURANCE, "coal_measures", "Stored fuel for long-running work.",
                "combustible coal chunks.", "clean coal with traces of iron."),
        IRONSTONE(SalienceAspect.ENDURANCE, "ironstone", "Dense metal for durable tools and structures.",
                "iron.", "nickel-bearing iron concentrate."),
        EVAPORITE_BEDS(SalienceAspect.RENEWAL, "evaporite_beds", "Salt and fertile chemistry for sustaining life.",
                "rock salt for food and preservation.", "sodium chloride and saltpeter."),
        BLACK_SHALE(SalienceAspect.CONTROL, "black_shale", "Signal-bearing shale touched by soul matter.",
                "redstone and crude soul material.", "copper, sulfur, iron, vanadium, and precious traces.");

        final SalienceAspect aspect;
        final String family;
        final String promise;
        final String immediate;
        final String refines;

        Identity(SalienceAspect aspect, String family, String promise, String immediate, String refines) {
            this.aspect = aspect;
            this.family = family;
            this.promise = promise;
            this.immediate = immediate;
            this.refines = refines;
        }

        String badge() { return Character.toString(0xE100 + aspect.badgeSlot()); }

        static Identity fromItemPath(String path) {
            return Arrays.stream(values()).filter(identity ->
                    path.equals(identity.family)
                            || path.equals("deepslate_" + identity.family)
                            || path.equals("ore_chunk_" + identity.family)
                            || path.equals("small_ore_chunk_" + identity.family)
                            || path.equals("crushed_" + identity.family)).findFirst().orElse(null);
        }
    }
}
