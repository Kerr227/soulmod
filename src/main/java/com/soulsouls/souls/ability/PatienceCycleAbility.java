package com.soulsouls.souls.ability;

import com.soulsouls.SoulSouls;
import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Patience slowly grows, then slowly fades, forever.
 *
 * <p>Starting at 10 hearts, the player gains a heart every 10 minutes up to 20 hearts.
 * Once full, they lose a heart a minute until they are back to 10, and the cycle restarts.
 *
 * <p>The timer counts <em>online</em> time, accumulated in the player's saved data. That
 * choice matters: counting wall-clock time would let someone log off at 20 hearts and come
 * back at 20 hearts, and counting ticks in memory would reset the progress on every server
 * restart. Because the counter is saved, logging out, dying, changing dimension and
 * restarting the server all just pause it.
 */
public class PatienceCycleAbility implements SoulAbility {
    private static final String PROGRESS = "patience_progress_ticks";
    private static final String PHASE = "patience_phase";

    private static final double PHASE_GROWING = 0.0;
    private static final double PHASE_FADING = 1.0;

    @Override
    public String id() {
        return "patience_cycle";
    }

    @Override
    public String displayName() {
        return "Patient Growth";
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                "patience_grow_minutes", 10.0,
                "patience_fade_minutes", 1.0,
                "patience_max_bonus_hearts", 10.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        double bonus = ctx.state(SoulSouls.BONUS_HEALTH_KEY, 0.0) / 2.0;
        boolean growing = ctx.state(PHASE, PHASE_GROWING) == PHASE_GROWING;
        return List.of(
                Text.literal("  Gains 1 heart every ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("patience_grow_minutes") + " min")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(", up to ").formatted(Formatting.GRAY))
                        .append(Text.literal((int) (10 + ctx.value("patience_max_bonus_hearts")) + " hearts")
                                .formatted(Formatting.WHITE)),
                Text.literal("  Then loses 1 heart every ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("patience_fade_minutes") + " min")
                                .formatted(Formatting.WHITE)),
                Text.literal("  Currently: ").formatted(Formatting.GRAY)
                        .append(Text.literal("+" + (int) bonus + " hearts, "
                                        + (growing ? "growing" : "fading"))
                                .formatted(Formatting.WHITE))
        );
    }

    @Override
    public void onAssign(AbilityContext ctx) {
        ctx.setState(SoulSouls.BONUS_HEALTH_KEY, 0.0);
        ctx.setState(PROGRESS, 0.0);
        ctx.setState(PHASE, PHASE_GROWING);
    }

    @Override
    public void onRemove(AbilityContext ctx) {
        // Hand the max-health bonus back so the next Soul starts from its own value.
        ctx.clearState(SoulSouls.BONUS_HEALTH_KEY);
        ctx.clearState(PROGRESS);
        ctx.clearState(PHASE);
    }

    @Override
    public void tick(AbilityContext ctx) {
        double maxBonusHealth = Math.max(0.0, ctx.value("patience_max_bonus_hearts")) * 2.0;
        double growTicks = Math.max(1.0, ctx.value("patience_grow_minutes") * 60.0 * 20.0);
        double fadeTicks = Math.max(1.0, ctx.value("patience_fade_minutes") * 60.0 * 20.0);

        double progress = ctx.state(PROGRESS, 0.0) + ctx.tickInterval();
        double phase = ctx.state(PHASE, PHASE_GROWING);
        double bonus = ctx.state(SoulSouls.BONUS_HEALTH_KEY, 0.0);

        double threshold = phase == PHASE_GROWING ? growTicks : fadeTicks;
        if (progress < threshold) {
            ctx.setState(PROGRESS, progress);
            return;
        }

        progress -= threshold;

        if (phase == PHASE_GROWING) {
            bonus = Math.min(maxBonusHealth, bonus + 2.0);
            if (bonus >= maxBonusHealth) {
                phase = PHASE_FADING;
                announce(ctx, "Your patience is at its peak.", true);
            } else {
                announce(ctx, "Patience grows: +" + (int) (bonus / 2.0) + " hearts", false);
            }
        } else {
            bonus = Math.max(0.0, bonus - 2.0);
            if (bonus <= 0.0) {
                phase = PHASE_GROWING;
                announce(ctx, "Your patience begins again.", false);
            } else {
                announce(ctx, "Patience fades: +" + (int) (bonus / 2.0) + " hearts", false);
            }
        }

        ctx.setState(PROGRESS, progress);
        ctx.setState(PHASE, phase);
        ctx.setState(SoulSouls.BONUS_HEALTH_KEY, bonus);

        // Applied by SoulManager on the next refresh, but do it now so the change is instant.
        ctx.manager().refreshMaxHealth(ctx.player(), ctx.soul(), ctx.data());
    }

    private void announce(AbilityContext ctx, String message, boolean loud) {
        ctx.actionBar(Text.literal(message).formatted(Formatting.AQUA));
        ctx.world().spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                loud ? 30 : 10, 0.5, 0.6, 0.5, 0.02);
        if (!ctx.config().patience_sounds) {
            return;
        }
        // A small chime on every step, and a fuller one when the cycle turns over.
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                loud ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
                net.minecraft.sound.SoundCategory.PLAYERS,
                loud ? 0.6F : 0.35F, loud ? 1.4F : 1.8F);
    }
}
