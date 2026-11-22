package com.minecraft.core.bukkit.menu.account.medal;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.medal.Medal;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MedalMenu extends Menu {

    /* Espaços restantes das linhas centrais (o slot 10 é o "Nenhuma") */
    private static final int[] SLOTS = {11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final Account account;

    public MedalMenu(Player player, Menu last) {
        super(player, "Selecionar medalha", last, 6);

        this.account = Core.getAccountController().of(player.getUniqueId());
        setShowTitlePage(false);
    }

    @Override
    public void handle() {
        clear();

        boolean usingNone = account.getMedal() == null || account.getMedal() == Medal.NONE;

        Item none = Item.of(Material.NAME_TAG, "§aNenhuma",
                        "",
                        "§7Remova sua medalha atual",
                        "",
                        usingNone ? "§aSelecionada!" : "§eClique para selecionar!")
                .flags(ItemFlag.values())
                .click(event -> {
                    if (usingNone) return;

                    account.setMedal(Medal.NONE);
                    sound(MenuSound.SUCCESS);
                    handle();
                });

        if (usingNone)
            none.enchantment(Enchantment.DURABILITY, 1);

        addItem(10, none);

        List<Medal> medals = Arrays.stream(Medal.values())
                .filter(medal -> medal != Medal.NONE)
                .collect(Collectors.toList());

        int page = getPageNumber();
        int from = (page - 1) * SLOTS.length;

        setTotalPages(Math.max(1, (medals.size() + SLOTS.length - 1) / SLOTS.length));

        for (int i = 0; i < SLOTS.length && from + i < medals.size(); i++) {
            Medal medal = medals.get(from + i);

            boolean owned = account.hasMedal(medal);
            boolean using = medal.equals(account.getMedal());

            Item icon = Item.of(Material.INK_SACK, owned ? 10 : 1,
                            (owned ? "§a" : "§c") + medal.getName(),
                            "",
                            "§7Medalha: " + medal.getColoredSymbol(),
                            "",
                            using ? "§aSelecionada!" : owned ? "§eClique para selecionar!" : "§cVocê não possui esta medalha.")
                    .flags(ItemFlag.values())
                    .click(event -> {
                        if (!owned || using) {
                            sound(MenuSound.ERROR);
                            return;
                        }

                        account.setMedal(medal);
                        sound(MenuSound.SUCCESS);
                        handle();
                    });

            if (using)
                icon.enchantment(Enchantment.DURABILITY, 1);

            addItem(SLOTS[i], icon);
        }

        addBorderPage(45, 53);

        if (isReturnable() && page <= 1)
            addBackButton(49);

        display();
    }
}
