package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import com.soulsouls.util.SoulCombat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Integrity holds together where other things come apart.
 *
 * <p>All passive, no active ability:
 * <ul>
 *   <li>It takes no fall damage at all.</li>
 *   <li>Explosions - TNT, creepers, beds, anything tagged as a blast - land at
 *       <em>half</em> strength.</li>
 *   <li>A blast that does get through <em>repairs its armour</em>, paid for out of the
 *       player's experience: the shock is absorbed and spent putting the plates back
 *       together.</li>
 *   <li>A mace hit has a chance to be turned straight back onto whoever swung it.</li>
 * </ul>
 */
public class IntegrityFallAbility implements SoulAbility {
    @Override
    public String id() {
        return "integrity_fall";
    }

    @Override
    public String displayName() {
        return "Unshaken";
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                // Set to 0 to turn fall immunity off entirely; 1 keeps it on.
                "integrity_fall_immunity", 1.0,
                "integrity_landing_particles", 1.0,
                // How many durability points each experience point buys back.
                "integrity_repair_per_xp", 3.0,
                // Most experience a single explosion may spend.
                "integrity_max_xp_per_explosion", 10.0,
                // Share of explosion damage that still gets through. 0.5 = half damage.
                // Set to 0 to make blasts harmless again, or 1 to take them in full.
                "integrity_explosion_damage_multiplier", 0.5,
                // Percentage chance that a mace hit is thrown back at the attacker.
                "integrity_mace_reflect_chance", 20.0,
                // Share of the reflected hit the attacker takes. 1.0 = all of it.
                "integrity_mace_reflect_power", 1.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        boolean immune = ctx.value("integrity_fall_immunity") > 0.0;
        int explosionPercent = (int) Math.round(ctx.value("integrity_explosion_damage_multiplier") * 100.0);
        return List.of(
                Text.literal("  Fall damage: ").formatted(Formatting.GRAY)
                        .append(Text.literal(immune ? "immune" : "normal").formatted(Formatting.WHITE)),
                Text.literal("  Explosions (TNT, creepers, beds): ").formatted(Formatting.GRAY)
                        .append(Text.literal(explosionPercent + "% damage").formatted(Formatting.WHITE)),
                Text.literal("  Mace hits: ").formatted(Formatting.GRAY)
                        .append(Text.literal(RefuseDeathAbility.formatPercent(
                                        ctx.value("integrity_mace_reflect_chance")))
                                .formatted(Formatting.WHITE))
                        .append(Text.literal(" chance to reflect them").formatted(Formatting.GRAY)),
                Text.literal("  Explosions repair your armour, paid for in experience")
                        .formatted(Formatting.GRAY),
                Text.literal("    " + (int) ctx.value("integrity_repair_per_xp")
                                + " durability per XP, up to "
                                + (int) ctx.value("integrity_max_xp_per_explosion") + " XP a blast")
                        .formatted(Formatting.DARK_GRAY));
    }

    @Override
    public boolean allowDamage(AbilityContext ctx, DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.IS_FALL) && ctx.value("integrity_fall_immunity") > 0.0) {
            if (ctx.value("integrity_landing_particles") > 0.0 && amount >= 3.0F) {
                ctx.world().spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        ctx.player().getX(), ctx.player().getY() + 0.1, ctx.player().getZ(),
                        Math.min(40, (int) (amount * 3)), 0.4, 0.1, 0.4, 0.05);
            }
            return false;
        }

        if (SoulCombat.isMaceHit(source) && reflectMace(ctx, source, amount)) {
            return false;
        }

        if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
            repairArmourWithExperience(ctx, amount);
            return absorbExplosion(ctx, source, amount);
        }

        return true;
    }

    /**
     * Halves a blast. Fabric's event cannot scale a hit, so the original is cancelled and a
     * smaller one is put through in its place - see
     * {@code SoulManager#damageBypassingSouls}. Armour wear, knockback and death handling
     * all still run, because the reduced hit is a real damage call.
     *
     * @return whether the original, full-strength hit should be allowed through
     */
    private boolean absorbExplosion(AbilityContext ctx, DamageSource source, float amount) {
        double multiplier = Math.max(0.0, Math.min(1.0, ctx.value("integrity_explosion_damage_multiplier")));
        if (multiplier >= 1.0) {
            return true;
        }

        ServerPlayerEntity player = ctx.player();
        ctx.world().spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0, player.getZ(),
                20, 0.5, 0.6, 0.5, 0.08);

        float reduced = (float) (amount * multiplier);
        if (reduced > 0.0F) {
            ctx.manager().damageBypassingSouls(player, source, reduced);
        }
        return false;
    }

    /**
     * A mace hit, sent back the way it came.
     *
     * @return true when the hit was reflected and should not land on this player
     */
    private boolean reflectMace(AbilityContext ctx, DamageSource source, float amount) {
        double chance = Math.max(0.0, Math.min(100.0, ctx.value("integrity_mace_reflect_chance")));
        if (chance <= 0.0 || Math.random() * 100.0 >= chance) {
            return false;
        }
        if (!(source.getAttacker() instanceof LivingEntity attacker) || attacker == ctx.player()) {
            return false;
        }

        ServerPlayerEntity player = ctx.player();
        float thrownBack = (float) Math.max(1.0, amount * ctx.value("integrity_mace_reflect_power"));
        attacker.damage(ctx.world(), ctx.world().getDamageSources().playerAttack(player), thrownBack);

        // The mace's own heavy landing sound, plus a hard shockwave where it was stopped.
        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 1.0F, 0.8F);
        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0F, 0.6F);
        ctx.world().spawnParticles(ParticleTypes.EXPLOSION,
                player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.4, 0.4, 0.4, 0.0);
        ctx.world().spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0, player.getZ(), 50, 0.6, 0.8, 0.6, 0.4);

        ctx.actionBar(Text.literal("You did not budge.").formatted(Formatting.BLUE));
        return true;
    }

    /**
     * Turns a blast into repairs. Experience is spent only for damage actually mended, so a
     * player with pristine armour or an empty bar loses nothing.
     */
    private void repairArmourWithExperience(AbilityContext ctx, float blastSize) {
        ServerPlayerEntity player = ctx.player();
        if (player.experienceLevel <= 0 && player.experienceProgress <= 0.0F) {
            return;
        }

        int repairPerXp = Math.max(1, (int) ctx.value("integrity_repair_per_xp"));
        int budget = (int) Math.min(ctx.value("integrity_max_xp_per_explosion"),
                Math.max(1.0, blastSize));

        int spent = 0;
        int repaired = 0;

        for (ItemStack piece : SoulCombat.armourOf(player)) {
            if (spent >= budget) {
                break;
            }
            if (piece.isEmpty() || !piece.isDamaged()) {
                continue;
            }

            int missing = piece.getDamage();
            int affordable = (budget - spent) * repairPerXp;
            int mend = Math.min(missing, affordable);
            if (mend <= 0) {
                continue;
            }

            piece.setDamage(missing - mend);
            repaired += mend;
            spent += Math.max(1, mend / repairPerXp);
        }

        if (repaired <= 0) {
            return;
        }

        player.addExperience(-spent);

        ctx.actionBar(Text.literal("The blast mends your armour (+" + repaired + ")")
                .formatted(Formatting.BLUE));
        ctx.world().spawnParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.6, 0.8, 0.6, 0.6);
        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.7F, 1.4F);
        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.4F, 1.8F);
    }
}
