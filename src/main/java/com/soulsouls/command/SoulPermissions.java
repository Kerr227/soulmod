package com.soulsouls.command;

import net.minecraft.server.command.ServerCommandSource;

/**
 * The one place that decides whether a command source counts as an administrator.
 *
 * <p>Minecraft 1.21.11 replaced the old integer permission levels on
 * {@link ServerCommandSource} with a {@code PermissionPredicate}, so every admin check in
 * the mod goes through here rather than calling the game API directly.
 */
public final class SoulPermissions {
    /** Vanilla "operator" level: can use /op-style commands. */
    public static final int ADMIN_LEVEL = 2;

    private SoulPermissions() {
    }

    public static boolean isAdmin(ServerCommandSource source) {
        return source.hasPermissionLevel(ADMIN_LEVEL);
    }
}
