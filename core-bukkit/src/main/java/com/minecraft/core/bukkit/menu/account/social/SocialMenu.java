package com.minecraft.core.bukkit.menu.account.social;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.account.friend.FriendMenu;
import com.minecraft.core.bukkit.menu.server.clan.ClanInviteMenu;
import com.minecraft.core.bukkit.menu.server.clan.ClanMenu;
import com.minecraft.core.bukkit.menu.server.party.PartyMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class SocialMenu extends Menu {

    public SocialMenu(Player player, Menu last) {
        super(player, "Social", last, 4);
    }

    @Override
    public void handle() {
        clear();

        Account account = Core.getAccountController().of(getPlayer().getUniqueId());

        addItem(11, Item.of(Material.CAKE, "§aParty",
                        "§7Entre em uma party para jogar",
                        "§7com seus amigos.",
                        "",
                        "§eClique para acessar!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new PartyMenu(getPlayer(), this).handle();
                }));

        addItem(13, Item.of(Material.PAINTING, "§aClan",
                        "§7Trave batalhas e faça aliados",
                        "§7para lutar junto.",
                        "",
                        "§eClique para acessar!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);

                    if (account.hasClan() && account.getClan() != null)
                        new ClanMenu(getPlayer(), account.getClan(), this).handle();
                    else
                        new ClanInviteMenu(getPlayer(), this).handle();
                }));

        addItem(15, Item.of(Material.COOKIE, "§aAmigos",
                        "§7Faça novos amigos e compartilhe",
                        "§7experiências.",
                        "",
                        "§eClique para acessar!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new FriendMenu(getPlayer(), this).handle();
                }));

        addBackButton(31);

        display();
    }
}
