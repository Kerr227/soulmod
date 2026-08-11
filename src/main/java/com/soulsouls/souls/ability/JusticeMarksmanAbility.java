package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Justice is about the bow.
 *
 * <p>Every arrow, crossbow bolt or trident a Justice player fires gets a small damage
 * multiplier applied on top of whatever the weapon and its enchantments already produced,
 * so it stacks correctly with Power and does not overwrite it.
 *
 * <p>The active ability ({@code /souls ability}) is Focus: for a few seconds, shots get a
 * second, larger multiplier. It is on a cooldown that survives relogging.
 */
public class JusticeMarksmanAbility implements SoulAbility {
    public static final String COOLDOWN = "justice_focus";
    private static final String FOCUS_UNTIL = "justice_focus_until";
    private static final String STREAK_BONUS = "justice_streak_bonus";
    private static final String SHOTS_IN_FLIGHT = "justice_shots_in_flight";
    private static final String LAST_SHOT_AT = "justice_last_shot_at";

    @Override
    public String id() {
        return "justice_marksman";
    }

    @Override
    public String displayName() {
        return "Marksman";
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public List<String> cooldownKeys() {
        return List.of(COOLDOWN);
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                "justice_projectile_multiplier", 2.0,
                "justice_streak_step", 0.1,
                "justice_streak_max", 1.0,
                "justice_focus_multiplier", 1.35,
                "justice_focus_duration_seconds", 6.0,
                "justice_focus_cooldown_seconds", 45.0,
                "justice_miss_timeout_seconds", 4.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  Arrow damage: ").formatted(Formatting.GRAY)
                        .append(Text.literal("x" + ctx.value("justice_projectile_multiplier")
                                        + ", +" + ctx.value("justice_streak_step") + " per hit up to +"
                                        + ctx.value("justice_streak_max"))
                                .formatted(Formatting.WHITE)),
                Text.literal("  Current streak bonus: ").formatted(Formatting.GRAY)
                        .append(Text.literal("+" + ctx.state(STREAK_BONUS, 0.0)).formatted(Formatting.WHITE)),
                Text.literal("  /souls ability -> Focus: ").formatted(Formatting.GRAY)
                        .append(Text.literal("x" + ctx.value("justice_focus_multiplier") + " for "
                                        + (int) ctx.value("justice_focus_duration_seconds") + "s")
                                .formatted(Formatting.WHITE))
        );
    }

    @Override
    public void onProjectileFired(AbilityContext ctx, PersistentProjectileEntity projectile) {
        // Base multiplier, plus whatever the current hit streak has earned.
        double multiplier = ctx.value("justice_projectile_multiplier") + ctx.state(STREAK_BONUS, 0.0);

        if (ctx.now() < ctx.state(FOCUS_UNTIL, 0.0)) {
            multiplier *= ctx.value("justice_focus_multiplier");
            projectile.setCritical(true);
        }

        if (multiplier != 1.0) {
            // Scales whatever damage the projectile already had, so Power enchantments still count.
            projectile.applyDamageModifier((float) multiplier);
        }

        // Every arrow is assumed to have missed until something tells us otherwise. The
        // arrow lands (or does not) long after this, so the streak is settled in onArrowHit.
        ctx.setState(SHOTS_IN_FLIGHT, 1.0);
        ctx.setState(LAST_SHOT_AT, ctx.now());
    }

    /**
     * A hit widens the multiplier by {@code justice_streak_step}, up to
     * {@code justice_streak_max}.
     */
    @Override
    public void onProjectileHit(AbilityContext ctx, net.minecraft.entity.LivingEntity victim) {
        double bonus = Math.min(ctx.value("justice_streak_max"),
                ctx.state(STREAK_BONUS, 0.0) + ctx.value("justice_streak_step"));
        ctx.setState(STREAK_BONUS, bonus);
        ctx.setState(SHOTS_IN_FLIGHT, 0.0);

        ctx.actionBar(Text.literal(String.format("Justice x%.2f",
                        ctx.value("justice_projectile_multiplier") + bonus))
                .formatted(Formatting.YELLOW));
    }

    @Override
    public void tick(AbilityContext ctx) {
        // A miss cannot be observed directly - an arrow that hits nothing just lands. So an
        // arrow that has not reported a hit within the timeout is counted as a miss, which
        // ends the streak.
        if (ctx.state(SHOTS_IN_FLIGHT, 0.0) <= 0.0) {
            return;
        }
        double firedAt = ctx.state(LAST_SHOT_AT, 0.0);
        if (firedAt <= 0.0 || ctx.now() - firedAt < ctx.value("justice_miss_timeout_seconds") * 1000.0) {
            return;
        }

        ctx.setState(SHOTS_IN_FLIGHT, 0.0);
        if (ctx.state(STREAK_BONUS, 0.0) > 0.0) {
            ctx.setState(STREAK_BONUS, 0.0);
            ctx.actionBar(Text.literal("Missed - back to standard.").formatted(Formatting.GRAY));
        }
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        if (!ctx.isReady(COOLDOWN)) {
            return false;
        }

        double durationSeconds = ctx.value("justice_focus_duration_seconds");
        ctx.setState(FOCUS_UNTIL, ctx.now() + durationSeconds * 1000.0);
        ctx.startCooldown(COOLDOWN, ctx.value("justice_focus_cooldown_seconds"));

        ctx.actionBar(Text.literal("Justice focuses.").formatted(Formatting.YELLOW));
        ctx.world().spawnParticles(ParticleTypes.END_ROD,
                ctx.player().getX(), ctx.player().getY() + 1.2, ctx.player().getZ(),
                25, 0.4, 0.5, 0.4, 0.02);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, net.minecraft.sound.SoundCategory.PLAYERS,
                0.8F, 1.6F);
        return true;
    }
}
