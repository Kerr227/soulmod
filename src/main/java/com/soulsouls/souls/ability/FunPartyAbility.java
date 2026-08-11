package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import com.soulsouls.util.SoulEffects;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import it.unimi.dsi.fastutil.ints.IntList;

/**
 * Fun's party trick: double-sneak and one of three things happens.
 *
 * <ul>
 *   <li><b>Party</b> - villagers appear all around you.</li>
 *   <li><b>Dog fight</b> - a pack of wolves appears, already tamed to you.</li>
 *   <li><b>Fireworks</b> - ten seconds of coloured fireworks that hurt everyone but you.</li>
 * </ul>
 *
 * <p>Each one shouts a line, heard only by players close enough to be part of it. Turn the
 * lines off with {@code soul_voice_lines} in the config.
 */
public class FunPartyAbility implements SoulAbility {
    public static final String COOLDOWN = "fun_party";
    private static final String FIREWORKS_UNTIL = "fun_fireworks_until";

    @Override
    public String id() {
        return "fun_party";
    }

    @Override
    public String displayName() {
        return "Party Trick";
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
                "fun_party_cooldown_seconds", 120.0,
                "fun_voice_radius", 10.0,
                "fun_villager_count", 8.0,
                "fun_wolf_count", 20.0,
                "fun_wolf_radius", 2.0,
                "fun_fireworks_seconds", 10.0,
                "fun_firework_damage", 3.0,
                "fun_firework_radius", 4.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  Double-sneak: one of three parties").formatted(Formatting.GRAY),
                Text.literal("    villagers, a wolf pack, or ten seconds of fireworks")
                        .formatted(Formatting.DARK_GRAY));
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        if (!ctx.isReady(COOLDOWN)) {
            return false;
        }
        ctx.startCooldown(COOLDOWN, ctx.value("fun_party_cooldown_seconds"));

        switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> party(ctx);
            case 1 -> dogFight(ctx);
            default -> fireworks(ctx);
        }
        return true;
    }

    // ------------------------------------------------------------------ the three parties

    private void party(AbilityContext ctx) {
        ServerWorld world = ctx.world();
        int count = Math.max(1, (int) ctx.value("fun_villager_count"));

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 / count) * i;
            BlockPos where = BlockPos.ofFloored(
                    ctx.player().getX() + Math.cos(angle) * 2.5,
                    ctx.player().getY(),
                    ctx.player().getZ() + Math.sin(angle) * 2.5);
            EntityType.VILLAGER.spawn(world, where, SpawnReason.COMMAND);
        }

        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                ctx.player().getX(), ctx.player().getY() + 1.0, ctx.player().getZ(),
                40, 2.0, 1.0, 2.0, 0.05);
        world.playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_VILLAGER_CELEBRATE, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        say(ctx, "partyy!!");
    }

    private void dogFight(AbilityContext ctx) {
        ServerWorld world = ctx.world();
        int count = Math.max(1, (int) ctx.value("fun_wolf_count"));
        double radius = Math.max(0.5, ctx.value("fun_wolf_radius"));

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 / count) * i;
            BlockPos where = BlockPos.ofFloored(
                    ctx.player().getX() + Math.cos(angle) * radius,
                    ctx.player().getY(),
                    ctx.player().getZ() + Math.sin(angle) * radius);

            WolfEntity wolf = EntityType.WOLF.spawn(world, where, SpawnReason.COMMAND);
            if (wolf != null) {
                // They belong to whoever threw the party, so they fight for them.
                wolf.setTamedBy(ctx.player());
                wolf.setSitting(false);
            }
        }

        world.playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_WOLF_HOWL, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        say(ctx, "dog fight!");
    }

    private void fireworks(AbilityContext ctx) {
        ctx.setState(FIREWORKS_UNTIL, ctx.now() + ctx.value("fun_fireworks_seconds") * 1000.0);
        launchFirework(ctx);
        say(ctx, "fireworks!");
    }

    /**
     * Keeps the firework show going, and does the damage itself so the Fun player is never
     * caught in their own display.
     */
    @Override
    public void tick(AbilityContext ctx) {
        double until = ctx.state(FIREWORKS_UNTIL, 0.0);
        if (until <= 0.0) {
            return;
        }
        if (ctx.now() >= until) {
            ctx.clearState(FIREWORKS_UNTIL);
            return;
        }

        // Roughly one rocket a second at the default tick interval.
        if (ThreadLocalRandom.current().nextInt(Math.max(1, 20 / Math.max(1, ctx.tickInterval()))) == 0) {
            launchFirework(ctx);
            damageNearbyEnemies(ctx);
        }
    }

    private void launchFirework(AbilityContext ctx) {
        ServerWorld world = ctx.world();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // A different colour every time, from the full range.
        int colour = random.nextInt(0xFFFFFF + 1);
        int fade = random.nextInt(0xFFFFFF + 1);

        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(1, List.of(
                new FireworkExplosionComponent(FireworkExplosionComponent.Type.LARGE_BALL,
                        IntList.of(colour), IntList.of(fade), true, true))));

        Vec3d at = new Vec3d(
                ctx.player().getX() + (random.nextDouble() - 0.5) * 4.0,
                ctx.player().getY() + 0.5,
                ctx.player().getZ() + (random.nextDouble() - 0.5) * 4.0);

        world.spawnEntity(new FireworkRocketEntity(world, at.x, at.y, at.z, rocket));
    }

    /** The display is only dangerous to other people. */
    private void damageNearbyEnemies(AbilityContext ctx) {
        double radius = ctx.value("fun_firework_radius");
        float damage = (float) ctx.value("fun_firework_damage");
        if (damage <= 0.0F) {
            return;
        }

        for (ServerPlayerEntity nearby : ctx.world().getPlayers()) {
            if (nearby == ctx.player() || nearby.squaredDistanceTo(ctx.player()) > radius * radius) {
                continue;
            }
            nearby.damage(ctx.world(),
                    ctx.world().getDamageSources().playerAttack(ctx.player()), damage);
        }
    }

    /** Says something, but only to the people near enough to be part of the party. */
    private void say(AbilityContext ctx, String line) {
        if (!ctx.config().soul_voice_lines) {
            return;
        }
        double radius = ctx.value("fun_voice_radius");
        Text message = Text.literal(ctx.player().getNameForScoreboard() + ": ")
                .formatted(Formatting.GRAY)
                .append(Text.literal(line).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));

        for (ServerPlayerEntity nearby : ctx.world().getPlayers()) {
            if (nearby.squaredDistanceTo(ctx.player()) <= radius * radius) {
                nearby.sendMessage(message, false);
            }
        }
        SoulEffects.burst(ctx.world(), ctx.player(), ParticleTypes.NOTE, 20);
    }
}
