package com.minecraft.event.listener;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.bukkit.command.single.EventCommand;
import com.minecraft.core.bukkit.user.UserModel;
import com.minecraft.event.Events;
import com.minecraft.event.game.EventManager;
import com.minecraft.event.game.GameEvent;
import com.minecraft.event.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public class UserListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;

        new User(event.getUniqueId());
    }

    @EventHandler
    public void onSpawn(PlayerSpawnLocationEvent event) {
        event.setSpawnLocation(Events.getEvents().getLocations().getSpawn());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);

        Player player = event.getPlayer();

        if (User.fetch(player.getUniqueId()) == null) {
            player.kickPlayer(Constant.ACCOUNT_LOAD_FAILED_MESSAGE);
            return;
        }

        GameEvent.reset(player);

        EventManager manager = Events.getEvents().getEventManager();
        Account account = Core.getAccountController().of(player.getUniqueId());

        /* Veio pelo /evento entrar ou /evento assistir de outro servidor */
        boolean spectate = account != null && account.hasCooldown(EventCommand.SPECTATE_KEY);

        if (manager.getPhase() == EventManager.Phase.CALLING && !spectate)
            manager.join(player);
        else if (manager.isRunning() && manager.getPhase() != EventManager.Phase.ENDING)
            manager.spectate(player);
        else
            player.sendMessage(EventCommand.PREFIX + "Não há nenhum evento acontecendo agora. Aguarde a próxima chamada!");

        manager.updateSidebars();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
        exit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKick(PlayerKickEvent event) {
        exit(event.getPlayer());
    }

    private void exit(Player player) {
        Events.getEvents().getEventManager().leave(player, true);

        UserModel.remove(player.getUniqueId());
    }
}
