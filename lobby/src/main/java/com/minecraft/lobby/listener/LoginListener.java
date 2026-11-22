package com.minecraft.lobby.listener;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.reconnect.Reconnect;
import com.minecraft.core.arcade.route.ArcadeRouteContext;
import com.minecraft.lobby.Lobby;
import com.minecraft.lobby.user.User;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public class LoginListener implements Listener {

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!event.getLoginResult().equals(AsyncPlayerPreLoginEvent.Result.ALLOWED)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Constant.ACCOUNT_LOAD_FAILED_MESSAGE);
            return;
        }

        new User(event.getUniqueId());
    }

    @EventHandler
    public void spawn(PlayerSpawnLocationEvent event) {
        Location spawn = Lobby.getLoader().getArchitect().getLocation("spawn");
        event.setSpawnLocation(spawn != null ? spawn : event.getPlayer().getWorld().getSpawnLocation());
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        User user = (User) User.of(player.getUniqueId());

        if (user == null) {
            player.kickPlayer(Constant.ACCOUNT_LOAD_FAILED_MESSAGE);
            return;
        }

        user.handle();
        Reconnect reconnect = Core.getReconnectData().of(player.getUniqueId());

        if (reconnect != null) {
            ArcadeRouteContext route = reconnect.getRoute();

            player.sendMessage(new String[]{
                    "",
                    "§c§lVocê desconectou de uma partida de " + route.getArcade().getFullName(),
                    "§c§lem andamento. Use /reconectar para voltar a jogar.",
                    ""
            });

            player.playSound(player.getLocation(), Sound.CLICK, 1.0f, 2.0f);
        }

        Account account = user.getAccount();

        com.minecraft.core.server.type.ServerType server = Lobby.getLoader().getArchitect().getServer();

        String title = server == com.minecraft.core.server.type.ServerType.HUB
                ? com.minecraft.core.Constant.SERVER_NAME.toUpperCase()
                : server.getName().toUpperCase();

        /* Conta criada há menos de 1 minuto = primeira entrada */
        boolean firstJoin = System.currentTimeMillis() - account.getContext().getCreatedAt() < 60_000L;

        account.title("§6§l" + title, firstJoin ? "§eSeja bem-vindo(a)!" : "§eOlá de novo, " + account.getName());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        exit(event.getPlayer());
    }

    @EventHandler
    public void kick(PlayerKickEvent event) {
        exit(event.getPlayer());
    }

    protected void exit(Player player) {
        Lobby.getLoader().getArchitect().quit(player);
    }
}
