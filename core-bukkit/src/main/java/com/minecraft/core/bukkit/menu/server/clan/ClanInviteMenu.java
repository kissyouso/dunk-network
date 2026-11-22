package com.minecraft.core.bukkit.menu.server.clan;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.clan.request.ClanRequest;
import com.minecraft.core.api.clan.Clan;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class ClanInviteMenu extends Menu {

    public ClanInviteMenu(Player player, Menu last) {
        super(player, "Clan - Convites", last, 5, 21);
    }

    @Override
    public void handle() {
        clear();

        Account account = Core.getAccountController().of(getPlayer().getUniqueId());

        List<ClanRequest> requests = Core.getClanData().getRequests().stream()
                .filter(request -> request.getReceiver().equals(account.getId()))
                .collect(Collectors.toList());

        if (requests.isEmpty()) {
            addItem(22, Item.of(Material.WEB, "§aSem clan",
                    "§7Mostra convites de clan recebidos."));
        } else {
            buildPageItems(requests, 10, (request, slot) -> {
                Clan clan = Core.getClanData().of(request.getId());
                Account author = Core.getAccountData().of(request.getAuthor());

                if (clan == null) return;

                String authorName = author != null ? author.getName() : "...";

                addItem(slot, Item.of(Material.PAINTING, "§a" + clan.getName(),
                                "§7Tag: §f" + clan.getTag(),
                                "§7Membros: §f" + clan.getMembers().size(),
                                "§7Convidado por: §f" + authorName,
                                "",
                                "§eClique para aceitar!")
                        .click(event -> {
                            sound(MenuSound.SUCCESS);
                            close();
                            getPlayer().chat("/clan aceitar " + authorName);
                        }));
            });
        }

        addBackButton(40);

        display();
    }
}
