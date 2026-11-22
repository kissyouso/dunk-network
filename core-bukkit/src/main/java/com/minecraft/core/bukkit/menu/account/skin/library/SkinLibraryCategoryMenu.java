package com.minecraft.core.bukkit.menu.account.skin.library;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.api.skin.Skin;
import com.minecraft.core.api.skin.objects.SkinCategory;
import com.minecraft.core.api.skin.library.SkinLibrary;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.account.skin.AnvilSkinMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SkinLibraryCategoryMenu extends Menu {

    public SkinLibraryCategoryMenu(Player player, Menu last) {
        super(player, "Biblioteca de skins", last, 5);
    }

    @Override
    public void handle() {
        clear();
        SkinCategory[] categories = SkinCategory.values();
        
        List<SkinCategory> validCategories = new ArrayList<>();
        for (SkinCategory cat : categories) {
            if (cat != SkinCategory.NONE && !SkinLibrary.getSkins(cat).isEmpty()) {
                validCategories.add(cat);
            }
        }
        
        if (validCategories.isEmpty()) {
            addItem(22, Item.of(Material.WEB, "§cNão há categorias disponíveis..."));
            if (isReturnable())
                addBackButton(getTotalSlots() - 5);
            display();
            return;
        }
        
        /* Até 5 por linha, com um espaço entre cada categoria, e cada linha centralizada */
        for (int index = 0; index < validCategories.size() && index < 10; index++) {
            int row = index / 5,
                    inRow = Math.min(5, validCategories.size() - row * 5),
                    column = (9 - (inRow * 2 - 1)) / 2 + (index % 5) * 2;

            buildCategory(9 + row * 18 + column, validCategories.get(index));
        }

        if (isReturnable())
            addBackButton(getTotalSlots() - 6);

        display();
    }

    protected void buildCategory(int slot, SkinCategory category) {
        List<Skin> skins = SkinLibrary.getSkins(category);
        Account account = Core.getAccountController().of(getPlayer().getUniqueId());

        if (skins.isEmpty()) {
            return;
        }
        
        Skin first = skins.get(0);

        addItem(slot, Item.of(Material.SKULL_ITEM, 3, "§a" + category.getName(),
                "§7Skins: §f" + skins.size(),
                "",
                "§eClique para ver!")
                .skullByBase64(first.getValue())
                .click(event -> {
                    sound(MenuSound.PAGINATED);

                    /* Padrão: primeiro escolhe entre Menino e Menina */
                    if (category == SkinCategory.PADRAO)
                        new SkinGenderMenu(getPlayer(), category, this).handle();
                    else
                        new SkinLibraryMenu(getPlayer(), category, this).handle();
                }));
        addItem(getTotalSlots() - 5, Item.of(Material.BARRIER, 3, "§cResetar skin")
                .click(event -> {
                    close();
                    sound(MenuSound.SUCCESS);

                    getPlayer().performCommand("skin #");
                }));
        addItem(getTotalSlots() - 4, Item.of(Material.ANVIL, "§aModificar Skin",
                        "§7Customize a sua skin",
                        "§7do seu jeito.",
                        "",
                        account.isVIP() ? "§eClique para escolher!" : "§cSem permissão.")
                .click(event -> {
                    close();

                    if (!account.isVIP()) {
                        sound(MenuSound.ERROR);

                        account.send("§cVocê não pode customizar sua skin!", "§cAdquira o rank " + RankType.VIP.getColoredName() + "§c em: §e" + Constant.SERVER_STORE);
                        return;
                    }

                    sound(MenuSound.SUCCESS);
                    new AnvilSkinMenu(getPlayer()).open();
                }));
    }
}
