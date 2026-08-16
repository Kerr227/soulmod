package com.soulsouls.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.soulsouls.SoulSouls;
import com.soulsouls.soul.Soul;
import com.soulsouls.soul.SoulRegistry;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The whole mod configuration, saved as {@code config/soulsouls.json}.
 *
 * <p>The file is self-repairing: on load, every registered Soul that is missing from the
 * file is added with its code defaults, and every tunable value a Soul gained since the
 * file was written is filled in. That means adding a new Soul never requires deleting the
 * config, and {@code /souls reload} picks up hand edits without a restart.
 */
public class SoulsConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    // ------------------------------------------------------------------ global settings

    /** Bumped by the mod when the format changes; do not edit. */
    public int config_version = 2;

    /** Give players a Soul automatically the first time they join. */
    public boolean assign_on_first_join = true;

    /** Announce every Soul assignment to the whole server instead of only the player. */
    public boolean announce_assignment_to_server = false;

    /**
     * Colour player names using scoreboard teams. This is what colours the name above the
     * head, the tab list and chat for vanilla clients. Turn it off if another plugin or
     * mod owns your teams.
     */
    public boolean use_scoreboard_teams = true;

    /** Send exact RGB names to the tab list (requires the mod's mixin; teams stay as a fallback). */
    public boolean rgb_tab_list_names = true;

    /** Sound played when a Soul is assigned. */
    public String assignment_sound = "minecraft:entity.ender_dragon.growl";

    /**
     * Show a heart either side of the name floating above a player's head, in the Soul's
     * colour: {@code (heart) Steve (heart)}.
     */
    public boolean nameplate_hearts = true;

    /** The heart character. Change it if your font does not have the default one. */
    public String nameplate_heart = "❤";

    /** How long the "YOUR SOUL IS:" moment lasts, in seconds. */
    public double assignment_ceremony_seconds = 6.0;

    /** Blind the player for the length of the ceremony. */
    public boolean assignment_blindness = true;

    /**
     * Extra seconds of fall immunity after the ceremony ends. Levitation drops you from a
     * height, so without this the ceremony itself could kill you.
     */
    public double assignment_fall_grace_seconds = 10.0;

    /** Souls shout a short line when a Fun ability fires. Turn off for a quieter server. */
    public boolean soul_voice_lines = true;

    /** Play a small sound each time Patience gains or loses a heart. */
    public boolean patience_sounds = true;

    /** Sound played where a player dies. */
    public String death_sound = "minecraft:block.beacon.deactivate";

    /** Pitch of the death sound. 2.0 plays it at double speed. */
    public double death_sound_pitch = 2.0;

    /** How often ability ticks run, in ticks. 5 = four times a second. */
    public int ability_tick_interval = 5;

    /** How often player Soul data is written to disk, in seconds. */
    public int autosave_interval_seconds = 60;

    /** Determination's death-refusal chance, in percent. Also settable with /souls settings refuse. */
    public double determination_refuse_chance = 40.0;

    /** Let ordinary players re-roll with /souls reset. Operators always can. */
    public boolean allow_player_reset = true;

    // ------------------------------------------------------------------ per-Soul settings

    public Map<String, SoulConfig> souls = new LinkedHashMap<>();

    // ------------------------------------------------------------------ access

    public SoulConfig soulConfig(Soul soul) {
        return this.souls.computeIfAbsent(soul.id(), key -> SoulConfig.fromDefaults(soul));
    }

    public int colorOf(Soul soul) {
        return parseColor(soulConfig(soul).color, soul.defaultColor());
    }

    /** The character drawn to the left of the name for this Soul. */
    public String heartLeftOf(Soul soul) {
        String override = soulConfig(soul).heart_left;
        return override == null || override.isEmpty() ? this.nameplate_heart : override;
    }

    /** The character drawn to the right of the name for this Soul. */
    public String heartRightOf(Soul soul) {
        String override = soulConfig(soul).heart_right;
        return override == null || override.isEmpty() ? this.nameplate_heart : override;
    }

    public double maxHealthOf(Soul soul) {
        double health = soulConfig(soul).max_health;
        // Guard against a config that would make players unkillable or instantly dead.
        return Math.max(1.0, Math.min(1024.0, health));
    }

    public double chanceOf(Soul soul) {
        return Math.max(0.0, soulConfig(soul).chance);
    }

    public boolean isEnabled(Soul soul) {
        return soulConfig(soul).enabled;
    }

    public double value(Soul soul, String key) {
        return value(soul, key, soul.defaultValue(key, 0.0));
    }

    public double value(Soul soul, String key, double fallback) {
        Map<String, Double> values = soulConfig(soul).values;
        if (values != null) {
            Double stored = values.get(key);
            if (stored != null) {
                return stored;
            }
        }
        return soul.defaultValue(key, fallback);
    }

    public void setValue(Soul soul, String key, double value) {
        SoulConfig config = soulConfig(soul);
        if (config.values == null) {
            config.values = new LinkedHashMap<>();
        }
        config.values.put(key, value);
    }

    // ------------------------------------------------------------------ loading & saving

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(SoulSouls.MOD_ID + ".json");
    }

    /**
     * Reads the config from disk, creating or repairing it as needed. A corrupt file is
     * moved aside rather than deleted so nothing is silently lost.
     */
    public static SoulsConfig load() {
        Path path = configPath();
        SoulsConfig config = null;

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, SoulsConfig.class);
            } catch (Exception exception) {
                SoulSouls.LOGGER.error("Could not read {} - falling back to defaults", path, exception);
                try {
                    Files.move(path, path.resolveSibling(SoulSouls.MOD_ID + ".json.broken"),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveFailure) {
                    SoulSouls.LOGGER.error("Could not move the broken config aside", moveFailure);
                }
            }
        }

        if (config == null) {
            config = new SoulsConfig();
        }
        if (config.souls == null) {
            config.souls = new LinkedHashMap<>();
        }

        config.sanitise();
        config.save();
        return config;
    }

    /**
     * Brings an older config file up to date.
     *
     * <p>Only ever touches values that the mod itself wrote and that a later version
     * changed its mind about, and only when they are still exactly what the old version
     * put there - a value the owner has edited by hand is always left alone.
     */
    private void migrate() {
        if (this.config_version < 2) {
            // 1.4.0: every Soul's hearts are drawn in that Soul's own hex colour, so the
            // two Souls that used coloured emoji (which ignore text colour entirely, and
            // are missing from Minecraft's own font) go back to the shared heart.
            clearEmojiHearts("humility", "\uD83E\uDE76", "\uD83E\uDD0D");
            clearEmojiHearts("memory", "\uD83D\uDC99", "\uD83D\uDC9B");

            // 1.4.0: Dedication's colour changed to #6F47DE.
            SoulConfig dedication = this.souls.get("dedication");
            if (dedication != null && "#FF9BE0".equalsIgnoreCase(dedication.color)) {
                dedication.color = "#6F47DE";
            }

            this.config_version = 2;
        }
    }

    private void clearEmojiHearts(String soulId, String oldLeft, String oldRight) {
        SoulConfig soul = this.souls.get(soulId);
        if (soul == null) {
            return;
        }
        if (oldLeft.equals(soul.heart_left)) {
            soul.heart_left = "";
        }
        if (oldRight.equals(soul.heart_right)) {
            soul.heart_right = "";
        }
    }

    /** Adds anything the file is missing and clamps values that would break the game. */
    private void sanitise() {
        migrate();

        this.ability_tick_interval = Math.max(1, Math.min(100, this.ability_tick_interval));
        this.autosave_interval_seconds = Math.max(5, Math.min(3600, this.autosave_interval_seconds));
        this.determination_refuse_chance = clampPercent(this.determination_refuse_chance);

        // Registration order, so the file reads in the same order as /souls list.
        Map<String, SoulConfig> ordered = new LinkedHashMap<>();
        for (Soul soul : SoulRegistry.all()) {
            SoulConfig existing = this.souls.get(soul.id());
            if (existing == null) {
                existing = SoulConfig.fromDefaults(soul);
            } else {
                existing.fillMissing(soul);
                if (existing.color == null || existing.color.isBlank()) {
                    existing.color = String.format("#%06X", soul.defaultColor());
                }
                if (existing.rarity == null || existing.rarity.isBlank()) {
                    existing.rarity = soul.defaultRarity().name().toLowerCase(java.util.Locale.ROOT);
                }
                if (existing.difficulty == null || existing.difficulty.isBlank()) {
                    existing.difficulty = soul.defaultDifficulty().name();
                }
                existing.chance = Math.max(0.0, existing.chance);
            }
            ordered.put(soul.id(), existing);
        }
        // Keep entries for Souls that are not registered right now (e.g. an add-on that is
        // temporarily disabled) so their settings are not thrown away.
        for (Map.Entry<String, SoulConfig> entry : this.souls.entrySet()) {
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        this.souls = ordered;
    }

    public void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            SoulSouls.LOGGER.error("Could not write {}", path, exception);
        }
    }

    // ------------------------------------------------------------------ helpers

    public static double clampPercent(double percent) {
        return Math.max(0.0, Math.min(100.0, percent));
    }

    /** Parses {@code #RRGGBB}, {@code 0xRRGGBB} or a plain decimal number. */
    public static int parseColor(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        } else if (cleaned.startsWith("0x") || cleaned.startsWith("0X")) {
            cleaned = cleaned.substring(2);
        }
        try {
            return Integer.parseInt(cleaned, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            try {
                return Integer.parseInt(cleaned) & 0xFFFFFF;
            } catch (NumberFormatException alsoIgnored) {
                return fallback;
            }
        }
    }
}
