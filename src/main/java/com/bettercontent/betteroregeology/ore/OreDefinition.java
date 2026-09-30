package com.bettercontent.betteroregeology.ore;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class OreDefinition {
    private String id;
    @SerializedName("display_name")
    private String displayName;
    private List<VariantDefinition> variants = List.of();
    private List<String> tags = List.of();

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public List<VariantDefinition> variants() {
        return variants;
    }

    public List<String> tags() {
        return tags;
    }

    public void validate() {
        require(id, "id");
        require(displayName, "display_name");
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("Ore " + id + " must define at least one variant");
        }

        Set<String> seenHosts = new java.util.LinkedHashSet<>();
        for (VariantDefinition variant : variants) {
            variant.validate();
            if (!seenHosts.add(variant.host())) {
                throw new IllegalArgumentException("Ore " + id + " defines duplicate host " + variant.host());
            }
        }

        if (tags == null) {
            tags = List.of();
        }
    }

    public VariantDefinition primaryVariant() {
        return variantByHost("stone").orElse(variants.get(0));
    }

    public String crushedItemId() {
        return "crushed_" + id;
    }

    public String oreChunkItemId() {
        return "ore_chunk_" + id;
    }

    public String smallOreChunkItemId() {
        return "small_ore_chunk_" + id;
    }

    public String surfaceSampleBlockId() {
        return "surface_sample_" + id;
    }

    public Optional<VariantDefinition> variantByHost(String host) {
        return variants.stream().filter(variant -> variant.host().equals(host)).findFirst();
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
    }

    public static final class VariantDefinition {
        private String host;
        @SerializedName("block_id")
        private String blockId;
        @SerializedName("texture_mode")
        private String textureMode;
        private TexturesDefinition textures;
        @SerializedName("copy_properties_from")
        private String copyPropertiesFrom;

        public String host() {
            return host;
        }

        public String blockId() {
            return blockId;
        }

        public TextureMode textureMode() {
            return TextureMode.fromSerialized(textureMode);
        }

        public TexturesDefinition textures() {
            return textures;
        }

        public String copyPropertiesFrom() {
            return copyPropertiesFrom;
        }

        private void validate() {
            require(host, "variants.host");
            require(blockId, "variants.block_id");
            require(textureMode, "variants.texture_mode");
            require(copyPropertiesFrom, "variants.copy_properties_from");
            Objects.requireNonNull(textures, "variants.textures");
            textureMode().validateTextures(textures);
        }
    }

    public static final class TexturesDefinition {
        private String all;
        private String side;
        private String top;
        private String bottom;
        private String north;
        private String east;
        private String south;
        private String west;
        private String up;
        private String down;

        public String all() {
            return all;
        }

        public String side() {
            return side;
        }

        public String top() {
            return top;
        }

        public String bottom() {
            return bottom;
        }

        public String north() {
            return north;
        }

        public String east() {
            return east;
        }

        public String south() {
            return south;
        }

        public String west() {
            return west;
        }

        public String up() {
            return up;
        }

        public String down() {
            return down;
        }
    }

    public enum TextureMode {
        CUBE_ALL,
        CUBE_COLUMN_LIKE,
        CUBE_SIDED;

        public static TextureMode fromSerialized(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "cube_all" -> CUBE_ALL;
                case "cube_column_like" -> CUBE_COLUMN_LIKE;
                case "cube_sided" -> CUBE_SIDED;
                default -> throw new IllegalArgumentException("Unsupported texture_mode: " + value);
            };
        }

        public void validateTextures(TexturesDefinition textures) {
            switch (this) {
                case CUBE_ALL -> require(textures.all(), "textures.all");
                case CUBE_COLUMN_LIKE -> {
                    require(textures.side(), "textures.side");
                    require(textures.top(), "textures.top");
                    require(textures.bottom(), "textures.bottom");
                }
                case CUBE_SIDED -> {
                    require(textures.north(), "textures.north");
                    require(textures.east(), "textures.east");
                    require(textures.south(), "textures.south");
                    require(textures.west(), "textures.west");
                    require(textures.up(), "textures.up");
                    require(textures.down(), "textures.down");
                }
            }
        }
    }
}
