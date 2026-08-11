package com.soulsouls.mixin;

import com.soulsouls.data.SoulManager;
import com.soulsouls.soul.Soul;
import com.soulsouls.util.SoulText;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Paints the tab-list name in the Soul's exact RGB colour.
 *
 * <p>Scoreboard teams already colour the name above the head, the tab list and chat, but
 * teams are limited to the 16 vanilla colours. This gives the tab list the true colour -
 * a pastel green Soul looks pastel green rather than "closest match: green".
 *
 * <p>{@code require = 0} is intentional. If a future Minecraft version renames this method,
 * the injection is skipped instead of crashing the game, and name colouring quietly falls
 * back to the scoreboard teams.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(method = "getPlayerListName", at = @At("RETURN"), cancellable = true, require = 0)
    private void soulsouls$colourTabListName(CallbackInfoReturnable<Text> callback) {
        SoulManager manager = SoulManager.get();
        if (manager == null || !manager.config().rgb_tab_list_names) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        Optional<Soul> soul = manager.soulOf(player);
        if (soul.isEmpty()) {
            return;
        }

        Text existing = callback.getReturnValue();
        String name = existing != null ? existing.getString() : player.getNameForScoreboard();
        callback.setReturnValue(SoulText.coloured(name, manager.config().colorOf(soul.get())));
    }
}
