package com.soulsouls.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.soulsouls.SoulSouls;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and writes Soul data to {@code <world>/soulsouls/players.json}.
 *
 * <p>Storing it inside the world save (rather than next to the config) means a world copy
 * or backup carries the Souls with it, and a server reset wipes them along with the world.
 * Writes go to a temporary file first and are then moved into place, so a crash mid-save
 * cannot truncate the real file.
 */
public final class SoulStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, PlayerSoulData>>() {
    }.getType();

    private final Path file;

    public SoulStorage(MinecraftServer server) {
        Path directory = server.getSavePath(WorldSavePath.ROOT).resolve(SoulSouls.MOD_ID);
        this.file = directory.resolve("players.json");
    }

    public Path file() {
        return this.file;
    }

    public Map<UUID, PlayerSoulData> load() {
        Map<UUID, PlayerSoulData> result = new LinkedHashMap<>();
        if (!Files.exists(this.file)) {
            return result;
        }

        Map<String, PlayerSoulData> raw;
        try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            raw = GSON.fromJson(reader, MAP_TYPE);
        } catch (Exception exception) {
            SoulSouls.LOGGER.error("Could not read {} - starting with no Soul data. "
                    + "The unreadable file is kept as players.json.broken", this.file, exception);
            try {
                Files.move(this.file, this.file.resolveSibling("players.json.broken"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveFailure) {
                SoulSouls.LOGGER.error("Could not move the broken data file aside", moveFailure);
            }
            return result;
        }

        if (raw == null) {
            return result;
        }

        for (Map.Entry<String, PlayerSoulData> entry : raw.entrySet()) {
            PlayerSoulData data = entry.getValue();
            if (data == null) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                if (data.uuid == null) {
                    data.uuid = entry.getKey();
                }
                data.ensureCollections();
                result.put(uuid, data);
            } catch (IllegalArgumentException ignored) {
                SoulSouls.LOGGER.warn("Skipping Soul data with a malformed UUID: {}", entry.getKey());
            }
        }
        return result;
    }

    public void save(Map<UUID, PlayerSoulData> data) {
        Map<String, PlayerSoulData> raw = new LinkedHashMap<>();
        for (Map.Entry<UUID, PlayerSoulData> entry : data.entrySet()) {
            raw.put(entry.getKey().toString(), entry.getValue());
        }

        try {
            Files.createDirectories(this.file.getParent());
            Path temporary = this.file.resolveSibling("players.json.tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(raw, MAP_TYPE, writer);
            }
            Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            SoulSouls.LOGGER.error("Could not write Soul data to {}", this.file, exception);
        }
    }
}
