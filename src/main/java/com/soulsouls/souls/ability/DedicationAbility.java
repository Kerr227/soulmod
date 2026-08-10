package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Dedication buys you time, not safety.
 *
 * <p>A lethal hit leaves the player on 1 HP and starts a countdown. Heal back above the
 * recovery threshold before it runs out and you live; fail and you die anyway, at the end
 * of the timer. Taking another lethal hit while the state is running does not extend it.
 *
 * <p>The countdown is a wall-clock deadline held in saved data, so logging out during it
 * does not cancel it: the timer is still running when you come back.
 */
public class DedicationAbility implements SoulAbility {
    public static final String COOLDOWN = "dedication";
    private static final String ACTIVE_UNTIL = "dedication_active_until";

    @Override
    public String id() {
        return "dedication";
    }

    @Override
    public String displayName() {
        return "Last Dedication";
    }

    @Override
    public List<String> cooldownKeys() {
        return List.of(COOLDOWN);
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                "dedication_duration_seconds", 10.0,
                "dedication_recovery_health", 6.0,
                "dedication_cooldown_seconds", 900.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  Survives a lethal hit at 1 HP for ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("dedication_duration_seconds") + "s")
                                .formatted(Formatting.WHITE)),
                Text.literal("  Heal to ").formatted(Formatting.GRAY)
                        .append(Text.literal(com.soulsouls.util.SoulText.hearts(ctx.value("dedication_recovery_health")))
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" in time or you die anyway").formatted(Formatting.GRAY))
        );
    }

    @Override
    public boolean allowDeath(AbilityContext ctx, DamageSource source, float amount) {
        if (!ctx.isVulnerable()) {
            return true;
        }
        // Already dedicating, or still on cooldown: this death is real.
        if (ctx.state(ACTIVE_UNTIL, 0.0) > 0.0 || !ctx.isReady(COOLDOWN)) {
            return true;
        }

        double durationSeconds = ctx.value("dedication_duration_seconds");
        ctx.setState(ACTIVE_UNTIL, ctx.now() + durationSeconds * 1000.0);
        ctx.startCooldown(COOLDOWN, ctx.value("dedication_cooldown_seconds") + durationSeconds);

        ctx.player().setHealth(1.0F);
        ctx.player().extinguish();
        ctx.effect(StatusEffects.GLOWING, (int) Math.round(durationSeconds * 20.0), 0);

        ctx.message(Text.literal("You are not done yet.")
                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        ctx.world().spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                60, 0.5, 0.8, 0.5, 0.2);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ITEM_TOTEM_USE, net.minecraft.sound.SoundCategory.PLAYERS, 0.8F, 1.4F);
        return false;
    }

    @Override
    public void tick(AbilityContext ctx) {
        double activeUntil = ctx.state(ACTIVE_UNTIL, 0.0);
        if (activeUntil <= 0.0) {
            return;
        }

        float recovery = (float) ctx.value("dedication_recovery_health");

        if (ctx.player().getHealth() >= recovery) {
            ctx.clearState(ACTIVE_UNTIL);
            ctx.actionBar(Text.literal("You held on.").formatted(Formatting.LIGHT_PURPLE));
            ctx.world().spawnParticles(ParticleTypes.HEART,
                    ctx.player().getX(), ctx.player().getY() + 1.8, ctx.player().getZ(),
                    12, 0.4, 0.4, 0.4, 0.02);
            return;
        }

        long millisLeft = (long) (activeUntil - ctx.now());
        if (millisLeft > 0) {
            ctx.actionBar(Text.literal("Dedication: " + Math.max(1, (millisLeft + 999) / 1000) + "s")
                    .formatted(Formatting.LIGHT_PURPLE));
            ctx.world().spawnParticles(ParticleTypes.SOUL,
                    ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                    3, 0.3, 0.5, 0.3, 0.01);
            return;
        }

        // Time is up and they never recovered.
        ctx.clearState(ACTIVE_UNTIL);
        ctx.message(Text.literal("Your dedication was not enough.").formatted(Formatting.DARK_PURPLE));
        ctx.manager().killBypassingSouls(ctx.player(), ctx.world().getDamageSources().genericKill());
    }

    @Override
    public void onRespawn(AbilityContext ctx) {
        // A respawned player is not mid-sacrifice any more.
        ctx.clearState(ACTIVE_UNTIL);
    }
}
