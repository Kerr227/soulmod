package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
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
 * Humility believes it is losing, right up until it hits back.
 *
 * <p>The cycle has two halves:
 * <ul>
 *   <li><b>Cowed</b> - once one person has done enough damage to it, Humility gets
 *       Resistance II and Weakness. It is hard to kill and hits for almost nothing, and it
 *       stays that way until it strikes the person who did it.</li>
 *   <li><b>Emboldened</b> - the moment it lands that hit, the Resistance and Weakness drop
 *       and it fights with Strength for a minute. When that runs out the cycle starts over.</li>
 * </ul>
 */
public class HumilityAbility implements SoulAbility {
    private static final String THREAT_UUID = "humility_threat";
    private static final String THREAT_DAMAGE = "humility_threat_damage";
    private static final String COWED = "humility_cowed";
    private static final String EMBOLDENED_UNTIL = "humility_emboldened_until";

    @Override
    public String id() {
        return "humility";
    }

    @Override
    public String displayName() {
        return "Cowed and Emboldened";
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                // Damage one attacker must deal before Humility is cowed.
                "humility_threat_damage", 12.0,
                "humility_resistance_amplifier", 1.0,
                "humility_weakness_amplifier", 0.0,
                "humility_strength_amplifier", 1.0,
                "humility_emboldened_seconds", 60.0,
                // How long a threat is remembered without further damage, in seconds.
                "humility_threat_memory_seconds", 30.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        boolean cowed = ctx.state(COWED, 0.0) > 0.0;
        boolean bold = ctx.now() < ctx.state(EMBOLDENED_UNTIL, 0.0);
        return List.of(
                Text.literal("  Take ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("humility_threat_damage") + " damage")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" from one person and you are cowed:")
                                .formatted(Formatting.GRAY)),
                Text.literal("    Resistance II and Weakness until you hit them back")
                        .formatted(Formatting.DARK_GRAY),
                Text.literal("  Then Strength for ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("humility_emboldened_seconds") + "s")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(", and the cycle begins again").formatted(Formatting.GRAY)),
                Text.literal("  Currently: ").formatted(Formatting.GRAY)
                        .append(Text.literal(cowed ? "cowed" : bold ? "emboldened" : "steady")
                                .formatted(cowed ? Formatting.BLUE
                                        : bold ? Formatting.GOLD : Formatting.WHITE)));
    }

    @Override
    public void afterDamage(AbilityContext ctx, DamageSource source, float taken) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return;
        }
        // Already cowed, or still riding the boldness: no new threat is tracked.
        if (ctx.state(COWED, 0.0) > 0.0 || ctx.now() < ctx.state(EMBOLDENED_UNTIL, 0.0)) {
            return;
        }

        String key = THREAT_UUID + "_" + attacker.getUuid();
        double total = ctx.state(key, 0.0) + taken;
        ctx.setState(key, total);
        ctx.setState(THREAT_DAMAGE, total);

        if (total < ctx.value("humility_threat_damage")) {
            return;
        }

        // This one has hurt us enough to be worth fearing.
        ctx.data().remembered_names.put(THREAT_UUID, attacker.getUuid().toString());
        ctx.setState(COWED, 1.0);
        ctx.clearState(key);
        ctx.manager().markDirty();

        ctx.message(Text.literal(attacker.getNameForScoreboard() + " is stronger than you.")
                .formatted(Formatting.BLUE));
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_BEACON_DEACTIVATE, net.minecraft.sound.SoundCategory.PLAYERS, 0.5F, 0.6F);
    }

    @Override
    public void onAttack(AbilityContext ctx, Entity target) {
        if (ctx.state(COWED, 0.0) <= 0.0) {
            return;
        }

        String threat = ctx.data().remembered_names.get(THREAT_UUID);
        if (threat == null || !(target instanceof ServerPlayerEntity victim)) {
            return;
        }
        if (!victim.getUuid().equals(parse(threat))) {
            return;
        }

        // Hitting the one you feared is what breaks the spell.
        ctx.setState(COWED, 0.0);
        ctx.data().remembered_names.remove(THREAT_UUID);
        ctx.setState(EMBOLDENED_UNTIL,
                ctx.now() + ctx.value("humility_emboldened_seconds") * 1000.0);
        ctx.manager().markDirty();

        ctx.player().removeStatusEffect(StatusEffects.WEAKNESS);
        ctx.player().removeStatusEffect(StatusEffects.RESISTANCE);

        ctx.message(Text.literal("You were never the weaker one.")
                .formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.world().spawnParticles(ParticleTypes.CRIT,
                ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                30, 0.4, 0.6, 0.4, 0.15);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.8F, 1.2F);
    }

    @Override
    public void tick(AbilityContext ctx) {
        int refresh = 60;

        if (ctx.state(COWED, 0.0) > 0.0) {
            ctx.refreshEffect(StatusEffects.RESISTANCE, refresh,
                    Math.max(0, (int) ctx.value("humility_resistance_amplifier")));
            ctx.refreshEffect(StatusEffects.WEAKNESS, refresh,
                    Math.max(0, (int) ctx.value("humility_weakness_amplifier")));
            return;
        }

        if (ctx.now() < ctx.state(EMBOLDENED_UNTIL, 0.0)) {
            ctx.refreshEffect(StatusEffects.STRENGTH, refresh,
                    Math.max(0, (int) ctx.value("humility_strength_amplifier")));
        }
    }

    private static UUID parse(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
