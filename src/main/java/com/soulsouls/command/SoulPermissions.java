package com.soulsouls.command;

import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;

/**
 * The one place that decides whether a command source counts as an administrator.
 *
 * <p>Minecraft 1.21.11 replaced {@code ServerCommandSource.hasPermissionLevel(int)} with a
 * permission-predicate system: a source carries a {@code PermissionPredicate}, and you ask
 * it whether it grants a particular {@code Permission}. The old numeric levels survive as
 * the {@link PermissionLevel} enum, where the classic "level 2" (the level vanilla requires
 * for cheat-style commands) is {@link PermissionLevel#GAMEMASTERS}.
 *
 * <p>Every admin check in the mod goes through here, so a future change to how Minecraft
 * models permissions is a one-line fix rather than a hunt through the command tree.
 */
public final class SoulPermissions {
    /** Vanilla's old permission level 2 - the level that gates cheat-style commands. */
    public static final PermissionLevel ADMIN_LEVEL = PermissionLevel.GAMEMASTERS;

    private static final Permission ADMIN_PERMISSION = new Permission.Level(ADMIN_LEVEL);

    private SoulPermissions() {
    }

    public static boolean isAdmin(ServerCommandSource source) {
        return source.getPermissions().hasPermission(ADMIN_PERMISSION);
    }
}
