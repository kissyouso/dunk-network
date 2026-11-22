package com.minecraft.core.bukkit.menu.account.stats;

import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.stats.BedWarsStatsMenu;
import com.minecraft.core.server.type.ServerType;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class StatsMenu extends Menu {

    private final Account target;

    public StatsMenu(Player player, Account target, Menu last) {
        super(player, player.getUniqueId().equals(target.getId()) ? "Suas estatísticas" : "Estatísticas de " + target.getName(), last, 3);

        this.target = target;
    }

    @Override
    public void handle() {
        clear();

        addItem(11, Item.of(Material.BED, "§eBedWars", "§7Clique para ver mais")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new BedWarsStatsMenu(getPlayer(), target, this).handle();
                }));

        /* O Academy mantém os atributos da espada visíveis, como no original */
        addItem(13, Item.of(Material.IRON_SWORD, "§eAcademy", "§7Clique para ver mais")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new com.minecraft.core.bukkit.menu.server.academy.AcademyStatsMenu(getPlayer(), target, this).handle();
                }));

        addItem(15, Item.of(Material.MUSHROOM_SOUP, "§eHunger Games", "§7Clique para ver mais")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new StatsInfoMenu(getPlayer(), target, ServerType.HUNGERGAMES, this).handle();
                }));

        if (isReturnable())
            addBackButton(22);
        else
            addCloseButton(22);

        display();
    }
}
