package com.soulsouls.data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the mod remembers about one player. Serialised straight to JSON by Gson, so
 * the field names are the on-disk format.
 *
 * <p>Timers are stored as wall-clock epoch milliseconds rather than tick counts. That is
 * deliberate: tick counters reset when the server restarts, which would let a player wipe
 * a cooldown by waiting for a restart or by reconnecting.
 *
 * <p>Ability state is a free-form {@code name -> number} map instead of typed fields, so a
 * new Soul can keep its own state without any change to the save format.
 */
public class PlayerSoulData {
    public String uuid;
    public String last_known_name = "";
    public String soul_id;
    public long assigned_at = 0L;

    /** Cooldown key -> epoch millis when it becomes ready again. */
    public Map<String, Long> cooldowns = new LinkedHashMap<>();

    /** Arbitrary persistent numbers owned by abilities. */
    public Map<String, Double> state = new LinkedHashMap<>();

    /** Mutual bonds, used by Retribution. */
    public Set<String> bonds = new LinkedHashSet<>();

    /** Bond requests this player has sent and that are waiting to be answered. */
    public Set<String> bond_requests = new LinkedHashSet<>();

    public PlayerSoulData() {
    }

    public PlayerSoulData(UUID uuid, String name) {
        this.uuid = uuid.toString();
        this.last_known_name = name;
    }

    public UUID uuid() {
        return UUID.fromString(this.uuid);
    }

    public boolean hasSoul() {
        return this.soul_id != null && !this.soul_id.isBlank();
    }

    /** Makes sure maps deserialised from an older/hand-edited file are never null. */
    public void ensureCollections() {
        if (this.cooldowns == null) {
            this.cooldowns = new LinkedHashMap<>();
        }
        if (this.state == null) {
            this.state = new LinkedHashMap<>();
        }
        if (this.bonds == null) {
            this.bonds = new LinkedHashSet<>();
        }
        if (this.bond_requests == null) {
            this.bond_requests = new LinkedHashSet<>();
        }
    }

    // ------------------------------------------------------------------ cooldowns

    public boolean isCooldownReady(String key) {
        Long readyAt = this.cooldowns.get(key);
        return readyAt == null || System.currentTimeMillis() >= readyAt;
    }

    public void setCooldown(String key, double seconds) {
        if (seconds <= 0.0) {
            this.cooldowns.remove(key);
        } else {
            this.cooldowns.put(key, System.currentTimeMillis() + Math.round(seconds * 1000.0));
        }
    }

    public long cooldownSecondsLeft(String key) {
        Long readyAt = this.cooldowns.get(key);
        if (readyAt == null) {
            return 0L;
        }
        long remaining = readyAt - System.currentTimeMillis();
        return remaining <= 0L ? 0L : (remaining + 999L) / 1000L;
    }

    public void clearCooldown(String key) {
        this.cooldowns.remove(key);
    }

    public void clearAllCooldowns() {
        this.cooldowns.clear();
    }

    // ------------------------------------------------------------------ ability state

    public double state(String key, double fallback) {
        Double value = this.state.get(key);
        return value == null ? fallback : value;
    }

    public void setState(String key, double value) {
        this.state.put(key, value);
    }

    public void clearState(String key) {
        this.state.remove(key);
    }

    /** Wipes ability state and cooldowns; used when a player changes Soul. */
    public void resetSoulState() {
        this.state.clear();
        this.cooldowns.clear();
    }

    // ------------------------------------------------------------------ bonds

    public boolean isBondedTo(UUID other) {
        return this.bonds.contains(other.toString());
    }

    public void addBond(UUID other) {
        this.bonds.add(other.toString());
        this.bond_requests.remove(other.toString());
    }

    public void removeBond(UUID other) {
        this.bonds.remove(other.toString());
    }
}
