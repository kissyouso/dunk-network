package com.minecraft.core.bukkit.menu.server.party;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.api.party.Party;
import com.minecraft.core.api.party.type.PartyType;
import com.minecraft.core.bukkit.api.menu.Heads;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class PartyPublicMenu extends Menu {

    private final Account account;

    public PartyPublicMenu(Player player, Menu last) {
        super(player, "Party - Públicas", last, 4, 7);

        this.account = Core.getAccountController().of(player.getUniqueId());
    }

    @Override
    public void handle() {
        clear();

        List<Party> list = Core.getPartyData().list(party -> party.isType(PartyType.PUBLIC));

        if (list.isEmpty()) {
            addItem(13, Item.of(Material.BARRIER, "§aNão há parties públicas",
                    "§7Mostra parties públicas disponíveis."));
        } else {
            buildPageItems(list, 27, 35, 10, (party, slot) -> {
                Account author = party.getAuthor();

                addItem(slot, Heads.of(author)
                        .name("§aParty de " + (author != null ? author.getNickname() : "..."))
                        .lore("§7Membros: §f" + party.getMembersCount() + "/" + party.getSlots(),
                                "",
                                !party.isFull() ? "§eClique para entrar!" : "§cParty cheia.")
                        .click(event -> {
                            if (party.isFull()) {
                                sound(MenuSound.ERROR);
                                account.send("§cA party está cheia neste momento.");
                                return;
                            }

                            close();
                            sound(MenuSound.DONE);

                            party.add(account);
                            account.send("§aVocê entrou na party de " + (author != null ? author.getNickname() : "...") + ".");
                        }));
            });
        }

        if (isReturnable())
            addBackButton(31);

        display();
    }
}
