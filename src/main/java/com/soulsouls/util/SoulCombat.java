package com.soulsouls.util;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;

/**
 * Small shared checks several Souls need to ask about a hit.
 *
 * <p>These live here rather than in one ability so Integrity and Dedication answer the
 * "was that a mace?" question in exactly the same way.
 */
public final class SoulCombat {
    /** The four worn armour slots, in the order a player sees them. */
    public static final EquipmentSlot[] ARMOUR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private SoulCombat() {
    }

    /**
     * Was this hit a mace?
     *
     * <p>Vanilla only tags the falling <em>smash</em> attack with its own damage type; an
     * ordinary swing of a mace arrives as a plain player attack. Both count here, so
     * "reflects mace hits" means what a player expects it to mean.
     */
    public static boolean isMaceHit(DamageSource source) {
        if (source.isIn(DamageTypeTags.MACE_SMASH)) {
            return true;
        }
        return source.getAttacker() instanceof LivingEntity attacker
                && attacker.getWeaponStack().isOf(Items.MACE);
    }

    /** True if the player is holding a Totem of Undying in either hand, as vanilla requires. */
    public static boolean holdingTotem(LivingEntity entity) {
        return entity.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)
                || entity.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }

    /** The four worn armour pieces, skipping empty slots. getArmorItems() is gone in 1.21.11. */
    public static java.util.List<ItemStack> armourOf(LivingEntity entity) {
        java.util.List<ItemStack> pieces = new java.util.ArrayList<>(4);
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            ItemStack piece = entity.getEquippedStack(slot);
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
        }
        return pieces;
    }

    /**
     * Total durability currently missing from the worn armour. Comparing this before and
     * after a hit is how Dedication measures exactly how much armour the hit cost.
     */
    public static int armourWear(LivingEntity entity) {
        int wear = 0;
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            ItemStack piece = entity.getEquippedStack(slot);
            if (!piece.isEmpty() && piece.isDamageable()) {
                wear += piece.getDamage();
            }
        }
        return wear;
    }
}
