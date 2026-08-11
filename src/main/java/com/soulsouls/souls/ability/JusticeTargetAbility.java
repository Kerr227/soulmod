package com.soulsouls.souls.ability;

import com.soulsouls.SoulSouls;
import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Justice hunts one person at a time.
 *
 * <p>{@code /justice target <player>} marks somebody. Marking costs a heart, held until
 * Justice kills them itself - a debt you carry for naming them. {@code /justice location}
 * points at where they are.
 *
 * <p>The target glows red so they can be picked out at distance, and stops glowing once
 * Justice is within 20 blocks: close enough to see them for real.
 *
 * <p>Killing the target returns the heart and adds another, stacking up to 20 hearts. Dying
 * with the mark still out simply keeps the debt.
 */
public class JusticeTargetAbility implements SoulAbility {
    /** uuid of the current target, in the remembered-names map. */
    public static final String TARGET_KEY = "justice_target";

    /** How many hearts Justice is currently down by, one per outstanding mark. */
    public static final String DEBT = "justice_heart_debt";

    /** Hearts earned from kills, folded into max health. */
    public static final String EARNED = "justice_hearts_earned";

    @Override
    public String id() {
        return "justice_target";
    }

    @Override
    public String displayName() {
        return "The Hunt";
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                "justice_reveal_distance", 20.0,
                "justice_heart_cost", 2.0,
                "justice_heart_reward", 2.0,
                "justice_max_bonus_health", 20.0,
                "justice_glow_refresh_seconds", 4.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        String target = ctx.data().remembered_names.get(TARGET_KEY + "_name");
        return List.of(
                Text.literal("  /justice target <player> to mark someone").formatted(Formatting.GRAY),
                Text.literal("  /justice location to find them").formatted(Formatting.GRAY),
                Text.literal("  Marking costs a heart until you kill them; the kill returns it and adds one")
                        .formatted(Formatting.DARK_GRAY),
                Text.literal("  Current target: ").formatted(Formatting.GRAY)
                        .append(Text.literal(target == null ? "nobody" : target)
                                .formatted(Formatting.YELLOW)));
    }

    // ------------------------------------------------------------------ marking

    /** @return an error message, or null when the mark was set */
    public static String mark(AbilityContext ctx, ServerPlayerEntity target) {
        if (target == ctx.player()) {
            return "You cannot hunt yourself.";
        }
        if (ctx.data().remembered_names.containsKey(TARGET_KEY)) {
            return "You already have a target. Kill them first.";
        }

        ctx.data().remembered_names.put(TARGET_KEY, target.getUuid().toString());
        ctx.data().remembered_names.put(TARGET_KEY + "_name", target.getNameForScoreboard());

        // The mark is paid for up front, and only returned by the kill.
        ctx.setState(DEBT, ctx.state(DEBT, 0.0) + ctx.value("justice_heart_cost"));
        ctx.manager().markDirty();
        applyHealth(ctx);

        ctx.message(Text.literal("You have marked ").formatted(Formatting.YELLOW)
                .append(Text.literal(target.getNameForScoreboard())
                        .formatted(Formatting.RED, Formatting.BOLD))
                .append(Text.literal(". A heart is theirs until they fall.")
                        .formatted(Formatting.YELLOW)));

        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_WITHER_SPAWN, net.minecraft.sound.SoundCategory.PLAYERS, 0.4F, 1.8F);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_BEACON_POWER_SELECT, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 0.8F);
        return null;
    }

    /** Where the target is, or null when there is no target online. */
    public static ServerPlayerEntity findTarget(AbilityContext ctx) {
        String raw = ctx.data().remembered_names.get(TARGET_KEY);
        if (raw == null) {
            return null;
        }
        try {
            return ctx.manager().server().getPlayerManager().getPlayer(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ the hunt

    @Override
    public void tick(AbilityContext ctx) {
        applyHealth(ctx);

        ServerPlayerEntity target = findTarget(ctx);
        if (target == null) {
            return;
        }

        double distance = Math.sqrt(target.squaredDistanceTo(ctx.player()));
        boolean far = distance > ctx.value("justice_reveal_distance");

        if (far) {
            // Glowing is what makes a distant target findable at all.
            int ticks = (int) Math.round(ctx.value("justice_glow_refresh_seconds") * 20.0);
            if (!target.hasStatusEffect(StatusEffects.GLOWING)) {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, ticks,
                        0, true, false, true));
            }
            ctx.world().spawnParticles(new DustParticleEffect(0xFF0000, 1.2F),
                    target.getX(), target.getY() + 2.2, target.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
        } else {
            // Close enough to see for yourself.
            target.removeStatusEffect(StatusEffects.GLOWING);
        }
    }

    @Override
    public void onKill(AbilityContext ctx, LivingEntity victim) {
        String raw = ctx.data().remembered_names.get(TARGET_KEY);
        if (raw == null || !(victim instanceof ServerPlayerEntity dead)) {
            return;
        }
        if (!raw.equals(dead.getUuid().toString())) {
            return;
        }

        ctx.data().remembered_names.remove(TARGET_KEY);
        ctx.data().remembered_names.remove(TARGET_KEY + "_name");

        // The borrowed heart comes home, and brings one with it.
        ctx.setState(DEBT, Math.max(0.0, ctx.state(DEBT, 0.0) - ctx.value("justice_heart_cost")));
        double earned = Math.min(ctx.value("justice_max_bonus_health"),
                ctx.state(EARNED, 0.0) + ctx.value("justice_heart_reward"));
        ctx.setState(EARNED, earned);
        ctx.manager().markDirty();
        applyHealth(ctx);

        dead.removeStatusEffect(StatusEffects.GLOWING);

        ctx.message(Text.literal("The hunt is finished. ").formatted(Formatting.YELLOW)
                .append(Text.literal("+1 heart.").formatted(Formatting.GREEN, Formatting.BOLD)));
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, net.minecraft.sound.SoundCategory.PLAYERS, 0.8F, 1.4F);
    }

    /** Folds the outstanding debt and the earned hearts into the shared bonus-health value. */
    private static void applyHealth(AbilityContext ctx) {
        double bonus = ctx.state(EARNED, 0.0) - ctx.state(DEBT, 0.0);
        if (Math.abs(ctx.state(SoulSouls.BONUS_HEALTH_KEY, 0.0) - bonus) < 0.001) {
            return;
        }
        ctx.setState(SoulSouls.BONUS_HEALTH_KEY, bonus);
        ctx.manager().refreshMaxHealth(ctx.player(), ctx.soul(), ctx.data());
    }

    @Override
    public void onRemove(AbilityContext ctx) {
        ctx.data().remembered_names.remove(TARGET_KEY);
        ctx.data().remembered_names.remove(TARGET_KEY + "_name");
        ctx.clearState(DEBT);
        ctx.clearState(EARNED);
        ctx.clearState(SoulSouls.BONUS_HEALTH_KEY);
    }
}
