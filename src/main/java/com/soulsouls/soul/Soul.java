package com.soulsouls.soul;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The immutable definition of a Soul.
 *
 * <p>A {@code Soul} is pure data plus a list of {@link SoulAbility abilities}. Everything
 * a server owner can tune (colour, chance, difficulty, rarity, max health and the
 * per-ability numbers) is stored here only as a <em>default</em>; the live value always
 * comes from the config file, so {@code /souls reload} can change behaviour without a
 * restart.
 *
 * <h2>Adding a new Soul</h2>
 * Build one with {@link #builder(String, String)} and register it in
 * {@code com.soulsouls.souls.Souls}. Nothing else in the mod needs to change - the
 * config file, the commands, the tab-completion and the random assignment all read from
 * the registry.
 *
 * <pre>{@code
 * Soul.builder("hope", "HOPE")
 *     .color(0xFFF7A8)
 *     .rarity(SoulRarity.LEGENDARY)
 *     .difficulty(SoulDifficulty.HARD)
 *     .description("Grows stronger during thunderstorms.")
 *     .chance(2.0)
 *     .maxHealth(20.0)
 *     .ability(new StormbornAbility())
 *     .build();
 * }</pre>
 */
public final class Soul {
    private final String id;
    private final String displayName;
    private final int defaultColor;
    private final SoulRarity defaultRarity;
    private final SoulDifficulty defaultDifficulty;
    private final String description;
    private final double defaultChance;
    private final double defaultMaxHealth;
    private final List<SoulAbility> abilities;
    private final Map<String, Double> defaultValues;

    private Soul(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.defaultColor = builder.color;
        this.defaultRarity = builder.rarity;
        this.defaultDifficulty = builder.difficulty;
        this.description = builder.description;
        this.defaultChance = builder.chance;
        this.defaultMaxHealth = builder.maxHealth;
        this.abilities = List.copyOf(builder.abilities);

        // Soul-level defaults win over ability-level defaults, so a Soul can re-tune an
        // ability it shares with another Soul (Courage reuses Bravery's momentum, for example).
        Map<String, Double> values = new LinkedHashMap<>();
        for (SoulAbility ability : this.abilities) {
            values.putAll(ability.defaultValues());
        }
        values.putAll(builder.values);
        this.defaultValues = Collections.unmodifiableMap(values);
    }

    public String id() {
        return this.id;
    }

    /** Upper-case name shown in titles and {@code /souls info}, e.g. {@code DETERMINATION}. */
    public String displayName() {
        return this.displayName;
    }

    public int defaultColor() {
        return this.defaultColor;
    }

    public SoulRarity defaultRarity() {
        return this.defaultRarity;
    }

    public SoulDifficulty defaultDifficulty() {
        return this.defaultDifficulty;
    }

    public String description() {
        return this.description;
    }

    public double defaultChance() {
        return this.defaultChance;
    }

    /** Default maximum health in half-hearts (20.0 = 10 hearts). */
    public double defaultMaxHealth() {
        return this.defaultMaxHealth;
    }

    public List<SoulAbility> abilities() {
        return this.abilities;
    }

    /** Default tuning values, merged from the abilities and then the Soul's own overrides. */
    public Map<String, Double> defaultValues() {
        return this.defaultValues;
    }

    public double defaultValue(String key, double fallback) {
        return this.defaultValues.getOrDefault(key, fallback);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Soul soul && soul.id.equals(this.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return "Soul[" + this.id + "]";
    }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    public static final class Builder {
        private final String id;
        private final String displayName;
        private int color = 0xFFFFFF;
        private SoulRarity rarity = SoulRarity.COMMON;
        private SoulDifficulty difficulty = SoulDifficulty.NORMAL;
        private String description = "";
        private double chance = 10.0;
        private double maxHealth = 20.0;
        private final List<SoulAbility> abilities = new ArrayList<>();
        private final Map<String, Double> values = new LinkedHashMap<>();

        private Builder(String id, String displayName) {
            this.id = Objects.requireNonNull(id, "id").toLowerCase(java.util.Locale.ROOT);
            this.displayName = Objects.requireNonNull(displayName, "displayName");
        }

        /** @param color packed 0xRRGGBB */
        public Builder color(int color) {
            this.color = color & 0xFFFFFF;
            return this;
        }

        public Builder rarity(SoulRarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder difficulty(SoulDifficulty difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /** Relative weight used when a Soul is rolled for a new player. */
        public Builder chance(double chance) {
            this.chance = chance;
            return this;
        }

        /** @param maxHealth in half-hearts; 20.0 is vanilla (10 hearts). */
        public Builder maxHealth(double maxHealth) {
            this.maxHealth = maxHealth;
            return this;
        }

        public Builder ability(SoulAbility ability) {
            this.abilities.add(ability);
            return this;
        }

        /** Overrides (or adds) a tunable value that ends up in the config file. */
        public Builder value(String key, double value) {
            this.values.put(key, value);
            return this;
        }

        public Soul build() {
            return new Soul(this);
        }
    }
}
