package com.bettercontent.betteroregeology.compat.exposure;

/** The same truncating, opaque-host SrcOver arithmetic as the authored texture generator. */
public final class ExposureComposite {
    private ExposureComposite() {}

    public static int over(int background, int foreground) {
        int alpha = foreground >>> 24;
        int inverse = 255 - alpha;
        int red = channel(background >>> 16, foreground >>> 16, alpha, inverse);
        int green = channel(background >>> 8, foreground >>> 8, alpha, inverse);
        int blue = channel(background, foreground, alpha, inverse);
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    private static int channel(int background, int foreground, int alpha, int inverse) {
        return ((foreground & 255) * alpha + (background & 255) * inverse) / 255;
    }
}
