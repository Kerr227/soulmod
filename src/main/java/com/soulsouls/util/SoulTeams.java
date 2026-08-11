package com.soulsouls.util;

import com.soulsouls.config.SoulsConfig;
import com.soulsouls.soul.Soul;
import com.soulsouls.soul.SoulRegistry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Colours player names using vanilla scoreboard teams.
 *
 * <p>This is the only fully server-side way to colour the name floating above a player's
 * head, and it colours the tab list and chat name at the same time. Teams only support the
 * 16 vanilla chat colours, so the Soul's RGB colour is snapped to the nearest one; the
 * exact RGB colour is still used everywhere the mod writes text itself (titles, messages,
 * and the tab list when the mixin is active).
 */
public final class SoulTeams {
    private static final String TEAM_PREFIX = "soul_";

    private SoulTeams() {
    }

    private static String teamName(Soul soul) {
        // Team names are limited in length, so keep the prefix short.
        String name = TEAM_PREFIX + soul.id();
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    /** Creates (or updates) the team for every registered Soul. Safe to call repeatedly. */
    public static void syncTeams(MinecraftServer server, SoulsConfig config) {
        if (!config.use_scoreboard_teams) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        for (Soul soul : SoulRegistry.all()) {
            String name = teamName(soul);
            Team team = scoreboard.getTeam(name);
            if (team == null) {
                team = scoreboard.addTeam(name);
            }
            team.setDisplayName(SoulText.soulName(soul, config));
            team.setColor(SoulText.nearestFormatting(config.colorOf(soul)));

            // A team prefix and suffix wrap the name everywhere vanilla draws it, which is
            // how the hearts end up either side of the name above the player's head.
            if (config.nameplate_hearts && config.nameplate_heart != null
                    && !config.nameplate_heart.isEmpty()) {
                int colour = config.colorOf(soul);
                team.setPrefix(SoulText.coloured(config.nameplate_heart + " ", colour));
                team.setSuffix(SoulText.coloured(" " + config.nameplate_heart, colour));
            } else {
                team.setPrefix(Text.empty());
                team.setSuffix(Text.empty());
            }

            // Souls are not a gameplay alliance - do not change how players interact.
            team.setFriendlyFireAllowed(true);
            team.setShowFriendlyInvisibles(false);
        }
    }

    public static void applyTeam(MinecraftServer server, SoulsConfig config, ServerPlayerEntity player, Soul soul) {
        if (!config.use_scoreboard_teams) {
            return;
        }
        Scoreboard scoreboard = server.getScoreboard();
        String name = teamName(soul);
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            syncTeams(server, config);
            team = scoreboard.getTeam(name);
            if (team == null) {
                return;
            }
        }
        scoreboard.addScoreHolderToTeam(player.getNameForScoreboard(), team);
    }

    /** Removes the player from whichever Soul team they are on, leaving other teams alone. */
    public static void clearTeam(MinecraftServer server, ServerPlayerEntity player) {
        Scoreboard scoreboard = server.getScoreboard();
        Team current = scoreboard.getScoreHolderTeam(player.getNameForScoreboard());
        if (current != null && current.getName().startsWith(TEAM_PREFIX)) {
            scoreboard.removeScoreHolderFromTeam(player.getNameForScoreboard(), current);
        }
    }
}
