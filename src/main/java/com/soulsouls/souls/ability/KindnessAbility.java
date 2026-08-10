package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Kindness protects - itself when hurt, and everyone nearby on demand.
 *
 * <p>Passively it regenerates once the player has been out of combat for a while, and
 * grants Resistance when badly hurt. The active ability heals and shields nearby players.
 * All of it is plain vanilla status effects, so it behaves predictably with milk, other
 * mods and PvP plugins.
 */
public class KindnessAbility implements SoulAbility {
    public static final String COOLDOWN = "kindness_aura";
    private static final String LAST_DAMAGE_AT = "kindness_last_damage_at";

    @Override
    public String id() {
        return "kindness";
    }

    @Override
    public String displayName() {
        return "Kind Soul";
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
                "kindness_out_of_combat_seconds", 12.0,
                "kindness_low_health_fraction", 0.35,
                "kindness_resistance_amplifier", 0.0,
                "kindness_aura_radius", 8.0,
                "kindness_aura_heal", 4.0,
                "kindness_aura_regeneration_seconds", 6.0,
                "kindness_aura_cooldown_seconds", 60.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  Regenerates after ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("kindness_out_of_combat_seconds") + "s")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" out of combat").formatted(Formatting.GRAY)),
                Text.literal("  Resistance below ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) (ctx.value("kindness_low_health_fraction") * 100) + "% health")
                                .formatted(Formatting.WHITE)),
                Text.literal("  /souls ability -> heals allies within ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("kindness_aura_radius") + " blocks")
                                .formatted(Formatting.WHITE))
        );
    }

    @Override
    public void afterDamage(AbilityContext ctx, DamageSource source, float taken) {
        ctx.data().setState(LAST_DAMAGE_AT, ctx.now());
    }

    @Override
    public void tick(AbilityContext ctx) {
        if (!ctx.isVulnerable()) {
            return;
        }

        // Out-of-combat regeneration.
        double sinceDamage = ctx.now() - ctx.state(LAST_DAMAGE_AT, 0.0);
        boolean outOfCombat = sinceDamage >= ctx.value("kindness_out_of_combat_seconds") * 1000.0;
        if (outOfCombat && ctx.player().getHealth() < ctx.player().getMaxHealth()) {
            ctx.refreshEffect(StatusEffects.REGENERATION, 60, 0);
        }

        // A cushion when things have gone badly.
        if (ctx.healthFraction() <= ctx.value("kindness_low_health_fraction")) {
            ctx.refreshEffect(StatusEffects.RESISTANCE, 60,
                    Math.max(0, (int) ctx.value("kindness_resistance_amplifier")));
        }
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        if (!ctx.isReady(COOLDOWN)) {
            return false;
        }

        double radius = ctx.value("kindness_aura_radius");
        double radiusSquared = radius * radius;
        float heal = (float) ctx.value("kindness_aura_heal");
        int regenerationTicks = (int) Math.round(ctx.value("kindness_aura_regeneration_seconds") * 20.0);

        int helped = 0;
        for (ServerPlayerEntity nearby : ctx.world().getPlayers()) {
            if (nearby.squaredDistanceTo(ctx.player()) > radiusSquared) {
                continue;
            }
            nearby.heal(heal);
            nearby.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    StatusEffects.REGENERATION, regenerationTicks, 0, true, false, true));
            ctx.world().spawnParticles(ParticleTypes.HEART,
                    nearby.getX(), nearby.getY() + 1.6, nearby.getZ(),
                    6, 0.4, 0.4, 0.4, 0.02);
            if (nearby != ctx.player()) {
                helped++;
                nearby.sendMessage(Text.literal("A kind soul reached out to you.")
                        .formatted(Formatting.GREEN), true);
            }
        }

        ctx.startCooldown(COOLDOWN, ctx.value("kindness_aura_cooldown_seconds"));
        ctx.actionBar(Text.literal(helped == 0
                        ? "You steady yourself."
                        : "You steady " + helped + (helped == 1 ? " ally." : " allies."))
                .formatted(Formatting.GREEN));
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, net.minecraft.sound.SoundCategory.PLAYERS, 0.7F, 1.8F);
        return true;
    }
}
