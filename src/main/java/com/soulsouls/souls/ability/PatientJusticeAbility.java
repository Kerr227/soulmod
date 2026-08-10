package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.Map;

/**
 * Patient Justice (Patience + Justice) trades time for power.
 *
 * <p>{@code /souls ability} starts charging: the player gains Resistance while they hold
 * still enough to concentrate. Running it again releases the charge as a burst that damages
 * hostile mobs around them, scaled by how long they charged. Letting the charge run past its
 * maximum simply caps it.
 *
 * <p>No time manipulation is involved - "charging" is a timer plus a status effect.
 */
public class PatientJusticeAbility implements SoulAbility {
    public static final String COOLDOWN = "patient_justice_release";
    private static final String CHARGE_STARTED_AT = "patient_justice_charge_started";

    @Override
    public String id() {
        return "patient_justice";
    }

    @Override
    public String displayName() {
        return "Charged Verdict";
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
                "pj_max_charge_seconds", 6.0,
                "pj_charge_resistance_amplifier", 1.0,
                "pj_min_damage", 4.0,
                "pj_max_damage", 14.0,
                "pj_radius", 5.0,
                "pj_cooldown_seconds", 40.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  /souls ability -> start charging (Resistance while charging)")
                        .formatted(Formatting.GRAY),
                Text.literal("  /souls ability again -> release, ").formatted(Formatting.GRAY)
                        .append(Text.literal(BraveryMomentumAbility.trim(ctx.value("pj_min_damage")) + "-"
                                        + BraveryMomentumAbility.trim(ctx.value("pj_max_damage")) + " damage")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" in ").formatted(Formatting.GRAY))
                        .append(Text.literal((int) ctx.value("pj_radius") + " blocks")
                                .formatted(Formatting.WHITE))
        );
    }

    @Override
    public void onRemove(AbilityContext ctx) {
        ctx.clearState(CHARGE_STARTED_AT);
    }

    @Override
    public void tick(AbilityContext ctx) {
        double startedAt = ctx.state(CHARGE_STARTED_AT, 0.0);
        if (startedAt <= 0.0) {
            return;
        }

        double heldSeconds = (ctx.now() - startedAt) / 1000.0;
        double maxSeconds = ctx.value("pj_max_charge_seconds");

        ctx.refreshEffect(StatusEffects.RESISTANCE, 40,
                Math.max(0, (int) ctx.value("pj_charge_resistance_amplifier")));

        double fraction = Math.min(1.0, heldSeconds / Math.max(0.1, maxSeconds));
        ctx.actionBar(Text.literal("Charging: " + (int) (fraction * 100) + "%")
                .formatted(fraction >= 1.0 ? Formatting.GREEN : Formatting.GRAY));

        ctx.world().spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                (int) (2 + fraction * 6), 0.4, 0.6, 0.4, 0.01);
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        double startedAt = ctx.state(CHARGE_STARTED_AT, 0.0);

        if (startedAt <= 0.0) {
            if (!ctx.isReady(COOLDOWN)) {
                return false;
            }
            ctx.setState(CHARGE_STARTED_AT, ctx.now());
            ctx.actionBar(Text.literal("You begin to focus.").formatted(Formatting.GREEN));
            ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE, net.minecraft.sound.SoundCategory.PLAYERS, 0.5F, 0.8F);
            return true;
        }

        return release(ctx, startedAt);
    }

    private boolean release(AbilityContext ctx, double startedAt) {
        double heldSeconds = (ctx.now() - startedAt) / 1000.0;
        double maxSeconds = Math.max(0.1, ctx.value("pj_max_charge_seconds"));
        double fraction = Math.min(1.0, heldSeconds / maxSeconds);

        double minDamage = ctx.value("pj_min_damage");
        double maxDamage = ctx.value("pj_max_damage");
        float damage = (float) (minDamage + (maxDamage - minDamage) * fraction);

        double radius = ctx.value("pj_radius");
        Box area = ctx.player().getBoundingBox().expand(radius);

        int hit = 0;
        for (Entity entity : ctx.world().getOtherEntities(ctx.player(), area)) {
            // Hostiles only: this is a burst, not an area attack that clips your own animals.
            if (!(entity instanceof LivingEntity living) || !(entity instanceof Monster)) {
                continue;
            }
            if (living.squaredDistanceTo(ctx.player()) > radius * radius) {
                continue;
            }
            living.damage(ctx.world(), ctx.world().getDamageSources().playerAttack(ctx.player()), damage);
            hit++;
        }

        ctx.clearState(CHARGE_STARTED_AT);
        ctx.startCooldown(COOLDOWN, ctx.value("pj_cooldown_seconds"));

        ctx.actionBar(Text.literal("Verdict delivered: " + (int) damage + " damage to " + hit
                + (hit == 1 ? " target" : " targets")).formatted(Formatting.GREEN));
        ctx.world().spawnParticles(ParticleTypes.END_ROD,
                ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                (int) (30 + fraction * 60), radius / 3.0, 0.6, radius / 3.0, 0.08);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.sound.SoundCategory.PLAYERS,
                0.8F, (float) (0.8 + fraction * 0.6));
        return true;
    }
}
