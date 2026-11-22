package com.minecraft.core.bukkit.menu.account.skin.library;

import com.minecraft.core.api.item.Item;
import com.minecraft.core.api.skin.library.SkinLibrary;
import com.minecraft.core.api.skin.objects.SkinCategory;
import com.minecraft.core.api.skin.objects.SkinGender;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Escolha entre skins de Menino e de Menina (usado na categoria Padrão).
 */
public class SkinGenderMenu extends Menu {

    private final SkinCategory category;

    public SkinGenderMenu(Player player, SkinCategory category, Menu last) {
        super(player, "Biblioteca - " + category.getName(), last, 3);

        this.category = category;
    }

    @Override
    public void handle() {
        clear();

        addGender(11, SkinGender.MAN, "§bMenino");
        addGender(15, SkinGender.FEMALE, "§dMenina");

        if (isReturnable())
            addBackButton(22);

        display();
    }

    private void addGender(int slot, SkinGender gender, String name) {
        List<SkinLibrary> skins = SkinLibrary.getLibrarySkins(category, gender);

        Item icon = Item.of(Material.SKULL_ITEM, 3, name, "§7Skins: §f" + skins.size(), "", "§eClique para ver!");

        if (!skins.isEmpty())
            icon.skullByBase64(skins.get(0).getSkin().getValue());

        addItem(slot, icon.click(event -> {
            if (skins.isEmpty()) {
                sound(MenuSound.ERROR);
                return;
            }

            sound(MenuSound.PAGINATED);
            new SkinLibraryMenu(getPlayer(), category, gender, this).handle();
        }));
    }
}
