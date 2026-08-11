package com.soulsouls.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.soulsouls.data.SoulManager;
import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.Soul;
import com.soulsouls.souls.ability.JusticeTargetAbility;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * {@code /justice target <player>} and {@code /justice location} - the Justice Soul's hunt.
 */
public final class JusticeCommand {
    private JusticeCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("justice")
                .then(CommandManager.literal("target")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(JusticeCommand::target)))
                .then(CommandManager.literal("location").executes(JusticeCommand::location)));
    }

    private static int target(CommandContext<ServerCommandSource> context) {
        AbilityContext ctx = justiceContext(context);
        if (ctx == null) {
            return 0;
        }

        ServerPlayerEntity victim;
        try {
            victim = EntityArgumentType.getPlayer(context, "player");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            context.getSource().sendError(Text.literal("That player could not be found."));
            return 0;
        }

        String failure = JusticeTargetAbility.mark(ctx, victim);
        if (failure != null) {
            context.getSource().sendError(Text.literal(failure));
            return 0;
        }
        return 1;
    }

    private static int location(CommandContext<ServerCommandSource> context) {
        AbilityContext ctx = justiceContext(context);
        if (ctx == null) {
            return 0;
        }

        ServerPlayerEntity target = JusticeTargetAbility.findTarget(ctx);
        if (target == null) {
            context.getSource().sendError(Text.literal(
                    "No target, or they are not online. Mark one with /justice target <player>."));
            return 0;
        }

        Vec3d from = new Vec3d(ctx.player().getX(), ctx.player().getY(), ctx.player().getZ());
        Vec3d to = new Vec3d(target.getX(), target.getY(), target.getZ());
        int distance = (int) from.distanceTo(to);

        context.getSource().sendFeedback(() -> Text.literal(target.getNameForScoreboard())
                .formatted(Formatting.RED, Formatting.BOLD)
                .append(Text.literal(" is at ").formatted(Formatting.GRAY))
                .append(Text.literal((int) to.x + ", " + (int) to.y + ", " + (int) to.z)
                        .formatted(Formatting.YELLOW))
                .append(Text.literal(" - " + distance + " blocks " + compass(from, to))
                        .formatted(Formatting.GRAY)), false);

        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                net.minecraft.sound.SoundCategory.PLAYERS, 0.8F, 1.4F);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ITEM_SPYGLASS_USE, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        return distance;
    }

    /** Rough bearing, so the number means something without opening a map. */
    private static String compass(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "east" : "west";
        }
        return dz > 0 ? "south" : "north";
    }

    /** The caller's Justice context, or null with the error already reported. */
    private static AbilityContext justiceContext(CommandContext<ServerCommandSource> context) {
        SoulManager manager = SoulManager.get();
        if (manager == null) {
            context.getSource().sendError(Text.literal("Soul system is not ready yet."));
            return null;
        }

        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("Only a player can hunt."));
            return null;
        }

        Optional<Soul> soul = manager.soulOf(player);
        boolean isJustice = soul.map(found -> found.abilities().stream()
                .anyMatch(ability -> ability instanceof JusticeTargetAbility)).orElse(false);
        if (!isJustice) {
            context.getSource().sendError(Text.literal("Only the Justice soul can hunt."));
            return null;
        }

        return manager.contextFor(player, soul.get());
    }
}
