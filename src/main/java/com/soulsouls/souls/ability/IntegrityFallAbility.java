package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;

/**
 * Integrity holds together where other things come apart.
 *
 * <p>Two passives, no active ability:
 * <ul>
 *   <li>It takes no fall damage at all.</li>
 *   <li>An explosion that would hurt it instead <em>repairs its armour</em>, paid for out
 *       of the player's experience - the blast is absorbed and spent putting the plates
 *       back together.</li>
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
                // 1 to cancel the explosion damage as well, 0 to only repair.
                "integrity_explosions_harmless", 1.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        boolean immune = ctx.value("integrity_fall_immunity") > 0.0;
        return List.of(
                Text.literal("  Fall damage: ").formatted(Formatting.GRAY)
                        .append(Text.literal(immune ? "immune" : "normal").formatted(Formatting.WHITE)),
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

        if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
            repairArmourWithExperience(ctx, amount);
            return ctx.value("integrity_explosions_harmless") <= 0.0;
        }

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

        for (ItemStack piece : player.getArmorItems()) {
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
                SoundEvents.BLOCK_ANVIL_USE, net.minecraft.sound.SoundCategory.PLAYERS, 0.7F, 1.4F);
        ctx.world().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.4F, 1.8F);
    }
}
