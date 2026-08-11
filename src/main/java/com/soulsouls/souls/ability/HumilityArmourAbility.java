package com.soulsouls.souls.ability;

import com.soulsouls.SoulSouls;
import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Humility is protected in inverse proportion to what it wears.
 *
 * <ul>
 *   <li>No armour at all - protected as if wearing full netherite.</li>
 *   <li>Anything up to iron - protected as if wearing full diamond.</li>
 *   <li>Full diamond or netherite - no protection whatsoever.</li>
 * </ul>
 *
 * <p>Implemented as an attribute modifier rather than by rewriting the armour calculation:
 * the modifier is removed, the real equipment armour is read, and a new modifier is added
 * to reach the target. That keeps it compatible with every other mod that touches armour.
 */
public class HumilityArmourAbility implements SoulAbility {
    private static final Identifier ARMOUR_MODIFIER_ID =
            Identifier.of(SoulSouls.MOD_ID, "humility_armour");

    @Override
    public String id() {
        return "humility_armour";
    }

    @Override
    public String displayName() {
        return "Inverted Guard";
    }

    @Override
    public Map<String, Double> defaultValues() {
        return Map.of(
                // Armour points. Vanilla: full iron 15, full diamond and netherite 20.
                "humility_light_threshold", 15.0,
                "humility_unarmoured_target", 20.0,
                "humility_light_target", 20.0,
                "humility_heavy_target", 0.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        return List.of(
                Text.literal("  No armour -> protected like netherite").formatted(Formatting.GRAY),
                Text.literal("  Up to iron -> protected like diamond").formatted(Formatting.GRAY),
                Text.literal("  Diamond or netherite -> no protection at all").formatted(Formatting.GRAY));
    }

    @Override
    public void onRemove(AbilityContext ctx) {
        EntityAttributeInstance armour = ctx.player().getAttributeInstance(EntityAttributes.ARMOR);
        if (armour != null) {
            armour.removeModifier(ARMOUR_MODIFIER_ID);
        }
    }

    @Override
    public void tick(AbilityContext ctx) {
        EntityAttributeInstance armour = ctx.player().getAttributeInstance(EntityAttributes.ARMOR);
        if (armour == null) {
            return;
        }

        // Drop our own modifier first so what is left is the real equipment value.
        armour.removeModifier(ARMOUR_MODIFIER_ID);
        double worn = armour.getValue();

        double target;
        if (worn <= 0.0) {
            target = ctx.value("humility_unarmoured_target");
        } else if (worn <= ctx.value("humility_light_threshold")) {
            target = ctx.value("humility_light_target");
        } else {
            target = ctx.value("humility_heavy_target");
        }

        double delta = target - worn;
        if (Math.abs(delta) > 0.0001) {
            armour.addPersistentModifier(new EntityAttributeModifier(
                    ARMOUR_MODIFIER_ID, delta, EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }
}
