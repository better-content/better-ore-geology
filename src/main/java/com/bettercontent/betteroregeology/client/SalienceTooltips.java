package com.bettercontent.betteroregeology.client;

import com.bettercontent.betteroregeology.RealisticOresMod;
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
                .append(Component.literal(identity.glyph + " " + identity.aspect)
                        .withStyle(style -> style.withFont(DEFAULT_FONT).withColor(identity.color))));
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
        HOTSTONE(0, "hotstone", "✦", "Impact", 0xFF4055, "Violent heat and heavy power.",
                "warms, burns, and yields magma.", "fissile, structural, and abyssal metals."),
        COPPER_BLOOM(1, "copper_bloom", "»", "Tempo", 0x00A985, "Responsive metal for fast mechanisms.",
                "copper.", "sulfur, iron, and traces of gold."),
        TIN_QUARTZ(2, "tin_quartz", "⚒", "Work", 0xF0E2C5, "Toolmaking crystal rock for productive industry.",
                "tin and quartz.", "specialized routes reveal gems and aluminum."),
        BRASSROOT(3, "brassroot", "➜", "Mobility", 0xE0B01F, "Brasswork for moving machines and transport.",
                "zinc for brass.", "lead, cadmium, and traces of silver."),
        COAL_MEASURES(4, "coal_measures", "∞", "Endurance", 0x52606A, "Stored fuel for long-running work.",
                "combustible coal chunks.", "clean coal with traces of iron."),
        IRONSTONE(5, "ironstone", "◆", "Robustness", 0xAF6A2F, "Dense metal for durable tools and structures.",
                "iron.", "nickel-bearing iron concentrate."),
        EVAPORITE_BEDS(6, "evaporite_beds", "✚", "Renewal", 0x6CCAF0, "Salt and fertile chemistry for sustaining life.",
                "rock salt for food and preservation.", "sodium chloride and saltpeter."),
        BLACK_SHALE(7, "black_shale", "⊕", "Control", 0x8E5BB7, "Signal-bearing shale touched by soul matter.",
                "redstone and crude soul material.", "copper, sulfur, iron, vanadium, and precious traces.");

        final int index;
        final String family;
        final String glyph;
        final String aspect;
        final int color;
        final String promise;
        final String immediate;
        final String refines;

        Identity(int index, String family, String glyph, String aspect, int color, String promise, String immediate, String refines) {
            this.index = index;
            this.family = family;
            this.glyph = glyph;
            this.aspect = aspect;
            this.color = color;
            this.promise = promise;
            this.immediate = immediate;
            this.refines = refines;
        }

        String badge() { return Character.toString(0xE100 + index); }

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
