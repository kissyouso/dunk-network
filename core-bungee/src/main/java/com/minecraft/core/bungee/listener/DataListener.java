package com.minecraft.core.bungee.listener;

import com.minecraft.core.api.punishment.Punishment;
import com.minecraft.core.api.punishment.objects.enums.PunishmentCategory;
import com.minecraft.core.api.reconnect.Reconnect;
import com.minecraft.core.arcade.route.ArcadeRouteContext;
import com.minecraft.core.bungee.BungeeCore;
import com.minecraft.core.bungee.event.list.account.AccountRefreshEvent;
import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.Rank;
import com.minecraft.core.account.context.objects.route.RouteContext;
import com.minecraft.core.api.mojang.MojangApi;
import com.minecraft.core.backend.data.list.user.AccountData;
import com.minecraft.core.backend.database.redis.message.types.account.AccountSkinResetMessage;
import com.minecraft.core.controller.list.AccountController;
import com.minecraft.core.server.Server;
import com.minecraft.core.server.access.NetworkAccess;
import com.minecraft.core.server.flag.ServerFlag;
import com.minecraft.core.server.payload.whitelist.ServerWhitelist;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DataListener implements Listener {

    private final BungeeCore bungee = BungeeCore.getInstance();

    private final AccountData data = Core.getAccountData();
    private final AccountController controller = Core.getAccountController();

    @EventHandler
    public void onAccountRefresh(AccountRefreshEvent event) {
        Account account = event.getAccount();

        if (Core.getPlatform().isOnlinePlayer(account.getId()))
            controller.save(account);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(PreLoginEvent event) {
        PendingConnection connection = event.getConnection();
        String userName = connection.getName();

        event.registerIntent(bungee);

        CompletableFuture.runAsync(() -> authenticate(connection, userName)).thenRun(() -> event.completeIntent(bungee));
    }

    /**
     * A prova de sessão é feita pelo BungeeCord e não tem retry: se o sessionserver estiver fora,
     * quem cai é o jogador. Nessa hora entramos em modo offline com o UUID real da conta, que
     * preserva a identidade sem expulsar ninguém.
     */
    private void authenticate(PendingConnection connection, String userName) {
        MojangApi.Premium premium = Core.MOJANG_API.premium(userName);

        boolean verified = premium.isPremium() && Core.MOJANG_API.isSessionServerOnline();

        if (premium.isPremium() && !verified)
            Core.getLogger().warning("Sessões da Mojang indisponíveis; " + userName + " entrou sem prova de sessão.");

        connection.setOnlineMode(verified);

        /* setUniqueId só vale com onlineMode desligado, então o UUID entra depois da flag. */
        if (!verified && premium.isPremium()) {
            connection.setUniqueId(premium.getId());
        } else if (premium.isPirate()) {
            connection.setUniqueId(UUID.nameUUIDFromBytes(("OfflinePlayer:" + userName.toUpperCase()).getBytes(StandardCharsets.UTF_8)));
        }
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        PendingConnection connection = event.getConnection();

        UUID id = connection.getUniqueId();

        String userName = connection.getName();
        String ip = connection.getAddress().getAddress().getHostAddress();

        event.registerIntent(bungee);

        CompletableFuture.runAsync(() -> loadAccountData(event, id, userName, ip)).thenRun(() -> event.completeIntent(bungee));
    }

    private void loadAccountData(LoginEvent event, UUID id, String userName, String ip) {
        try {
            Account account = data.of(id, true);
            if (account == null) account = createNewAccount(event, id, userName);

            if (account == null || !isAccountAllowedToJoin(account, event)) return;

            updateAccountDetails(account, userName, ip, event.getConnection().isOnlineMode());
            controller.save(account);

        } catch (Exception e) {
            controller.remove(id);
            Core.getLogger().log(Level.WARNING, "Erro ao carregar a conta de " + userName, e);
        }
    }

    private Account createNewAccount(LoginEvent event, UUID id, String userName) {
        Account account = data.save(new Account(id, userName));
        if (account == null) {
            Core.getLogger().warning("Conta não encontrada para " + userName);
            event.setCancelReason(TextComponent.fromLegacyText(Constant.ACCOUNT_LOAD_FAILED_MESSAGE));
            event.setCancelled(true);
        }

        return account;
    }

    private boolean isAccountAllowedToJoin(Account account, LoginEvent event) {
        // Verificar punição apenas por UUID do jogador, não por IP
        // Isso evita que o autor de uma punição seja bloqueado por punições que ele mesmo aplicou
        Punishment punishment = Core.getPunishmentData().of(account.getId(), PunishmentCategory.BAN);

        if (punishment != null) {
            data.removeCache(account);

            event.setCancelReason(TextComponent.fromLegacyText(String.format(Constant.BAN_TEMPLATE_MESSAGE,
                    punishment.isTemporary() ? "temporariamente" : "permanentemente",
                    punishment.getReason().getName(),
                    punishment.getId())));

            event.setCancelled(true);
            return false;
        }

        /* Manutenção e slots (página "Acesso" do editor) */
        NetworkAccess access = NetworkAccess.current();

        if (access.isMaintenance() && !access.canBypassMaintenance(account)) {
            data.removeCache(account);

            event.setCancelReason(TextComponent.fromLegacyText(access.getMaintenanceMessageColored()));
            event.setCancelled(true);
            return false;
        }

        if (access.getSlots() > 0 && !access.canBypassSlots(account) && Core.getServerData().getOnlinePlayers() >= access.getSlots()) {
            data.removeCache(account);

            event.setCancelReason(TextComponent.fromLegacyText(access.getFullMessageColored()));
            event.setCancelled(true);
            return false;
        }

        Server local = Core.getLocalServer();

        if (local != null) {
            ServerWhitelist whitelist = local.getWhitelist();

            if (whitelist.isEnabled() && !whitelist.hasPlayer(account.getName()) && !account.isStaffer()) {
                data.removeCache(account);

                event.setCancelReason(TextComponent.fromLegacyText(Constant.SERVER_TITLE + "\n\n" +
                        "§cServidores indisponíveis no momento.\n" +
                        "§cTente novamente mais tarde!\n\n" +
                        "§cDúvidas? Consulte: §e" + Constant.SERVER_DISCORD));

                event.setCancelled(true);
                return false;
            }
        }

        return true;
    }

    private void updateAccountDetails(Account account, String userName, String ip, boolean isOnlineMode) {
        if (!account.isPremium() && isOnlineMode)
            account.setPremium(true);

        /* Rank removido do enum (ex.: YEAR_2026) chega aqui como tipo nulo;
         * repara para o maior rank válido disponível antes de seguir o login. */
        if (!account.getRank().hasValidType())
            account.repairRank();

        if (account.getIpAddress() == null || !account.getIpAddress().equalsIgnoreCase(ip))
            account.setIpAddress(ip);

        if (!account.getName().equalsIgnoreCase(userName))
            account.setName(userName);

        data.cancelExpiration(account);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPostLogin(PostLoginEvent event) {
        ProxiedPlayer proxied = event.getPlayer();

        CompletableFuture.runAsync(() -> postLoginAccountSetup(proxied));
    }

    private void postLoginAccountSetup(ProxiedPlayer proxied) {
        Account account = controller.of(proxied.getUniqueId());

        if (account == null) {
            proxied.disconnect(TextComponent.fromLegacyText(Constant.ACCOUNT_LOAD_FAILED_MESSAGE));
            return;
        }

        account.setOnline(true);
        account.notifyFriends();

        Core.getLogger().info(account.getName() + " entrou no servidor.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer proxied = event.getPlayer();
        CompletableFuture.runAsync(() -> handlePlayerDisconnect(proxied));
    }

    private void handlePlayerDisconnect(ProxiedPlayer proxied) {
        Account account = controller.of(proxied.getUniqueId());

        if (account == null) return;

        account.setOnline(false);

        if (account.getRoute().isValidArcade()) {
            ArcadeRouteContext route = account.getArcadeRoute();

            if (route.isValid() && route.getArcade().getServer().hasFlag(ServerFlag.RECONNECT)) {
                Core.getReconnectData().save(new Reconnect(proxied.getUniqueId(), route));
            }
        }

        if (account.isUsingFake()) {
            account.setTag(account.getDefaultTag());
            account.resetFake();
            new AccountSkinResetMessage(account).send();
        }

        account.setRoute(RouteContext.bungee());

        account.updateLastLogin();
        account.notifyFriends("§b" + account.getNickname() + "§e desconectou.");

        Core.getAccountData().startExpiration(account);
    }
}
