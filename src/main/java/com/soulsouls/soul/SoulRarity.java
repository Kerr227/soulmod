package com.soulsouls.soul;

import net.minecraft.util.Formatting;

import java.util.Locale;

/**
 * Flavour tier of a Soul. This is display-only: how often a Soul is actually
 * rolled is decided by its {@code chance} weight in the config, not by rarity.
 */
public enum SoulRarity {
    COMMON(Formatting.WHITE),
    UNCOMMON(Formatting.GREEN),
    RARE(Formatting.AQUA),
    VERY_RARE(Formatting.LIGHT_PURPLE),
    LEGENDARY(Formatting.GOLD),
    SECRET(Formatting.DARK_GRAY);

    private final Formatting formatting;

    SoulRarity(Formatting formatting) {
        this.formatting = formatting;
    }

    public Formatting formatting() {
        return this.formatting;
    }

    public String display() {
        return this.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public static SoulRarity parse(String name, SoulRarity fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
