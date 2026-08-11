package com.soulsouls.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.soulsouls.data.PlayerSoulData;
import com.soulsouls.data.SoulManager;
import com.soulsouls.souls.ability.MemoryAbility;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Memory Soul's own commands.
 *
 * <ul>
 *   <li>{@code /memory list} - everyone remembered, green if they are online and alive,
 *       red if they are dead or gone.</li>
 *   <li>{@code /memory reset} - forget everybody.</li>
 * </ul>
 *
 * <p>{@code /memories} is registered as an alias, so both spellings work.
 */
public final class MemoryCommand {
    private MemoryCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("memory")
                .then(CommandManager.literal("list").executes(MemoryCommand::list))
                .then(CommandManager.literal("memories").executes(MemoryCommand::list))
                .then(CommandManager.literal("reset").executes(MemoryCommand::reset)));

        // The brief uses both /memory and /memories, so both are accepted.
        dispatcher.register(CommandManager.literal("memories")
                .executes(MemoryCommand::list)
                .then(CommandManager.literal("list").executes(MemoryCommand::list))
                .then(CommandManager.literal("reset").executes(MemoryCommand::reset)));
    }

    private static int list(CommandContext<ServerCommandSource> context) {
        PlayerSoulData data = memoryData(context);
        if (data == null) {
            return 0;
        }
        SoulManager manager = SoulManager.get();

        double deathX = data.state(MemoryAbility.DEATH_X, Double.NaN);
        if (!Double.isNaN(deathX)) {
            context.getSource().sendFeedback(() -> Text.literal("Last death: ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal((int) deathX + ", "
                                    + (int) data.state(MemoryAbility.DEATH_Y, 0.0) + ", "
                                    + (int) data.state(MemoryAbility.DEATH_Z, 0.0))
                            .formatted(Formatting.YELLOW)), false);
        }

        // Only entries that came from a scan; other Souls use this map for their own things.
        List<String> scanned = new ArrayList<>();
        for (String key : data.state.keySet()) {
            if (key.startsWith(MemoryAbility.SEEN_PREFIX)) {
                scanned.add(key.substring(MemoryAbility.SEEN_PREFIX.length()));
            }
        }

        if (scanned.isEmpty()) {
            context.getSource().sendFeedback(() ->
                    Text.literal("You have not remembered anyone yet. Use /souls ability near people.")
                            .formatted(Formatting.GRAY), false);
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("Remembered (" + scanned.size() + "):")
                .formatted(Formatting.GRAY), false);

        for (String rawUuid : scanned) {
            String name = data.remembered_names.getOrDefault(rawUuid, rawUuid);
            boolean alive = isAlive(manager, rawUuid);
            context.getSource().sendFeedback(() -> Text.literal("- ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(name).formatted(alive ? Formatting.GREEN : Formatting.RED))
                    .append(Text.literal(alive ? "  (alive)" : "  (dead or offline)")
                            .formatted(Formatting.DARK_GRAY)), false);
        }
        return scanned.size();
    }

    private static int reset(CommandContext<ServerCommandSource> context) {
        PlayerSoulData data = memoryData(context);
        if (data == null) {
            return 0;
        }

        int cleared = 0;
        for (String key : new ArrayList<>(data.state.keySet())) {
            if (key.startsWith(MemoryAbility.SEEN_PREFIX)) {
                data.clearState(key);
                data.remembered_names.remove(key.substring(MemoryAbility.SEEN_PREFIX.length()));
                cleared++;
            }
        }
        SoulManager.get().markDirty();

        int total = cleared;
        context.getSource().sendFeedback(() ->
                Text.literal("Forgotten " + total + (total == 1 ? " person." : " people."))
                        .formatted(Formatting.YELLOW), false);
        return total;
    }

    /** Online and not dead counts as alive; anything else is red. */
    private static boolean isAlive(SoulManager manager, String rawUuid) {
        try {
            ServerPlayerEntity player = manager.server().getPlayerManager()
                    .getPlayer(UUID.fromString(rawUuid));
            return player != null && player.isAlive();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /** The caller's data if they carry Memory, or null with the error already reported. */
    private static PlayerSoulData memoryData(CommandContext<ServerCommandSource> context) {
        SoulManager manager = SoulManager.get();
        if (manager == null) {
            context.getSource().sendError(Text.literal("Soul system is not ready yet."));
            return null;
        }

        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("Only a player has memories."));
            return null;
        }

        boolean hasMemory = manager.soulOf(player)
                .map(soul -> soul.abilities().stream().anyMatch(a -> a instanceof MemoryAbility))
                .orElse(false);
        if (!hasMemory) {
            context.getSource().sendError(Text.literal("Only the Memory soul keeps a list."));
            return null;
        }

        return manager.dataOf(player);
    }

    /** Unused, kept so the import of Map stays meaningful for future entries. */
    static Map<String, String> names(PlayerSoulData data) {
        return data.remembered_names;
    }
}
