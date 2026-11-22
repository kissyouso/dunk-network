package com.minecraft.core.bukkit.menu.account.max;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.joinmessage.JoinMessage;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.account.AccountMenu;
import com.minecraft.core.bukkit.menu.account.joinmessage.JoinMessageMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class MaxResourcesMenu extends Menu {

    public static final String EXCLUSIVE_MESSAGE = "§cEste recurso é exclusivo do Style+.";

    public MaxResourcesMenu(Player player, Menu last) {
        super(player, "Recursos Style+", last, 4);
    }

    @Override
    public void handle() {
        clear();

        Account account = Core.getAccountController().of(getPlayer().getUniqueId());
        boolean max = AccountMenu.isDunkPlus(account);

        TagColor color = TagColor.of(account.getTagColor());

        /* Centralizado: Cor do + no 12 e Mensagens de entrada no 14 (Engenhocas desativadas por enquanto) */
        addItem(12, Item.of(Material.INK_SACK, color.getDye(), "§aCor do +",
                        "§7Altera a cor do §e+ §7da sua",
                        "§7tag com sinal (Prime+/Social+).",
                        "",
                        "§7Atual: " + color.getColoredName(),
                        "",
                        "§eClique para alterar!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new TagColorMenu(getPlayer(), this).handle();
                }));

        JoinMessage joinMessage = account.getSelectedJoinMessageEnum();

        addItem(14, Item.of(Material.BOOK_AND_QUILL, "§aMensagens de entrada",
                        "§7Escolha a mensagem enviada",
                        "§7quando você entrar no lobby.",
                        "",
                        "§7Atual: §f" + (joinMessage != null ? joinMessage.getName() : "Nenhuma"),
                        "",
                        max ? "§eClique para alterar!" : "§cExclusivo Style+.")
                .click(event -> {
                    if (!max) {
                        sound(MenuSound.ERROR);
                        account.send(EXCLUSIVE_MESSAGE);
                        return;
                    }

                    sound(MenuSound.PAGINATED);
                    new JoinMessageMenu(getPlayer(), this).handle();
                }));

        if (isReturnable())
            addBackButton(31);

        display();
    }
}
