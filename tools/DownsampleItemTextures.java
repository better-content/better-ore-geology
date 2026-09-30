import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Builds curated 16x16 processing-item sprites from committed ImageGen masters. */
public final class DownsampleItemTextures {
    private static final int MASTER_SIZE = 1024;
    private static final int RUNTIME_SIZE = 16;
    private static final Pattern FAMILY_ROW = Pattern.compile(
            "\\\"([^\\\"]+)\\\"\\s*:\\s*\\{\\s*\\\"morphology\\\"\\s*:\\s*\\\"[^\\\"]+\\\"\\s*,\\s*\\\"palette\\\"\\s*:\\s*\\[([^]]+)]\\s*}");
    private static final Pattern CONCENTRATE_ROW = Pattern.compile(
            "\\\"([^\\\"]+)\\\"\\s*:\\s*\\{\\s*\\\"palette\\\"\\s*:\\s*\\[([^]]+)]\\s*}");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    private record Form(String directory, String prefix, String suffix, int x, int y, int width, int height,
                        int minimumPixels, int maximumPixels) {}
    private record Bounds(int left, int top, int right, int bottom) {
        int width() { return right - left + 1; }
        int height() { return bottom - top + 1; }
    }
    private record Pixel(int x, int y, int alpha, double distanceFromCenter) {}
    private record FillPixel(int x, int y, int color, int neighbors, double distanceFromCenter) {}

    private static final Form SMALL = new Form("small_chunks", "small_ore_chunk_", "", 4, 5, 8, 8, 16, 30);
    private static final Form FULL = new Form("ore_chunks", "ore_chunk_", "", 2, 4, 12, 10, 42, 70);
    private static final Form CRUSHED = new Form("crushed_feeds", "crushed_", "", 3, 6, 10, 6, 30, 40);
    private static final Form CONCENTRATE = new Form("concentrates", "", "_concentrate", 3, 7, 10, 6, 28, 42);

    private DownsampleItemTextures() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        if (args.length == 4 && args[0].equals("--import")) {
            Form form = form(args[1]);
            importMaster(root, form, args[2], Path.of(args[3]));
            return;
        }
        if (args.length != 1 || !(args[0].equals("--write") || args[0].equals("--check"))) {
            throw new IllegalArgumentException("usage: --write | --check | --import <kind> <id> <candidate.png>");
        }
        boolean write = args[0].equals("--write");
        Map<String, int[]> families = readPalettes(root.resolve("tools/ore_art_manifest.json"), FAMILY_ROW);
        Map<String, int[]> concentrates = readPalettes(root.resolve("tools/concentrate_art_manifest.json"), CONCENTRATE_ROW);
        process(root, SMALL, families, write);
        process(root, FULL, families, write);
        process(root, CRUSHED, families, write);
        process(root, CONCENTRATE, concentrates, write);
    }

    private static Form form(String name) {
        return switch (name) {
            case "small_chunks" -> SMALL;
            case "ore_chunks" -> FULL;
            case "crushed_feeds" -> CRUSHED;
            case "concentrates" -> CONCENTRATE;
            default -> throw new IllegalArgumentException("unknown form " + name);
        };
    }

    private static void importMaster(Path root, Form form, String id, Path candidatePath) throws IOException {
        BufferedImage candidate = read(candidatePath);
        if (!candidate.getColorModel().hasAlpha()) {
            throw new IOException("candidate has no real alpha channel: " + candidatePath);
        }
        Bounds bounds = visibleBounds(candidate, 48);
        BufferedImage master = new BufferedImage(MASTER_SIZE, MASTER_SIZE, BufferedImage.TYPE_INT_ARGB);
        double scale = Math.min(896.0 / bounds.width(), 896.0 / bounds.height());
        int width = Math.max(1, (int) Math.round(bounds.width() * scale));
        int height = Math.max(1, (int) Math.round(bounds.height() * scale));
        int x = (MASTER_SIZE - width) / 2;
        int y = (MASTER_SIZE - height) / 2;
        Graphics2D graphics = master.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(candidate, x, y, x + width, y + height,
                bounds.left(), bounds.top(), bounds.right() + 1, bounds.bottom() + 1, null);
        graphics.dispose();
        Path output = root.resolve("art/item-masters").resolve(form.directory()).resolve(id + ".png");
        Files.createDirectories(output.getParent());
        ImageIO.write(master, "png", output.toFile());
        System.out.println("imported " + output);
    }

    private static void process(Path root, Form form, Map<String, int[]> palettes, boolean write) throws IOException {
        Path masterDirectory = root.resolve("art/item-masters").resolve(form.directory());
        Path runtimeDirectory = root.resolve("src/main/resources/assets/better_ore_geology/textures/item");
        Path previewDirectory = root.resolve("build/texture-previews").resolve(form.directory());
        if (write) {
            try (var paths = Files.list(masterDirectory)) {
                for (Path path : paths.filter(candidate -> candidate.getFileName().toString().endsWith(".png")).toList()) {
                    String id = path.getFileName().toString().replaceFirst("\\.png$", "");
                    if (!palettes.containsKey(id)) Files.delete(path);
                }
            }
            try (var paths = Files.list(runtimeDirectory)) {
                for (Path path : paths.filter(candidate -> candidate.getFileName().toString().endsWith(".png")).toList()) {
                    String filename = path.getFileName().toString();
                    if (!filename.startsWith(form.prefix()) || !filename.endsWith(form.suffix() + ".png")) continue;
                    String id = filename.substring(form.prefix().length(), filename.length() - form.suffix().length() - 4);
                    if (!palettes.containsKey(id)) Files.delete(path);
                }
            }
        }
        Set<Integer> distinctSprites = new HashSet<>();
        for (Map.Entry<String, int[]> entry : palettes.entrySet()) {
            String id = entry.getKey();
            Path masterPath = masterDirectory.resolve(id + ".png");
            BufferedImage master = read(masterPath);
            if (master.getWidth() != MASTER_SIZE || master.getHeight() != MASTER_SIZE || !master.getColorModel().hasAlpha()) {
                throw new IOException("master must be 1024x1024 RGBA: " + masterPath);
            }
            BufferedImage sprite = reduce(master, form, entry.getValue());
            Path runtimePath = runtimeDirectory.resolve(form.prefix() + id + form.suffix() + ".png");
            int[] pixels = sprite.getRGB(0, 0, RUNTIME_SIZE, RUNTIME_SIZE, null, 0, RUNTIME_SIZE);
            if (!distinctSprites.add(Arrays.hashCode(pixels))) {
                throw new IOException("duplicate generated sprite in " + form.directory() + ": " + runtimePath);
            }
            if (write) {
                ImageIO.write(sprite, "png", runtimePath.toFile());
                Files.createDirectories(previewDirectory);
                ImageIO.write(nearestPreview(sprite), "png", previewDirectory.resolve(id + ".png").toFile());
            } else {
                BufferedImage committed = read(runtimePath);
                assertPixelsEqual(sprite, committed, runtimePath);
            }
            validate(sprite, form, entry.getValue(), runtimePath);
        }
        System.out.printf("%s %d %s%n", write ? "wrote" : "verified", palettes.size(), form.directory());
    }

    private static BufferedImage reduce(BufferedImage master, Form form, int[] palette) throws IOException {
        Bounds bounds = visibleBounds(master, 24);
        double scale = Math.min((double) form.width() / bounds.width(), (double) form.height() / bounds.height());
        int width = Math.max(1, Math.min(form.width(), (int) Math.round(bounds.width() * scale)));
        int height = Math.max(1, Math.min(form.height(), (int) Math.round(bounds.height() * scale)));
        BufferedImage sampled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sampled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(master, 0, 0, width, height,
                bounds.left(), bounds.top(), bounds.right() + 1, bounds.bottom() + 1, null);
        graphics.dispose();

        int threshold = chooseThreshold(sampled, form.minimumPixels(), form.maximumPixels());
        BufferedImage sprite = new BufferedImage(RUNTIME_SIZE, RUNTIME_SIZE, BufferedImage.TYPE_INT_ARGB);
        List<Pixel> visiblePixels = new ArrayList<>();
        int offsetX = form.x() + (form.width() - width) / 2;
        int offsetY = form.y() + form.height() - height;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = sampled.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha >= threshold) {
                    int outputX = offsetX + x;
                    int outputY = offsetY + y;
                    sprite.setRGB(outputX, outputY, 0xff000000 | nearestColor(argb, palette));
                    double dx = outputX - (form.x() + (form.width() - 1) / 2.0);
                    double dy = outputY - (form.y() + (form.height() - 1) / 2.0);
                    visiblePixels.add(new Pixel(outputX, outputY, alpha, dx * dx + dy * dy));
                }
            }
        }
        visiblePixels.sort(Comparator.comparingInt(Pixel::alpha)
                .thenComparing(Comparator.comparingDouble(Pixel::distanceFromCenter).reversed()));
        for (int index = 0; index < visiblePixels.size() - form.maximumPixels(); index++) {
            Pixel pixel = visiblePixels.get(index);
            sprite.setRGB(pixel.x(), pixel.y(), 0);
        }
        fillToMinimum(sprite, form);
        if (form == FULL) splitFullChunk(sprite, form);
        return sprite;
    }

    /** A full inventory chunk is several broken fragments, with a clear central fracture. */
    private static void splitFullChunk(BufferedImage sprite, Form form) {
        int fractureX = form.x() + form.width() / 2;
        for (int y = form.y(); y < form.y() + form.height(); y++) sprite.setRGB(fractureX, y, 0);
    }

    private static void fillToMinimum(BufferedImage sprite, Form form) throws IOException {
        while (visiblePixelCount(sprite) < form.minimumPixels()) {
            FillPixel best = null;
            for (int y = form.y(); y < form.y() + form.height(); y++) {
                for (int x = form.x(); x < form.x() + form.width(); x++) {
                    if ((sprite.getRGB(x, y) >>> 24) != 0) continue;
                    int neighbors = 0;
                    int color = 0;
                    for (int offsetY = -1; offsetY <= 1; offsetY++) {
                        for (int offsetX = -1; offsetX <= 1; offsetX++) {
                            if (offsetX == 0 && offsetY == 0) continue;
                            int adjacentX = x + offsetX, adjacentY = y + offsetY;
                            if (adjacentX < form.x() || adjacentX >= form.x() + form.width()
                                    || adjacentY < form.y() || adjacentY >= form.y() + form.height()) continue;
                            int adjacent = sprite.getRGB(adjacentX, adjacentY);
                            if ((adjacent >>> 24) == 255) {
                                neighbors++;
                                if (color == 0) color = adjacent;
                            }
                        }
                    }
                    if (neighbors == 0) continue;
                    double dx = x - (form.x() + (form.width() - 1) / 2.0);
                    double dy = y - (form.y() + (form.height() - 1) / 2.0);
                    FillPixel candidate = new FillPixel(x, y, color, neighbors, dx * dx + dy * dy);
                    if (best == null || candidate.neighbors() > best.neighbors()
                            || (candidate.neighbors() == best.neighbors()
                            && candidate.distanceFromCenter() < best.distanceFromCenter())) best = candidate;
                }
            }
            if (best == null) throw new IOException("cannot fill sprite to minimum pixel count");
            sprite.setRGB(best.x(), best.y(), best.color());
        }
    }

    private static int visiblePixelCount(BufferedImage image) {
        int visible = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getRGB(x, y) >>> 24) == 255) visible++;
        return visible;
    }

    private static int chooseThreshold(BufferedImage image, int minimum, int maximum) {
        int target = (minimum + maximum) / 2;
        int bestThreshold = 1;
        int bestDistance = Integer.MAX_VALUE;
        for (int threshold = 1; threshold <= 254; threshold++) {
            int visible = 0;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                if ((image.getRGB(x, y) >>> 24) >= threshold) visible++;
            int distance = visible < minimum ? minimum - visible : visible > maximum ? visible - maximum : Math.abs(visible - target);
            if (distance < bestDistance) { bestDistance = distance; bestThreshold = threshold; }
        }
        return bestThreshold;
    }

    private static void validate(BufferedImage image, Form form, int[] palette, Path path) throws IOException {
        int visible = 0;
        List<Integer> allowed = Arrays.stream(palette).boxed().toList();
        for (int y = 0; y < RUNTIME_SIZE; y++) for (int x = 0; x < RUNTIME_SIZE; x++) {
            int argb = image.getRGB(x, y);
            int alpha = argb >>> 24;
            if (alpha != 0 && alpha != 255) throw new IOException("partial alpha in " + path);
            if (alpha == 255) {
                visible++;
                if (x < form.x() || x >= form.x() + form.width() || y < form.y() || y >= form.y() + form.height())
                    throw new IOException("pixel outside form envelope in " + path);
                if (!allowed.contains(argb & 0xffffff)) throw new IOException("off-palette pixel in " + path);
            }
        }
        if (visible < form.minimumPixels() || visible > form.maximumPixels())
            throw new IOException(path + " has " + visible + " visible pixels");
    }

    private static Map<String, int[]> readPalettes(Path path, Pattern rowPattern) throws IOException {
        Map<String, int[]> result = new LinkedHashMap<>();
        Matcher rows = rowPattern.matcher(Files.readString(path));
        while (rows.find()) {
            Matcher colors = COLOR.matcher(rows.group(2));
            List<Integer> palette = new ArrayList<>();
            while (colors.find()) palette.add(Integer.parseInt(colors.group().substring(1), 16));
            if (palette.size() != 5) throw new IOException("expected five colors for " + rows.group(1));
            result.put(rows.group(1), palette.stream().mapToInt(Integer::intValue).toArray());
        }
        if (result.isEmpty()) throw new IOException("no palettes in " + path);
        return result;
    }

    private static Bounds visibleBounds(BufferedImage image, int alphaThreshold) throws IOException {
        int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, y) >>> 24) >= alphaThreshold) {
                left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y);
            }
        }
        if (right < left) throw new IOException("image has no visible alpha content");
        return new Bounds(left, top, right, bottom);
    }

    private static int nearestColor(int argb, int[] palette) {
        double[] source = lab((argb >>> 16) & 255, (argb >>> 8) & 255, argb & 255);
        int best = palette[0];
        double distance = Double.MAX_VALUE;
        for (int color : palette) {
            double[] candidate = lab((color >>> 16) & 255, (color >>> 8) & 255, color & 255);
            double current = Math.pow(source[0] - candidate[0], 2)
                    + Math.pow(source[1] - candidate[1], 2) + Math.pow(source[2] - candidate[2], 2);
            if (current < distance) { distance = current; best = color; }
        }
        return best;
    }

    private static double[] lab(int red, int green, int blue) {
        double r = pivotRgb(red / 255.0), g = pivotRgb(green / 255.0), b = pivotRgb(blue / 255.0);
        double x = (r * .4124 + g * .3576 + b * .1805) / .95047;
        double y = (r * .2126 + g * .7152 + b * .0722);
        double z = (r * .0193 + g * .1192 + b * .9505) / 1.08883;
        x = pivotXyz(x); y = pivotXyz(y); z = pivotXyz(z);
        return new double[] {116 * y - 16, 500 * (x - y), 200 * (y - z)};
    }

    private static double pivotRgb(double value) { return value > .04045 ? Math.pow((value + .055) / 1.055, 2.4) : value / 12.92; }
    private static double pivotXyz(double value) { return value > .008856 ? Math.cbrt(value) : 7.787 * value + 16.0 / 116.0; }

    private static BufferedImage nearestPreview(BufferedImage source) {
        BufferedImage output = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(source, 0, 0, 256, 256, null);
        graphics.dispose();
        return output;
    }

    private static BufferedImage read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("missing image " + path);
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("unsupported image " + path);
        return image;
    }

    private static void assertPixelsEqual(BufferedImage expected, BufferedImage actual, Path path) throws IOException {
        if (actual.getWidth() != expected.getWidth() || actual.getHeight() != expected.getHeight())
            throw new IOException("wrong runtime dimensions " + path);
        for (int y = 0; y < expected.getHeight(); y++) for (int x = 0; x < expected.getWidth(); x++)
            if (expected.getRGB(x, y) != actual.getRGB(x, y)) throw new IOException("stale runtime sprite " + path);
    }
}
