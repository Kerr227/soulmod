package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Integrity does not take fall damage. That is the whole Soul.
 *
 * <p>It hooks the damage event and cancels anything tagged {@code minecraft:is_fall}, which
 * covers ordinary falls, being knocked off a ledge, ender pearls and elytra crashes -
 * every fall the game itself calls a fall. No flight, no jump boost, no speed.
 */
public class IntegrityFallAbility implements SoulAbility {
    public static final String COOLDOWN = "integrity_launch";

    @Override
    public String id() {
        return "integrity_fall";
    }

    @Override
    public String displayName() {
        return "Unshaken";
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                // Set to 0 to turn fall immunity off entirely; 1 keeps it on.
                "integrity_fall_immunity", 1.0,
                "integrity_landing_particles", 1.0,
                "integrity_launch_amplifier", 9.0,
                "integrity_launch_seconds", 1.0,
                "integrity_launch_cooldown_seconds", 20.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        boolean immune = ctx.value("integrity_fall_immunity") > 0.0;
        return List.of(
                Text.literal("  Fall damage: ").formatted(Formatting.GRAY)
                        .append(Text.literal(immune ? "immune" : "normal").formatted(Formatting.WHITE)),
                Text.literal("  Double-sneak: ").formatted(Formatting.GRAY)
                        .append(Text.literal("launch straight up").formatted(Formatting.WHITE)));
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public List<String> cooldownKeys() {
        return List.of(COOLDOWN);
    }

    /**
     * Double-sneak to launch. A brief, very strong Levitation throws the player upwards -
     * which is only safe because Integrity ignores the landing.
     */
    @Override
    public boolean activate(AbilityContext ctx) {
        if (!ctx.isReady(COOLDOWN)) {
            return false;
        }

        int ticks = (int) Math.round(Math.max(0.1, ctx.value("integrity_launch_seconds")) * 20.0);
        int amplifier = Math.max(0, (int) ctx.value("integrity_launch_amplifier"));
        ctx.effect(StatusEffects.LEVITATION, ticks, amplifier);
        ctx.startCooldown(COOLDOWN, ctx.value("integrity_launch_cooldown_seconds"));

        ctx.actionBar(Text.literal("Up you go.").formatted(Formatting.BLUE));
        ctx.world().spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                ctx.player().getX(), ctx.player().getY() + 0.2, ctx.player().getZ(),
                30, 0.4, 0.2, 0.4, 0.08);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.5F, 1.9F);
        return true;
    }

    @Override
    public boolean allowDamage(AbilityContext ctx, DamageSource source, float amount) {
        if (ctx.value("integrity_fall_immunity") <= 0.0) {
            return true;
        }
        if (!source.isIn(DamageTypeTags.IS_FALL)) {
            return true;
        }

        if (ctx.value("integrity_landing_particles") > 0.0 && amount >= 3.0F) {
            ctx.world().spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    ctx.player().getX(), ctx.player().getY() + 0.1, ctx.player().getZ(),
                    Math.min(40, (int) (amount * 3)), 0.4, 0.1, 0.4, 0.05);
        }
        return false;
    }
}
