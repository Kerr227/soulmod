package com.soulsouls.souls.ability;

import com.soulsouls.config.SoulsConfig;
import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Determination's signature power: a chance to simply refuse to die.
 *
 * <p>The chance lives in the global config as {@code determination_refuse_chance} rather
 * than in this Soul's {@code values} block, because the spec asks for a dedicated
 * {@code /souls settings refuse <percentage>} command.
 *
 * <p>Anti-abuse: a successful refusal starts a cooldown stored as a wall-clock deadline,
 * so a player cannot reset it by relogging or by waiting for a server restart. While the
 * cooldown is running, deaths happen normally.
 */
public class RefuseDeathAbility implements SoulAbility {
    public static final String COOLDOWN = "refuse";

    @Override
    public String id() {
        return "refuse_death";
    }

    @Override
    public String displayName() {
        return "Refuse Death";
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                "refuse_cooldown_seconds", 300.0,
                "refuse_health_restored", 6.0,
                "refuse_resistance_seconds", 5.0
        );
    }

    @Override
    public List<String> cooldownKeys() {
        return List.of(COOLDOWN);
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  Refuse Chance: ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(formatPercent(ctx.config().determination_refuse_chance))
                                .formatted(Formatting.WHITE)),
                Text.literal("  Cooldown: ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("refuse_cooldown_seconds") + "s")
                                .formatted(Formatting.WHITE))
        );
    }

    @Override
    public boolean allowDeath(AbilityContext ctx, DamageSource source, float amount) {
        if (!ctx.isVulnerable() || !ctx.isReady(COOLDOWN)) {
            return true;
        }

        SoulsConfig config = ctx.config();
        double chance = SoulsConfig.clampPercent(config.determination_refuse_chance);
        if (chance <= 0.0) {
            return true;
        }
        if (Math.random() * 100.0 >= chance) {
            return true;
        }

        ServerPlayerEntity player = ctx.player();

        // Whatever refuses a death has to leave the player alive, or the death fires again
        // on the next tick.
        float restored = (float) Math.max(1.0, ctx.value("refuse_health_restored"));
        player.setHealth(Math.min(player.getMaxHealth(), restored));
        player.extinguish();
        player.clearStatusEffects();

        int resistanceTicks = (int) Math.round(ctx.value("refuse_resistance_seconds") * 20.0);
        if (resistanceTicks > 0) {
            ctx.effect(StatusEffects.RESISTANCE, resistanceTicks, 1);
            ctx.effect(StatusEffects.REGENERATION, resistanceTicks, 0);
        }

        ctx.startCooldown(COOLDOWN, ctx.value("refuse_cooldown_seconds"));

        // The Undertale line, in Determination red.
        ctx.message(Text.literal("But it refused.").formatted(Formatting.RED, Formatting.BOLD));

        ServerWorld world = ctx.world();
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1.0, player.getZ(),
                80, 0.5, 0.8, 0.5, 0.25);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TOTEM_USE, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);

        return false;
    }

    static String formatPercent(double percent) {
        return Math.abs(percent - Math.rint(percent)) < 0.01
                ? (long) Math.rint(percent) + "%"
                : String.format("%.1f%%", percent);
    }
}
