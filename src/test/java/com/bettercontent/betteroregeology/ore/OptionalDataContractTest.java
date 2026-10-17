package com.bettercontent.betteroregeology.ore;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Optional providers must disappear cleanly without changing the full-pack processing payload. */
final class OptionalDataContractTest {
    private static final Path DATA = Path.of("src/main/resources/data/better_ore_geology");
    private static final Set<String> SHARED_DOMAINS = Set.of("minecraft", "forge", "c", "better_ore_geology");

    @Test
    void everyOptionalRecipeProviderIsGuardedAndAllProvidersEnableTheSameRecipe() throws IOException {
        Set<String> checkedProviders = new HashSet<>();
        for (Path path : jsonFiles(DATA.resolve("recipes"))) {
            JsonObject recipe = read(path);
            JsonObject payload = recipe.deepCopy();
            payload.remove("conditions");
            Set<String> providers = new TreeSet<>();
            findProviders(payload, providers);
            Set<String> guards = new HashSet<>();
            if (recipe.has("conditions")) {
                for (JsonElement element : recipe.getAsJsonArray("conditions")) {
                    JsonObject condition = element.getAsJsonObject();
                    assertEquals("forge:mod_loaded", condition.get("type").getAsString(), path.toString());
                    guards.add(condition.get("modid").getAsString());
                }
            }
            assertTrue(guards.containsAll(providers), () -> path + " requires " + providers + " but guards " + guards);
            checkedProviders.addAll(providers);
            Set<String> fullPack = new HashSet<>(providers);
            fullPack.addAll(guards);
            assertTrue(enabled(guards, fullPack), path + " must remain enabled with all providers");
            for (String missing : providers) {
                Set<String> installed = new HashSet<>(fullPack);
                installed.remove(missing);
                assertFalse(enabled(guards, installed), path + " must skip before parsing absent " + missing);
            }
        }
        assertTrue(checkedProviders.containsAll(Set.of("chemlib", "tconstruct", "bloodmagic", "create")));
    }

    @Test
    void foreignTagEntriesAreOptionalWhileNativeReferencesRemainRequired() throws IOException {
        Set<String> optionalDomains = new HashSet<>();
        for (Path path : jsonFiles(DATA.resolve("tags"))) {
            for (JsonElement value : read(path).getAsJsonArray("values")) {
                String id = value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("id").getAsString();
                String domain = id.replaceFirst("^#", "").split(":", 2)[0];
                if (!SHARED_DOMAINS.contains(domain)) {
                    assertTrue(value.isJsonObject(), path + ": missing optional flag on " + id);
                    assertFalse(value.getAsJsonObject().get("required").getAsBoolean(), path + ": required " + id);
                    optionalDomains.add(domain);
                } else {
                    assertTrue(value.isJsonPrimitive() || !value.getAsJsonObject().has("required")
                            || value.getAsJsonObject().get("required").getAsBoolean(),
                            path + ": native/shared reference unexpectedly weakened");
                }
            }
        }
        assertTrue(optionalDomains.containsAll(Set.of("aether", "fallout_wastelands_", "chemlib")));
    }

    @Test
    void copperAndTinCookingPreserveCountsAndProviderConditions() throws IOException {
        for (String method : List.of("furnace", "blasting")) {
            JsonObject copper = read(DATA.resolve("recipes/thermal/" + method + "/copper_bloom_chunk.json"));
            assertEquals("tconstruct:copper_nugget", copper.getAsJsonObject("result").get("item").getAsString());
            assertEquals(4, copper.getAsJsonObject("result").get("count").getAsInt());
            JsonObject tin = read(DATA.resolve("recipes/thermal/" + method + "/concentrate_tin.json"));
            assertEquals("chemlib:tin_nugget", tin.getAsJsonObject("result").get("item").getAsString());
            assertEquals(4, tin.getAsJsonObject("result").get("count").getAsInt());
        }
        JsonObject crossMod = read(DATA.resolve("recipes/compat/createsifter/sifting/evaporite_beds/brass_wet.json"));
        JsonArray conditions = crossMod.getAsJsonArray("conditions");
        Set<String> mods = new HashSet<>();
        conditions.forEach(value -> mods.add(value.getAsJsonObject().get("modid").getAsString()));
        assertTrue(mods.contains("createsifter"), "Existing serializer condition must survive");
        assertTrue(mods.contains("chemlib"), "Foreign coproduct provider must be guarded");
        assertTrue(mods.contains("bloodmagic"), "Foreign saltpeter provider must be guarded");
    }

    private static boolean enabled(Set<String> guards, Set<String> installed) {
        return installed.containsAll(guards);
    }

    private static void findProviders(JsonElement value, Set<String> result) {
        if (value.isJsonObject()) {
            value.getAsJsonObject().entrySet().forEach(entry -> findProviders(entry.getValue(), result));
        } else if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(child -> findProviders(child, result));
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString().replaceFirst("^#", "");
            if (text.matches("[a-z0-9_]+:[a-z0-9_./-]+")) {
                String domain = text.split(":", 2)[0];
                if (!SHARED_DOMAINS.contains(domain)) result.add(domain);
            }
        }
    }

    private static List<Path> jsonFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
