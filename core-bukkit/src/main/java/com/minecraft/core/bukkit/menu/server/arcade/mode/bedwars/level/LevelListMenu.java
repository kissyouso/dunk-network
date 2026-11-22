package com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.level;

import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Heads;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.member.list.bedwars.BedMember;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.text.NumberFormat;
import java.util.Locale;

public class LevelListMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final NumberFormat FORMAT = NumberFormat.getInstance(Locale.US);

    private final BedMember member;
    private int page = 1;

    public LevelListMenu(Player player, BedMember member, Menu last) {
        super(player, "Lista de níveis", last, 5);

        this.member = member;
        setShowTitlePage(false);
    }

    @Override
    public void handle() {
        clear();

        int pages = (BedMember.MAX_LEVEL + SLOTS.length - 1) / SLOTS.length;
        int current = member != null ? member.getLevel() : 1;

        for (int i = 0; i < SLOTS.length; i++) {
            int level = (page - 1) * SLOTS.length + i + 1;

            if (level > BedMember.MAX_LEVEL) break;

            Item icon = Item.of(Material.INK_SACK, BedMember.getLevelDye(level), BedMember.getLevelName(level),
                            "§7Nível: §f" + level,
                            "§7Símbolo: " + BedMember.getLevelColor(level) + "✦",
                            "§7XP necessário: §f" + FORMAT.format(BedMember.getTotalXpForLevel(level)),
                            "",
                            "§7Página " + page + "/" + pages)
                    .flags(ItemFlag.values());

            if (level == current)
                icon.enchantment(Enchantment.DURABILITY, 1);

            addItem(SLOTS[i], icon);
        }

        if (page > 1) {
            addItem(39, Heads.back().name("§aPágina anterior").lore("§7Clique para voltar.")
                    .click(event -> {
                        page--;
                        sound(MenuSound.PAGINATED);
                        handle();
                    }));
        }

        if (isReturnable())
            addBackButton(40);

        if (page < pages) {
            addItem(41, Heads.next().name("§aPróxima página").lore("§7Clique para avançar.")
                    .click(event -> {
                        page++;
                        sound(MenuSound.PAGINATED);
                        handle();
                    }));
        }

        display();
    }
}
