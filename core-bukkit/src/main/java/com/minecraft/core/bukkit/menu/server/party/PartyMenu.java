package com.minecraft.core.bukkit.menu.server.party;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.api.party.Party;
import com.minecraft.core.bukkit.api.menu.Heads;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class PartyMenu extends Menu {

    private final Account account;

    public PartyMenu(Player player, Menu last) {
        super(player, "Party", last, 4);

        this.account = Core.getAccountController().of(player.getUniqueId());
    }

    @Override
    public void handle() {
        clear();

        Party party = account.hasParty() ? account.getParty() : null;

        if (party == null) {
            addItem(10, Item.of(Material.BARRIER, "§aVocê não está em uma party",
                    "§7Crie ou entre em uma party."));

            addItem(13, Item.of(Material.ANVIL, "§aCriar party",
                            "§7Cria uma nova party.")
                    .click(event -> {
                        sound(MenuSound.SUCCESS);
                        close();
                        getPlayer().chat("/party criar");
                    }));
        } else {
            Account author = party.getAuthor();

            addItem(10, Heads.of(author).name("§aParty de " + (author != null ? author.getNickname() : "..."))
                    .lore("§7Membros: §f" + party.getMembersCount() + "/" + party.getSlots(),
                            "§7Tipo: §f" + party.getType().name().toLowerCase()));

            addItem(13, Item.of(Material.BARRIER, party.isAuthor(account.getId()) ? "§cApagar party" : "§cSair da party",
                            party.isAuthor(account.getId()) ? "§7Apaga a sua party atual." : "§7Sai da sua party atual.")
                    .click(event -> {
                        sound(MenuSound.ERROR);
                        close();
                        getPlayer().chat(party.isAuthor(account.getId()) ? "/party apagar" : "/party sair");
                    }));
        }

        addItem(14, Item.of(Material.BOOK, "§aConvites recebidos",
                        "§7Mostra convites pendentes.")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new PartyInviteMenu(getPlayer(), this).handle();
                }));

        addItem(15, Item.of(Material.BOOKSHELF, "§aParties públicas",
                        "§7Mostra parties públicas.")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new PartyPublicMenu(getPlayer(), this).handle();
                }));

        if (isReturnable())
            addBackButton(31);

        display();
    }
}
