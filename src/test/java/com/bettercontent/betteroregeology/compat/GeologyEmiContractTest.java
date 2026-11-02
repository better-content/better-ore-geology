package com.bettercontent.betteroregeology.compat;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Source/data registration guards; native/client acceptance is a separate test tier. */
class GeologyEmiContractTest {
    @Test void loadedRecipesDetermineBothDirectionsAndTheirRemainders() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/bettercontent/betteroregeology/compat/emi/GeologyEmiPlugin.java"));
        assertTrue(source.contains("@EmiEntrypoint"));
        assertTrue(source.contains("isLoaded(\"excavated_variants\")"));
        assertTrue(source.contains("ExcavatedVariantSupport.identify(item)"));
        assertTrue(source.contains("split.matches(grid, level)"));
        assertTrue(source.contains("split.assemble(grid, level.registryAccess())"));
        assertTrue(source.contains("split.getRemainingItems(grid).get(0)"));
        assertTrue(source.contains("join.matches(grid, level)"));
        assertTrue(source.contains("join.assemble(grid, level.registryAccess())"));
        assertTrue(source.contains("setRemainder(EmiStack.of(returned))"));
        assertTrue(source.contains("List.of(output, rock)"));
        assertTrue(source.contains("supportsRecipeTree() { return false; }"));
        assertTrue(source.contains("removeEmiStacks"));
        assertFalse(source.contains("addEmiStack("));
        for (String name : new String[]{"separation", "reassembly"}) {
            Path path = Path.of("src/main/resources/data/better_ore_geology/recipes/compat/excavated_variants/" + name + ".json");
            assertEquals("better_ore_geology:excavated_" + name,
                    JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("type").getAsString());
            assertTrue(source.contains("id(\"compat/excavated_variants/" + name + "\")"));
        }
    }
}
