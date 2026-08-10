package com.soulsouls.soul;

import net.minecraft.util.Formatting;

import java.util.Locale;

/**
 * How hard a Soul is to play well. Purely descriptive - it is shown in
 * {@code /souls info} and {@code /souls list} and can be changed per Soul with
 * {@code /souls settings difficulty <soul> <difficulty>}.
 */
public enum SoulDifficulty {
    EASY(Formatting.GREEN),
    NORMAL(Formatting.YELLOW),
    HARD(Formatting.GOLD),
    EXTREME(Formatting.RED),
    LEGENDARY(Formatting.LIGHT_PURPLE);

    private final Formatting formatting;

    SoulDifficulty(Formatting formatting) {
        this.formatting = formatting;
    }

    public Formatting formatting() {
        return this.formatting;
    }

    public static SoulDifficulty parse(String name, SoulDifficulty fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
