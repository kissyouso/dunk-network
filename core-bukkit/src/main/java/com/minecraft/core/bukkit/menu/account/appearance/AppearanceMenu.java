package com.minecraft.core.bukkit.menu.account.appearance;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.account.medal.MedalMenu;
import com.minecraft.core.bukkit.menu.account.skin.SkinMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class AppearanceMenu extends Menu {

    public AppearanceMenu(Player player, Menu last) {
        super(player, "Aparência", last, 4);
    }

    @Override
    public void handle() {
        clear();

        Account account = Core.getAccountController().of(getPlayer().getUniqueId());

        addItem(11, Item.of(Material.ITEM_FRAME, "§aSkin",
                        "§7Altere sua skin atual.",
                        "",
                        "§fSelecionada: §e" + SkinMenu.getSkinName(account),
                        "",
                        "§eClique para acessar!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new SkinMenu(getPlayer(), this).handle();
                }));

        addItem(15, Item.of(Material.DOUBLE_PLANT, "§aMedalhas",
                        "§7Visualize suas medalhas",
                        "§7e configure seus slots!",
                        "",
                        "§eClique para acessar!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new MedalMenu(getPlayer(), this).handle();
                }));

        if (isReturnable())
            addBackButton(31);

        display();
    }
}
