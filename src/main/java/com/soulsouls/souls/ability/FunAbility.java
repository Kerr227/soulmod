package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fun is unpredictable and, on purpose, never actually strong.
 *
 * <p>It grants Luck passively and, every few minutes, rolls a small harmless event: a
 * short buff, a shower of sparks, a silly noise, or a snack. Nothing on this list changes a
 * fight, which is the point - the Soul is a mood, not a weapon.
 *
 * <p>To add your own event, drop a case into {@link #rollEvent}; the number of cases is read
 * from {@code EVENT_COUNT}.
 */
public class FunAbility implements SoulAbility {
    private static final String NEXT_EVENT_AT = "fun_next_event_at";
    private static final int EVENT_COUNT = 6;

    @Override
    public String id() {
        return "fun";
    }

    @Override
    public String displayName() {
        return "Anything Goes";
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                "fun_luck_amplifier", 0.0,
                "fun_min_event_minutes", 2.0,
                "fun_max_event_minutes", 5.0,
                "fun_reward_chance", 25.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  Permanent Luck ").formatted(Formatting.GRAY)
                        .append(Text.literal(BraveryMomentumAbility.roman((int) ctx.value("fun_luck_amplifier") + 1))
                                .formatted(Formatting.WHITE)),
                Text.literal("  Something harmless happens every ").formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("fun_min_event_minutes") + "-"
                                        + (int) ctx.value("fun_max_event_minutes") + " min")
                                .formatted(Formatting.WHITE))
        );
    }

    @Override
    public void tick(AbilityContext ctx) {
        ctx.refreshEffect(StatusEffects.LUCK, 400, Math.max(0, (int) ctx.value("fun_luck_amplifier")));

        if (!ctx.isVulnerable()) {
            return;
        }

        double nextAt = ctx.state(NEXT_EVENT_AT, 0.0);
        if (nextAt <= 0.0) {
            scheduleNext(ctx);
            return;
        }
        if (ctx.now() < nextAt) {
            return;
        }

        scheduleNext(ctx);
        rollEvent(ctx, ThreadLocalRandom.current().nextInt(EVENT_COUNT));
    }

    private void scheduleNext(AbilityContext ctx) {
        double min = Math.max(0.1, ctx.value("fun_min_event_minutes"));
        double max = Math.max(min, ctx.value("fun_max_event_minutes"));
        double minutes = min + ThreadLocalRandom.current().nextDouble() * (max - min);
        ctx.setState(NEXT_EVENT_AT, ctx.now() + minutes * 60_000.0);
    }

    /** One harmless surprise. Add a case here and bump {@code EVENT_COUNT} to extend the list. */
    private void rollEvent(AbilityContext ctx, int event) {
        switch (event) {
            case 0 -> {
                ctx.effect(StatusEffects.SPEED, 400, 0);
                say(ctx, "Zoom.");
            }
            case 1 -> {
                ctx.effect(StatusEffects.JUMP_BOOST, 400, 1);
                say(ctx, "Boing!");
            }
            case 2 -> {
                ctx.effect(StatusEffects.SATURATION, 60, 0);
                say(ctx, "Snack time.");
            }
            case 3 -> fireworks(ctx);
            case 4 -> {
                ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                        SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, net.minecraft.sound.SoundCategory.PLAYERS,
                        1.0F, 0.5F);
                say(ctx, "Did you hear that?");
            }
            default -> reward(ctx);
        }
    }

    private void fireworks(AbilityContext ctx) {
        ctx.world().spawnParticles(ParticleTypes.FIREWORK,
                ctx.player().getX(), ctx.player().getY() + 2.0, ctx.player().getZ(),
                60, 1.0, 0.8, 1.0, 0.15);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, net.minecraft.sound.SoundCategory.PLAYERS,
                1.0F, 1.0F);
        say(ctx, "Ta-da!");
    }

    private void reward(AbilityContext ctx) {
        if (ThreadLocalRandom.current().nextDouble() * 100.0 >= ctx.value("fun_reward_chance")) {
            say(ctx, "...nothing happened. How fun.");
            return;
        }

        ItemStack prize = switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> new ItemStack(Items.COOKIE, 4);
            case 1 -> new ItemStack(Items.CAKE);
            case 2 -> new ItemStack(Items.EXPERIENCE_BOTTLE, 3);
            default -> new ItemStack(Items.EMERALD);
        };

        if (!ctx.player().giveItemStack(prize)) {
            ctx.player().dropItem(prize, false);
        }
        say(ctx, "A gift! You are welcome.");
        ctx.world().spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                ctx.player().getX(), ctx.player().getY() + 1.5, ctx.player().getZ(),
                20, 0.5, 0.5, 0.5, 0.02);
    }

    private void say(AbilityContext ctx, String message) {
        ctx.actionBar(Text.literal(message).formatted(Formatting.LIGHT_PURPLE));
    }
}
