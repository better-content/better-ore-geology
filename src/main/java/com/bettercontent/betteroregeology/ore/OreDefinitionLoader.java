package com.bettercontent.betteroregeology.ore;

import com.google.gson.Gson;
import com.bettercontent.betteroregeology.RealisticOresMod;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import net.minecraftforge.fml.ModList;

public final class OreDefinitionLoader {
    private static final Gson GSON = new Gson();

    private OreDefinitionLoader() {
    }

    public static List<OreDefinition> loadAll() {
        return loadDirectory("better_ore_geology", OreDefinition.class).stream()
                .peek(OreDefinition::validate)
                .toList();
    }

    static <T> List<T> loadDirectory(String directoryName, Class<T> type) {
        Path directory = modDataDirectory(directoryName);
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> readJson(path, type))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load JSON directory " + directory, exception);
        }
    }

    private static <T> T readJson(Path path, Class<T> type) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            T parsed = GSON.fromJson(reader, type);
            if (parsed == null) {
                throw new IllegalStateException("Parsed null JSON object from " + path);
            }
            return parsed;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }

    static Path modDataDirectory(String directoryName) {
        var modFile = ModList.get().getModFileById(RealisticOresMod.MOD_ID);
        if (modFile == null) {
            throw new IllegalStateException("Mod file not found for " + RealisticOresMod.MOD_ID);
        }

        Path directory = modFile.getFile().findResource("data", RealisticOresMod.MOD_ID, directoryName);
        if (!Files.exists(directory)) {
            throw new IllegalStateException("Missing resource directory " + directory);
        }
        return directory;
    }
}
