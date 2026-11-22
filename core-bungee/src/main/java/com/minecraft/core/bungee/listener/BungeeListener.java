package com.minecraft.core.bungee.listener;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.bungee.event.list.update.UpdateEvent;
import com.minecraft.core.server.Server;
import com.minecraft.core.server.access.NetworkAccess;
import com.minecraft.core.server.type.ServerType;
import com.minecraft.core.util.list.StringUtil;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BungeeListener implements Listener {

    /* Segunda linha do MOTD (sorteada a cada ping) */
    private final List<String> descriptions = Arrays.asList(
            "§6§lRANK §1§lBETA §6§lNA LOJA!",
            /* Desativado por enquanto: "§e§lNOVO MODO: §6ACADEMY", */
            "§e§lVENHA JOGAR!"
    );

    @EventHandler
    public void onUpdate(UpdateEvent event) {
        CompletableFuture.runAsync(() -> {
            Core.getServerData().updatePlayerCounter(ProxyServer.getInstance().getOnlineCount());
            Core.getAccountController().list().forEach(Account::checkInfos);
        });
    }

    @EventHandler
    public void motd(ProxyPingEvent event) {
        ServerPing ping = event.getResponse();

        String description = descriptions.get(Core.RANDOM.nextInt(descriptions.size()));

        Server server = Core.getServerData().local();

        if (server != null && server.getWhitelist().isEnabled())
            description = "§c§lESTAMOS EM MANUTENÇÃO!";

        NetworkAccess access = NetworkAccess.current();

        if (access.isMaintenance()) {
            description = access.getMaintenanceMotdColored();

            /* Versão diferente da do jogador: a lista mostra "Manutenção" em vermelho no lugar dos jogadores */
            ping.setVersion(new ServerPing.Protocol("§cManutenção", 1));
        }

        if (access.getSlots() > 0 && ping.getPlayers() != null)
            ping.getPlayers().setMax(access.getSlots());

        ping.setDescriptionComponent(new TextComponent(StringUtil.StringHelper.makeCenteredMotd("§e§l✦ §6§lREDE STYLE §e§l✦") + "\n" + StringUtil.StringHelper.makeCenteredMotd(description)));;

        event.setResponse(ping);
    }

    @EventHandler
    public void connect(ServerConnectEvent event) {
        ProxiedPlayer proxied = event.getPlayer();
        
        // Verificar whitelist do proxy ao entrar pela primeira vez
        if (event.getReason().equals(ServerConnectEvent.Reason.JOIN_PROXY)) {
            Server localServer = Core.getServerData().local();
            
            if (localServer != null && localServer.getWhitelist().isEnabled()) {
                Account account = Core.getAccountController().of(proxied.getUniqueId());
                
                if (account != null) {
                    String playerName = account.getNickname().toLowerCase();
                    
                    // Verificar se o jogador está na whitelist ou tem permissão de staff
                    if (!account.isStaffer() && !localServer.getWhitelist().hasPlayer(playerName) &&
                        !localServer.getWhitelist().hasRank(account.getRank().getType())) {
                        
                        event.setCancelled(true);
                        proxied.disconnect(TextComponent.fromLegacyText(
                            "§c§lWHITELIST ATIVA!\n\n" +
                            "§7O servidor está atualmente em manutenção.\n" +
                            "§7Apenas jogadores autorizados podem entrar.\n\n" +
                            "§7Servidor: §f" + localServer.getNameAndId()
                        ));
                        return;
                    }
                }
            }
        }
        
        // Servidores exclusivos (página "Acesso" do editor)
        if (event.getTarget() != null && !event.getReason().equals(ServerConnectEvent.Reason.JOIN_PROXY)) {
            Server targetServer = Core.getServerData().findByProxyServer(event.getTarget());
            Account account = Core.getAccountController().of(proxied.getUniqueId());

            if (targetServer != null && account != null) {
                NetworkAccess access = NetworkAccess.current();

                if (!access.canJoin(account, targetServer.getType())) {
                    event.setCancelled(true);

                    proxied.sendMessage(TextComponent.fromLegacyText("§cO servidor " + targetServer.getType().getName()
                            + " é exclusivo para " + access.getRequiredRank(targetServer.getType()).getColoredName() + "§c ou superior."));
                    return;
                }
            }
        }

        // Verificar whitelist do servidor de destino
        if (event.getTarget() != null) {
            Server targetServer = Core.getServerData().findByProxyServer(event.getTarget());
            
            if (targetServer != null && targetServer.getWhitelist().isEnabled()) {
                Account account = Core.getAccountController().of(proxied.getUniqueId());
                
                if (account != null) {
                    String playerName = account.getNickname().toLowerCase();
                    
                    // Verificar se o jogador está na whitelist ou tem permissão de staff
                    if (!account.isStaffer() && !targetServer.getWhitelist().hasPlayer(playerName) &&
                        !targetServer.getWhitelist().hasRank(account.getRank().getType())) {
                        
                        event.setCancelled(true);
                        
                        if (event.getReason().equals(ServerConnectEvent.Reason.JOIN_PROXY)) {
                            proxied.disconnect(TextComponent.fromLegacyText(
                                "§c§lWHITELIST ATIVA!\n\n" +
                                "§7O servidor está atualmente em manutenção.\n" +
                                "§7Apenas jogadores autorizados podem entrar."
                            ));
                        } else {
                            proxied.sendMessage(TextComponent.fromLegacyText(
                                "§cO servidor §e" + targetServer.getName() + "§c está em whitelist!"
                            ));
                        }
                        return;
                    }
                }
            }
        }
        
        if (event.getReason().equals(ServerConnectEvent.Reason.JOIN_PROXY)) {
            Account account = Core.getAccountController().of(proxied.getUniqueId());

            if (account == null) {
                event.setCancelled(true);

                proxied.disconnect(TextComponent.fromLegacyText(Constant.ACCOUNT_LOAD_FAILED_MESSAGE));
                return;
            }

            ServerType category = proxied.getPendingConnection().isOnlineMode() ? ServerType.HUB : ServerType.AUTH;

            /* Se o editor está em "auto", publica o link com o domínio usado pelo jogador */
            if (proxied.getPendingConnection().getVirtualHost() != null)
                com.minecraft.core.bungee.BungeeCore.getInstance().updateEditorUrl(
                        proxied.getPendingConnection().getVirtualHost().getHostString());

            Server server = Core.getServerData().of(category);

            if (server == null || server.getProxyServer() == null) {
                event.setCancelled(true);

                proxied.disconnect(TextComponent.fromLegacyText(String.format(Constant.SERVER_NOT_FOUND_MESSAGE, category.getName())));
                return;
            }

            event.setTarget(server.getProxyServer());
        }
    }
}
