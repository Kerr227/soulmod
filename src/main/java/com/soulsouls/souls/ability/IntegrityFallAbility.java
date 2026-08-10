package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
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
                "integrity_landing_particles", 1.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        boolean immune = ctx.value("integrity_fall_immunity") > 0.0;
        return List.of(Text.literal("  Fall damage: ").formatted(Formatting.GRAY)
                .append(Text.literal(immune ? "immune" : "normal").formatted(Formatting.WHITE)));
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
