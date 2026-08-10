package com.soulsouls.soul;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Holds every Soul the mod knows about, keyed by id.
 *
 * <p>Registration order is preserved, so {@code /souls list} shows Souls in the order
 * they are declared in {@code com.soulsouls.souls.Souls}.
 */
public final class SoulRegistry {
    private static final Map<String, Soul> SOULS = new LinkedHashMap<>();

    private SoulRegistry() {
    }

    public static Soul register(Soul soul) {
        if (SOULS.containsKey(soul.id())) {
            throw new IllegalStateException("Duplicate soul id: " + soul.id());
        }
        SOULS.put(soul.id(), soul);
        return soul;
    }

    public static Optional<Soul> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(SOULS.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public static boolean contains(String id) {
        return id != null && SOULS.containsKey(id.toLowerCase(java.util.Locale.ROOT));
    }

    public static Collection<Soul> all() {
        return SOULS.values();
    }

    public static Collection<String> ids() {
        return SOULS.keySet();
    }

    public static int size() {
        return SOULS.size();
    }
}
