import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Reproduces the approved standalone-first deposit textures from transparent ImageGen masters. */
public final class GenerateDepositTextures {
    private static final int SIZE = 16;
    private static final List<String> FAMILIES = List.of(
            "coal_measures", "ironstone", "copper_bloom", "tin_quartz",
            "brassroot", "evaporite_beds", "hotstone", "black_shale");
    private static final List<String> FACES = List.of("north", "east", "south", "west", "up", "down");

    private GenerateDepositTextures() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        if (args.length == 1 && args[0].equals("--validate-masters")) {
            validateMasterSuite(root);
            return;
        }
        if (args.length == 5 && args[0].equals("--preview")) {
            preview(root, args[1], Integer.parseInt(args[2]), Path.of(args[3]), Path.of(args[4]));
            return;
        }
        if (args.length != 1 || !(args[0].equals("--write") || args[0].equals("--check"))) {
            throw new IllegalArgumentException(
                    "usage: --write | --check | --validate-masters"
                            + " | --preview FAMILY VARIANT MASTER OUTPUT_DIRECTORY");
        }
        reproduce(root, args[0].equals("--write"));
    }

    private static void reproduce(Path root, boolean write) throws IOException {
        Path output = root.resolve("src/main/resources/assets/better_ore_geology/textures/block");
        HostTextures hosts = hostTextures(root);
        int written = 0;
        for (String family : FAMILIES) {
            for (int variant = 0; variant < 3; variant++) {
                BufferedImage overlay = reduce(validateMaster(master(root, family, variant)));
                for (String host : List.of("stone", "deepslate")) {
                    for (String face : FACES) {
                        BufferedImage expected = composite(hosts.forFace(host, face), overlay);
                        String prefix = host.equals("stone") ? "" : "deepslate_";
                        Path path = output.resolve(prefix + family + "_" + variant + "_" + face + ".png");
                        if (write) ImageIO.write(expected, "png", path.toFile());
                        else assertEqual(expected, ImageIO.read(path.toFile()), path);
                        written++;
                    }
                }
            }
        }
        System.out.println((write ? "wrote" : "verified") + " " + written
                + " standalone-first deposit faces from 24 approved alpha masters");
    }

    private static void preview(
            Path root, String family, int variant, Path master, Path output
    ) throws IOException {
        if (!FAMILIES.contains(family)) throw new IOException("unknown family " + family);
        if (variant < 0 || variant > 2) throw new IOException("variant must be 0, 1, or 2");
        BufferedImage overlay = reduce(validateMaster(master));
        HostTextures hosts = hostTextures(root);
        Files.createDirectories(output);
        for (String host : List.of("stone", "deepslate")) {
            for (String face : FACES) {
                String prefix = host.equals("stone") ? "" : "deepslate_";
                Path path = output.resolve(prefix + family + "_" + variant + "_" + face + ".png");
                ImageIO.write(composite(hosts.forFace(host, face), overlay), "png", path.toFile());
            }
        }
        System.out.println("previewed " + family + " variant " + variant + " with "
                + visiblePixels(overlay) + "/256 visible overlay pixels");
    }

    private static void validateMasterSuite(Path root) throws IOException {
        int count = 0;
        List<String> fingerprints = new ArrayList<>();
        for (String family : FAMILIES) for (int variant = 0; variant < 3; variant++) {
            BufferedImage overlay = reduce(validateMaster(master(root, family, variant)));
            String fingerprint = fingerprint(overlay);
            if (fingerprints.contains(fingerprint)) {
                throw new IOException(family + " variant " + variant + " duplicates another texture");
            }
            fingerprints.add(fingerprint);
            count++;
        }
        System.out.println("validated " + count + " standalone transparent masters");
    }

    private static BufferedImage validateMaster(Path path) throws IOException {
        BufferedImage source = ImageIO.read(path.toFile());
        if (source == null) throw new IOException("invalid master " + path);
        if (source.getWidth() != source.getHeight() || source.getWidth() < 1024) {
            throw new IOException("master must be square and at least 1024px: " + path);
        }
        if (!source.getColorModel().hasAlpha()) throw new IOException("master lacks alpha: " + path);
        long pixels = (long) source.getWidth() * source.getHeight();
        long clear = 0, solid = 0, perimeter = 0, perimeterSolid = 0;
        int inset = Math.max(4, source.getWidth() / 160);
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int alpha = source.getRGB(x, y) >>> 24;
            if (alpha <= 16) clear++;
            if (alpha >= 224) solid++;
            if (x < inset || y < inset || x >= source.getWidth() - inset
                    || y >= source.getHeight() - inset) {
                perimeter++;
                if (alpha >= 224) perimeterSolid++;
            }
        }
        if (clear < pixels * 35 / 100) throw new IOException("master needs genuine open alpha: " + path);
        if (solid < pixels * 8 / 100) throw new IOException("master has too little solid mineral: " + path);
        if (perimeterSolid > perimeter * 70 / 100) {
            throw new IOException("master perimeter resembles an opaque matte: " + path);
        }
        BufferedImage reduced = reduce(source);
        int visible = visiblePixels(reduced);
        if (visible < 50 || visible > 120) {
            throw new IOException("master reduces to " + visible
                    + " visible pixels; expected 50..120: " + path);
        }
        int touchedEdges = 0;
        for (int edge = 0; edge < 4; edge++) if (edgePixels(reduced, edge) > 0) touchedEdges++;
        if (touchedEdges < 2) throw new IOException("master must cross at least two block edges: " + path);
        return source;
    }

    /** Point-samples generated RGBA once; no cleanup, quantization, or morphology edit. */
    private static BufferedImage reduce(BufferedImage source) {
        BufferedImage result = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            int sourceY = (int) (((long) (2 * y + 1) * source.getHeight()) / (2L * SIZE));
            for (int x = 0; x < SIZE; x++) {
                int sourceX = (int) (((long) (2 * x + 1) * source.getWidth()) / (2L * SIZE));
                result.setRGB(x, y, source.getRGB(sourceX, sourceY));
            }
        }
        return result;
    }

    private static BufferedImage composite(BufferedImage host, BufferedImage overlay) {
        BufferedImage result = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int background = host.getRGB(x, y);
            int foreground = overlay.getRGB(x, y);
            int alpha = foreground >>> 24;
            int inverse = 255 - alpha;
            int red = blend((foreground >>> 16) & 255, (background >>> 16) & 255, alpha, inverse);
            int green = blend((foreground >>> 8) & 255, (background >>> 8) & 255, alpha, inverse);
            int blue = blend(foreground & 255, background & 255, alpha, inverse);
            result.setRGB(x, y, 0xff000000 | red << 16 | green << 8 | blue);
        }
        return result;
    }

    private static int blend(int foreground, int background, int alpha, int inverse) {
        // GraphicsMagick's accepted review composites use truncating 8-bit SrcOver arithmetic.
        return (foreground * alpha + background * inverse) / 255;
    }

    private static HostTextures hostTextures(Path root) throws IOException {
        Path directory = root.resolve("art/host-textures/minecraft-1.20.1");
        return new HostTextures(readHost(directory.resolve("stone.png")),
                readHost(directory.resolve("deepslate.png")), readHost(directory.resolve("deepslate_top.png")));
    }

    private static BufferedImage readHost(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null || image.getWidth() != SIZE || image.getHeight() != SIZE) {
            throw new IOException("host texture must be 16x16: " + path);
        }
        return image;
    }

    private static Path master(Path root, String family, int variant) {
        return root.resolve("art/block-masters/geology-v7").resolve(family)
                .resolve("variant_" + variant + ".png");
    }

    private static int visiblePixels(BufferedImage image) {
        int result = 0;
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            if ((image.getRGB(x, y) >>> 24) >= 128) result++;
        }
        return result;
    }

    private static int edgePixels(BufferedImage image, int edge) {
        int result = 0;
        for (int offset = 0; offset < SIZE; offset++) {
            int x = edge == 0 ? 0 : edge == 1 ? SIZE - 1 : offset;
            int y = edge == 2 ? 0 : edge == 3 ? SIZE - 1 : offset;
            if ((image.getRGB(x, y) >>> 24) >= 128) result++;
        }
        return result;
    }

    private static String fingerprint(BufferedImage image) {
        StringBuilder result = new StringBuilder(SIZE * SIZE);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            result.append((image.getRGB(x, y) >>> 24) >= 128 ? '1' : '0');
        }
        return result.toString();
    }

    private static void assertEqual(BufferedImage expected, BufferedImage actual, Path path) throws IOException {
        if (actual == null || actual.getWidth() != SIZE || actual.getHeight() != SIZE) {
            throw new IOException("invalid texture " + path);
        }
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            if (expected.getRGB(x, y) != actual.getRGB(x, y)) {
                throw new IOException("generated texture differs at " + x + "," + y + ": " + path);
            }
        }
    }

    private record HostTextures(BufferedImage stone, BufferedImage deepslate, BufferedImage deepslateTop) {
        BufferedImage forFace(String host, String face) {
            if (host.equals("stone")) return stone;
            return face.equals("up") || face.equals("down") ? deepslateTop : deepslate;
        }
    }
}
