package com.soulsouls.util;

import com.soulsouls.config.SoulsConfig;
import com.soulsouls.soul.Soul;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

/**
 * Text and colour helpers. Souls use full RGB colours, but a few vanilla systems
 * (scoreboard teams, and therefore name tags above the head) only understand the 16
 * {@link Formatting} colours, so {@link #nearestFormatting(int)} maps an RGB colour onto
 * the closest one.
 */
public final class SoulText {
    private SoulText() {
    }

    public static MutableText coloured(String text, int rgb) {
        return Text.literal(text).setStyle(net.minecraft.text.Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    public static MutableText coloured(String text, Soul soul, SoulsConfig config) {
        return coloured(text, config.colorOf(soul));
    }

    /** The Soul's name in its own colour, e.g. a bright red "DETERMINATION". */
    public static MutableText soulName(Soul soul, SoulsConfig config) {
        return coloured(soul.displayName(), config.colorOf(soul));
    }

    public static MutableText prefix() {
        return Text.literal("[Souls] ").formatted(Formatting.DARK_GRAY);
    }

    public static MutableText info(String text) {
        return Text.literal(text).formatted(Formatting.GRAY);
    }

    public static MutableText success(String text) {
        return Text.literal(text).formatted(Formatting.GREEN);
    }

    public static MutableText error(String text) {
        return Text.literal(text).formatted(Formatting.RED);
    }

    /** "#FF0000" for display in /souls info and /souls debug. */
    public static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /** Health in half-hearts rendered as hearts, e.g. 30.0 -> "15 hearts". */
    public static String hearts(double halfHearts) {
        double hearts = halfHearts / 2.0;
        if (Math.abs(hearts - Math.rint(hearts)) < 0.01) {
            long whole = Math.round(hearts);
            return whole + (whole == 1 ? " heart" : " hearts");
        }
        return String.format("%.1f hearts", hearts);
    }

    public static String seconds(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "READY";
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }

    /**
     * Closest vanilla chat colour to an RGB value, by squared distance in RGB space.
     * Used for scoreboard team colours, which is how names above heads get coloured for
     * vanilla clients.
     */
    public static Formatting nearestFormatting(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        Formatting best = Formatting.WHITE;
        int bestDistance = Integer.MAX_VALUE;

        for (Formatting formatting : Formatting.values()) {
            if (!formatting.isColor()) {
                continue;
            }
            Integer value = formatting.getColorValue();
            if (value == null) {
                continue;
            }
            int otherRed = (value >> 16) & 0xFF;
            int otherGreen = (value >> 8) & 0xFF;
            int otherBlue = value & 0xFF;

            int distance = (red - otherRed) * (red - otherRed)
                    + (green - otherGreen) * (green - otherGreen)
                    + (blue - otherBlue) * (blue - otherBlue);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = formatting;
            }
        }
        return best;
    }
}
