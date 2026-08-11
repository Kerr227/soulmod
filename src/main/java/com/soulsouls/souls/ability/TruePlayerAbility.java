package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Determination's reward for a killing streak: TRUE PLAYER.
 *
 * <p>Ten player kills without dying and the Soul announces itself - a Warden's roar, every
 * player within ten blocks slowed to a crawl, and Strength V for the one who earned it. It
 * all falls away after ten seconds, and the streak resets to zero whether it fires or not.
 *
 * <p>Dying breaks the streak, which is what keeps ten in a row meaningful.
 */
public class TruePlayerAbility implements SoulAbility {
    private static final String STREAK = "true_player_streak";
    private static final String ACTIVE_UNTIL = "true_player_active_until";

    @Override
    public String id() {
        return "true_player";
    }

    @Override
    public String displayName() {
        return "TRUE PLAYER";
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                "true_player_kills_required", 10.0,
                "true_player_radius", 10.0,
                "true_player_duration_seconds", 10.0,
                "true_player_strength_amplifier", 4.0,
                "true_player_slowness_amplifier", 5.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  Kill ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("true_player_kills_required")
                                        + " players in a row")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" without dying:").formatted(Formatting.GRAY)),
                Text.literal("    a Warden's roar, everyone within ").formatted(Formatting.DARK_GRAY)
                        .append(Text.literal((int) ctx.value("true_player_radius") + " blocks")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" slowed, Strength V for you")
                                .formatted(Formatting.DARK_GRAY)),
                Text.literal("  Streak: ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.state(STREAK, 0.0) + "/"
                                        + (int) ctx.value("true_player_kills_required"))
                                .formatted(Formatting.RED)));
    }

    @Override
    public void onKill(AbilityContext ctx, LivingEntity victim) {
        if (!(victim instanceof ServerPlayerEntity)) {
            return;
        }

        double streak = ctx.state(STREAK, 0.0) + 1.0;
        double required = ctx.value("true_player_kills_required");

        if (streak < required) {
            ctx.setState(STREAK, streak);
            ctx.actionBar(Text.literal("Streak " + (int) streak + "/" + (int) required)
                    .formatted(Formatting.RED));
            return;
        }

        ctx.setState(STREAK, 0.0);
        unleash(ctx);
    }

    private void unleash(AbilityContext ctx) {
        double seconds = ctx.value("true_player_duration_seconds");
        int ticks = (int) Math.round(seconds * 20.0);
        ctx.setState(ACTIVE_UNTIL, ctx.now() + seconds * 1000.0);

        ctx.effect(StatusEffects.STRENGTH, ticks,
                Math.max(0, (int) ctx.value("true_player_strength_amplifier")));

        double radius = ctx.value("true_player_radius");
        double radiusSquared = radius * radius;
        int slowness = Math.max(0, (int) ctx.value("true_player_slowness_amplifier"));

        for (ServerPlayerEntity nearby : ctx.world().getPlayers()) {
            if (nearby == ctx.player() || nearby.squaredDistanceTo(ctx.player()) > radiusSquared) {
                continue;
            }
            nearby.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks,
                    slowness, false, true, true));
            nearby.sendMessage(Text.literal("A TRUE PLAYER is here.")
                    .formatted(Formatting.DARK_RED, Formatting.BOLD), false);
        }

        com.soulsouls.util.SoulEffects.title(ctx.player(),
                Text.literal("TRUE PLAYER").formatted(Formatting.RED, Formatting.BOLD),
                Text.literal("ten in a row").formatted(Formatting.GRAY),
                10, 60, 20);

        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                net.minecraft.sound.SoundEvents.ENTITY_WARDEN_ROAR,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        ctx.world().spawnParticles(ParticleTypes.SCULK_SOUL,
                ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                80, radius / 3.0, 1.0, radius / 3.0, 0.12);
    }

    /** Dying breaks the streak - the run has to be unbroken to count. */
    @Override
    public boolean allowDeath(AbilityContext ctx, DamageSource source, float amount) {
        if (ctx.state(STREAK, 0.0) > 0.0) {
            ctx.setState(STREAK, 0.0);
        }
        return true;
    }

    @Override
    public void tick(AbilityContext ctx) {
        double until = ctx.state(ACTIVE_UNTIL, 0.0);
        if (until > 0.0 && ctx.now() >= until) {
            ctx.clearState(ACTIVE_UNTIL);
        }
    }
}
