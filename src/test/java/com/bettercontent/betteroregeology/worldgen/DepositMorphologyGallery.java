package com.bettercontent.betteroregeology.worldgen;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;

/** Deterministic code-native review renderer; not shipped in the runtime JAR. */
public final class DepositMorphologyGallery {
    private static final int WIDTH = 1500;
    private static final int HEIGHT = 940;
    private static final Color BACKGROUND = new Color(22, 25, 29);
    private static final Color PANEL = new Color(32, 37, 43);
    private static final Color GRID = new Color(61, 68, 76);
    private static final Map<String, Color> COLORS = Map.of(
            "coal_measures", new Color(105, 111, 119),
            "ironstone", new Color(194, 105, 55),
            "copper_bloom", new Color(70, 181, 139),
            "tin_quartz", new Color(210, 208, 191),
            "brassroot", new Color(190, 163, 66),
            "evaporite_beds", new Color(212, 197, 174),
            "hotstone", new Color(224, 69, 49),
            "black_shale", new Color(122, 84, 160));

    private DepositMorphologyGallery() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new IllegalArgumentException("expected output directory");
        Path output = Path.of(arguments[0]);
        Files.createDirectories(output);
        JsonObject manifest = JsonParser.parseString(Files.readString(Path.of("tools/geological_worldgen.json")))
                .getAsJsonObject();
        Map<String, Object> metrics = new LinkedHashMap<>();
        int familyIndex = 0;
        for (String family : manifest.keySet()) {
            JsonObject definition = manifest.getAsJsonObject(family);
            DepositMorphology morphology = morphology(definition.get("morphology").getAsString());
            metrics.put(family, metrics(family, morphology, definition));
            for (int archetype = 0; archetype < morphology.archetypes().size(); archetype++) {
                String file = "%02d-%s--%02d-%s.png".formatted(familyIndex + 1, family, archetype + 1,
                        slug(morphology.archetypes().get(archetype)));
                render(output.resolve(file), family, morphology, archetype, definition);
            }
            familyIndex++;
        }
        Files.writeString(output.resolve("metrics.json"), new GsonBuilder().setPrettyPrinting().create().toJson(metrics) + "\n");
    }

    private static void render(Path path, String family, DepositMorphology morphology, int archetype,
                               JsonObject definition) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
        graphics.setColor(Color.WHITE);
        graphics.drawString(title(family) + " / " + (archetype + 1) + ". " + morphology.archetypes().get(archetype), 42, 48);
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        graphics.setColor(new Color(175, 183, 191));
        graphics.drawString(morphology.getSerializedName() + "  |  deterministic samples at min / target / max body budget", 43, 76);

        String[] classes = {"HOME — dominant Y-band body", "ECHO — distant-band body"};
        for (int row = 0; row < 2; row++) {
            int target = definition.get((row == 0 ? "home" : "echo") + "_budget").getAsInt();
            int spread = definition.get((row == 0 ? "home" : "echo") + "_spread").getAsInt();
            DepositClass depositClass = row == 0 ? DepositClass.HOME : DepositClass.ECHO;
            graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 17));
            graphics.setColor(COLORS.get(family));
            graphics.drawString(classes[row] + "  Y " + band(definition, row == 0 ? "home" : "echo"), 43, 113 + row * 407);
            int[] budgets = {target - spread, target, target + spread};
            for (int column = 0; column < 3; column++) {
                int x = 38 + column * 487;
                int y = 127 + row * 407;
                graphics.setColor(PANEL);
                graphics.fillRect(x, y, 464, 374);
                graphics.setColor(GRID);
                graphics.drawRect(x, y, 464, 374);
                DepositSampleContext context = new DepositSampleContext(0x6e6f646573L,
                        new BlockPos(family.hashCode() + column * 137, target + row * 31, archetype * 211),
                        0x9e3779b97f4a7c15L * (1L + column + row * 5L + archetype * 17L), budgets[column], depositClass);
                List<BlockPos> shape = morphology.sampleArchetype(context, archetype);
                graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
                graphics.setColor(Color.WHITE);
                graphics.drawString((column == 0 ? "MIN" : column == 1 ? "TARGET" : "MAX") + "  "
                        + budgets[column] + " blocks  span " + spans(shape), x + 14, y + 23);
                drawIsometric(graphics, shape, x + 232, y + 173, COLORS.get(family));
                drawProjection(graphics, shape, x + 16, y + 273, 132, 82, 'x', 'y', "FRONT x/y", COLORS.get(family));
                drawProjection(graphics, shape, x + 166, y + 273, 132, 82, 'x', 'z', "TOP x/z", COLORS.get(family));
                drawProjection(graphics, shape, x + 316, y + 273, 132, 82, 'z', 'y', "SIDE z/y", COLORS.get(family));
            }
        }
        graphics.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void drawIsometric(Graphics2D graphics, List<BlockPos> points, int centerX, int centerY, Color color) {
        List<BlockPos> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingInt((BlockPos p) -> p.getX() + p.getZ() + p.getY())
                .thenComparingInt(BlockPos::getY));
        int isoMinX = points.stream().mapToInt(p -> p.getX() - p.getZ()).min().orElse(0);
        int isoMaxX = points.stream().mapToInt(p -> p.getX() - p.getZ()).max().orElse(0);
        int isoMinY = points.stream().mapToInt(p -> p.getX() + p.getZ() - p.getY() * 2).min().orElse(0);
        int isoMaxY = points.stream().mapToInt(p -> p.getX() + p.getZ() - p.getY() * 2).max().orElse(0);
        int unit = Math.max(7, Math.min(14, Math.min(390 / Math.max(1, isoMaxX - isoMinX + 2),
                400 / Math.max(1, isoMaxY - isoMinY + 3))));
        int centerIsoX = (isoMinX + isoMaxX) * unit / 2;
        int centerIsoY = (isoMinY + isoMaxY) * unit / 4;
        for (BlockPos point : sorted) {
            int x = centerX + (point.getX() - point.getZ()) * unit - centerIsoX;
            int y = centerY + (point.getX() + point.getZ()) * unit / 2 - point.getY() * unit - centerIsoY;
            int half = Math.max(3, unit / 2);
            Polygon top = polygon(x, y - unit, x + unit, y - half, x, y, x - unit, y - half);
            Polygon left = polygon(x - unit, y - half, x, y, x, y + unit + 1, x - unit, y + half + 1);
            Polygon right = polygon(x + unit, y - half, x, y, x, y + unit + 1, x + unit, y + half + 1);
            graphics.setColor(brighten(color, 1.18F)); graphics.fillPolygon(top);
            graphics.setColor(brighten(color, 0.76F)); graphics.fillPolygon(left);
            graphics.setColor(brighten(color, 0.92F)); graphics.fillPolygon(right);
            graphics.setColor(new Color(18, 20, 23)); graphics.drawPolygon(top); graphics.drawPolygon(left); graphics.drawPolygon(right);
        }
    }

    private static void drawProjection(Graphics2D graphics, List<BlockPos> points, int x, int y, int width, int height,
                                       char horizontal, char vertical, String label, Color color) {
        graphics.setColor(new Color(25, 29, 34)); graphics.fillRect(x, y, width, height);
        graphics.setColor(GRID); graphics.drawRect(x, y, width, height);
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11)); graphics.setColor(new Color(173, 180, 187));
        graphics.drawString(label, x + 5, y + 12);
        int minH = points.stream().mapToInt(p -> coordinate(p, horizontal)).min().orElse(0);
        int maxH = points.stream().mapToInt(p -> coordinate(p, horizontal)).max().orElse(0);
        int minV = points.stream().mapToInt(p -> coordinate(p, vertical)).min().orElse(0);
        int maxV = points.stream().mapToInt(p -> coordinate(p, vertical)).max().orElse(0);
        double scale = Math.min((width - 14.0) / Math.max(1, maxH - minH + 1), (height - 22.0) / Math.max(1, maxV - minV + 1));
        scale = Math.max(2.0, Math.min(6.0, scale));
        Set<Long> cells = new HashSet<>();
        for (BlockPos point : points) cells.add((((long) coordinate(point, horizontal)) << 32) ^ (coordinate(point, vertical) & 0xffffffffL));
        graphics.setColor(color);
        for (long cell : cells) {
            int h = (int) (cell >> 32), v = (int) cell;
            int px = x + 7 + (int) Math.round((h - minH) * scale);
            int py = y + height - 7 - (int) Math.round((v - minV) * scale);
            graphics.fillRect(px, py, Math.max(2, (int) scale), Math.max(2, (int) scale));
        }
    }

    private static Map<String, Object> metrics(String family, DepositMorphology morphology, JsonObject definition) {
        Set<Set<BlockPos>> unique = new HashSet<>();
        int[] archetypes = new int[4];
        int minX = Integer.MAX_VALUE, maxX = 0, minY = Integer.MAX_VALUE, maxY = 0, minZ = Integer.MAX_VALUE, maxZ = 0;
        for (int index = 0; index < 256; index++) {
            boolean echo = index % 4 == 0;
            String prefix = echo ? "echo" : "home";
            int target = definition.get(prefix + "_budget").getAsInt();
            int spread = definition.get(prefix + "_spread").getAsInt();
            int budget = target - spread + Math.floorMod(index * 17, spread * 2 + 1);
            DepositSampleContext context = new DepositSampleContext(0x6e6f646573L,
                    new BlockPos(index * 37 - 4000, index % 97 - 48, index * 53 - 6000),
                    index * 0x9e3779b97f4a7c15L, budget, echo ? DepositClass.ECHO : DepositClass.HOME);
            int archetype = morphology.selectArchetype(context);
            archetypes[archetype]++;
            List<BlockPos> shape = morphology.sampleArchetype(context, archetype);
            unique.add(Set.copyOf(shape));
            minX = Math.min(minX, span(shape, 'x')); maxX = Math.max(maxX, span(shape, 'x'));
            minY = Math.min(minY, span(shape, 'y')); maxY = Math.max(maxY, span(shape, 'y'));
            minZ = Math.min(minZ, span(shape, 'z')); maxZ = Math.max(maxZ, span(shape, 'z'));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("morphology", morphology.getSerializedName());
        result.put("samples", 256);
        result.put("unique_silhouettes", unique.size());
        result.put("archetype_counts", List.of(archetypes[0], archetypes[1], archetypes[2], archetypes[3]));
        result.put("span_x_min_max", List.of(minX, maxX)); result.put("span_y_min_max", List.of(minY, maxY));
        result.put("span_z_min_max", List.of(minZ, maxZ));
        result.put("home_y", List.of(definition.getAsJsonArray("home").get(0).getAsInt(), definition.getAsJsonArray("home").get(1).getAsInt()));
        result.put("echo_y", List.of(definition.getAsJsonArray("echo").get(0).getAsInt(), definition.getAsJsonArray("echo").get(1).getAsInt()));
        return result;
    }

    private static DepositMorphology morphology(String name) {
        for (DepositMorphology value : DepositMorphology.values()) if (value.getSerializedName().equals(name)) return value;
        throw new IllegalArgumentException(name);
    }

    private static int coordinate(BlockPos point, char axis) {
        return switch (axis) { case 'x' -> point.getX(); case 'y' -> point.getY(); case 'z' -> point.getZ(); default -> 0; };
    }
    private static int span(List<BlockPos> points, char axis) {
        return points.stream().mapToInt(p -> coordinate(p, axis)).max().orElse(0)
                - points.stream().mapToInt(p -> coordinate(p, axis)).min().orElse(0) + 1;
    }
    private static String spans(List<BlockPos> points) { return span(points, 'x') + "x" + span(points, 'y') + "x" + span(points, 'z'); }
    private static String band(JsonObject definition, String key) { return definition.getAsJsonArray(key).get(0) + ".." + definition.getAsJsonArray(key).get(1); }
    private static String title(String value) { return java.util.Arrays.stream(value.split("_")).map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1)).reduce((a, b) -> a + " " + b).orElse(value); }
    private static String slug(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""); }
    private static Polygon polygon(int... values) { Polygon polygon = new Polygon(); for (int i = 0; i < values.length; i += 2) polygon.addPoint(values[i], values[i + 1]); return polygon; }
    private static Color brighten(Color color, float factor) { return new Color(Math.min(255, (int) (color.getRed() * factor)), Math.min(255, (int) (color.getGreen() * factor)), Math.min(255, (int) (color.getBlue() * factor))); }
}
