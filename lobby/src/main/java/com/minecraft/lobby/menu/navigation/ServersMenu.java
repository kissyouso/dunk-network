package com.minecraft.lobby.menu.navigation;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.server.arcade.custom.CustomArcadeMenu;
import com.minecraft.core.server.type.ServerType;
import com.minecraft.core.util.Util;
import com.minecraft.lobby.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ServersMenu extends Menu {

    private final User user;
    private final NavigationType type;

    public ServersMenu(Player player, NavigationType type) {
        super(player, type.equals(NavigationType.SERVER) ? "Selecione um modo" : "Selecionar Sala", 3, 8);

        this.user = (User) User.of(getPlayer().getUniqueId());
        this.type = type;

        setShowTitlePage(false);
    }

    @Override
    public void handle() {
        clear();

        switch (type) {
            case SERVER: {
                buildMode(10, Material.BED, "Bed Wars", ServerType.BEDWARS);
                buildMode(11, Material.DIAMOND_SWORD, "Duels", ServerType.DUELS);
                buildMode(12, Material.WATCH, "Academy", ServerType.HUB_PVP);

                addItem(16, Item.of(Material.NAME_TAG, "§eSala customizada",
                                "§7Crie sua propria sala",
                                "§7para jogar com amigos.",
                                "",
                                "§7Exclusivo para §1BETA§7!",
                                "",
                                "§aClique para ver mais!")
                        .click(event -> {
                            sound(MenuSound.PAGINATED);
                            new CustomRoomListMenu(getPlayer(), this).handle();
                        }));
                break;
            }

            case ROOM: {
                addItem(10, Item.of(Material.WOOL, 14, "§cLobby #1",
                                "§7Jogadores: §a" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers(),
                                "",
                                "§cSala atual.")
                        .click(event -> {
                            close();
                            sound(MenuSound.ERROR);
                        }));
                break;
            }
        }

        display();
    }

    protected void buildMode(int slot, Material icon, String name, ServerType server) {
        ServerType serverLobby = server.isLobby() ? server : server.getServerLobby();
        ServerType realServer = server.isLobby() ? server.getRealServerOfLobby() : server;

        int online = 0;

        if (serverLobby != null)
            online += Core.getServerData().getOnlinePlayers(serverLobby);

        if (realServer != null && !realServer.equals(serverLobby))
            online += Core.getServerData().getOnlinePlayers(realServer);

        addItem(slot, Item.of(icon, "§a" + name, "§7" + Util.formatNumber(online) + " jogando.")
                .flags(ItemFlag.values())
                .click(event -> {
                    close();
                    sound(MenuSound.DONE);

                    user.getAccount().redirect(serverLobby != null ? serverLobby : server);
                }));
    }

    /* Salas customizadas públicas */
    public static class CustomRoomListMenu extends Menu {

        public CustomRoomListMenu(Player player, Menu last) {
            super(player, "Salas customizadas públicas", last, 5);
        }

        @Override
        public void handle() {
            clear();

            addItem(13, Item.of(Material.BARRIER, "§cNenhuma sala pública disponivel",
                    "§7Tente novamente em instantes."));

            addBackButton(31);

            display();
        }
    }

    protected void buildComponent(int slot, ServerType server, String... lore) {
        Item item = Item.of(Material.getMaterial(server.getIconId()), "§a" + server.getName())
                .flags(ItemFlag.values());

        ServerType serverLobby = server.getServerLobby();

        int online = Core.getServerData().getOnlinePlayers(server) + (serverLobby != null ? Core.getServerData().getOnlinePlayers(serverLobby) : 0);

        List<String> itemLore = new ArrayList<>(Arrays.asList(
                "§8" + server.getStyle().getName(),
                ""));

        itemLore.addAll(Arrays.asList(lore));

        itemLore.addAll(Arrays.asList(
                "",
                "§a" + Constant.ARROW_ALT_SYMBOL + " Clique para jogar!",
                "§7" + Util.formatNumber(online) + " jogando."
        ));

        // Finalizando configuração de Item
        item.lore(itemLore)
                .updater(view -> {
                    if (!view.getTitle().equalsIgnoreCase(getTitle())) return;

                    // Atualizando última e penúltima linha
                    boolean blink = (System.currentTimeMillis() / 1000) % 2 == 0;

                    int onlinePlayers = Core.getServerData().getOnlinePlayers(server) + (serverLobby != null ? Core.getServerData().getOnlinePlayers(serverLobby) : 0);

                    itemLore.set(itemLore.size() - 2, "§a" + (blink ? Constant.ARROW_ALT_SYMBOL : "") + " Clique para jogar!");
                    itemLore.set(itemLore.size() - 1, "§7" + Util.formatNumber(onlinePlayers) + " jogando.");

                    item.lore(itemLore);

                    view.setItem(slot, item);

                    getPlayer().updateInventory();
                });

        item.click(event -> {
            close();
            sound(MenuSound.DONE);

            user.getAccount().redirect(serverLobby != null ? serverLobby : server);
        });

        addItem(slot, item);
    }

    public enum NavigationType {
        SERVER, ROOM
    }
}
