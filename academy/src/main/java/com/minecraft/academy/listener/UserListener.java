package com.minecraft.academy.listener;

import com.minecraft.academy.Academy;
import com.minecraft.academy.arena.Arena;
import com.minecraft.academy.user.User;
import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.bukkit.user.UserModel;
import com.minecraft.core.member.list.pvp.PvPMember;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.util.UUID;

public class UserListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;

        UUID id = event.getUniqueId();

        /* Coins da Academy são as coins do PvP */
        PvPMember member = Core.getPvpData().of(id, true);

        if (member == null)
            member = Core.getPvpData().save(new PvPMember(id, event.getName()));

        if (member == null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Constant.ACCOUNT_LOAD_FAILED_MESSAGE);
            return;
        }

        Core.getMemberController().save(member);
        Core.getPvpData().cancelExpiration(id);

        new User(id);
    }

    @EventHandler
    public void onSpawn(PlayerSpawnLocationEvent event) {
        User user = User.fetch(event.getPlayer().getUniqueId());

        if (user != null)
            event.setSpawnLocation(resolve(user).getSpawn());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);

        Player player = event.getPlayer();
        User user = User.fetch(player.getUniqueId());

        if (user == null) {
            player.kickPlayer(Constant.ACCOUNT_LOAD_FAILED_MESSAGE);
            return;
        }

        Arena arena = resolve(user);
        user.setPendingArena(null);

        arena.join(user);
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
        User user = User.fetch(player.getUniqueId());

        if (user == null) return;

        if (user.getArena() != null)
            user.getArena().quit(user);

        Core.getPvpData().startExpiration(player.getUniqueId());
        Core.getMemberController().remove(player.getUniqueId(), PvPMember.class);

        UserModel.remove(player.getUniqueId());
    }

    /* Arena escolhida no lobby (salva na conta) ou a arena padrão */
    private Arena resolve(User user) {
        if (user.getPendingArena() == null) {
            Account account = user.getAccount();

            if (account != null && account.getAcademy().getRoute() != null) {
                user.setPendingArena(account.getAcademy().getRoute());

                account.getAcademy().setRoute(null);
                Core.getPlatform().runAsync(account::saveAcademy);
            }
        }

        Academy academy = Academy.getAcademy();
        Arena arena = user.getPendingArena() == null ? null : academy.getArenaStorage().getArena(user.getPendingArena());

        return arena != null && arena.isEnabled() ? arena : academy.getArenaStorage().getDefaultArena();
    }
}
