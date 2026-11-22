package com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.cosmetic.BedCosmeticsMenu;
import com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.level.LevelListMenu;
import com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.preference.PreferenceMenu;
import com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.quick.QuickBuyMenu;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.server.type.ServerType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

public class BedWarsMenu extends Menu {

    private final BedMember member;

    public BedWarsMenu(Player player) {
        super(player, "Menu do BedWars", 4);

        Account account = Core.getAccountController().of(player.getUniqueId());
        BedMember loaded = Core.getBedWarsData().of(player.getUniqueId(), true);

        this.member = loaded != null ? loaded : (BedMember) account.loadMember(ServerType.BEDWARS);
    }

    @Override
    public void handle() {
        clear();

        /* Se o membro for nulo, não carregar os dados */
        if (member == null) {
            addErrorButton("§cNão foi possível carregar os seus dados...");
            display();
            return;
        }

        /* Centralizado: Compra rápida, Preferências e Cosméticos na linha do meio; Lista de níveis embaixo */
        addItem(11, Item.of(Material.CHEST, "§aCompra rápida",
                        "§7Altere os favoritos",
                        "§7da loja.",
                        "",
                        "§eClique para ver!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new QuickBuyMenu(getPlayer(), member, this).handle();
                }));

        addItem(13, Item.of(Material.REDSTONE_COMPARATOR, "§aPreferências",
                        "§7Altere as preferências",
                        "§7do Bed Wars.",
                        "",
                        "§eClique para ver!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new PreferenceMenu(getPlayer(), member, this).handle();
                }));

        addItem(15, Item.of(Material.RAW_FISH, 3, "§aCosméticos",
                        "§7Escolha cosméticos",
                        "§7exclusivos do Bed Wars.",
                        "",
                        "§eClique para ver!")
                .flags(ItemFlag.values())
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new BedCosmeticsMenu(getPlayer(), this).handle();
                }));

        addItem(31, Item.of(Material.PAPER, "§aLista de níveis",
                        "§7Visualize todos os",
                        "§7níveis do Bed Wars.",
                        "",
                        "§eClique para ver!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new LevelListMenu(getPlayer(), member, this).handle();
                }));

        display();
    }
}
