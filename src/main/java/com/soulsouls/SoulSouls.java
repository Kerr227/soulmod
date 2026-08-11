package com.soulsouls;

import com.soulsouls.command.JusticeCommand;
import com.soulsouls.command.MemoryCommand;
import com.soulsouls.command.SoulsCommand;
import com.soulsouls.config.SoulsConfig;
import com.soulsouls.data.SoulManager;
import com.soulsouls.souls.Souls;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Soul Souls - an Undertale-inspired Soul system for Minecraft.
 *
 * <p>This class does one thing: it wires Fabric's events to {@link SoulManager}, which owns
 * all the actual behaviour. Every event handler here is deliberately tiny, because the
 * interesting logic belongs to the individual {@code SoulAbility} implementations.
 */
public class SoulSouls implements ModInitializer {
    public static final String MOD_ID = "soulsouls";
    public static final Logger LOGGER = LoggerFactory.getLogger("Soul Souls");

    /**
     * Shared ability-state key for "extra max health on top of this Soul's base value".
     * Souls that change max health over time (Patience) write it here and
     * {@link SoulManager#refreshMaxHealth} folds it into the attribute modifier.
     */
    public static final String BONUS_HEALTH_KEY = "bonus_health";

    @Override
    public void onInitialize() {
        // Souls must exist before the config is read: the config file is generated from
        // whatever is registered.
        Souls.registerAll();

        registerLifecycleEvents();
        registerPlayerEvents();
        registerCombatEvents();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            SoulsCommand.register(dispatcher);
            MemoryCommand.register(dispatcher);
            JusticeCommand.register(dispatcher);
        });

        LOGGER.info("Soul Souls ready");
    }

    private void registerLifecycleEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SoulsConfig config = SoulsConfig.load();
            SoulManager.start(server, config);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SoulManager.stop());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            SoulManager manager = SoulManager.get();
            if (manager != null) {
                manager.tick();
            }
        });
    }

    private void registerPlayerEvents() {
        ServerPlayerEvents.JOIN.register(player -> {
            SoulManager manager = SoulManager.get();
            if (manager != null) {
                manager.onJoin(player);
            }
        });

        ServerPlayerEvents.LEAVE.register(player -> {
            SoulManager manager = SoulManager.get();
            if (manager != null) {
                manager.onLeave(player);
            }
        });

        // Fires after a death respawn and after a dimension change - in both cases the
        // player entity is a new object that needs its Soul re-applied.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            SoulManager manager = SoulManager.get();
            if (manager != null) {
                manager.onRespawn(newPlayer);
            }
        });
    }

    private void registerCombatEvents() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            SoulManager manager = SoulManager.get();
            if (manager == null || !(entity instanceof ServerPlayerEntity player)) {
                return true;
            }
            return manager.allowDamage(player, source, amount);
        });

        ServerPlayerEvents.ALLOW_DEATH.register((player, source, amount) -> {
            SoulManager manager = SoulManager.get();
            return manager == null || manager.allowDeath(player, source, amount);
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseTaken, taken, blocked) -> {
            SoulManager manager = SoulManager.get();
            if (manager == null) {
                return;
            }
            if (entity instanceof ServerPlayerEntity player) {
                manager.afterDamage(player, source, taken);
            }
            // An arrow connecting is the only reliable signal that a shot did not miss.
            if (source.getSource() instanceof PersistentProjectileEntity projectile
                    && projectile.getOwner() instanceof ServerPlayerEntity shooter
                    && shooter != entity) {
                manager.onProjectileHit(shooter, entity);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            SoulManager manager = SoulManager.get();
            if (manager != null) {
                manager.onDeath(entity, source);
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            SoulManager manager = SoulManager.get();
            if (manager != null && player instanceof ServerPlayerEntity serverPlayer) {
                manager.onAttack(serverPlayer, target);
            }
            // Never consume the interaction: this is an observer, not a replacement for
            // vanilla attacking.
            return ActionResult.PASS;
        });

        // Arrows, crossbow bolts and tridents are ordinary entities, so the cleanest place
        // to buff a Soul's shots is the moment the projectile enters the world.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            SoulManager manager = SoulManager.get();
            if (manager == null || !(entity instanceof PersistentProjectileEntity projectile)) {
                return;
            }
            if (projectile.getOwner() instanceof ServerPlayerEntity owner) {
                manager.onProjectileFired(owner, projectile);
            }
        });
    }
}
