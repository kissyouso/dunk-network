package com.minecraft.core.bukkit.listener.server;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.account.context.objects.route.RouteContext;
import com.minecraft.core.arcade.route.ArcadeRouteContext;
import com.minecraft.core.arcade.route.join.Join;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.bukkit.api.vanish.Vanish;
import com.minecraft.core.bukkit.event.type.account.AccountVanishLogEvent;
import com.minecraft.core.bukkit.user.UserModel;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import static com.minecraft.core.bukkit.api.vanish.Vanish.hideAndShow;

public class VanishListener implements Listener {

    @EventHandler
    public void log(AccountVanishLogEvent event) {
        Account account = event.getAccount();

        boolean entered = event.isState(AccountVanishLogEvent.VanishState.JOIN);

        /* Em servidores arcade */
        if (Core.getServerType().isArcade()) {
            UserModel user = UserModel.of(account.getId());

            /* Alternar a entrada para VANISH e, ao sair, voltar para PLAYER.
             * Antes o LEAVE também gravava VANISH: o staff ficava marcado como
             * espectador para sempre e a próxima partida o tratava como morto. */
            if (user != null)
                user.setJoin(entered ? Join.VANISH : Join.PLAYER);
        }

        /* A rota é copiada para o próximo servidor (lobby/partida). Se o VANISH
         * continuar gravado nela, o jogador entra como espectador em GM1 e o
         * BedWars o marca como DEAD no início da partida. */
        if (!entered)
            clearRouteVanish(account);

        /* Atualizar a scoreboard do staff com a linha "MODO VANISH" */
        Player player = account.player();

        if (player != null) {
            Sidebar sidebar = BukkitCore.getManager().getSidebar().of(player);

            if (sidebar != null && sidebar.isShowing())
                sidebar.display();
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Account account = Core.getAccountController().of(player.getUniqueId());

        /* Ao trocar de servidor o modo de jogo é o que o servidor anterior deixou.
         * Vanish ativo mantém o criativo (GM1) para voar; sem vanish, staff volta
         * ao survival (GM0) para entrar na partida e morrer/renascer normalmente.
         * Quem está com o modo de construção ligado continua em criativo. */
        if (account != null) {
            if (Vanish.has(player)) {
                if (account.hasRank(RankType.HELPER))
                    player.setGameMode(GameMode.CREATIVE);
            } else if (account.isStaffer() && !account.getToggle().isAllowBuild()
                    && player.getGameMode() == GameMode.CREATIVE) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }

        Vanish.getList().forEach(vanish -> hideAndShow(vanish.getPlayer(), AccountVanishLogEvent.VanishState.JOIN));
    }

    @EventHandler
    public void leave(PlayerQuitEvent event) {
        Vanish.remove(event.getPlayer());
        clearVanishState(event.getPlayer());
    }

    @EventHandler
    public void kicked(PlayerKickEvent event) {
        Vanish.remove(event.getPlayer());
        clearVanishState(event.getPlayer());
    }

    /* Sair do servidor com vanish ativo não pode deixar o join/rota em VANISH. */
    private void clearVanishState(Player player) {
        Account account = Core.getAccountController().of(player.getUniqueId());

        if (account == null) return;

        UserModel user = UserModel.of(account.getId());

        if (user != null && user.isVanish())
            user.setJoin(Join.PLAYER);

        clearRouteVanish(account);
    }

    private void clearRouteVanish(Account account) {
        RouteContext route = account.getRoute();

        if (route == null) return;

        ArcadeRouteContext arcade = route.getArcade();

        if (arcade == null || !arcade.getJoin().equals(Join.VANISH)) return;

        arcade.setJoin(Join.PLAYER);

        account.setRoute(route);
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();

        if (Vanish.has(player))
            event.setCancelled(true);
    }

    @EventHandler
    public void vanishInteract(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof Player) {
            Player player = event.getPlayer(), clicked = (Player) event.getRightClicked();

            ItemStack hand = player.getItemInHand();

            if (Vanish.has(player)) {
                /* Abrir inventário */
                if (hand == null || hand.getType().equals(Material.AIR)) {
                    player.performCommand("invsee " + clicked.getName());
                    return;
                }
            }
        }
    }
}
