package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Justice is about the bow.
 *
 * <p>Every arrow, crossbow bolt or trident a Justice player fires gets a small damage
 * multiplier applied on top of whatever the weapon and its enchantments already produced,
 * so it stacks correctly with Power and does not overwrite it.
 *
 * <p>The active ability ({@code /souls ability}) is Focus: for a few seconds, shots get a
 * second, larger multiplier. It is on a cooldown that survives relogging.
 */
public class JusticeMarksmanAbility implements SoulAbility {
    public static final String COOLDOWN = "justice_focus";
    private static final String FOCUS_UNTIL = "justice_focus_until";

    @Override
    public String id() {
        return "justice_marksman";
    }

    @Override
    public String displayName() {
        return "Marksman";
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
                "justice_projectile_multiplier", 1.15,
                "justice_focus_multiplier", 1.35,
                "justice_focus_duration_seconds", 6.0,
                "justice_focus_cooldown_seconds", 45.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  Arrow damage: ").formatted(Formatting.GRAY)
                        .append(Text.literal("x" + ctx.value("justice_projectile_multiplier"))
                                .formatted(Formatting.WHITE)),
                Text.literal("  /souls ability -> Focus: ").formatted(Formatting.GRAY)
                        .append(Text.literal("x" + ctx.value("justice_focus_multiplier") + " for "
                                        + (int) ctx.value("justice_focus_duration_seconds") + "s")
                                .formatted(Formatting.WHITE))
        );
    }

    @Override
    public void onProjectileFired(AbilityContext ctx, PersistentProjectileEntity projectile) {
        double multiplier = ctx.value("justice_projectile_multiplier");

        if (ctx.now() < ctx.state(FOCUS_UNTIL, 0.0)) {
            multiplier *= ctx.value("justice_focus_multiplier");
            projectile.setCritical(true);
        }

        if (multiplier != 1.0) {
            // Scales whatever damage the projectile already had, so Power enchantments still count.
            projectile.applyDamageModifier((float) multiplier);
        }
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        if (!ctx.isReady(COOLDOWN)) {
            return false;
        }

        double durationSeconds = ctx.value("justice_focus_duration_seconds");
        ctx.setState(FOCUS_UNTIL, ctx.now() + durationSeconds * 1000.0);
        ctx.startCooldown(COOLDOWN, ctx.value("justice_focus_cooldown_seconds"));

        ctx.actionBar(Text.literal("Justice focuses.").formatted(Formatting.YELLOW));
        ctx.world().spawnParticles(ParticleTypes.END_ROD,
                ctx.player().getX(), ctx.player().getY() + 1.2, ctx.player().getZ(),
                25, 0.4, 0.5, 0.4, 0.02);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, net.minecraft.sound.SoundCategory.PLAYERS,
                0.8F, 1.6F);
        return true;
    }
}
