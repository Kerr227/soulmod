package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Fury builds up under punishment and then lets go.
 *
 * <p>Damage taken accumulates. Once enough has piled up, a Warden's roar goes off: everyone
 * within a few blocks is pinned in place and blinded, while Fury itself gets Strength IV and
 * heals. It then goes quiet for a long cooldown.
 *
 * <p>"Cannot move at all" is Slowness at a very high amplifier, which is how vanilla itself
 * immobilises - there is no separate root effect.
 */
public class FuryAbility implements SoulAbility {
    public static final String COOLDOWN = "fury_roar";
    private static final String DAMAGE_POOL = "fury_damage_pool";

    @Override
    public String id() {
        return "fury";
    }

    @Override
    public String displayName() {
        return "Wrath";
    }

    @Override
    public List<String> cooldownKeys() {
        return List.of(COOLDOWN);
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.ofEntries(
                Map.entry("fury_trigger_damage", 20.0),
                Map.entry("fury_radius", 5.0),
                Map.entry("fury_hold_seconds", 4.0),
                Map.entry("fury_slowness_amplifier", 6.0),
                Map.entry("fury_strength_amplifier", 3.0),
                Map.entry("fury_strength_seconds", 8.0),
                Map.entry("fury_regeneration_amplifier", 2.0),
                Map.entry("fury_cooldown_seconds", 200.0)
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  Take ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("fury_trigger_damage") + " damage")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" to build the roar").formatted(Formatting.GRAY)),
                Text.literal("  Roar: everyone within ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("fury_radius") + " blocks")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" is pinned and blinded").formatted(Formatting.GRAY)),
                Text.literal("  You get Strength ").formatted(Formatting.GRAY)
                        .append(Text.literal(BraveryMomentumAbility.roman(
                                        (int) ctx.value("fury_strength_amplifier") + 1))
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" and heal").formatted(Formatting.GRAY)),
                Text.literal("  Charge: ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.state(DAMAGE_POOL, 0.0) + "/"
                                        + (int) ctx.value("fury_trigger_damage"))
                                .formatted(Formatting.WHITE)));
    }

    @Override
    public void afterDamage(AbilityContext ctx, DamageSource source, float taken) {
        if (!ctx.isReady(COOLDOWN)) {
            return;
        }

        double pool = ctx.state(DAMAGE_POOL, 0.0) + taken;
        if (pool < ctx.value("fury_trigger_damage")) {
            ctx.setState(DAMAGE_POOL, pool);
            return;
        }

        ctx.setState(DAMAGE_POOL, 0.0);
        roar(ctx);
    }

    private void roar(AbilityContext ctx) {
        ctx.startCooldown(COOLDOWN, ctx.value("fury_cooldown_seconds"));

        int holdTicks = (int) Math.round(ctx.value("fury_hold_seconds") * 20.0);
        int slowness = Math.max(0, (int) ctx.value("fury_slowness_amplifier"));
        double radius = ctx.value("fury_radius");
        double radiusSquared = radius * radius;

        for (ServerPlayerEntity nearby : ctx.world().getPlayers()) {
            if (nearby == ctx.player() || nearby.squaredDistanceTo(ctx.player()) > radiusSquared) {
                continue;
            }
            nearby.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, holdTicks,
                    slowness, false, true, true));
            nearby.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, holdTicks,
                    0, false, true, true));
            nearby.sendMessage(Text.literal("Something furious just screamed.")
                    .formatted(Formatting.DARK_RED), true);
        }

        int strengthTicks = (int) Math.round(ctx.value("fury_strength_seconds") * 20.0);
        ctx.effect(StatusEffects.STRENGTH, strengthTicks,
                Math.max(0, (int) ctx.value("fury_strength_amplifier")));
        ctx.effect(StatusEffects.REGENERATION, strengthTicks,
                Math.max(0, (int) ctx.value("fury_regeneration_amplifier")));

        ctx.actionBar(Text.literal("FURY").formatted(Formatting.DARK_RED, Formatting.BOLD));
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_WARDEN_ROAR, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        ctx.world().spawnParticles(ParticleTypes.SCULK_SOUL,
                ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                60, radius / 2.0, 1.0, radius / 2.0, 0.1);
    }
}
