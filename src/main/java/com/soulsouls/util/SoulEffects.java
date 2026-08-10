package com.soulsouls.util;

import com.soulsouls.config.SoulsConfig;
import com.soulsouls.soul.Soul;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Titles, sounds and particles. Everything here is sent as a packet to a specific player,
 * so it works on a dedicated server with vanilla clients.
 */
public final class SoulEffects {
    private SoulEffects() {
    }

    // ------------------------------------------------------------------ titles

    public static void title(ServerPlayerEntity player, Text title, Text subtitle,
                             int fadeIn, int stay, int fadeOut) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
        if (subtitle != null) {
            player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        }
        player.networkHandler.sendPacket(new TitleS2CPacket(title));
    }

    // ------------------------------------------------------------------ sound

    public static void sound(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
        sound(player, RegistryEntry.of(sound), volume, pitch);
    }

    public static void sound(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, float volume, float pitch) {
        player.networkHandler.sendPacket(new PlaySoundS2CPacket(
                sound, SoundCategory.MASTER,
                player.getX(), player.getY(), player.getZ(),
                volume, pitch, player.getEntityWorld().getRandom().nextLong()));
    }

    /** Plays a sound for everyone nearby (used for the more dramatic Soul moments). */
    public static void soundAround(ServerWorld world, double x, double y, double z,
                                   SoundEvent sound, float volume, float pitch) {
        world.playSound(null, x, y, z, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    /**
     * Resolves a sound id from the config, e.g. {@code minecraft:entity.ender_dragon.growl}.
     * Falls back to the Ender Dragon growl if the id is unknown, so a typo in the config
     * cannot break Soul assignment.
     */
    public static SoundEvent soundFromId(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier != null) {
            SoundEvent found = Registries.SOUND_EVENT.get(identifier);
            if (found != null) {
                return found;
            }
        }
        return SoundEvents.ENTITY_ENDER_DRAGON_GROWL;
    }

    // ------------------------------------------------------------------ particles

    public static void particles(ServerWorld world, ParticleEffect particle, double x, double y, double z,
                                 int count, double spreadX, double spreadY, double spreadZ, double speed) {
        world.spawnParticles(particle, x, y, z, count, spreadX, spreadY, spreadZ, speed);
    }

    /** A ring of particles around a player - the generic "an ability fired" tell. */
    public static void burst(ServerWorld world, ServerPlayerEntity player, ParticleEffect particle, int count) {
        world.spawnParticles(particle,
                player.getX(), player.getY() + 1.0, player.getZ(),
                count, 0.5, 0.8, 0.5, 0.05);
    }

    // ------------------------------------------------------------------ the assignment moment

    /**
     * The dramatic "YOUR SOUL IS ..." reveal: a title in the Soul's colour, a deep sound
     * and a burst of particles.
     */
    public static void announceSoul(ServerPlayerEntity player, Soul soul, SoulsConfig config,
                                    ParticleEffect particle) {
        SoulEffects.title(player,
                Text.literal("YOUR SOUL IS").formatted(net.minecraft.util.Formatting.WHITE),
                SoulText.coloured("\"" + soul.displayName() + "\"", config.colorOf(soul)),
                10, 70, 20);

        sound(player, soundFromId(config.assignment_sound), 1.0F, 1.0F);

        ServerWorld world = player.getEntityWorld();
        burst(world, player, particle, 60);
    }
}
