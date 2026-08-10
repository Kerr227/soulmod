package com.soulsouls.soul;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;

/**
 * One behaviour attached to a {@link Soul}.
 *
 * <p>Every hook has a no-op default, so an ability only implements the events it cares
 * about. Abilities are stateless singletons - all per-player state lives in
 * {@code PlayerSoulData} and is reached through the {@link AbilityContext}, which is what
 * makes cooldowns and timers survive relogs, deaths and restarts.
 *
 * <h2>Adding a new ability</h2>
 * <ol>
 *   <li>Create a class implementing this interface in {@code com.soulsouls.souls.ability}.</li>
 *   <li>Declare its tunable numbers in {@link #defaultValues()} - they appear in the config
 *       automatically and are read back with {@code ctx.value("my_key")}.</li>
 *   <li>Attach it to a Soul with {@code .ability(new MyAbility())} in
 *       {@code com.soulsouls.souls.Souls}.</li>
 * </ol>
 */
public interface SoulAbility {
    /** Stable identifier, also used as the prefix for this ability's cooldown keys. */
    String id();

    /** Human readable name shown by {@code /souls info}, e.g. "Refuse Death". */
    String displayName();

    /**
     * Tunable numbers this ability exposes to the config file. Keys should be prefixed
     * with something specific to avoid clashing with other abilities on the same Soul.
     */
    default Map<String, Double> defaultValues() {
        return Map.of();
    }

    /** Extra lines describing the current, config-resolved numbers in {@code /souls info}. */
    default List<Text> describe(AbilityContext ctx) {
        return List.of();
    }

    // ------------------------------------------------------------------ lifecycle

    /** Called when this Soul is given to a player (assignment, {@code /souls give}, {@code /souls set}). */
    default void onAssign(AbilityContext ctx) {
    }

    /** Called when the player loses this Soul, so the ability can clean up after itself. */
    default void onRemove(AbilityContext ctx) {
    }

    /** Called when a player carrying this Soul joins the server. */
    default void onJoin(AbilityContext ctx) {
    }

    /** Called after respawning and after a dimension change, when the player entity is rebuilt. */
    default void onRespawn(AbilityContext ctx) {
    }

    /**
     * Periodic tick, every {@code ability_tick_interval} ticks (5 by default, i.e. 4x a
     * second). Use {@link AbilityContext#tickInterval()} when converting to real time.
     */
    default void tick(AbilityContext ctx) {
    }

    // ------------------------------------------------------------------ combat

    /**
     * Return {@code false} to cancel incoming damage entirely (used by Integrity for fall
     * damage). Partial reduction is done with vanilla Resistance instead.
     */
    default boolean allowDamage(AbilityContext ctx, DamageSource source, float amount) {
        return true;
    }

    /**
     * Return {@code false} to refuse a death that would otherwise happen. Anything that
     * returns {@code false} <em>must</em> leave the player alive (heal them), otherwise the
     * death event fires again on the next tick.
     */
    default boolean allowDeath(AbilityContext ctx, DamageSource source, float amount) {
        return true;
    }

    /** Called after the player took damage and survived. */
    default void afterDamage(AbilityContext ctx, DamageSource source, float taken) {
    }

    /** Called when the player melee-attacks an entity. */
    default void onAttack(AbilityContext ctx, Entity target) {
    }

    /** Called when the player kills a living entity. */
    default void onKill(AbilityContext ctx, LivingEntity victim) {
    }

    /** Called when an arrow/trident fired by this player enters the world, before it flies. */
    default void onProjectileFired(AbilityContext ctx, PersistentProjectileEntity projectile) {
    }

    /**
     * Called on every online Soul holder when any player dies, so a Soul can react to
     * something that happened to somebody else (Retribution uses this).
     *
     * @param killer the player responsible, or {@code null} if it was not a player kill
     */
    default void onOtherPlayerDeath(AbilityContext ctx, ServerPlayerEntity victim, ServerPlayerEntity killer) {
    }

    // ------------------------------------------------------------------ active use

    /**
     * Triggered by {@code /souls ability}. Return {@code true} if the ability actually did
     * something (the command then reports success). Implementations own their cooldown.
     */
    default boolean activate(AbilityContext ctx) {
        return false;
    }

    /** Whether {@code /souls ability} should list this ability as activatable. */
    default boolean isActive() {
        return false;
    }

    /** Cooldown keys this ability wants shown by {@code /souls debug}. */
    default List<String> cooldownKeys() {
        return List.of();
    }
}
