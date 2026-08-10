package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Hatred hurts what it touches, and then it hurts its owner.
 *
 * <p>Wither Touch: hitting a player inflicts a short, low-level Wither, on a per-attacker
 * cooldown so a fast weapon cannot stack it into an execution.
 *
 * <p>Instability: every kill adds a stack. Stacks make the Wither last longer, but past a
 * threshold the Soul starts turning on its owner - Nausea and Weakness, and a slow drain of
 * health. Stacks decay on their own, so the Soul is strongest right after a fight and
 * settles back down if the player stops killing.
 */
public class HatredAbility implements SoulAbility {
    public static final String TOUCH_COOLDOWN = "hatred_touch";
    private static final String STACKS = "hatred_stacks";
    private static final String LAST_DECAY_AT = "hatred_last_decay_at";

    @Override
    public String id() {
        return "hatred_touch";
    }

    @Override
    public String displayName() {
        return "Wither Touch";
    }

    @Override
    public List<String> cooldownKeys() {
        return List.of(TOUCH_COOLDOWN);
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.ofEntries(
                Map.entry("hatred_wither_seconds", 4.0),
                Map.entry("hatred_wither_amplifier", 0.0),
                Map.entry("hatred_touch_cooldown_seconds", 3.0),
                Map.entry("hatred_affects_mobs", 0.0),
                Map.entry("hatred_max_stacks", 10.0),
                Map.entry("hatred_seconds_per_stack", 0.3),
                Map.entry("hatred_stack_decay_seconds", 120.0),
                Map.entry("hatred_instability_threshold", 5.0),
                Map.entry("hatred_instability_damage", 1.0)
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        int stacks = (int) ctx.state(STACKS, 0.0);
        return List.of(
                Text.literal("  Hits apply Wither ").formatted(Formatting.GRAY)
                        .append(Text.literal(BraveryMomentumAbility.roman(
                                        (int) ctx.value("hatred_wither_amplifier") + 1))
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" for ").formatted(Formatting.GRAY))
                        .append(Text.literal(BraveryMomentumAbility.trim(ctx.value("hatred_wither_seconds")) + "s")
                                .formatted(Formatting.WHITE)),
                Text.literal("  Instability: ").formatted(Formatting.GRAY)
                        .append(Text.literal(stacks + "/" + (int) ctx.value("hatred_max_stacks") + " stacks")
                                .formatted(Formatting.WHITE)),
                Text.literal("  Above ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("hatred_instability_threshold") + " stacks")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" the Soul turns on you").formatted(Formatting.GRAY))
        );
    }

    @Override
    public void onAttack(AbilityContext ctx, Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        boolean isPlayer = target instanceof ServerPlayerEntity;
        if (!isPlayer && ctx.value("hatred_affects_mobs") <= 0.0) {
            return;
        }
        if (!ctx.isReady(TOUCH_COOLDOWN)) {
            return;
        }

        int stacks = (int) ctx.state(STACKS, 0.0);
        double seconds = ctx.value("hatred_wither_seconds") + stacks * ctx.value("hatred_seconds_per_stack");
        int ticks = (int) Math.round(seconds * 20.0);
        int amplifier = Math.max(0, (int) ctx.value("hatred_wither_amplifier"));

        living.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, ticks, amplifier,
                false, true, true));
        ctx.startCooldown(TOUCH_COOLDOWN, ctx.value("hatred_touch_cooldown_seconds"));

        ctx.world().spawnParticles(ParticleTypes.SMOKE,
                target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
    }

    @Override
    public void onKill(AbilityContext ctx, LivingEntity victim) {
        if (!(victim instanceof ServerPlayerEntity) && !(victim instanceof Monster)) {
            return;
        }
        double max = ctx.value("hatred_max_stacks");
        double stacks = Math.min(max, ctx.state(STACKS, 0.0) + 1.0);
        ctx.setState(STACKS, stacks);

        if (stacks >= ctx.value("hatred_instability_threshold")) {
            ctx.actionBar(Text.literal("The hatred is getting harder to hold.")
                    .formatted(Formatting.DARK_GRAY));
        }
    }

    @Override
    public void tick(AbilityContext ctx) {
        double stacks = ctx.state(STACKS, 0.0);

        // Stacks bleed away over time.
        double decaySeconds = ctx.value("hatred_stack_decay_seconds");
        if (stacks > 0.0 && decaySeconds > 0.0) {
            double lastDecay = ctx.state(LAST_DECAY_AT, 0.0);
            if (lastDecay <= 0.0) {
                ctx.data().setState(LAST_DECAY_AT, ctx.now());
            } else if (ctx.now() - lastDecay >= decaySeconds * 1000.0) {
                stacks = Math.max(0.0, stacks - 1.0);
                ctx.setState(STACKS, stacks);
                ctx.data().setState(LAST_DECAY_AT, ctx.now());
            }
        }

        if (!ctx.isVulnerable()) {
            return;
        }

        // Constant, quiet reminder of what the player is carrying.
        ctx.world().spawnParticles(ParticleTypes.SMOKE,
                ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                1, 0.3, 0.5, 0.3, 0.005);

        double threshold = ctx.value("hatred_instability_threshold");
        if (stacks < threshold) {
            return;
        }

        int overload = (int) (stacks - threshold) + 1;
        ctx.refreshEffect(StatusEffects.NAUSEA, 100, 0);
        if (overload >= 3) {
            ctx.refreshEffect(StatusEffects.WEAKNESS, 100, 0);
        }

        // The Soul feeds on its owner once it is this unstable. Never lethal on its own:
        // it stops at half a heart.
        float drain = (float) ctx.value("hatred_instability_damage") * overload / 20.0F * ctx.tickInterval();
        float health = ctx.player().getHealth();
        if (health - drain > 1.0F) {
            ctx.player().setHealth(health - drain);
            if (Math.random() < 0.05) {
                ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                        SoundEvents.ENTITY_WITHER_SPAWN, net.minecraft.sound.SoundCategory.PLAYERS, 0.15F, 2.0F);
            }
        }
    }
}
