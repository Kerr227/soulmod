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
 * Perseverance gets tougher the worse things get.
 *
 * <p>Three layers, each on its own cooldown so none of them can be farmed:
 * a Resistance buff after a heavy hit, a small Strength boost while below a health
 * threshold, and a stronger last-stand Resistance when close to death.
 */
public class PerseveranceAbility implements SoulAbility {
    public static final String BRACE_COOLDOWN = "perseverance_brace";
    public static final String LAST_STAND_COOLDOWN = "perseverance_last_stand";

    @Override
    public String id() {
        return "perseverance";
    }

    @Override
    public String displayName() {
        return "Endure";
    }

    @Override
    public List<String> cooldownKeys() {
        return List.of(BRACE_COOLDOWN, LAST_STAND_COOLDOWN);
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                "perseverance_heavy_damage", 6.0,
                "perseverance_brace_seconds", 6.0,
                "perseverance_brace_cooldown_seconds", 25.0,
                "perseverance_low_health_fraction", 0.3,
                "perseverance_strength_amplifier", 0.0,
                "perseverance_last_stand_fraction", 0.15,
                "perseverance_last_stand_seconds", 6.0,
                "perseverance_last_stand_cooldown_seconds", 120.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  Resistance after taking ").formatted(Formatting.GRAY)
                        .append(Text.literal(BraveryMomentumAbility.trim(ctx.value("perseverance_heavy_damage"))
                                + " damage at once").formatted(Formatting.WHITE)),
                Text.literal("  Strength below ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) (ctx.value("perseverance_low_health_fraction") * 100) + "% health")
                                .formatted(Formatting.WHITE)),
                Text.literal("  Last stand below ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) (ctx.value("perseverance_last_stand_fraction") * 100) + "% health")
                                .formatted(Formatting.WHITE))
        );
    }

    @Override
    public void afterDamage(AbilityContext ctx, DamageSource source, float taken) {
        if (taken < ctx.value("perseverance_heavy_damage") || !ctx.isReady(BRACE_COOLDOWN)) {
            return;
        }

        int ticks = (int) Math.round(ctx.value("perseverance_brace_seconds") * 20.0);
        ctx.effect(StatusEffects.RESISTANCE, ticks, 0);
        ctx.startCooldown(BRACE_COOLDOWN, ctx.value("perseverance_brace_cooldown_seconds"));

        ctx.actionBar(Text.literal("You brace yourself.").formatted(Formatting.DARK_PURPLE));
        ctx.world().spawnParticles(ParticleTypes.ENCHANTED_HIT,
                ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                15, 0.4, 0.5, 0.4, 0.05);
    }

    @Override
    public void tick(AbilityContext ctx) {
        if (!ctx.isVulnerable()) {
            return;
        }

        double fraction = ctx.healthFraction();

        if (fraction <= ctx.value("perseverance_low_health_fraction")) {
            ctx.refreshEffect(StatusEffects.STRENGTH, 60,
                    Math.max(0, (int) ctx.value("perseverance_strength_amplifier")));
        }

        if (fraction <= ctx.value("perseverance_last_stand_fraction") && ctx.isReady(LAST_STAND_COOLDOWN)) {
            int ticks = (int) Math.round(ctx.value("perseverance_last_stand_seconds") * 20.0);
            ctx.effect(StatusEffects.RESISTANCE, ticks, 1);
            ctx.startCooldown(LAST_STAND_COOLDOWN, ctx.value("perseverance_last_stand_cooldown_seconds"));

            ctx.actionBar(Text.literal("You refuse to fall.").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
            ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                    SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.6F, 0.6F);
            ctx.world().spawnParticles(ParticleTypes.SCULK_SOUL,
                    ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                    25, 0.5, 0.6, 0.5, 0.03);
        }
    }
}
