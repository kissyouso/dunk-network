package com.minecraft.core.bukkit.menu.server.collectible.title;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.collectible.CollectibleCategory;
import com.minecraft.core.api.collectible.operator.CollectibleOperator;
import com.minecraft.core.api.collectible.type.title.TitleCategory;
import com.minecraft.core.api.collectible.type.title.TitleCollectible;
import com.minecraft.core.api.collectible.type.title.TitleList;
import com.minecraft.core.api.collectible.type.title.TitleStat;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.HashMap;
import java.util.Map;

/**
 * Títulos separados por categoria (Gerais, Bed Wars, Duels...).
 */
public class TitleCategoryMenu extends Menu {

    /* Uma linha centralizada, com um espaço entre cada categoria */
    private static final int[] SLOTS = {10, 12, 14, 16};

    private final Account account;

    public TitleCategoryMenu(Player player, Menu last) {
        super(player, "Títulos", last, 3);

        this.account = Core.getAccountController().of(player.getUniqueId());
    }

    @Override
    public void handle() {
        clear();

        TitleCollectible current = current(account);

        addItem(4, Item.of(Material.NAME_TAG, "§aTítulo atual",
                        current == null
                                ? new String[]{"§7Nenhum título equipado.", "", "§7Escolha um nas categorias abaixo."}
                                : new String[]{"§f" + current.getName(), "", "§eClique para remover!"})
                .flags(ItemFlag.values())
                .click(event -> {
                    if (current == null) {
                        sound(MenuSound.ERROR);
                        return;
                    }

                    CollectibleOperator.unload(getPlayer(), CollectibleCategory.TITLE);
                    account.send("§cVocê removeu o seu título.");

                    sound(MenuSound.SUCCESS);
                    handle();
                }));

        TitleCategory[] categories = TitleCategory.values();

        for (int index = 0; index < categories.length && index < SLOTS.length; index++) {
            TitleCategory category = categories[index];

            addItem(SLOTS[index], Item.of(category.getIcon(), "§a" + category.getName(),
                            "§7" + category.getDescription(),
                            "",
                            "§eClique para ver!")
                    .flags(ItemFlag.values())
                    .click(event -> {
                        sound(MenuSound.PAGINATED);
                        open(getPlayer(), category, this);
                    }));
        }

        if (isReturnable())
            addBackButton();

        display();
    }

    /**
     * Carrega as estatísticas da categoria fora da thread principal e abre a lista já com os valores.
     */
    public static void open(Player player, TitleCategory category, Menu last) {
        Account account = Core.getAccountController().of(player.getUniqueId());

        Bukkit.getScheduler().runTaskAsynchronously(BukkitCore.getInstance(), () -> {
            Map<TitleStat, Long> stats = new HashMap<>();

            for (TitleStat stat : TitleStat.values()) {
                if (stat.getCategory() == category)
                    stats.put(stat, stat.load(account));
            }

            Bukkit.getScheduler().runTask(BukkitCore.getInstance(), () -> {
                if (player.isOnline())
                    new TitleListMenu(player, category, stats, last).handle();
            });
        });
    }

    static TitleCollectible current(Account account) {
        for (String id : account.getActiveCollectibles()) {
            if (Core.getCollectibleController().of(id) instanceof TitleCollectible)
                return (TitleCollectible) Core.getCollectibleController().of(id);
        }

        return null;
    }
}
