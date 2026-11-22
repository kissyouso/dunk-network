package com.minecraft.core.bukkit.menu.account.max;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.manager.list.TagManager;
import com.minecraft.core.bukkit.menu.account.AccountMenu;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

public class TagColorMenu extends Menu {

    public TagColorMenu(Player player, Menu last) {
        super(player, "Cor do +", last, 4);
    }

    @Override
    public void handle() {
        clear();

        Account account = Core.getAccountController().of(getPlayer().getUniqueId());
        TagColor current = TagColor.of(account.getTagColor());

        /* Previa na tag "+" do jogador, com o "+" na cor padrao (roxo) */
        String preview = account.getPlusTag().getPrefix();

        for (TagColor color : TagColor.values()) {
            boolean selected = color == current;

            Item icon = Item.of(Material.INK_SACK, color.getDye(), color.getColoredName(),
                            "§7Prévia:",
                            " " + preview.replace("§d+", color.getColor() + "+") + account.getName(),
                            "",
                            selected ? "§aSelecionada" : "§7Não selecionada",
                            "",
                            selected ? "§aJá está em uso." : "§eClique para selecionar!")
                    .flags(ItemFlag.values())
                    .click(event -> {
                        if (selected) {
                            sound(MenuSound.ERROR);
                            return;
                        }

                        if (!AccountMenu.isDunkPlus(account)) {
                            sound(MenuSound.ERROR);
                            account.send(MaxResourcesMenu.EXCLUSIVE_MESSAGE);
                            return;
                        }

                        account.setTagColor(color.getColor());
                        TagManager.updateTag(account);

                        sound(MenuSound.SUCCESS);
                        handle();
                    });

            if (selected)
                icon.enchantment(Enchantment.DURABILITY, 1);

            addItem(color.getSlot(), icon);
        }

        if (isReturnable())
            addBackButton(31);

        display();
    }
}
