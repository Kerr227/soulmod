package com.soulsouls.souls.ability;

import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.SoulAbility;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Curiosity finds things.
 *
 * <p>Double-sneak (or {@code /souls ability}) and the nearest structure within range is
 * revealed. Each kind of structure can only be found once - after that Curiosity moves on
 * to something it has not seen, which is what stops it being a repeatable compass.
 *
 * <p>Which structures it knows about is fixed by the list below; the search itself is
 * vanilla's own {@code locateStructure}, the same one {@code /locate} uses.
 */
public class CuriosityAbility implements SoulAbility {
    public static final String COOLDOWN = "curiosity_locate";
    private static final String FOUND_PREFIX = "curiosity_found_";

    /** The structures Curiosity can look for, in the order it tries them. */
    private static final List<Target> TARGETS = List.of(
            new Target("village", StructureTags.VILLAGE),
            new Target("mineshaft", StructureTags.MINESHAFT),
            new Target("shipwreck", StructureTags.SHIPWRECK),
            new Target("ruined_portal", StructureTags.RUINED_PORTAL),
            new Target("ocean_ruin", StructureTags.OCEAN_RUIN),
            new Target("stronghold", StructureTags.EYE_OF_ENDER_LOCATED),
            new Target("buried_treasure", StructureTags.ON_TREASURE_MAPS),
            new Target("woodland_mansion", StructureTags.ON_WOODLAND_EXPLORER_MAPS),
            new Target("ocean_monument", StructureTags.ON_OCEAN_EXPLORER_MAPS),
            new Target("trial_chamber", StructureTags.ON_TRIAL_CHAMBERS_MAPS));

    private record Target(String name, TagKey<Structure> tag) {
    }

    @Override
    public String id() {
        return "curiosity";
    }

    @Override
    public String displayName() {
        return "Wanderlust";
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
                "curiosity_range", 1000.0,
                "curiosity_cooldown_seconds", 500.0
        );
    }

    @Override
    public List<Text> describe(AbilityContext ctx) {
        List<String> found = new ArrayList<>();
        for (Target target : TARGETS) {
            if (ctx.state(FOUND_PREFIX + target.name(), 0.0) > 0.0) {
                found.add(target.name());
            }
        }
        return List.of(
                Text.literal("  Double-sneak: find the nearest structure within ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal((int) ctx.value("curiosity_range") + " blocks")
                                .formatted(Formatting.WHITE)),
                Text.literal("  Already found: ").formatted(Formatting.GRAY)
                        .append(Text.literal(found.isEmpty() ? "nothing yet" : String.join(", ", found))
                                .formatted(Formatting.WHITE)));
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        if (!ctx.isReady(COOLDOWN)) {
            return false;
        }

        int range = Math.max(16, (int) ctx.value("curiosity_range"));
        BlockPos from = ctx.player().getBlockPos();

        for (Target target : TARGETS) {
            // Each structure is a one-off: once found, Curiosity looks for something else.
            if (ctx.state(FOUND_PREFIX + target.name(), 0.0) > 0.0) {
                continue;
            }

            BlockPos found;
            try {
                found = ctx.world().locateStructure(target.tag(), from, range / 16, false);
            } catch (Exception exception) {
                // A structure type that does not generate in this dimension is not an error.
                continue;
            }
            if (found == null) {
                continue;
            }

            ctx.setState(FOUND_PREFIX + target.name(), 1.0);
            ctx.startCooldown(COOLDOWN, ctx.value("curiosity_cooldown_seconds"));
            announce(ctx, target, found, from);
            return true;
        }

        ctx.message(Text.literal("Curiosity finds nothing new within reach.")
                .formatted(Formatting.GRAY));
        return false;
    }

    private void announce(AbilityContext ctx, Target target, BlockPos found, BlockPos from) {
        int distance = (int) Math.sqrt(from.getSquaredDistance(found));

        ctx.message(Text.literal("Curiosity found a ").formatted(Formatting.GRAY)
                .append(Text.literal(target.name().replace('_', ' ')).formatted(Formatting.AQUA))
                .append(Text.literal(" at ").formatted(Formatting.GRAY))
                .append(Text.literal(found.getX() + ", " + found.getZ()).formatted(Formatting.WHITE))
                .append(Text.literal(" (" + distance + " blocks away)").formatted(Formatting.DARK_GRAY)));

        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, net.minecraft.sound.SoundCategory.PLAYERS, 0.8F, 1.5F);
        ctx.world().playSound(null, ctx.player().getX(), ctx.player().getY(), ctx.player().getZ(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 0.7F);
        ctx.world().spawnParticles(ParticleTypes.END_ROD,
                ctx.player().getX(), ctx.player().getY() + 1.2, ctx.player().getZ(),
                40, 0.5, 0.7, 0.5, 0.06);
    }
}
