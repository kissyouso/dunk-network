package com.minecraft.core.bukkit.menu.server.arcade.mode.duels;

import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.arcade.feature.ArcadeFeature;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.server.arcade.editor.ArcadeEditingMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class DuelsInventoryMenu extends Menu {

    public DuelsInventoryMenu(Player player) {
        super(player, "Selecionar inventario", 4);
    }

    @Override
    public void handle() {
        clear();

        for (DuelsMenu.Mode mode : DuelsMenu.Mode.values()) {
            if (mode.getInventorySlot() < 0) continue;

            ArcadeCategory arcade = mode.getArcade();
            Material icon = mode == DuelsMenu.Mode.SIMULATOR ? Material.POTION : mode.getIcon();

            addItem(mode.getInventorySlot(), Item.of(icon, mode.getData(), "§a" + mode.getName(),
                            "§7Modo: §f" + mode.getRoom(),
                            "§7Inventario: §fPadrao",
                            "",
                            "§eClique para editar.")
                    .click(event -> {
                        if (arcade == null || !arcade.hasFeature(ArcadeFeature.EDITABLE_MENU)) {
                            sound(MenuSound.ERROR);
                            getPlayer().sendMessage("§cO inventário deste modo não pode ser editado.");
                            return;
                        }

                        sound(MenuSound.PAGINATED);
                        new ArcadeEditingMenu(getPlayer(), arcade, this).handle();
                    }));
        }

        display();
    }
}
