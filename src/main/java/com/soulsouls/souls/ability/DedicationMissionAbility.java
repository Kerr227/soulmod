package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import com.soulsouls.util.SoulCombat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Dedication's second wind: punish it long enough and it remembers what it came to do.
 *
 * <p>Damage taken is added up in a rolling window. Cross the threshold and the Soul
 * declares the mission: Speed III and Resistance III, mace hits turned straight back onto
 * whoever swung them, and every point of durability the armour loses converted into
 * health - the plates give way so the body does not have to.
 *
 * <p>The window, the threshold and the length of the state are all in the config, and the
 * state's deadline is a wall-clock time held in saved data, so it keeps running across a
 * relog exactly like every other timer in this mod.
 */
public class DedicationMissionAbility implements SoulAbility {
    public static final String COOLDOWN = "dedication_mission";

    /** Damage accumulated in the current window, in half-hearts. */
    private static final String BUILDUP = "mission_buildup";

    /** When the current accumulation window expires, in epoch millis. */
    private static final String WINDOW_UNTIL = "mission_window_until";

    /** When the mission state itself ends, in epoch millis. 0 when not running. */
    private static final String ACTIVE_UNTIL = "mission_active_until";

    /** Armour wear measured at the head of the current hit, to diff against afterwards. */
    private static final String ARMOUR_BEFORE = "mission_armour_before";

    @Override
    public String id() {
        return "dedication_mission";
    }

    @Override
    public String displayName() {
        return "Complete Your Mission";
    }

    @Override
    public List<String> cooldownKeys() {
        return List.of(COOLDOWN);
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.ofEntries(
                // Damage that has to pile up before the mission is declared, in half-hearts.
                // 30 is fifteen hearts' worth.
                Map.entry("mission_damage_threshold", 30.0),
                // How long the pile-up counts for. Stop being hit for this long and it resets.
                Map.entry("mission_window_seconds", 30.0),
                // How long the mission state runs once it is declared.
                Map.entry("mission_duration_seconds", 10.0),
                // Speed III and Resistance III are amplifier 2.
                Map.entry("mission_speed_amplifier", 2.0),
                Map.entry("mission_resistance_amplifier", 2.0),
                Map.entry("mission_cooldown_seconds", 120.0),
                // 1 to reflect mace hits during the mission, 0 to take them normally.
                Map.entry("mission_reflect_mace", 1.0),
                // Share of a reflected mace hit the attacker takes.
                Map.entry("mission_reflect_power", 1.0),
                // Half-hearts healed per point of armour durability lost. Armour has a lot
                // of durability, so this is deliberately small: 0.25 turns a hit that costs
                // four points across the set into one heart back.
                Map.entry("mission_health_per_durability", 0.25),
                // Most health a single hit may return, in half-hearts.
                Map.entry("mission_max_health_per_hit", 8.0)
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        boolean active = ctx.state(ACTIVE_UNTIL, 0.0) > ctx.now();
        double buildup = ctx.state(BUILDUP, 0.0);
        double threshold = ctx.value("mission_damage_threshold");
        long cooldown = ctx.cooldownSecondsLeft(COOLDOWN);

        return List.of(
                Text.literal("  Take ").formatted(Formatting.GRAY)
                        .append(Text.literal(com.soulsouls.util.SoulText.hearts(threshold))
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" within ").formatted(Formatting.GRAY))
                        .append(Text.literal((int) ctx.value("mission_window_seconds") + "s")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" to declare the mission").formatted(Formatting.GRAY)),
                Text.literal("    Speed ").formatted(Formatting.DARK_GRAY)
                        .append(Text.literal(BraveryMomentumAbility.roman(
                                (int) ctx.value("mission_speed_amplifier") + 1)))
                        .append(Text.literal(" and Resistance "))
                        .append(Text.literal(BraveryMomentumAbility.roman(
                                (int) ctx.value("mission_resistance_amplifier") + 1)))
                        .append(Text.literal(" for "))
                        .append(Text.literal((int) ctx.value("mission_duration_seconds") + "s")),
                Text.literal("    Mace hits are reflected, and armour damage becomes health")
                        .formatted(Formatting.DARK_GRAY),
                Text.literal("  Progress: ").formatted(Formatting.GRAY)
                        .append(Text.literal(active
                                        ? "MISSION ACTIVE"
                                        : cooldown > 0
                                                ? "resting, " + cooldown + "s"
                                                : (int) buildup + "/" + (int) threshold)
                                .formatted(active ? Formatting.LIGHT_PURPLE : Formatting.WHITE))
        );
    }

    // ------------------------------------------------------------------ combat

    /**
     * Runs at the head of the hit, before anything has been applied.
     *
     * <p>Two jobs: bounce a mace back if the mission is running, and take a reading of how
     * worn the armour is so {@link #afterDamage} can tell exactly what this hit cost.
     */
    @Override
    public boolean allowDamage(AbilityContext ctx, DamageSource source, float amount) {
        if (!ctx.isVulnerable()) {
            return true;
        }

        boolean active = ctx.state(ACTIVE_UNTIL, 0.0) > ctx.now();
        if (active && ctx.value("mission_reflect_mace") > 0.0
                && SoulCombat.isMaceHit(source)
                && source.getAttacker() instanceof LivingEntity attacker
                && attacker != ctx.player()) {
            reflect(ctx, attacker, amount);
            return false;
        }

        ctx.setState(ARMOUR_BEFORE, SoulCombat.armourWear(ctx.player()));
        return true;
    }

    private void reflect(AbilityContext ctx, LivingEntity attacker, float amount) {
        ServerPlayerEntity player = ctx.player();
        float thrownBack = (float) Math.max(1.0, amount * ctx.value("mission_reflect_power"));
        attacker.damage(ctx.world(), ctx.world().getDamageSources().playerAttack(player), thrownBack);

        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 1.0F, 1.2F);
        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0F, 1.4F);
        ctx.world().spawnParticles(ParticleTypes.CRIT,
                player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.7, 0.5, 0.3);
        ctx.actionBar(Text.literal("Turned back on them.").formatted(Formatting.LIGHT_PURPLE));
    }

    @Override
    public void afterDamage(AbilityContext ctx, DamageSource source, float taken) {
        if (!ctx.isVulnerable()) {
            return;
        }

        if (ctx.state(ACTIVE_UNTIL, 0.0) > ctx.now()) {
            armourIntoHealth(ctx);
            // Damage taken while the mission is already running does not count towards the
            // next one: the pile-up starts fresh when this state ends.
            return;
        }

        accumulate(ctx, taken);
    }

    /**
     * Spends the armour this hit just chewed through as health.
     *
     * <p>Measured as the real change in durability across the worn set, so tougher armour
     * with more plates to give really does keep the player alive longer, and a player in
     * no armour at all gets nothing.
     */
    private void armourIntoHealth(AbilityContext ctx) {
        double before = ctx.state(ARMOUR_BEFORE, -1.0);
        if (before < 0.0) {
            return;
        }
        ctx.clearState(ARMOUR_BEFORE);

        int lost = SoulCombat.armourWear(ctx.player()) - (int) before;
        if (lost <= 0) {
            return;
        }

        ServerPlayerEntity player = ctx.player();
        float healed = (float) Math.min(ctx.value("mission_max_health_per_hit"),
                lost * ctx.value("mission_health_per_durability"));
        if (healed <= 0.0F || player.getHealth() >= player.getMaxHealth()) {
            return;
        }

        player.heal(healed);

        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, SoundCategory.PLAYERS, 0.9F, 1.6F);
        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.5F, 1.8F);
        ctx.world().spawnParticles(ParticleTypes.HEART,
                player.getX(), player.getY() + 1.6, player.getZ(), 6, 0.4, 0.3, 0.4, 0.02);
        ctx.world().spawnParticles(ParticleTypes.ENCHANTED_HIT,
                player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.6, 0.5, 0.1);
    }

    /** Adds a hit to the rolling pile-up and declares the mission if it is enough. */
    private void accumulate(AbilityContext ctx, float taken) {
        if (taken <= 0.0F || !ctx.isReady(COOLDOWN)) {
            return;
        }

        double windowSeconds = ctx.value("mission_window_seconds");
        double buildup = ctx.state(WINDOW_UNTIL, 0.0) > ctx.now() ? ctx.state(BUILDUP, 0.0) : 0.0;
        buildup += taken;

        ctx.setState(BUILDUP, buildup);
        ctx.setState(WINDOW_UNTIL, ctx.now() + windowSeconds * 1000.0);

        double threshold = ctx.value("mission_damage_threshold");
        if (buildup < threshold) {
            return;
        }

        declare(ctx);
    }

    private void declare(AbilityContext ctx) {
        double seconds = ctx.value("mission_duration_seconds");
        ctx.clearState(BUILDUP);
        ctx.clearState(WINDOW_UNTIL);
        ctx.setState(ACTIVE_UNTIL, ctx.now() + seconds * 1000.0);
        ctx.startCooldown(COOLDOWN, ctx.value("mission_cooldown_seconds") + seconds);

        int ticks = (int) Math.round(seconds * 20.0);
        ctx.effect(StatusEffects.SPEED, ticks, Math.max(0, (int) ctx.value("mission_speed_amplifier")));
        ctx.effect(StatusEffects.RESISTANCE, ticks, Math.max(0, (int) ctx.value("mission_resistance_amplifier")));

        ServerPlayerEntity player = ctx.player();
        ctx.message(Text.literal("You need to complete your mission.")
                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));

        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0F, 1.2F);
        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.PLAYERS, 0.9F, 1.0F);
        ctx.world().spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1.0, player.getZ(), 70, 0.6, 0.9, 0.6, 0.3);
    }

    @Override
    public void tick(AbilityContext ctx) {
        double activeUntil = ctx.state(ACTIVE_UNTIL, 0.0);
        if (activeUntil <= 0.0) {
            return;
        }

        long millisLeft = (long) (activeUntil - ctx.now());
        if (millisLeft > 0) {
            ctx.actionBar(Text.literal("Mission: " + Math.max(1, (millisLeft + 999) / 1000) + "s")
                    .formatted(Formatting.LIGHT_PURPLE));
            ctx.world().spawnParticles(ParticleTypes.END_ROD,
                    ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                    2, 0.4, 0.5, 0.4, 0.01);
            return;
        }

        ctx.clearState(ACTIVE_UNTIL);
        ctx.actionBar(Text.literal("The mission is over.").formatted(Formatting.DARK_PURPLE));
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.PLAYERS, 0.6F, 1.2F);
    }

    @Override
    public void onRespawn(AbilityContext ctx) {
        ctx.clearState(ACTIVE_UNTIL);
        ctx.clearState(BUILDUP);
        ctx.clearState(WINDOW_UNTIL);
        ctx.clearState(ARMOUR_BEFORE);
    }
}
