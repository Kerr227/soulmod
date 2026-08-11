package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Perseverance gets tougher the worse things get.
 *
 * <p>Three layers, each on its own cooldown so none of them can be farmed:
 * a Resistance buff after a heavy hit, a small Strength boost while below a health
 * threshold, and a stronger last-stand Resistance when close to death.
 */
public class PerseveranceAbility implements SoulAbility {
    public static final String BRACE_COOLDOWN = "perseverance_brace";
    public static final String LAST_STAND_COOLDOWN = "perseverance_last_stand";
    public static final String REPORT_COOLDOWN = "perseverance_report";

    /** Per-attacker running damage total: {@code perseverance_damage_<uuid>}. */
    private static final String DAMAGE_PREFIX = "perseverance_damage_";

    @Override
    public String id() {
        return "perseverance";
    }

    @Override
    public String displayName() {
        return "Endure";
    }

    @Override
    public List<String> cooldownKeys() {
        return List.of(BRACE_COOLDOWN, LAST_STAND_COOLDOWN);
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                "perseverance_heavy_damage", 6.0,
                "perseverance_brace_seconds", 6.0,
                "perseverance_brace_cooldown_seconds", 25.0,
                "perseverance_low_health_fraction", 0.3,
                "perseverance_strength_amplifier", 0.0,
                "perseverance_last_stand_fraction", 0.15,
                "perseverance_last_stand_seconds", 6.0,
                "perseverance_last_stand_cooldown_seconds", 120.0,
                // Damage from the last few seconds is summarised back to you.
                "perseverance_report_window_seconds", 5.0,
                "perseverance_report_cooldown_seconds", 5.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  Resistance after taking ").formatted(Formatting.GRAY)
                        .append(Text.literal(BraveryMomentumAbility.trim(ctx.value("perseverance_heavy_damage"))
                                + " damage at once").formatted(Formatting.WHITE)),
                Text.literal("  Strength below ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) (ctx.value("perseverance_low_health_fraction") * 100) + "% health")
                                .formatted(Formatting.WHITE)),
                Text.literal("  Last stand below ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) (ctx.value("perseverance_last_stand_fraction") * 100) + "% health")
                                .formatted(Formatting.WHITE))
        );
    }

    @Override
    public void afterDamage(AbilityContext ctx, DamageSource source, float taken) {
        recordAttacker(ctx, source, taken);

        if (taken < ctx.value("perseverance_heavy_damage") || !ctx.isReady(BRACE_COOLDOWN)) {
            return;
        }

        int ticks = (int) Math.round(ctx.value("perseverance_brace_seconds") * 20.0);
        ctx.effect(StatusEffects.RESISTANCE, ticks, 0);
        ctx.startCooldown(BRACE_COOLDOWN, ctx.value("perseverance_brace_cooldown_seconds"));

        ctx.actionBar(Text.literal("You brace yourself.").formatted(Formatting.DARK_PURPLE));
        ctx.world().spawnParticles(ParticleTypes.ENCHANTED_HIT,
                ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                15, 0.4, 0.5, 0.4, 0.05);
    }

    /**
     * Remembers who hit you and for how much, so the report can name them. Entries older
     * than the window are dropped as they are read.
     */
    private void recordAttacker(AbilityContext ctx, DamageSource source, float taken) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return;
        }
        String key = DAMAGE_PREFIX + attacker.getUuid();
        ctx.data().setState(key, ctx.state(key, 0.0) + taken);
        ctx.data().setState(key + "_at", ctx.now());
        ctx.data().remembered_names.put(attacker.getUuid().toString(),
                attacker.getNameForScoreboard());
        ctx.manager().markDirty();
    }

    /** Tells the player who has been hurting them, worst first. */
    private void reportAttackers(AbilityContext ctx) {
        double window = ctx.value("perseverance_report_window_seconds") * 1000.0;
        List<Map.Entry<String, Double>> recent = new ArrayList<>();

        for (String key : new ArrayList<>(ctx.data().state.keySet())) {
            if (!key.startsWith(DAMAGE_PREFIX) || key.endsWith("_at")) {
                continue;
            }
            double at = ctx.state(key + "_at", 0.0);
            if (ctx.now() - at > window) {
                ctx.data().clearState(key);
                ctx.data().clearState(key + "_at");
                continue;
            }
            recent.add(Map.entry(key.substring(DAMAGE_PREFIX.length()), ctx.state(key, 0.0)));
        }

        if (recent.isEmpty()) {
            return;
        }
        recent.sort((left, right) -> Double.compare(right.getValue(), left.getValue()));

        for (Map.Entry<String, Double> entry : recent) {
            String name = ctx.data().remembered_names.getOrDefault(entry.getKey(), "Someone");
            // Damage is reported in hearts, which is how players read their own health bar.
            String hearts = String.format("%.1f", entry.getValue() / 2.0);
            ctx.message(Text.literal(name).formatted(Formatting.WHITE)
                    .append(Text.literal(" had damaged you " + hearts + " hearts.")
                            .formatted(Formatting.DARK_PURPLE)));
        }
        ctx.startCooldown(REPORT_COOLDOWN, ctx.value("perseverance_report_cooldown_seconds"));
    }

    @Override
    public void tick(AbilityContext ctx) {
        if (ctx.isReady(REPORT_COOLDOWN)) {
            reportAttackers(ctx);
        }

        if (!ctx.isVulnerable()) {
            return;
        }

        double fraction = ctx.healthFraction();

        if (fraction <= ctx.value("perseverance_low_health_fraction")) {
            ctx.refreshEffect(StatusEffects.STRENGTH, 60,
                    Math.max(0, (int) ctx.value("perseverance_strength_amplifier")));
        }

        if (fraction <= ctx.value("perseverance_last_stand_fraction") && ctx.isReady(LAST_STAND_COOLDOWN)) {
            int ticks = (int) Math.round(ctx.value("perseverance_last_stand_seconds") * 20.0);
            ctx.effect(StatusEffects.RESISTANCE, ticks, 1);
            ctx.startCooldown(LAST_STAND_COOLDOWN, ctx.value("perseverance_last_stand_cooldown_seconds"));

            ctx.actionBar(Text.literal("You refuse to fall.").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
            ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                    SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.6F, 0.6F);
            ctx.world().spawnParticles(ParticleTypes.SCULK_SOUL,
                    ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                    25, 0.5, 0.6, 0.5, 0.03);
        }
    }
}
