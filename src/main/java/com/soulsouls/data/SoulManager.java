package com.soulsouls.data;

import com.soulsouls.SoulSouls;
import com.soulsouls.config.SoulsConfig;
import com.soulsouls.soul.AbilityContext;
import com.soulsouls.soul.Soul;
import com.soulsouls.soul.SoulAbility;
import com.soulsouls.soul.SoulRegistry;
import com.soulsouls.util.SoulEffects;
import com.soulsouls.util.SoulTeams;
import com.soulsouls.util.SoulText;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * The heart of the mod: owns the config, the saved player data, Soul assignment, and the
 * dispatch of every event to the abilities of whichever Soul a player is carrying.
 *
 * <p>There is one instance per running server, created on {@code SERVER_STARTED} and
 * dropped on {@code SERVER_STOPPING}, which keeps everything correct on integrated
 * (single-player) servers where worlds are opened and closed repeatedly.
 */
public final class SoulManager {
    /** Identifier of the attribute modifier this mod uses for Soul max health. */
    public static final Identifier MAX_HEALTH_MODIFIER_ID = Identifier.of(SoulSouls.MOD_ID, "soul_max_health");

    private static SoulManager instance;

    private final MinecraftServer server;
    private final SoulStorage storage;
    private final Map<UUID, PlayerSoulData> players;
    private SoulsConfig config;

    private final Random random = new Random();

    /** Horizontal distance each player covered since the last ability tick (Bravery uses it). */
    private final Map<UUID, Vec3d> lastPositions = new HashMap<>();
    private final Map<UUID, Double> movedSinceLastTick = new HashMap<>();

    /**
     * Players whose death must not be refused. Abilities that eventually give up and let a
     * player die (Dedication) add themselves here for the duration of the killing blow.
     */
    private final java.util.Set<UUID> deathBypass = new java.util.HashSet<>();

    private boolean dirty;
    private long lastSaveMillis = System.currentTimeMillis();
    private int tickCounter;

    private SoulManager(MinecraftServer server, SoulsConfig config) {
        this.server = server;
        this.config = config;
        this.storage = new SoulStorage(server);
        this.players = this.storage.load();
    }

    // ------------------------------------------------------------------ lifecycle

    public static SoulManager start(MinecraftServer server, SoulsConfig config) {
        instance = new SoulManager(server, config);
        SoulTeams.syncTeams(server, config);
        SoulSouls.LOGGER.info("Loaded {} Souls and Soul data for {} players",
                SoulRegistry.size(), instance.players.size());
        return instance;
    }

    public static void stop() {
        if (instance != null) {
            instance.saveNow();
            instance = null;
        }
    }

    /** May be null when no server is running (e.g. on the title screen in single player). */
    public static SoulManager get() {
        return instance;
    }

    public MinecraftServer server() {
        return this.server;
    }

    public SoulsConfig config() {
        return this.config;
    }

    /** Applies a freshly loaded config; used by {@code /souls reload}. */
    public void setConfig(SoulsConfig config) {
        this.config = config;
        SoulTeams.syncTeams(this.server, config);
        for (ServerPlayerEntity player : this.server.getPlayerManager().getPlayerList()) {
            soulOf(player).ifPresent(soul -> {
                SoulTeams.applyTeam(this.server, config, player, soul);
                refreshMaxHealth(player, soul, dataOf(player));
                refreshNameForEveryone(player);
            });
        }
    }

    // ------------------------------------------------------------------ data access

    public PlayerSoulData dataOf(ServerPlayerEntity player) {
        return dataOf(player.getUuid(), player.getNameForScoreboard());
    }

    public PlayerSoulData dataOf(UUID uuid, String name) {
        PlayerSoulData data = this.players.computeIfAbsent(uuid, key -> {
            markDirty();
            return new PlayerSoulData(key, name == null ? "" : name);
        });
        data.ensureCollections();
        if (name != null && !name.isEmpty() && !name.equals(data.last_known_name)) {
            data.last_known_name = name;
            markDirty();
        }
        return data;
    }

    /** Offline lookup - returns null when the player has never been seen. */
    public PlayerSoulData peek(UUID uuid) {
        PlayerSoulData data = this.players.get(uuid);
        if (data != null) {
            data.ensureCollections();
        }
        return data;
    }

    public Optional<Soul> soulOf(ServerPlayerEntity player) {
        return soulOf(dataOf(player));
    }

    public Optional<Soul> soulOf(PlayerSoulData data) {
        return data.hasSoul() ? SoulRegistry.get(data.soul_id) : Optional.empty();
    }

    public AbilityContext contextFor(ServerPlayerEntity player, Soul soul) {
        return new AbilityContext(this, player, soul, dataOf(player));
    }

    // ------------------------------------------------------------------ assignment

    /**
     * Picks a Soul using the configured chances. Souls that are disabled or have a chance
     * of 0 are never picked. Returns empty when every Soul is unavailable.
     */
    public Optional<Soul> rollSoul() {
        List<Soul> candidates = new ArrayList<>();
        double total = 0.0;
        for (Soul soul : SoulRegistry.all()) {
            if (!this.config.isEnabled(soul)) {
                continue;
            }
            double chance = this.config.chanceOf(soul);
            if (chance <= 0.0) {
                continue;
            }
            candidates.add(soul);
            total += chance;
        }
        if (candidates.isEmpty() || total <= 0.0) {
            return Optional.empty();
        }

        double roll = this.random.nextDouble() * total;
        for (Soul soul : candidates) {
            roll -= this.config.chanceOf(soul);
            if (roll <= 0.0) {
                return Optional.of(soul);
            }
        }
        return Optional.of(candidates.get(candidates.size() - 1));
    }

    /**
     * Gives a player a Soul, replacing any Soul they already had.
     *
     * @param dramatic show the full "YOUR SOUL IS" title, sound and particles
     */
    public void assign(ServerPlayerEntity player, Soul soul, boolean dramatic) {
        PlayerSoulData data = dataOf(player);

        // Let the old Soul clean up (attribute modifiers, lingering effects) before swapping.
        soulOf(data).ifPresent(previous -> {
            AbilityContext context = new AbilityContext(this, player, previous, data);
            for (SoulAbility ability : previous.abilities()) {
                ability.onRemove(context);
            }
        });

        data.soul_id = soul.id();
        data.assigned_at = System.currentTimeMillis();
        data.resetSoulState();
        markDirty();

        AbilityContext context = new AbilityContext(this, player, soul, data);
        for (SoulAbility ability : soul.abilities()) {
            ability.onAssign(context);
        }

        refreshMaxHealth(player, soul, data);
        player.setHealth(player.getMaxHealth());

        SoulTeams.applyTeam(this.server, this.config, player, soul);
        refreshNameForEveryone(player);

        if (dramatic) {
            SoulEffects.announceSoul(player, soul, this.config, ParticleTypes.TOTEM_OF_UNDYING);
            player.sendMessage(SoulText.prefix()
                    .append(Text.literal("Your soul is now "))
                    .append(SoulText.soulName(soul, this.config))
                    .append(Text.literal(".")), false);

            if (this.config.announce_assignment_to_server) {
                Text announcement = SoulText.prefix()
                        .append(Text.literal(player.getNameForScoreboard()).formatted(Formatting.WHITE))
                        .append(Text.literal(" awakened the "))
                        .append(SoulText.soulName(soul, this.config))
                        .append(Text.literal(" soul."));
                this.server.getPlayerManager().broadcast(announcement, false);
            }
        }
    }

    /** Removes a player's Soul so the next roll can give them a new one. */
    public void reset(ServerPlayerEntity player) {
        PlayerSoulData data = dataOf(player);
        soulOf(data).ifPresent(soul -> {
            AbilityContext context = new AbilityContext(this, player, soul, data);
            for (SoulAbility ability : soul.abilities()) {
                ability.onRemove(context);
            }
        });

        data.soul_id = null;
        data.resetSoulState();
        markDirty();

        clearMaxHealthModifier(player);
        SoulTeams.clearTeam(this.server, player);
        refreshNameForEveryone(player);
    }

    // ------------------------------------------------------------------ player events

    public void onJoin(ServerPlayerEntity player) {
        PlayerSoulData data = dataOf(player);

        if (!data.hasSoul() && this.config.assign_on_first_join) {
            Optional<Soul> rolled = rollSoul();
            if (rolled.isPresent()) {
                assign(player, rolled.get(), true);
                return;
            }
            SoulSouls.LOGGER.warn("No Soul could be rolled for {} - every Soul is disabled "
                    + "or has a chance of 0", player.getNameForScoreboard());
            return;
        }

        soulOf(data).ifPresent(soul -> {
            SoulTeams.applyTeam(this.server, this.config, player, soul);
            refreshMaxHealth(player, soul, data);
            refreshNameForEveryone(player);

            AbilityContext context = new AbilityContext(this, player, soul, data);
            for (SoulAbility ability : soul.abilities()) {
                ability.onJoin(context);
            }
        });
    }

    public void onLeave(ServerPlayerEntity player) {
        this.lastPositions.remove(player.getUuid());
        this.movedSinceLastTick.remove(player.getUuid());
        this.deathBypass.remove(player.getUuid());
        saveNow();
    }

    /** Fired after respawning and after a dimension change - the player entity is new both times. */
    public void onRespawn(ServerPlayerEntity player) {
        PlayerSoulData data = dataOf(player);
        soulOf(data).ifPresent(soul -> {
            SoulTeams.applyTeam(this.server, this.config, player, soul);
            refreshMaxHealth(player, soul, data);
            refreshNameForEveryone(player);

            AbilityContext context = new AbilityContext(this, player, soul, data);
            for (SoulAbility ability : soul.abilities()) {
                ability.onRespawn(context);
            }
        });
    }

    // ------------------------------------------------------------------ ticking

    public void tick() {
        this.tickCounter++;

        int interval = Math.max(1, this.config.ability_tick_interval);
        if (this.tickCounter % interval == 0) {
            for (ServerPlayerEntity player : this.server.getPlayerManager().getPlayerList()) {
                trackMovement(player);

                PlayerSoulData data = dataOf(player);
                Optional<Soul> soul = soulOf(data);
                if (soul.isEmpty()) {
                    continue;
                }

                AbilityContext context = new AbilityContext(this, player, soul.get(), data);
                refreshMaxHealth(player, soul.get(), data);

                for (SoulAbility ability : soul.get().abilities()) {
                    try {
                        ability.tick(context);
                    } catch (Exception exception) {
                        SoulSouls.LOGGER.error("Ability {} of soul {} failed while ticking {}",
                                ability.id(), soul.get().id(), player.getNameForScoreboard(), exception);
                    }
                }
            }
        }

        long now = System.currentTimeMillis();
        if (this.dirty && now - this.lastSaveMillis >= this.config.autosave_interval_seconds * 1000L) {
            saveNow();
        }
    }

    private void trackMovement(ServerPlayerEntity player) {
        Vec3d current = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d previous = this.lastPositions.put(player.getUuid(), current);
        if (previous == null) {
            this.movedSinceLastTick.put(player.getUuid(), 0.0);
            return;
        }
        double dx = current.x - previous.x;
        double dz = current.z - previous.z;
        this.movedSinceLastTick.put(player.getUuid(), Math.sqrt(dx * dx + dz * dz));
    }

    /** Horizontal distance the player covered during the last ability tick, in blocks. */
    public double distanceMovedLastTick(ServerPlayerEntity player) {
        return this.movedSinceLastTick.getOrDefault(player.getUuid(), 0.0);
    }

    // ------------------------------------------------------------------ event dispatch

    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        PlayerSoulData data = dataOf(player);
        Optional<Soul> soul = soulOf(data);
        if (soul.isEmpty()) {
            return true;
        }
        AbilityContext context = new AbilityContext(this, player, soul.get(), data);
        for (SoulAbility ability : soul.get().abilities()) {
            try {
                if (!ability.allowDamage(context, source, amount)) {
                    return false;
                }
            } catch (Exception exception) {
                SoulSouls.LOGGER.error("Ability {} failed in allowDamage", ability.id(), exception);
            }
        }
        return true;
    }

    public boolean allowDeath(ServerPlayerEntity player, DamageSource source, float amount) {
        if (this.deathBypass.contains(player.getUuid())) {
            return true;
        }
        PlayerSoulData data = dataOf(player);
        Optional<Soul> soul = soulOf(data);
        if (soul.isEmpty()) {
            return true;
        }
        AbilityContext context = new AbilityContext(this, player, soul.get(), data);
        for (SoulAbility ability : soul.get().abilities()) {
            try {
                if (!ability.allowDeath(context, source, amount)) {
                    return false;
                }
            } catch (Exception exception) {
                SoulSouls.LOGGER.error("Ability {} failed in allowDeath", ability.id(), exception);
            }
        }
        return true;
    }

    public void afterDamage(ServerPlayerEntity player, DamageSource source, float taken) {
        forEachAbility(player, (context, ability) -> ability.afterDamage(context, source, taken));
    }

    public void onAttack(ServerPlayerEntity player, Entity target) {
        forEachAbility(player, (context, ability) -> ability.onAttack(context, target));
    }

    public void onProjectileFired(ServerPlayerEntity player, PersistentProjectileEntity projectile) {
        forEachAbility(player, (context, ability) -> ability.onProjectileFired(context, projectile));
    }

    /**
     * Called when any living entity dies. Feeds the killer's {@code onKill} hook and, for
     * player victims, every online Soul holder's {@code onOtherPlayerDeath} hook.
     */
    public void onDeath(LivingEntity victim, DamageSource source) {
        Entity attacker = source.getAttacker();
        if (attacker instanceof ServerPlayerEntity killer) {
            forEachAbility(killer, (context, ability) -> ability.onKill(context, victim));
        }

        if (victim instanceof ServerPlayerEntity deadPlayer) {
            ServerPlayerEntity killer = attacker instanceof ServerPlayerEntity player ? player : null;
            for (ServerPlayerEntity observer : this.server.getPlayerManager().getPlayerList()) {
                if (observer == deadPlayer) {
                    continue;
                }
                forEachAbility(observer, (context, ability) ->
                        ability.onOtherPlayerDeath(context, deadPlayer, killer));
            }
        }
    }

    private interface AbilityAction {
        void run(AbilityContext context, SoulAbility ability);
    }

    private void forEachAbility(ServerPlayerEntity player, AbilityAction action) {
        PlayerSoulData data = dataOf(player);
        Optional<Soul> soul = soulOf(data);
        if (soul.isEmpty()) {
            return;
        }
        AbilityContext context = new AbilityContext(this, player, soul.get(), data);
        for (SoulAbility ability : soul.get().abilities()) {
            try {
                action.run(context, ability);
            } catch (Exception exception) {
                SoulSouls.LOGGER.error("Ability {} of soul {} failed",
                        ability.id(), soul.get().id(), exception);
            }
        }
    }

    /**
     * Lets an ability kill a player it previously saved, without its own {@code allowDeath}
     * hook catching the killing blow again.
     */
    public void killBypassingSouls(ServerPlayerEntity player, DamageSource source) {
        UUID uuid = player.getUuid();
        this.deathBypass.add(uuid);
        try {
            player.damage(player.getEntityWorld(), source, Float.MAX_VALUE);
        } finally {
            this.deathBypass.remove(uuid);
        }
    }

    // ------------------------------------------------------------------ max health

    /**
     * Keeps the player's max-health modifier in step with their Soul.
     *
     * <p>Called on join, respawn, assignment and on every ability tick. The periodic check
     * is what makes this survive dimension changes, {@code /kill}, other mods clearing
     * attributes, and anything else that rebuilds the player's attribute container.
     */
    public void refreshMaxHealth(ServerPlayerEntity player, Soul soul, PlayerSoulData data) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (instance == null) {
            return;
        }

        // Abilities such as Patience add a temporary bonus on top of the Soul's base value.
        double target = this.config.maxHealthOf(soul) + data.state(SoulSouls.BONUS_HEALTH_KEY, 0.0);
        target = Math.max(1.0, Math.min(1024.0, target));
        double delta = target - instance.getBaseValue();

        EntityAttributeModifier existing = instance.getModifier(MAX_HEALTH_MODIFIER_ID);
        if (existing != null && Math.abs(existing.value() - delta) < 0.0001) {
            return;
        }

        instance.removeModifier(MAX_HEALTH_MODIFIER_ID);
        if (Math.abs(delta) > 0.0001) {
            instance.addPersistentModifier(new EntityAttributeModifier(
                    MAX_HEALTH_MODIFIER_ID, delta, EntityAttributeModifier.Operation.ADD_VALUE));
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public void clearMaxHealthModifier(ServerPlayerEntity player) {
        EntityAttributeInstance instance = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (instance != null) {
            instance.removeModifier(MAX_HEALTH_MODIFIER_ID);
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    // ------------------------------------------------------------------ name colouring

    /** Re-sends this player's tab-list entry so a Soul change recolours their name immediately. */
    public void refreshNameForEveryone(ServerPlayerEntity player) {
        this.server.getPlayerManager().sendToAll(
                new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, player));
    }

    // ------------------------------------------------------------------ saving

    public void markDirty() {
        this.dirty = true;
    }

    public void saveNow() {
        this.storage.save(this.players);
        this.dirty = false;
        this.lastSaveMillis = System.currentTimeMillis();
    }

    public Map<UUID, PlayerSoulData> allData() {
        return this.players;
    }
}
