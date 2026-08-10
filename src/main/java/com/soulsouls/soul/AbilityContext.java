package com.soulsouls.soul;

import com.soulsouls.config.SoulConfig;
import com.soulsouls.config.SoulsConfig;
import com.soulsouls.data.PlayerSoulData;
import com.soulsouls.data.SoulManager;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * Everything an ability needs, handed to it on each hook.
 *
 * <p>Config lookups, cooldowns and persistent per-player numbers all go through here, so
 * abilities never touch the config file or the save file directly.
 */
public final class AbilityContext {
    private final SoulManager manager;
    private final ServerPlayerEntity player;
    private final Soul soul;
    private final PlayerSoulData data;

    public AbilityContext(SoulManager manager, ServerPlayerEntity player, Soul soul, PlayerSoulData data) {
        this.manager = manager;
        this.player = player;
        this.soul = soul;
        this.data = data;
    }

    public SoulManager manager() {
        return this.manager;
    }

    public ServerPlayerEntity player() {
        return this.player;
    }

    public Soul soul() {
        return this.soul;
    }

    public PlayerSoulData data() {
        return this.data;
    }

    public ServerWorld world() {
        return this.player.getEntityWorld();
    }

    public SoulsConfig config() {
        return this.manager.config();
    }

    public SoulConfig soulConfig() {
        return this.manager.config().soulConfig(this.soul);
    }

    /** How many ticks pass between {@link SoulAbility#tick(AbilityContext)} calls. */
    public int tickInterval() {
        return this.manager.config().ability_tick_interval;
    }

    // ------------------------------------------------------------------ config values

    /** Live value from the config, falling back to the Soul's compiled-in default. */
    public double value(String key) {
        return this.manager.config().value(this.soul, key);
    }

    public double value(String key, double fallback) {
        return this.manager.config().value(this.soul, key, fallback);
    }

    public int intValue(String key) {
        return (int) Math.round(value(key));
    }

    /** A value expressed in seconds, converted to ticks. */
    public int ticksFromSeconds(String key) {
        return (int) Math.round(value(key) * 20.0);
    }

    // ------------------------------------------------------------------ cooldowns

    /**
     * Cooldowns are stored as wall-clock deadlines, so logging out and back in does not
     * skip them.
     */
    public boolean isReady(String key) {
        return this.data.isCooldownReady(key);
    }

    public void startCooldown(String key, double seconds) {
        this.data.setCooldown(key, seconds);
        this.manager.markDirty();
    }

    public long cooldownSecondsLeft(String key) {
        return this.data.cooldownSecondsLeft(key);
    }

    public void clearCooldown(String key) {
        this.data.clearCooldown(key);
        this.manager.markDirty();
    }

    // ------------------------------------------------------------------ persistent state

    public double state(String key, double fallback) {
        return this.data.state(key, fallback);
    }

    public void setState(String key, double value) {
        this.data.setState(key, value);
        this.manager.markDirty();
    }

    public void clearState(String key) {
        this.data.clearState(key);
        this.manager.markDirty();
    }

    /** Wall-clock milliseconds, the clock all timers in this mod are based on. */
    public long now() {
        return System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ shortcuts

    public void message(Text text) {
        this.player.sendMessage(text, false);
    }

    public void actionBar(Text text) {
        this.player.sendMessage(text, true);
    }

    /**
     * Applies a status effect with sensible defaults for a Soul power: no particles and
     * a HUD icon, so the screen does not get noisy.
     */
    public void effect(RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int ticks, int amplifier) {
        this.player.addStatusEffect(new StatusEffectInstance(effect, ticks, amplifier, true, false, true));
    }

    public void effect(RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int ticks) {
        effect(effect, ticks, 0);
    }

    /** Refreshes an effect only when it is missing or about to run out, to avoid re-sending packets every tick. */
    public void refreshEffect(RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int ticks, int amplifier) {
        StatusEffectInstance active = this.player.getStatusEffect(effect);
        if (active == null || active.getAmplifier() < amplifier || active.getDuration() < ticks / 2) {
            effect(effect, ticks, amplifier);
        }
    }

    public boolean isVulnerable() {
        return !this.player.isCreative() && !this.player.isSpectator();
    }

    public float healthFraction() {
        float max = this.player.getMaxHealth();
        return max <= 0.0F ? 0.0F : this.player.getHealth() / max;
    }

    /** Convenience for the very common "give Regeneration" case. */
    public void regenerate(int ticks, int amplifier) {
        effect(StatusEffects.REGENERATION, ticks, amplifier);
    }
}
