package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Retribution (Justice + Perseverance) answers for the people it is bonded to.
 *
 * <p>Bonds are mutual and are made with {@code /souls bond <player>}: both players have to
 * run it for each other. When a bonded player is killed <em>by another player</em>, the
 * Retribution player enters Vengeance for a while: Strength and Speed, and the killer is
 * outlined with Glowing so they can be found.
 *
 * <p>Vengeance has a long cooldown, ends on its own timer and never re-triggers on the same
 * killer while it is running, so nobody is permanently marked.
 */
public class RetributionAbility implements SoulAbility {
    public static final String COOLDOWN = "retribution_vengeance";
    private static final String VENGEANCE_UNTIL = "retribution_vengeance_until";

    @Override
    public String id() {
        return "retribution";
    }

    @Override
    public String displayName() {
        return "Vengeance";
    }

    @Override
    public List<String> cooldownKeys() {
        return List.of(COOLDOWN);
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                "retribution_vengeance_seconds", 60.0,
                "retribution_strength_amplifier", 0.0,
                "retribution_speed_amplifier", 0.0,
                "retribution_reveal_seconds", 30.0,
                "retribution_cooldown_seconds", 600.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  Bond with allies using ").formatted(Formatting.GRAY)
                        .append(Text.literal("/souls bond <player>").formatted(Formatting.WHITE)),
                Text.literal("  If a bonded ally is killed by a player: ").formatted(Formatting.GRAY),
                Text.literal("    Strength and Speed for ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("retribution_vengeance_seconds") + "s")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(", and the killer glows").formatted(Formatting.GRAY))
        );
    }

    @Override
    public void onOtherPlayerDeath(AbilityContext ctx, ServerPlayerEntity victim, ServerPlayerEntity killer) {
        if (killer == null || killer == ctx.player()) {
            return;
        }
        UUID victimId = victim.getUuid();
        if (!ctx.data().isBondedTo(victimId)) {
            return;
        }
        if (!ctx.isReady(COOLDOWN)) {
            return;
        }

        double durationSeconds = ctx.value("retribution_vengeance_seconds");
        int durationTicks = (int) Math.round(durationSeconds * 20.0);

        ctx.setState(VENGEANCE_UNTIL, ctx.now() + durationSeconds * 1000.0);
        ctx.startCooldown(COOLDOWN, ctx.value("retribution_cooldown_seconds"));

        ctx.effect(StatusEffects.STRENGTH, durationTicks,
                Math.max(0, (int) ctx.value("retribution_strength_amplifier")));
        ctx.effect(StatusEffects.SPEED, durationTicks,
                Math.max(0, (int) ctx.value("retribution_speed_amplifier")));

        int revealTicks = (int) Math.round(ctx.value("retribution_reveal_seconds") * 20.0);
        if (revealTicks > 0) {
            killer.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, revealTicks, 0,
                    true, false, true));
        }

        // A title, so it cannot be missed in the middle of a fight.
        com.soulsouls.util.SoulEffects.title(ctx.player(),
                Text.literal("GET REVENGE").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                Text.literal("kill ").formatted(Formatting.GRAY)
                        .append(Text.literal(killer.getNameForScoreboard())
                                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)),
                10, 80, 20);

        ctx.message(Text.literal(victim.getNameForScoreboard() + " has fallen. ")
                .formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal(killer.getNameForScoreboard())
                        .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
                .append(Text.literal(" will answer for it.").formatted(Formatting.LIGHT_PURPLE)));

        ctx.world().spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                ctx.player().getX(), ctx.player().getY() + 1.8, ctx.player().getZ(),
                20, 0.5, 0.4, 0.5, 0.02);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_WITHER_SPAWN, net.minecraft.sound.SoundCategory.PLAYERS, 0.35F, 1.7F);
    }

    @Override
    public void onAttack(AbilityContext ctx, Entity target) {
        // Purely flavour: while Vengeance is running, hits throw off angry particles.
        if (ctx.now() < ctx.state(VENGEANCE_UNTIL, 0.0)) {
            ctx.world().spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                    target.getX(), target.getY() + 1.0, target.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
        }
    }

    /** True while the Soul is in its Vengeance state; used by {@code /souls debug}. */
    public static boolean isAvenging(AbilityContext ctx) {
        return ctx.now() < ctx.state(VENGEANCE_UNTIL, 0.0);
    }
}
