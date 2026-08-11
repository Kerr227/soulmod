package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/**
 * Courage (Bravery + Justice) charges in and covers the people around it.
 *
 * <p>{@code /souls ability} throws the player forward in the direction they are looking and
 * starts a short Charge: Speed and Strength for the Courage player, and Resistance for
 * nearby allies.
 *
 * <p>Deviation from the brief worth knowing about: "take some of an ally's incoming damage"
 * is not something vanilla can express without rewriting the damage pipeline, so allies are
 * given Resistance instead. The effect is the same - they take less - but the damage is
 * absorbed rather than transferred to the Courage player.
 */
public class CourageDashAbility implements SoulAbility {
    public static final String COOLDOWN = "courage_charge";

    @Override
    public String id() {
        return "courage_charge";
    }

    @Override
    public String displayName() {
        return "Heroic Charge";
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
                "courage_dash_power", 1.1,
                "courage_dash_lift", 0.35,
                "courage_charge_seconds", 6.0,
                "courage_speed_amplifier", 1.0,
                "courage_strength_amplifier", 0.0,
                "courage_guard_radius", 6.0,
                "courage_guard_seconds", 6.0,
                "courage_cooldown_seconds", 30.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  /souls ability -> dash forward").formatted(Formatting.GRAY),
                Text.literal("  Speed and Strength for ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("courage_charge_seconds") + "s")
                                .formatted(Formatting.WHITE)),
                Text.literal("  Allies within ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("courage_guard_radius") + " blocks")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" gain Resistance").formatted(Formatting.GRAY))
        );
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        if (!ctx.isReady(COOLDOWN)) {
            return false;
        }

        ServerPlayerEntity player = ctx.player();

        // Push the player along their line of sight. A player's position is client-driven,
        // so the new velocity has to be pushed to them explicitly or nothing happens.
        Vec3d look = player.getRotationVector();
        Vec3d dash = new Vec3d(look.x, 0.0, look.z).normalize().multiply(ctx.value("courage_dash_power"));
        player.setVelocity(dash.x, ctx.value("courage_dash_lift"), dash.z);
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

        int chargeTicks = (int) Math.round(ctx.value("courage_charge_seconds") * 20.0);
        ctx.effect(StatusEffects.SPEED, chargeTicks, Math.max(0, (int) ctx.value("courage_speed_amplifier")));
        ctx.effect(StatusEffects.STRENGTH, chargeTicks, Math.max(0, (int) ctx.value("courage_strength_amplifier")));

        double radius = ctx.value("courage_guard_radius");
        double radiusSquared = radius * radius;
        int guardTicks = (int) Math.round(ctx.value("courage_guard_seconds") * 20.0);
        for (ServerPlayerEntity ally : ctx.world().getPlayers()) {
            if (ally == player || ally.squaredDistanceTo(player) > radiusSquared) {
                continue;
            }
            ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, guardTicks, 0,
                    true, false, true));
            ally.sendMessage(Text.literal("Courage stands with you.").formatted(Formatting.GOLD), true);
        }

        ctx.startCooldown(COOLDOWN, ctx.value("courage_cooldown_seconds"));
        ctx.actionBar(Text.literal("Charge!").formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.world().spawnParticles(ParticleTypes.FLAME,
                player.getX(), player.getY() + 0.4, player.getZ(), 30, 0.4, 0.2, 0.4, 0.06);
        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.7F, 1.2F);
        return true;
    }
}
