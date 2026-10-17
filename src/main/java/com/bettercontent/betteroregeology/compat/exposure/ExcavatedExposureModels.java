package com.bettercontent.betteroregeology.compat.exposure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/** EV 3.0.9 host-major model/sprite naming contract. Does not alter host geometry. */
public final class ExcavatedExposureModels {
    private ExcavatedExposureModels() {}

    public static JsonElement decorate(JsonElement model, String modelLocation, String family) {
        int separator = modelLocation.lastIndexOf('_');
        if (separator < 0) throw new IllegalArgumentException("Unexpected EV model location: " + modelLocation);
        int counter = Integer.parseInt(modelLocation.substring(separator + 1));
        int first = counter / 12 * 12;
        String stem = modelLocation.substring(0, separator + 1);
        JsonObject result = model.deepCopy().getAsJsonObject();
        JsonObject textures = result.getAsJsonObject("textures");
        if (textures == null || textures.size() == 0) {
            throw new IllegalStateException("EV exposure model has no host textures: " + modelLocation);
        }
        // Snapshot before extending: every slot refers to the same host group's sprite ordinal.
        JsonObject originals = textures.deepCopy();
        for (Map.Entry<String, JsonElement> entry : originals.entrySet()) {
            String texture = entry.getValue().getAsString();
            String ordinal = null;
            if (texture.startsWith(modelLocation + "_")) {
                ordinal = texture.substring(modelLocation.length());
                if (!ordinal.matches("_[0-9]+")) {
                    throw new IllegalStateException("Unexpected EV sprite ordinal: " + texture);
                }
            }
            for (int slot = 0; slot < 12; slot++) {
                String sibling = ordinal != null ? stem + (first + slot) + ordinal
                        : texture.startsWith("#") ? "#slot" + slot + "_" + texture.substring(1) : texture;
                textures.addProperty("slot" + slot + "_" + entry.getKey(), sibling);
            }
        }
        result.addProperty("loader", "better_ore_geology:exposure");
        result.addProperty("family", family);
        return result;
    }
}
