package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Memory keeps a record of where it fell and who it has met.
 *
 * <p>Dying prints the exact coordinates of the death, so the walk back is never guesswork.
 * Double-sneak scans everyone in front of the player into a list, which
 * {@code /memory memories} reads back.
 *
 * <p>The list lives in the player's saved Soul data, so it survives relogs and restarts
 * like every other piece of Soul state.
 */
public class MemoryAbility implements SoulAbility {
    public static final String COOLDOWN = "memory_scan";

    /** Prefix for a remembered player: {@code memory_seen_<uuid>} holds when they were met. */
    public static final String SEEN_PREFIX = "memory_seen_";

    /** Where this player last died. */
    public static final String DEATH_X = "memory_death_x";
    public static final String DEATH_Y = "memory_death_y";
    public static final String DEATH_Z = "memory_death_z";

    /** When the current scan began, or 0 when nothing is charging. */
    public static final String SCAN_STARTED_AT = "memory_scan_started_at";

    @Override
    public String id() {
        return "memory";
    }

    @Override
    public String displayName() {
        return "Recall";
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
                "memory_scan_radius", 10.0,
                "memory_scan_cooldown_seconds", 30.0,
                // The scan is not instant - it has to hold for this long to finish.
                "memory_scan_charge_seconds", 5.0,
                // Being hit mid-scan adds this to the cooldown and cancels the scan.
                "memory_interrupt_penalty_seconds", 500.0,
                // Most people the list will hold; oldest are dropped past this.
                "memory_max_remembered", 100.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        long remembered = ctx.data().state.keySet().stream()
                .filter(key -> key.startsWith(SEEN_PREFIX))
                .count();
        return List.of(
                Text.literal("  /souls ability: remember everyone within ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("memory_scan_radius") + " blocks")
                                .formatted(Formatting.WHITE)),
                Text.literal("  The scan takes ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("memory_scan_charge_seconds") + "s")
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" - being hit cancels it").formatted(Formatting.GRAY)),
                Text.literal("  Read the list with ").formatted(Formatting.GRAY)
                        .append(Text.literal("/memory list").formatted(Formatting.WHITE)),
                Text.literal("  Remembered so far: ").formatted(Formatting.GRAY)
                        .append(Text.literal(String.valueOf(remembered)).formatted(Formatting.WHITE)),
                Text.literal("  You are told where you died.").formatted(Formatting.GRAY));
    }

    /** Remembers the spot. The message itself is sent on the way out, before the screen fades. */
    public static void rememberDeath(AbilityContext ctx) {
        ServerPlayerEntity player = ctx.player();
        ctx.setState(DEATH_X, Math.floor(player.getX()));
        ctx.setState(DEATH_Y, Math.floor(player.getY()));
        ctx.setState(DEATH_Z, Math.floor(player.getZ()));

        ctx.message(Text.literal("You died at ").formatted(Formatting.GRAY)
                .append(Text.literal((int) Math.floor(player.getX()) + ", "
                                + (int) Math.floor(player.getY()) + ", "
                                + (int) Math.floor(player.getZ()))
                        .formatted(Formatting.YELLOW))
                .append(Text.literal(". Memory will not lose it.").formatted(Formatting.GRAY)));
    }

    /** Being hit while scanning ruins your concentration, and costs you dearly. */
    @Override
    public void afterDamage(AbilityContext ctx, DamageSource source, float taken) {
        if (ctx.state(SCAN_STARTED_AT, 0.0) <= 0.0) {
            return;
        }

        ctx.clearState(SCAN_STARTED_AT);
        double penalty = ctx.value("memory_interrupt_penalty_seconds");
        ctx.startCooldown(COOLDOWN, penalty);

        ctx.message(Text.literal("Your concentration breaks. Memory will not settle for "
                        + (int) penalty + " seconds.")
                .formatted(Formatting.RED));
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_BEACON_DEACTIVATE, net.minecraft.sound.SoundCategory.PLAYERS,
                0.7F, 0.6F);
    }

    /** Runs the charge, and finishes the scan when it completes. */
    @Override
    public void tick(AbilityContext ctx) {
        double startedAt = ctx.state(SCAN_STARTED_AT, 0.0);
        if (startedAt <= 0.0) {
            return;
        }

        double chargeMillis = Math.max(0.1, ctx.value("memory_scan_charge_seconds")) * 1000.0;
        double elapsed = ctx.now() - startedAt;

        if (elapsed < chargeMillis) {
            int percent = (int) ((elapsed / chargeMillis) * 100.0);
            ctx.actionBar(Text.literal("Remembering... " + percent + "%").formatted(Formatting.YELLOW));
            ctx.world().spawnParticles(ParticleTypes.ENCHANT,
                    ctx.player().getX(), ctx.player().getY() + 1.5, ctx.player().getZ(),
                    6, 0.6, 0.4, 0.6, 0.3);
            return;
        }

        ctx.clearState(SCAN_STARTED_AT);
        finishScan(ctx);
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        if (!ctx.isReady(COOLDOWN)) {
            return false;
        }
        if (ctx.state(SCAN_STARTED_AT, 0.0) > 0.0) {
            return false;
        }

        // The scan only begins here; tick() finishes it once the charge completes.
        ctx.setState(SCAN_STARTED_AT, ctx.now());
        ctx.actionBar(Text.literal("Remembering...").formatted(Formatting.YELLOW));
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, net.minecraft.sound.SoundCategory.PLAYERS,
                0.6F, 0.8F);
        return true;
    }

    private void finishScan(AbilityContext ctx) {
        double radius = ctx.value("memory_scan_radius");
        double radiusSquared = radius * radius;
        int learned = 0;

        for (ServerPlayerEntity nearby : ctx.world().getPlayers()) {
            if (nearby == ctx.player() || nearby.squaredDistanceTo(ctx.player()) > radiusSquared) {
                continue;
            }
            // You cannot remember a face you never saw.
            if (nearby.isInvisible()) {
                continue;
            }
            if (ctx.data().remembered_names.size() >= (int) ctx.value("memory_max_remembered")) {
                break;
            }
            String key = SEEN_PREFIX + nearby.getUuid();
            if (ctx.state(key, 0.0) > 0.0) {
                continue;
            }
            ctx.setState(key, ctx.now());
            ctx.data().remembered_names.put(nearby.getUuid().toString(), nearby.getNameForScoreboard());
            learned++;
        }

        ctx.manager().markDirty();
        ctx.startCooldown(COOLDOWN, ctx.value("memory_scan_cooldown_seconds"));

        ctx.message(Text.literal(learned == 0
                        ? "Nobody new to remember."
                        : "Remembered " + learned + (learned == 1 ? " person." : " people."))
                .formatted(Formatting.YELLOW));
        ctx.world().spawnParticles(ParticleTypes.ENCHANT,
                ctx.player().getX(), ctx.player().getY() + 1.5, ctx.player().getZ(),
                30, 1.0, 0.5, 1.0, 0.4);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, net.minecraft.sound.SoundCategory.PLAYERS,
                0.7F, 1.2F);
    }
}
