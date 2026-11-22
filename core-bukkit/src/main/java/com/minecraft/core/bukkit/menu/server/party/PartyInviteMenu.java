package com.minecraft.core.bukkit.menu.server.party;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.api.party.request.PartyRequest;
import com.minecraft.core.bukkit.api.menu.Heads;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class PartyInviteMenu extends Menu {

    public PartyInviteMenu(Player player, Menu last) {
        super(player, "Party - Convites", last, 4, 7);
    }

    @Override
    public void handle() {
        clear();

        Account account = Core.getAccountController().of(getPlayer().getUniqueId());
        List<PartyRequest> requests = Core.getPartyData().listRequestsByReceiver(account.getId());

        if (requests.isEmpty()) {
            addItem(13, Item.of(Material.BARRIER, "§aNenhum convite recebido",
                    "§7Mostra convites pendentes."));
        } else {
            buildPageItems(requests, 27, 35, 10, (request, slot) -> {
                Account sender = Core.getAccountData().of(request.getSender());
                String name = sender != null ? sender.getNickname() : "...";

                addItem(slot, Heads.of(sender).name("§aParty de " + name)
                        .lore("§eClique para aceitar!")
                        .click(event -> {
                            sound(MenuSound.SUCCESS);
                            close();
                            getPlayer().chat("/party aceitar " + name);
                        }));
            });
        }

        if (isReturnable())
            addBackButton(31);

        display();
    }
}
