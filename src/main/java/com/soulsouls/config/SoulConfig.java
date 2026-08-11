package com.soulsouls.config;

import com.soulsouls.soul.Soul;
import com.soulsouls.soul.SoulDifficulty;
import com.soulsouls.soul.SoulRarity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The config block for a single Soul. Field names are the JSON keys, so keep them
 * snake_case and readable - server owners edit this file by hand.
 */
public class SoulConfig {
    /** A disabled Soul is never rolled and cannot be given out. */
    public boolean enabled = true;

    /** Hex colour, {@code #RRGGBB}. Used for the hearts, titles and messages. */
    public String color = "#FFFFFF";

    /**
     * The two characters drawn either side of the player's name. Leave empty to use the
     * global {@code nameplate_heart}. Souls with their own pair (Humility, Memory) fill
     * these in.
     */
    public String heart_left = "";
    public String heart_right = "";

    /** Relative weight for random assignment. 0 means "never roll this Soul". */
    public double chance = 10.0;

    /** Display-only tier: common, uncommon, rare, very_rare, legendary, secret. */
    public String rarity = "common";

    /** Display-only: EASY, NORMAL, HARD, EXTREME, LEGENDARY. */
    public String difficulty = "NORMAL";

    /** Maximum health in half-hearts. 20 = 10 hearts, 30 = 15 hearts. */
    public double max_health = 20.0;

    /** Per-ability tuning numbers. Missing keys are refilled from the code defaults on load. */
    public Map<String, Double> values = new LinkedHashMap<>();

    public static SoulConfig fromDefaults(Soul soul) {
        SoulConfig config = new SoulConfig();
        config.enabled = true;
        config.color = String.format("#%06X", soul.defaultColor());
        config.heart_left = soul.defaultHeartLeft();
        config.heart_right = soul.defaultHeartRight();
        config.chance = soul.defaultChance();
        config.rarity = soul.defaultRarity().name().toLowerCase(java.util.Locale.ROOT);
        config.difficulty = soul.defaultDifficulty().name();
        config.max_health = soul.defaultMaxHealth();
        config.values = new LinkedHashMap<>(soul.defaultValues());
        return config;
    }

    /**
     * Adds any values that exist in code but not yet in the file. This is what lets a new
     * Soul (or a new tunable on an existing Soul) show up in an old config without the
     * owner having to delete it.
     *
     * @return true if anything was added
     */
    public boolean fillMissing(Soul soul) {
        boolean changed = false;
        if (this.values == null) {
            this.values = new LinkedHashMap<>();
            changed = true;
        }
        for (Map.Entry<String, Double> entry : soul.defaultValues().entrySet()) {
            if (!this.values.containsKey(entry.getKey())) {
                this.values.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    public SoulRarity rarity(Soul soul) {
        return SoulRarity.parse(this.rarity, soul.defaultRarity());
    }

    public SoulDifficulty difficulty(Soul soul) {
        return SoulDifficulty.parse(this.difficulty, soul.defaultDifficulty());
    }
}
