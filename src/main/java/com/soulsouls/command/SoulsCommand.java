package com.soulsouls.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.soulsouls.config.SoulConfig;
import com.soulsouls.config.SoulsConfig;
import com.soulsouls.data.PlayerSoulData;
import com.soulsouls.data.SoulManager;
import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.Soul;
import com.soulsouls.soul.SoulAbility;
import com.soulsouls.soul.SoulDifficulty;
import com.soulsouls.soul.SoulRegistry;
import com.soulsouls.util.SoulText;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code /souls} command tree.
 *
 * <p>Anything that changes another player, or the configuration, needs permission level 2
 * (the usual "operator" level). The two commands the brief lists as player-facing -
 * {@code /souls give <soul>} and {@code /souls reset} - are gated by the config flags
 * {@code allow_player_give} and {@code allow_player_reset} instead, so a server that wants
 * Souls to stay random can turn them off without losing the admin versions.
 */
public final class SoulsCommand {
    // Admin checks all go through SoulPermissions, which owns the 1.21.11 permission API.

    private static final SuggestionProvider<ServerCommandSource> SOUL_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String id : SoulRegistry.ids()) {
            if (id.startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> DIFFICULTY_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining().toUpperCase(Locale.ROOT);
        for (SoulDifficulty difficulty : SoulDifficulty.values()) {
            if (difficulty.name().startsWith(remaining)) {
                builder.suggest(difficulty.name());
            }
        }
        return builder.buildFuture();
    };

    private SoulsCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("souls")
                .executes(SoulsCommand::info)

                .then(CommandManager.literal("info").executes(SoulsCommand::info))
                .then(CommandManager.literal("list").executes(SoulsCommand::list))
                .then(CommandManager.literal("ability").executes(SoulsCommand::ability))
                .then(CommandManager.literal("reset").executes(SoulsCommand::resetSelf))

                .then(CommandManager.literal("bond")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(SoulsCommand::bond)))

                .then(CommandManager.literal("give")
                        // Ordering matters: the single-argument form is tried first, and
                        // Brigadier falls through to the admin form when the first word is
                        // not a Soul id.
                        .then(CommandManager.argument("soul", StringArgumentType.word())
                                .suggests(SOUL_SUGGESTIONS)
                                .executes(SoulsCommand::giveSelf))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> SoulPermissions.isAdmin(source))
                                .then(CommandManager.argument("soul", StringArgumentType.word())
                                        .suggests(SOUL_SUGGESTIONS)
                                        .executes(SoulsCommand::giveOther))))

                .then(CommandManager.literal("set")
                        .requires(source -> SoulPermissions.isAdmin(source))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("soul", StringArgumentType.word())
                                        .suggests(SOUL_SUGGESTIONS)
                                        .executes(SoulsCommand::giveOther))))

                .then(CommandManager.literal("reload")
                        .requires(source -> SoulPermissions.isAdmin(source))
                        .executes(SoulsCommand::reload))

                .then(CommandManager.literal("debug")
                        .executes(context -> debug(context, null))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> SoulPermissions.isAdmin(source))
                                .executes(context ->
                                        debug(context, EntityArgumentType.getPlayer(context, "player")))))

                .then(CommandManager.literal("settings")
                        .requires(source -> SoulPermissions.isAdmin(source))
                        .then(CommandManager.literal("chance")
                                .then(CommandManager.argument("soul", StringArgumentType.word())
                                        .suggests(SOUL_SUGGESTIONS)
                                        .then(CommandManager.argument("percentage", DoubleArgumentType.doubleArg(0.0))
                                                .executes(SoulsCommand::settingsChance))))
                        .then(CommandManager.literal("refuse")
                                .then(CommandManager.argument("percentage",
                                                DoubleArgumentType.doubleArg(0.0, 100.0))
                                        .executes(SoulsCommand::settingsRefuse)))
                        .then(CommandManager.literal("difficulty")
                                .then(CommandManager.argument("soul", StringArgumentType.word())
                                        .suggests(SOUL_SUGGESTIONS)
                                        .then(CommandManager.argument("difficulty", StringArgumentType.word())
                                                .suggests(DIFFICULTY_SUGGESTIONS)
                                                .executes(SoulsCommand::settingsDifficulty))))));
    }

    // ------------------------------------------------------------------ /souls info

    private static int info(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("Only a player has a Soul. Try /souls list."));
            return 0;
        }

        SoulManager manager = manager(context);
        if (manager == null) {
            return 0;
        }

        Optional<Soul> soul = manager.soulOf(player);
        if (soul.isEmpty()) {
            context.getSource().sendFeedback(() -> SoulText.prefix()
                    .append(Text.literal("You do not have a Soul yet.").formatted(Formatting.GRAY)), false);
            return 0;
        }

        SoulsConfig config = manager.config();
        Soul found = soul.get();
        SoulConfig settings = config.soulConfig(found);
        AbilityContext abilityContext = manager.contextFor(player, found);

        context.getSource().sendFeedback(() -> line("Your Soul: ", SoulText.soulName(found, config)), false);
        context.getSource().sendFeedback(() -> line("Color: ",
                SoulText.coloured(SoulText.hex(config.colorOf(found)), config.colorOf(found))), false);
        context.getSource().sendFeedback(() -> line("Rarity: ",
                Text.literal(settings.rarity(found).display()).formatted(settings.rarity(found).formatting())), false);
        context.getSource().sendFeedback(() -> line("Difficulty: ",
                Text.literal(settings.difficulty(found).name())
                        .formatted(settings.difficulty(found).formatting())), false);
        context.getSource().sendFeedback(() -> line("Max Health: ",
                Text.literal(SoulText.hearts(player.getMaxHealth())).formatted(Formatting.WHITE)), false);

        if (!found.description().isBlank()) {
            context.getSource().sendFeedback(() ->
                    Text.literal(found.description()).formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
        }

        context.getSource().sendFeedback(() ->
                Text.literal("Abilities:").formatted(Formatting.GRAY), false);
        for (SoulAbility soulAbility : found.abilities()) {
            context.getSource().sendFeedback(() -> Text.literal("- ").formatted(Formatting.GRAY)
                    .append(Text.literal(soulAbility.displayName()).formatted(Formatting.WHITE)), false);
            for (Text detail : soulAbility.describe(abilityContext)) {
                context.getSource().sendFeedback(() -> detail, false);
            }
            for (String key : soulAbility.cooldownKeys()) {
                long left = abilityContext.cooldownSecondsLeft(key);
                context.getSource().sendFeedback(() -> Text.literal("  Cooldown (" + key + "): ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(SoulText.seconds(left))
                                .formatted(left == 0 ? Formatting.GREEN : Formatting.YELLOW)), false);
            }
        }
        return 1;
    }

    // ------------------------------------------------------------------ /souls list

    private static int list(CommandContext<ServerCommandSource> context) {
        SoulManager manager = manager(context);
        if (manager == null) {
            return 0;
        }
        SoulsConfig config = manager.config();

        context.getSource().sendFeedback(() -> Text.literal("Souls (" + SoulRegistry.size() + "):")
                .formatted(Formatting.GRAY), false);

        for (Soul soul : SoulRegistry.all()) {
            SoulConfig settings = config.soulConfig(soul);
            MutableText row = Text.literal("- ").formatted(Formatting.DARK_GRAY)
                    .append(SoulText.soulName(soul, config))
                    .append(Text.literal("  " + settings.difficulty(soul).name())
                            .formatted(settings.difficulty(soul).formatting()))
                    .append(Text.literal("  chance " + trim(config.chanceOf(soul)))
                            .formatted(Formatting.DARK_GRAY));
            if (!settings.enabled) {
                row.append(Text.literal("  (disabled)").formatted(Formatting.RED));
            }
            context.getSource().sendFeedback(() -> row, false);
        }
        return SoulRegistry.size();
    }

    // ------------------------------------------------------------------ /souls give, set, reset

    private static int giveSelf(CommandContext<ServerCommandSource> context) {
        SoulManager manager = manager(context);
        if (manager == null) {
            return 0;
        }

        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("Only a player can equip a Soul this way."));
            return 0;
        }

        boolean isAdmin = SoulPermissions.isAdmin(context.getSource());
        if (!isAdmin && !manager.config().allow_player_give) {
            context.getSource().sendError(Text.literal("Choosing your own Soul is disabled on this server."));
            return 0;
        }

        Optional<Soul> soul = resolveSoul(context, "soul");
        if (soul.isEmpty()) {
            return 0;
        }
        if (!isAdmin && !manager.config().isEnabled(soul.get())) {
            context.getSource().sendError(Text.literal("That Soul is disabled on this server."));
            return 0;
        }

        manager.assign(player, soul.get(), true);
        context.getSource().sendFeedback(() ->
                Text.literal("success while equipping new soul!").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int giveOther(CommandContext<ServerCommandSource> context) {
        SoulManager manager = manager(context);
        if (manager == null) {
            return 0;
        }

        ServerPlayerEntity target;
        try {
            target = EntityArgumentType.getPlayer(context, "player");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            context.getSource().sendError(Text.literal("That player could not be found."));
            return 0;
        }

        Optional<Soul> soul = resolveSoul(context, "soul");
        if (soul.isEmpty()) {
            return 0;
        }

        manager.assign(target, soul.get(), true);
        SoulsConfig config = manager.config();
        context.getSource().sendFeedback(() ->
                Text.literal("success while equipping new soul!").formatted(Formatting.GREEN)
                        .append(Text.literal(" (" + target.getNameForScoreboard() + " -> ")
                                .formatted(Formatting.GRAY))
                        .append(SoulText.soulName(soul.get(), config))
                        .append(Text.literal(")").formatted(Formatting.GRAY)), true);
        return 1;
    }

    private static int resetSelf(CommandContext<ServerCommandSource> context) {
        SoulManager manager = manager(context);
        if (manager == null) {
            return 0;
        }

        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("Only a player can reset their Soul."));
            return 0;
        }

        if (!SoulPermissions.isAdmin(context.getSource()) && !manager.config().allow_player_reset) {
            context.getSource().sendError(Text.literal("Resetting your Soul is disabled on this server."));
            return 0;
        }

        manager.reset(player);

        // Roll straight away, so the player is never left without a Soul.
        Optional<Soul> rolled = manager.rollSoul();
        if (rolled.isPresent()) {
            manager.assign(player, rolled.get(), true);
        } else {
            context.getSource().sendFeedback(() ->
                    Text.literal("Your Soul was reset, but no Soul could be rolled.")
                            .formatted(Formatting.YELLOW), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ /souls ability

    private static int ability(CommandContext<ServerCommandSource> context) {
        SoulManager manager = manager(context);
        if (manager == null) {
            return 0;
        }

        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("Only a player can use a Soul ability."));
            return 0;
        }

        Optional<Soul> soul = manager.soulOf(player);
        if (soul.isEmpty()) {
            context.getSource().sendError(Text.literal("You do not have a Soul."));
            return 0;
        }

        AbilityContext abilityContext = manager.contextFor(player, soul.get());
        boolean anyActive = false;
        for (SoulAbility soulAbility : soul.get().abilities()) {
            if (!soulAbility.isActive()) {
                continue;
            }
            anyActive = true;
            if (soulAbility.activate(abilityContext)) {
                return 1;
            }
            for (String key : soulAbility.cooldownKeys()) {
                long left = abilityContext.cooldownSecondsLeft(key);
                if (left > 0) {
                    context.getSource().sendError(Text.literal(
                            soulAbility.displayName() + " is not ready (" + SoulText.seconds(left) + ")"));
                    return 0;
                }
            }
        }

        context.getSource().sendError(Text.literal(anyActive
                ? "Your ability could not be used right now."
                : "Your Soul has no ability to activate."));
        return 0;
    }

    // ------------------------------------------------------------------ /souls bond

    private static int bond(CommandContext<ServerCommandSource> context) {
        SoulManager manager = manager(context);
        if (manager == null) {
            return 0;
        }

        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("Only a player can bond."));
            return 0;
        }

        ServerPlayerEntity target;
        try {
            target = EntityArgumentType.getPlayer(context, "player");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            context.getSource().sendError(Text.literal("That player could not be found."));
            return 0;
        }

        if (target == player) {
            context.getSource().sendError(Text.literal("You cannot bond with yourself."));
            return 0;
        }

        PlayerSoulData mine = manager.dataOf(player);
        PlayerSoulData theirs = manager.dataOf(target);
        String myId = player.getUuid().toString();
        UUID targetId = target.getUuid();

        if (mine.isBondedTo(targetId)) {
            context.getSource().sendError(Text.literal("You are already bonded with "
                    + target.getNameForScoreboard() + "."));
            return 0;
        }

        // A bond only exists once both sides have asked for it.
        if (theirs.bond_requests.contains(myId)) {
            mine.addBond(targetId);
            theirs.addBond(player.getUuid());
            manager.markDirty();

            Text message = Text.literal("Soul bond formed: ").formatted(Formatting.LIGHT_PURPLE)
                    .append(Text.literal(player.getNameForScoreboard()).formatted(Formatting.WHITE))
                    .append(Text.literal(" and ").formatted(Formatting.LIGHT_PURPLE))
                    .append(Text.literal(target.getNameForScoreboard()).formatted(Formatting.WHITE));
            player.sendMessage(message, false);
            target.sendMessage(message, false);
            return 1;
        }

        mine.bond_requests.add(target.getUuid().toString());
        manager.markDirty();
        context.getSource().sendFeedback(() -> Text.literal("Bond offered to "
                + target.getNameForScoreboard() + ".").formatted(Formatting.LIGHT_PURPLE), false);
        target.sendMessage(Text.literal(player.getNameForScoreboard()
                        + " wants to bond souls with you. Run /souls bond "
                        + player.getNameForScoreboard() + " to accept.")
                .formatted(Formatting.LIGHT_PURPLE), false);
        return 1;
    }

    // ------------------------------------------------------------------ /souls reload

    private static int reload(CommandContext<ServerCommandSource> context) {
        SoulManager manager = manager(context);
        if (manager == null) {
            return 0;
        }
        manager.setConfig(SoulsConfig.load());
        context.getSource().sendFeedback(() ->
                Text.literal("Soul configuration reloaded.").formatted(Formatting.GREEN), true);
        return 1;
    }

    // ------------------------------------------------------------------ /souls settings

    private static int settingsChance(CommandContext<ServerCommandSource> context) {
        SoulManager manager = manager(context);
        if (manager == null) {
            return 0;
        }
        Optional<Soul> soul = resolveSoul(context, "soul");
        if (soul.isEmpty()) {
            return 0;
        }

        double percentage = Math.max(0.0, DoubleArgumentType.getDouble(context, "percentage"));
        SoulsConfig config = manager.config();
        config.soulConfig(soul.get()).chance = percentage;
        config.save();

        context.getSource().sendFeedback(() -> Text.literal("Chance for ").formatted(Formatting.GRAY)
                .append(SoulText.soulName(soul.get(), config))
                .append(Text.literal(" set to " + trim(percentage)).formatted(Formatting.GRAY)), true);
        return 1;
    }

    private static int settingsRefuse(CommandContext<ServerCommandSource> context) {
        SoulManager manager = manager(context);
        if (manager == null) {
            return 0;
        }
        double percentage = SoulsConfig.clampPercent(DoubleArgumentType.getDouble(context, "percentage"));
        manager.config().determination_refuse_chance = percentage;
        manager.config().save();

        context.getSource().sendFeedback(() -> Text.literal("Determination refuse chance set to "
                + trim(percentage) + "%").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int settingsDifficulty(CommandContext<ServerCommandSource> context) {
        SoulManager manager = manager(context);
        if (manager == null) {
            return 0;
        }
        Optional<Soul> soul = resolveSoul(context, "soul");
        if (soul.isEmpty()) {
            return 0;
        }

        String raw = StringArgumentType.getString(context, "difficulty");
        SoulDifficulty difficulty = SoulDifficulty.parse(raw, null);
        if (difficulty == null) {
            context.getSource().sendError(Text.literal("Unknown difficulty: " + raw
                    + ". Valid values: EASY, NORMAL, HARD, EXTREME, LEGENDARY"));
            return 0;
        }

        SoulsConfig config = manager.config();
        config.soulConfig(soul.get()).difficulty = difficulty.name();
        config.save();

        context.getSource().sendFeedback(() -> Text.literal("Difficulty for ").formatted(Formatting.GRAY)
                .append(SoulText.soulName(soul.get(), config))
                .append(Text.literal(" set to ").formatted(Formatting.GRAY))
                .append(Text.literal(difficulty.name()).formatted(difficulty.formatting())), true);
        return 1;
    }

    // ------------------------------------------------------------------ /souls debug

    private static int debug(CommandContext<ServerCommandSource> context, ServerPlayerEntity explicitTarget) {
        SoulManager manager = manager(context);
        if (manager == null) {
            return 0;
        }

        ServerPlayerEntity target = explicitTarget != null ? explicitTarget : context.getSource().getPlayer();
        if (target == null) {
            context.getSource().sendError(Text.literal("Specify a player: /souls debug <player>"));
            return 0;
        }

        PlayerSoulData data = manager.dataOf(target);
        Optional<Soul> soul = manager.soulOf(data);
        SoulsConfig config = manager.config();

        context.getSource().sendFeedback(() -> Text.literal("--- Soul debug: "
                + target.getNameForScoreboard() + " ---").formatted(Formatting.DARK_GRAY), false);

        if (soul.isEmpty()) {
            context.getSource().sendFeedback(() ->
                    Text.literal("Soul: none").formatted(Formatting.GRAY), false);
            return 1;
        }

        Soul found = soul.get();
        context.getSource().sendFeedback(() -> line("Soul: ", SoulText.soulName(found, config)), false);
        context.getSource().sendFeedback(() -> line("Color: ",
                Text.literal(SoulText.hex(config.colorOf(found))).formatted(Formatting.WHITE)), false);
        context.getSource().sendFeedback(() -> line("Health: ",
                Text.literal(String.format("%.1f / %.1f", target.getHealth(), target.getMaxHealth()))
                        .formatted(Formatting.WHITE)), false);
        context.getSource().sendFeedback(() -> line("Refuse Chance: ",
                Text.literal(trim(config.determination_refuse_chance) + "%").formatted(Formatting.WHITE)), false);

        AbilityContext abilityContext = manager.contextFor(target, found);
        context.getSource().sendFeedback(() -> Text.literal("Cooldowns:").formatted(Formatting.GRAY), false);
        boolean any = false;
        for (SoulAbility soulAbility : found.abilities()) {
            for (String key : soulAbility.cooldownKeys()) {
                any = true;
                long left = abilityContext.cooldownSecondsLeft(key);
                context.getSource().sendFeedback(() -> Text.literal("  " + key + ": ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(SoulText.seconds(left))
                                .formatted(left == 0 ? Formatting.GREEN : Formatting.YELLOW)), false);
            }
        }
        if (!any) {
            context.getSource().sendFeedback(() ->
                    Text.literal("  (none)").formatted(Formatting.DARK_GRAY), false);
        }

        if (!data.state.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("State:").formatted(Formatting.GRAY), false);
            for (Map.Entry<String, Double> entry : data.state.entrySet()) {
                context.getSource().sendFeedback(() -> Text.literal("  " + entry.getKey() + ": "
                        + trim(entry.getValue())).formatted(Formatting.DARK_GRAY), false);
            }
        }
        if (!data.bonds.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("Bonds: " + data.bonds.size())
                    .formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ helpers

    private static SoulManager manager(CommandContext<ServerCommandSource> context) {
        SoulManager manager = SoulManager.get();
        if (manager == null) {
            context.getSource().sendError(Text.literal("Soul system is not ready yet."));
        }
        return manager;
    }

    private static Optional<Soul> resolveSoul(CommandContext<ServerCommandSource> context, String argument) {
        String id = StringArgumentType.getString(context, argument);
        Optional<Soul> soul = SoulRegistry.get(id);
        if (soul.isEmpty()) {
            context.getSource().sendError(Text.literal("Unknown soul: " + id
                    + ". Try /souls list for the full set."));
        }
        return soul;
    }

    private static MutableText line(String label, Text value) {
        return Text.literal(label).formatted(Formatting.GRAY).append(value);
    }

    private static String trim(double value) {
        return Math.abs(value - Math.rint(value)) < 0.001
                ? String.valueOf((long) Math.rint(value))
                : String.format("%.2f", value);
    }

    /** Kept for reference by anything that wants the raw difficulty list. */
    public static List<String> difficultyNames() {
        return java.util.Arrays.stream(SoulDifficulty.values()).map(Enum::name).toList();
    }
}
