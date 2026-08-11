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

import java.util.Map;

/**
 * {@code /memory memories} - the list of people the Memory Soul has scanned, plus the last
 * place its owner died.
 */
public final class MemoryCommand {
    private MemoryCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("memory")
                .then(CommandManager.literal("memories").executes(MemoryCommand::memories)));
    }

    private static int memories(CommandContext<ServerCommandSource> context) {
        SoulManager manager = SoulManager.get();
        if (manager == null) {
            context.getSource().sendError(Text.literal("Soul system is not ready yet."));
            return 0;
        }

        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("Only a player has memories."));
            return 0;
        }

        boolean hasMemory = manager.soulOf(player)
                .map(soul -> soul.abilities().stream().anyMatch(a -> a instanceof MemoryAbility))
                .orElse(false);
        if (!hasMemory) {
            context.getSource().sendError(Text.literal("Only the Memory soul keeps a list."));
            return 0;
        }

        PlayerSoulData data = manager.dataOf(player);

        double deathX = data.state(MemoryAbility.DEATH_X, Double.NaN);
        if (!Double.isNaN(deathX)) {
            context.getSource().sendFeedback(() -> Text.literal("Last death: ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal((int) deathX + ", "
                                    + (int) data.state(MemoryAbility.DEATH_Y, 0.0) + ", "
                                    + (int) data.state(MemoryAbility.DEATH_Z, 0.0))
                            .formatted(Formatting.YELLOW)), false);
        }

        if (data.remembered_names.isEmpty()) {
            context.getSource().sendFeedback(() ->
                    Text.literal("You have not remembered anyone yet. Double-sneak near people.")
                            .formatted(Formatting.GRAY), false);
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("Remembered ("
                + data.remembered_names.size() + "):").formatted(Formatting.GRAY), false);
        for (Map.Entry<String, String> entry : data.remembered_names.entrySet()) {
            context.getSource().sendFeedback(() -> Text.literal("- ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(entry.getValue()).formatted(Formatting.BLUE)), false);
        }
        return data.remembered_names.size();
    }
}
