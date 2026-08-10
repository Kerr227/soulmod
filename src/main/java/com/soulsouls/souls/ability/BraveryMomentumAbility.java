package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Bravery rewards keeping moving, and nothing else.
 *
 * <p>Once the player has been moving for a moment they get a small Speed effect, which
 * runs out shortly after they stop. Deliberately modest: Speed I by default, no attack
 * bonus, no permanent buff, and it is configurable down to nothing.
 */
public class BraveryMomentumAbility implements SoulAbility {
    private static final String MOMENTUM = "bravery_momentum_ticks";

    @Override
    public String id() {
        return "bravery_momentum";
    }

    @Override
    public String displayName() {
        return "Momentum";
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                // Blocks per tick that counts as "moving" - normal walking is about 0.21.
                "bravery_move_threshold", 0.08,
                "bravery_build_up_seconds", 1.5,
                "bravery_linger_seconds", 2.0,
                "bravery_speed_amplifier", 0.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        int level = (int) ctx.value("bravery_speed_amplifier") + 1;
        return List.of(
                Text.literal("  While moving: ").formatted(Formatting.GRAY)
                        .append(Text.literal("Speed " + roman(level)).formatted(Formatting.WHITE)),
                Text.literal("  Builds up after ").formatted(Formatting.GRAY)
                        .append(Text.literal(trim(ctx.value("bravery_build_up_seconds")) + "s")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(", fades ").formatted(Formatting.GRAY))
                        .append(Text.literal(trim(ctx.value("bravery_linger_seconds")) + "s")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" after stopping").formatted(Formatting.GRAY))
        );
    }

    @Override
    public void onRemove(AbilityContext ctx) {
        ctx.clearState(MOMENTUM);
    }

    @Override
    public void tick(AbilityContext ctx) {
        double moved = ctx.manager().distanceMovedLastTick(ctx.player());
        double perTick = moved / Math.max(1, ctx.tickInterval());

        double momentum = ctx.state(MOMENTUM, 0.0);
        double buildUpTicks = Math.max(1.0, ctx.value("bravery_build_up_seconds") * 20.0);

        if (perTick >= ctx.value("bravery_move_threshold")) {
            momentum = Math.min(buildUpTicks, momentum + ctx.tickInterval());
        } else {
            // Momentum drains twice as fast as it builds, so stopping actually costs something.
            momentum = Math.max(0.0, momentum - ctx.tickInterval() * 2.0);
        }
        ctx.data().setState(MOMENTUM, momentum);

        if (momentum >= buildUpTicks) {
            int lingerTicks = (int) Math.round(ctx.value("bravery_linger_seconds") * 20.0);
            int amplifier = Math.max(0, (int) ctx.value("bravery_speed_amplifier"));
            // Refresh rather than re-apply, so the client is not spammed with effect packets.
            ctx.refreshEffect(StatusEffects.SPEED, lingerTicks + ctx.tickInterval(), amplifier);
        }
    }

    static String roman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }

    static String trim(double value) {
        return Math.abs(value - Math.rint(value)) < 0.01
                ? String.valueOf((long) Math.rint(value))
                : String.valueOf(value);
    }
}
