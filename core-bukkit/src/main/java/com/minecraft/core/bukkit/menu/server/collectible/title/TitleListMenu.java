package com.minecraft.core.bukkit.menu.server.collectible.title;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.collectible.CollectibleCategory;
import com.minecraft.core.api.collectible.operator.CollectibleOperator;
import com.minecraft.core.api.collectible.type.title.TitleCategory;
import com.minecraft.core.api.collectible.type.title.TitleCollectible;
import com.minecraft.core.api.collectible.type.title.TitleList;
import com.minecraft.core.api.collectible.type.title.TitleStat;
import com.minecraft.core.api.collectible.rarity.CollectibleRarity;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Títulos de uma categoria, com prévia usando os valores reais do jogador.
 */
public class TitleListMenu extends Menu {

    private final Account account;
    private final TitleCategory category;
    private final Map<TitleStat, Long> stats;

    public TitleListMenu(Player player, TitleCategory category, Map<TitleStat, Long> stats, Menu last) {
        super(player, "Títulos - " + category.getName(), last, 5, 21);

        this.account = Core.getAccountController().of(player.getUniqueId());
        this.category = category;
        this.stats = stats;
    }

    @Override
    public void handle() {
        clear();

        List<TitleCollectible> titles = TitleList.list(category);
        TitleCollectible current = TitleCategoryMenu.current(account);

        buildPageItems(titles, 10, (title, slot) -> {
            boolean using = title.equals(current);
            boolean allowed = title.canUse(account);

            /* Animado: mostra um quadro com o brilho no meio do texto */
            String preview = title.render(stats, title.getFrames() > 1 ? title.getFrames() / 5 : 0);

            List<String> lore = new ArrayList<>();

            if (title.getRarity() != CollectibleRarity.COMUM || title.getFrames() > 1)
                lore.add("§8" + title.getRarity().getName() + (title.getFrames() > 1 ? " • Animado" : ""));

            lore.add(preview);

            if (title.getRequirement() != null) {
                lore.add("");
                lore.add("§7" + title.getRequirement());
            }

            lore.add("");
            lore.add(using ? "§aEm uso! §7Clique para remover." : allowed ? "§eClique para usar!" : "§cBloqueado.");

            Item item = Item.of(category.getIcon(), preview, lore).flags(ItemFlag.values());

            if (using)
                item.enchantment(Enchantment.DURABILITY, 1);

            addItem(slot, item.click(event -> {
                if (!allowed) {
                    sound(MenuSound.ERROR);
                    account.send("§c" + (title.getRequirement() != null ? title.getRequirement() : "Você não pode usar este título."));
                    return;
                }

                CollectibleOperator.unload(getPlayer(), CollectibleCategory.TITLE);

                if (using) {
                    account.send("§cVocê removeu o título §f" + title.getName() + "§c.");
                } else {
                    CollectibleOperator.start(getPlayer(), title);
                    account.send("§aVocê equipou o título §f" + title.getName() + "§a.");
                }

                sound(MenuSound.SUCCESS);
                handle();
            }));
        });

        if (isReturnable())
            addBackButton();

        display();
    }
}
