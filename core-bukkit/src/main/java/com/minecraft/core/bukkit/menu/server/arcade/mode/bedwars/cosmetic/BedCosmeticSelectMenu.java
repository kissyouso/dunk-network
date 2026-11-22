package com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.cosmetic;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.cosmetic.BedCosmetic;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.List;

public class BedCosmeticSelectMenu extends Menu {

    private static final int[] SLOTS = {11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final BedMember member;
    private final BedCosmetic.Category category;

    public BedCosmeticSelectMenu(Player player, BedMember member, BedCosmetic.Category category, Menu last) {
        super(player, category.getName(), last, 4);

        this.member = member;
        this.category = category;
    }

    @Override
    public void handle() {
        clear();

        Account account = Core.getAccountController().of(getPlayer().getUniqueId());
        BedCosmetic chosen = member.getCosmetic(category);

        /* Opção "Nenhuma" */
        boolean noneSelected = chosen == null;

        addItem(10, Item.of(Material.BARRIER, "§aNenhuma",
                        noneSelected ? "§aSelecionado" : "§cNão selecionado",
                        "",
                        "§eBotão esquerdo: §fSelecionar")
                .click(event -> {
                    if (noneSelected) return;

                    member.setCosmetic(category, null);
                    sound(MenuSound.SUCCESS);
                    handle();
                }));

        List<BedCosmetic> cosmetics = BedCosmetic.of(category);

        for (int i = 0; i < cosmetics.size() && i < SLOTS.length; i++) {
            BedCosmetic cosmetic = cosmetics.get(i);

            boolean owned = cosmetic.has(account);
            boolean selected = cosmetic == chosen;

            String color = category == BedCosmetic.Category.COLOR ? cosmetic.getValue() : (owned ? "§a" : "§c");

            Material material = Material.getMaterial(cosmetic.getMaterial());

            Item icon = Item.of(material != null ? material : Material.PAPER, cosmetic.getData(), color + cosmetic.getName())
                    .flags(ItemFlag.values());

            if (cosmetic.isVip())
                icon.lore("§7Exclusivo para §aVIP",
                        "",
                        selected ? "§aSelecionado" : "§cNão selecionado",
                        "",
                        owned ? "§eBotão esquerdo: §fSelecionar" : "§cVocê não possui esse cosmético.");
            else
                icon.lore(selected ? "§aSelecionado" : "§cNão selecionado",
                        "",
                        "§eBotão esquerdo: §fSelecionar");

            if (selected)
                icon.enchantment(Enchantment.DURABILITY, 1);

            addItem(SLOTS[i], icon.click(event -> {
                if (!owned || selected) {
                    sound(MenuSound.ERROR);
                    return;
                }

                member.setCosmetic(category, cosmetic);
                sound(MenuSound.SUCCESS);
                handle();
            }));
        }

        if (isReturnable())
            addBackButton(31);

        display();
    }
}
