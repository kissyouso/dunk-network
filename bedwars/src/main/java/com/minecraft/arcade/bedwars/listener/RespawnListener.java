package com.minecraft.arcade.bedwars.listener;

import com.minecraft.arcade.bedwars.arcade.Arcade;
import com.minecraft.arcade.bedwars.arcade.arena.Arena;
import com.minecraft.arcade.bedwars.user.User;
import com.minecraft.core.Core;
import com.minecraft.core.arcade.route.state.ArcadeState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Rede de segurança das mortes: se um golpe escapar dos cancelamentos de dano e a
 * morte for pelo motor do jogo, o jogador entra no cooldown de respawn (5s) e o kit
 * é reaplicado depois que o cliente reconstrói o inventário.
 */
public class RespawnListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        User user = (User) User.of(player.getUniqueId());

        if (user == null || user.getArena() == null) return;

        Arena arena = user.getArena();
        Arcade arcade = arena.getArcade();

        event.getDrops().clear();
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDeathMessage(null);

        Core.getPlatform().runSync(() -> {
            if (!player.isOnline()) return;

            player.spigot().respawn();

            /* Eliminado fica espectador; morte comum espera o cooldown de 5s */
            if (user.inState(ArcadeState.DEAD))
                arcade.load(user);
            else
                arena.startRespawn(user);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        User user = (User) User.of(event.getPlayer().getUniqueId());

        if (user == null || user.getArena() == null || user.inState(ArcadeState.DEAD)) return;

        if (user.getTeam() != null && user.getTeam().getBase() != null)
            event.setRespawnLocation(user.getTeam().getBase());

        Core.getPlatform().runSync(() -> user.getArena().getArcade().handleHotbar(user), 4L);
    }
}
