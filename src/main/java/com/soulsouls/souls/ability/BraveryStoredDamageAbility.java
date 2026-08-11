package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Bravery takes it and gives it back - but against armour, not flesh.
 *
 * <p>Every hit Bravery survives is stored. On the tenth hit the stored total is discharged
 * into the next person it strikes as pure durability damage: their armour takes everything
 * Bravery has been carrying, and they take no health damage from it at all.
 *
 * <p>The counter and the stored total both live in saved data, so a fight that spans a
 * relog or a restart still pays out.
 */
public class BraveryStoredDamageAbility implements SoulAbility {
    private static final String HITS_TAKEN = "bravery_hits_taken";
    private static final String STORED_DAMAGE = "bravery_stored_damage";
    private static final String CHARGED = "bravery_charged";

    @Override
    public String id() {
        return "bravery_stored_damage";
    }

    @Override
    public String displayName() {
        return "Answered in Kind";
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                "bravery_hits_to_charge", 10.0,
                // Durability points inflicted per point of stored damage.
                "bravery_durability_per_damage", 1.0,
                "bravery_max_stored_damage", 200.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        int hits = (int) ctx.state(HITS_TAKEN, 0.0);
        int needed = (int) ctx.value("bravery_hits_to_charge");
        boolean charged = ctx.state(CHARGED, 0.0) > 0.0;

        return List.of(
                Text.literal("  Stores every hit you take. After ").formatted(Formatting.GRAY)
                        .append(Text.literal(needed + " hits").formatted(Formatting.WHITE))
                        .append(Text.literal(" your next strike shreds armour").formatted(Formatting.GRAY)),
                Text.literal("  The target takes no health damage from it, only durability")
                        .formatted(Formatting.DARK_GRAY),
                Text.literal("  Charge: ").formatted(Formatting.GRAY)
                        .append(Text.literal(charged
                                        ? "READY (" + (int) ctx.state(STORED_DAMAGE, 0.0) + " stored)"
                                        : hits + "/" + needed)
                                .formatted(charged ? Formatting.GOLD : Formatting.WHITE)));
    }

    @Override
    public void afterDamage(AbilityContext ctx, DamageSource source, float taken) {
        if (ctx.state(CHARGED, 0.0) > 0.0) {
            // Already loaded: keep the charge until it is spent.
            return;
        }

        double stored = Math.min(ctx.value("bravery_max_stored_damage"),
                ctx.state(STORED_DAMAGE, 0.0) + taken);
        double hits = ctx.state(HITS_TAKEN, 0.0) + 1.0;

        ctx.setState(STORED_DAMAGE, stored);
        ctx.setState(HITS_TAKEN, hits);

        if (hits < ctx.value("bravery_hits_to_charge")) {
            ctx.actionBar(Text.literal("Bravery stores the blow (" + (int) hits + "/"
                            + (int) ctx.value("bravery_hits_to_charge") + ")")
                    .formatted(Formatting.GOLD));
            return;
        }

        ctx.setState(CHARGED, 1.0);
        ctx.actionBar(Text.literal("Bravery is charged - your next hit will shred armour.")
                .formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_ANVIL_LAND, net.minecraft.sound.SoundCategory.PLAYERS, 0.6F, 1.6F);
    }

    @Override
    public void onAttack(AbilityContext ctx, Entity target) {
        if (ctx.state(CHARGED, 0.0) <= 0.0 || !(target instanceof ServerPlayerEntity victim)) {
            return;
        }

        double stored = ctx.state(STORED_DAMAGE, 0.0);
        int durability = (int) Math.round(stored * ctx.value("bravery_durability_per_damage"));
        if (durability <= 0) {
            return;
        }

        // Spread the whole stored total across whatever they are wearing. Nothing here
        // touches their health - the punishment is entirely to their gear.
        int spread = 0;
        for (ItemStack piece : IntegrityFallAbility.armourOf(victim)) {
            if (piece.isEmpty() || !piece.isDamageable()) {
                continue;
            }
            int share = Math.max(1, durability / 4);
            int newDamage = Math.min(piece.getMaxDamage() - 1, piece.getDamage() + share);
            spread += newDamage - piece.getDamage();
            piece.setDamage(newDamage);
        }

        ctx.setState(STORED_DAMAGE, 0.0);
        ctx.setState(HITS_TAKEN, 0.0);
        ctx.setState(CHARGED, 0.0);

        ctx.actionBar(Text.literal("You return " + spread + " points of punishment.")
                .formatted(Formatting.GOLD));
        victim.sendMessage(Text.literal("Your armour buckles under stored fury.")
                .formatted(Formatting.GOLD), true);

        ctx.world().spawnParticles(ParticleTypes.CRIT,
                victim.getX(), victim.getY() + 1.0, victim.getZ(), 40, 0.4, 0.6, 0.4, 0.2);
        ctx.world().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.ENTITY_ITEM_BREAK, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 0.8F);
    }
}
