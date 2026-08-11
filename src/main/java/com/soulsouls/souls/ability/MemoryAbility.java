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
                "memory_scan_cooldown_seconds", 30.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        long remembered = ctx.data().state.keySet().stream()
                .filter(key -> key.startsWith(SEEN_PREFIX))
                .count();
        return List.of(
                Text.literal("  Double-sneak: remember everyone within ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("memory_scan_radius") + " blocks")
                                .formatted(Formatting.WHITE)),
                Text.literal("  Read the list with ").formatted(Formatting.GRAY)
                        .append(Text.literal("/memory memories").formatted(Formatting.WHITE)),
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

    @Override
    public void afterDamage(AbilityContext ctx, DamageSource source, float taken) {
        // Nothing to do - the death itself is recorded from SoulManager.
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        if (!ctx.isReady(COOLDOWN)) {
            return false;
        }

        double radius = ctx.value("memory_scan_radius");
        double radiusSquared = radius * radius;
        int learned = 0;

        for (ServerPlayerEntity nearby : ctx.world().getPlayers()) {
            if (nearby == ctx.player() || nearby.squaredDistanceTo(ctx.player()) > radiusSquared) {
                continue;
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

        ctx.actionBar(Text.literal(learned == 0
                        ? "Nobody new to remember."
                        : "Remembered " + learned + (learned == 1 ? " person." : " people."))
                .formatted(Formatting.YELLOW));
        ctx.world().spawnParticles(ParticleTypes.ENCHANT,
                ctx.player().getX(), ctx.player().getY() + 1.5, ctx.player().getZ(),
                30, 1.0, 0.5, 1.0, 0.4);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, net.minecraft.sound.SoundCategory.PLAYERS,
                0.7F, 1.2F);
        return true;
    }
}
