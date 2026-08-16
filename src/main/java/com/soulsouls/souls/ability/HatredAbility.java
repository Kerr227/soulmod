package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hatred is not a fighter's Soul - it is a dead thing that keeps walking.
 *
 * <p>What it does, all of it passive:
 * <ul>
 *   <li><b>Wither Touch</b> - hitting something inflicts a long, heavy Wither, on a
 *       per-attacker cooldown so a fast weapon cannot stack it into an execution.</li>
 *   <li><b>Spite</b> - being hit hands back Strength IV for ten seconds. Keep getting hit
 *       and it keeps going; the moment ten seconds pass without a hit the rage burns out
 *       and will not come back for a long cooldown.</li>
 *   <li><b>One of the Undead</b> - hostile mobs will not attack it. Standing near them
 *       heals it instead, and the Nether treats it as a native: permanent Resistance II
 *       for as long as it stays down there.</li>
 *   <li><b>Immunity</b> - harmful effects are stripped, and five of them are turned into
 *       their opposites.</li>
 *   <li><b>Instability</b> - every kill adds a stack. Past a threshold the Soul starts
 *       feeding on its owner. Stacks decay, so it settles if the player stops killing.</li>
 * </ul>
 */
public class HatredAbility implements SoulAbility {
    public static final String TOUCH_COOLDOWN = "hatred_touch";
    public static final String SPITE_COOLDOWN = "hatred_spite";

    private static final String STACKS = "hatred_stacks";
    private static final String LAST_DECAY_AT = "hatred_last_decay_at";

    /** When the current Strength window runs out, in epoch millis. 0 when not raging. */
    private static final String SPITE_UNTIL = "hatred_spite_until";

    /** Last time the undead aura healed, so healing is paced in real seconds. */
    private static final String LAST_AURA_HEAL_AT = "hatred_last_aura_heal_at";

    /** 1 once the Nether greeting has been shown for the current visit. */
    private static final String NETHER_GREETED = "hatred_nether_greeted";

    /**
     * What each harmful effect becomes. Anything harmful that is not listed here is simply
     * removed, so Hatred takes no damage from negative effects at all.
     */
    private static final Map<RegistryEntry<StatusEffect>, RegistryEntry<StatusEffect>> INVERSIONS = Map.of(
            StatusEffects.WITHER, StatusEffects.REGENERATION,
            StatusEffects.WEAKNESS, StatusEffects.STRENGTH,
            StatusEffects.SLOWNESS, StatusEffects.SPEED,
            StatusEffects.MINING_FATIGUE, StatusEffects.HASTE,
            StatusEffects.BLINDNESS, StatusEffects.NIGHT_VISION,
            StatusEffects.POISON, StatusEffects.REGENERATION);

    @Override
    public String id() {
        return "hatred_touch";
    }

    @Override
    public String displayName() {
        return "Wither Touch";
    }

    @Override
    public List<String> cooldownKeys() {
        return List.of(TOUCH_COOLDOWN, SPITE_COOLDOWN);
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.ofEntries(
                // --- Wither Touch. Longer and heavier than it was: Wither III for 10s.
                Map.entry("hatred_wither_seconds", 10.0),
                Map.entry("hatred_wither_amplifier", 2.0),
                Map.entry("hatred_touch_cooldown_seconds", 3.0),
                Map.entry("hatred_affects_mobs", 0.0),

                // --- Spite: Strength IV while the hits keep coming.
                Map.entry("hatred_spite_strength_seconds", 10.0),
                Map.entry("hatred_spite_amplifier", 3.0),
                Map.entry("hatred_spite_cooldown_seconds", 500.0),

                // --- One of the Undead.
                Map.entry("hatred_undead_pacify", 1.0),
                Map.entry("hatred_undead_radius", 16.0),
                Map.entry("hatred_undead_heal_radius", 8.0),
                // Health restored per second while at least one hostile is close, in
                // half-hearts. Set to 0 to keep the pacifying but drop the healing.
                Map.entry("hatred_undead_heal_per_second", 1.0),
                Map.entry("hatred_nether_resistance", 1.0),
                // Resistance II. This is an amplifier, so 1 means level II.
                Map.entry("hatred_nether_resistance_amplifier", 1.0),

                // --- Instability.
                Map.entry("hatred_max_stacks", 10.0),
                Map.entry("hatred_seconds_per_stack", 0.3),
                Map.entry("hatred_stack_decay_seconds", 120.0),
                Map.entry("hatred_instability_threshold", 5.0),
                Map.entry("hatred_instability_damage", 1.0),
                Map.entry("hatred_invert_effects", 1.0)
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        int stacks = (int) ctx.state(STACKS, 0.0);
        long spiteLeft = ctx.cooldownSecondsLeft(SPITE_COOLDOWN);
        return List.of(
                Text.literal("  Hits apply Wither ").formatted(Formatting.GRAY)
                        .append(Text.literal(BraveryMomentumAbility.roman(
                                        (int) ctx.value("hatred_wither_amplifier") + 1))
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" for ").formatted(Formatting.GRAY))
                        .append(Text.literal(BraveryMomentumAbility.trim(ctx.value("hatred_wither_seconds")) + "s")
                                .formatted(Formatting.WHITE)),
                Text.literal("  Being hit gives Strength ").formatted(Formatting.GRAY)
                        .append(Text.literal(BraveryMomentumAbility.roman(
                                        (int) ctx.value("hatred_spite_amplifier") + 1))
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" for ").formatted(Formatting.GRAY))
                        .append(Text.literal(BraveryMomentumAbility.trim(
                                        ctx.value("hatred_spite_strength_seconds")) + "s")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(spiteLeft > 0 ? " (burnt out, " + spiteLeft + "s)" : " (ready)")
                                .formatted(spiteLeft > 0 ? Formatting.DARK_RED : Formatting.DARK_GREEN)),
                Text.literal("    Go ").formatted(Formatting.DARK_GRAY)
                        .append(Text.literal(BraveryMomentumAbility.trim(
                                ctx.value("hatred_spite_strength_seconds")) + "s"))
                        .append(Text.literal(" without being hit and it burns out for "))
                        .append(Text.literal((int) ctx.value("hatred_spite_cooldown_seconds") + "s")),
                Text.literal("  Hostile mobs ignore you and heal you when close")
                        .formatted(Formatting.GRAY),
                Text.literal("  Permanent Resistance ").formatted(Formatting.GRAY)
                        .append(Text.literal(BraveryMomentumAbility.roman(
                                        (int) ctx.value("hatred_nether_resistance_amplifier") + 1))
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" while in the Nether").formatted(Formatting.GRAY)),
                Text.literal("  Instability: ").formatted(Formatting.GRAY)
                        .append(Text.literal(stacks + "/" + (int) ctx.value("hatred_max_stacks") + " stacks")
                                .formatted(Formatting.WHITE)),
                Text.literal("  Above ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("hatred_instability_threshold") + " stacks")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" the Soul turns on you").formatted(Formatting.GRAY)),
                Text.literal("  Harmful effects are inverted: ").formatted(Formatting.GRAY),
                Text.literal("    Wither and Poison heal, Weakness gives Strength,")
                        .formatted(Formatting.DARK_GRAY),
                Text.literal("    Slowness gives Speed, Mining Fatigue gives Haste,")
                        .formatted(Formatting.DARK_GRAY),
                Text.literal("    Blindness gives Night Vision. Everything else is stripped.")
                        .formatted(Formatting.DARK_GRAY)
        );
    }

    @Override
    public void onAttack(AbilityContext ctx, Entity target) {
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        boolean isPlayer = target instanceof ServerPlayerEntity;
        if (!isPlayer && ctx.value("hatred_affects_mobs") <= 0.0) {
            return;
        }
        if (!ctx.isReady(TOUCH_COOLDOWN)) {
            return;
        }

        int stacks = (int) ctx.state(STACKS, 0.0);
        double seconds = ctx.value("hatred_wither_seconds") + stacks * ctx.value("hatred_seconds_per_stack");
        int ticks = (int) Math.round(seconds * 20.0);
        int amplifier = Math.max(0, (int) ctx.value("hatred_wither_amplifier"));

        living.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, ticks, amplifier,
                false, true, true));
        ctx.startCooldown(TOUCH_COOLDOWN, ctx.value("hatred_touch_cooldown_seconds"));

        ctx.world().spawnParticles(ParticleTypes.SMOKE,
                target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
    }

    // ------------------------------------------------------------------ spite

    /**
     * Being hurt is what wakes Hatred up.
     *
     * <p>The first hit grants Strength and opens a window. Every further hit inside that
     * window refreshes it, so a real fight keeps the rage alive indefinitely. The tick
     * below is what notices the window closing and puts it on its long cooldown.
     */
    @Override
    public void afterDamage(AbilityContext ctx, DamageSource source, float taken) {
        if (!ctx.isVulnerable()) {
            return;
        }

        double windowSeconds = ctx.value("hatred_spite_strength_seconds");
        boolean raging = ctx.state(SPITE_UNTIL, 0.0) > ctx.now();

        // Not already raging and still burnt out: this hit does nothing for us.
        if (!raging && !ctx.isReady(SPITE_COOLDOWN)) {
            return;
        }

        int ticks = (int) Math.round(windowSeconds * 20.0);
        int amplifier = Math.max(0, (int) ctx.value("hatred_spite_amplifier"));
        ctx.effect(StatusEffects.STRENGTH, ticks, amplifier);
        ctx.setState(SPITE_UNTIL, ctx.now() + windowSeconds * 1000.0);

        if (!raging) {
            ctx.message(Text.literal("You are owed this.")
                    .formatted(Formatting.DARK_RED, Formatting.BOLD));
            ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                    SoundEvents.ENTITY_WITHER_HURT, SoundCategory.PLAYERS, 0.8F, 0.5F);
            ctx.world().spawnParticles(ParticleTypes.LARGE_SMOKE,
                    ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                    30, 0.5, 0.7, 0.5, 0.02);
        }
    }

    /** Closes the Strength window once the hits stop, and starts the long cooldown. */
    private void tickSpite(AbilityContext ctx) {
        double until = ctx.state(SPITE_UNTIL, 0.0);
        if (until <= 0.0) {
            return;
        }
        if (ctx.now() < until) {
            return;
        }

        ctx.clearState(SPITE_UNTIL);
        ctx.player().removeStatusEffect(StatusEffects.STRENGTH);
        ctx.startCooldown(SPITE_COOLDOWN, ctx.value("hatred_spite_cooldown_seconds"));
        ctx.message(Text.literal("The hatred burns out. "
                        + (int) ctx.value("hatred_spite_cooldown_seconds") + "s until it can rise again.")
                .formatted(Formatting.DARK_GRAY));
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.PLAYERS, 0.7F, 0.6F);
    }

    // ------------------------------------------------------------------ one of the undead

    /**
     * Hostile mobs treat Hatred as one of their own.
     *
     * <p>Any hostile that has picked this player as its target drops it again, and anger
     * from the ones that hold grudges (zombified piglins, wolves and the like) is cleared.
     * Standing close to them then trickles health back, so a mob-heavy fight is where
     * Hatred is safest rather than where it is in danger.
     *
     * <p>This runs on the ability tick rather than by mixing into mob targeting: a mob may
     * hold the player as a target for a fraction of a second before it is dropped, but it
     * never gets a swing in, and no other mod's targeting is interfered with.
     */
    private void tickUndeadAura(AbilityContext ctx) {
        if (ctx.value("hatred_undead_pacify") <= 0.0) {
            return;
        }

        ServerPlayerEntity player = ctx.player();
        double radius = Math.max(1.0, ctx.value("hatred_undead_radius"));
        double healRadius = Math.max(0.0, ctx.value("hatred_undead_heal_radius"));
        double healRadiusSq = healRadius * healRadius;

        List<MobEntity> nearby = ctx.world().getEntitiesByClass(MobEntity.class,
                player.getBoundingBox().expand(radius),
                mob -> mob instanceof Monster && mob.isAlive());

        boolean anyClose = false;
        for (MobEntity mob : nearby) {
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
            if (mob instanceof Angerable angerable) {
                angerable.stopAnger();
            }
            if (healRadius > 0.0 && mob.squaredDistanceTo(player) <= healRadiusSq) {
                anyClose = true;
            }
        }

        if (!anyClose) {
            return;
        }

        double perSecond = ctx.value("hatred_undead_heal_per_second");
        if (perSecond <= 0.0 || player.getHealth() >= player.getMaxHealth()) {
            return;
        }

        // Paced off the wall clock so the rate is the same whatever the tick interval is.
        double last = ctx.state(LAST_AURA_HEAL_AT, 0.0);
        if (ctx.now() - last < 1000.0) {
            return;
        }
        ctx.setState(LAST_AURA_HEAL_AT, ctx.now());
        player.heal((float) perSecond);

        ctx.world().spawnParticles(ParticleTypes.SCULK_SOUL,
                player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.4, 0.6, 0.4, 0.01);
    }

    /**
     * The Nether is home. Resistance II is kept topped up for as long as the player is
     * down there and taken away the moment they leave.
     */
    private void tickNether(AbilityContext ctx) {
        boolean inNether = World.NETHER.equals(ctx.world().getRegistryKey());

        if (!inNether) {
            // Re-arm the greeting so it plays again on the next trip through a portal.
            if (ctx.state(NETHER_GREETED, 0.0) > 0.0) {
                ctx.clearState(NETHER_GREETED);
            }
            return;
        }
        if (ctx.value("hatred_nether_resistance") <= 0.0) {
            return;
        }

        int amplifier = Math.max(0, (int) ctx.value("hatred_nether_resistance_amplifier"));

        // "Permanent" is done as a short effect refreshed on every tick rather than an
        // infinite one, so it disappears by itself the instant the player leaves the
        // Nether - including on a crash, a relog, or a /kill.
        ctx.refreshEffect(StatusEffects.RESISTANCE, 200, amplifier);

        if (ctx.state(NETHER_GREETED, 0.0) > 0.0) {
            return;
        }
        ctx.setState(NETHER_GREETED, 1.0);

        ServerPlayerEntity player = ctx.player();
        ctx.message(Text.literal("You are one of the undead.")
                .formatted(Formatting.DARK_RED, Formatting.BOLD));
        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundCategory.PLAYERS, 1.0F, 0.5F);
        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.8F, 0.7F);
        ctx.world().spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.6, 0.9, 0.6, 0.05);
        ctx.world().spawnParticles(ParticleTypes.ASH,
                player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.8, 1.0, 0.8, 0.02);
    }

    @Override
    public void onKill(AbilityContext ctx, LivingEntity victim) {
        if (!(victim instanceof ServerPlayerEntity) && !(victim instanceof Monster)) {
            return;
        }
        double max = ctx.value("hatred_max_stacks");
        double stacks = Math.min(max, ctx.state(STACKS, 0.0) + 1.0);
        ctx.setState(STACKS, stacks);

        if (stacks >= ctx.value("hatred_instability_threshold")) {
            ctx.actionBar(Text.literal("The hatred is getting harder to hold.")
                    .formatted(Formatting.DARK_GRAY));
        }
    }

    /**
     * Hatred does not suffer. Anything harmful that lands on it is stripped off, and the
     * six effects above are turned into their opposites.
     *
     * <p>This is polled rather than intercepted at the point the effect is applied: it runs
     * on the ability tick, so a harmful effect exists for at most a fraction of a second.
     * Doing it here keeps the whole Soul in one file and needs no mixin.
     */
    private void invertHarmfulEffects(AbilityContext ctx) {
        if (ctx.value("hatred_invert_effects") <= 0.0) {
            return;
        }

        // Copied first: removing an effect modifies the live collection.
        List<StatusEffectInstance> active = new ArrayList<>(ctx.player().getStatusEffects());
        for (StatusEffectInstance instance : active) {
            RegistryEntry<StatusEffect> effect = instance.getEffectType();
            if (effect.value().getCategory() != StatusEffectCategory.HARMFUL) {
                continue;
            }

            RegistryEntry<StatusEffect> replacement = INVERSIONS.get(effect);
            ctx.player().removeStatusEffect(effect);

            if (replacement != null) {
                ctx.player().addStatusEffect(new StatusEffectInstance(replacement,
                        instance.getDuration(), instance.getAmplifier(), true, false, true));
            }
        }
    }

    @Override
    public void onRespawn(AbilityContext ctx) {
        // Dying ends the rage without charging the cooldown, and re-arms the Nether line.
        ctx.clearState(SPITE_UNTIL);
        ctx.clearState(NETHER_GREETED);
    }

    @Override
    public void tick(AbilityContext ctx) {
        invertHarmfulEffects(ctx);

        double stacks = ctx.state(STACKS, 0.0);

        // Stacks bleed away over time.
        double decaySeconds = ctx.value("hatred_stack_decay_seconds");
        if (stacks > 0.0 && decaySeconds > 0.0) {
            double lastDecay = ctx.state(LAST_DECAY_AT, 0.0);
            if (lastDecay <= 0.0) {
                ctx.setState(LAST_DECAY_AT, ctx.now());
            } else if (ctx.now() - lastDecay >= decaySeconds * 1000.0) {
                stacks = Math.max(0.0, stacks - 1.0);
                ctx.setState(STACKS, stacks);
                ctx.setState(LAST_DECAY_AT, ctx.now());
            }
        }

        if (!ctx.isVulnerable()) {
            return;
        }

        tickSpite(ctx);
        tickUndeadAura(ctx);
        tickNether(ctx);

        // Constant, quiet reminder of what the player is carrying.
        ctx.world().spawnParticles(ParticleTypes.SMOKE,
                ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                1, 0.3, 0.5, 0.3, 0.005);

        double threshold = ctx.value("hatred_instability_threshold");
        if (stacks < threshold) {
            return;
        }

        // Note: no Nausea or Weakness here any more. Hatred now turns harmful effects into
        // helpful ones, so debuffing itself would hand it Strength and Speed instead.
        int overload = (int) (stacks - threshold) + 1;

        // The Soul feeds on its owner once it is this unstable. Never lethal on its own:
        // it stops at half a heart.
        float drain = (float) ctx.value("hatred_instability_damage") * overload / 20.0F * ctx.tickInterval();
        float health = ctx.player().getHealth();
        if (health - drain > 1.0F) {
            ctx.player().setHealth(health - drain);
            if (Math.random() < 0.05) {
                ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                        SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.15F, 2.0F);
            }
        }
    }
}
