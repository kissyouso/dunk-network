package com.minecraft.core.bukkit.menu.server.collectible;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.collectible.CollectibleCategory;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.api.joinmessage.JoinMessage;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.account.AccountMenu;
import com.minecraft.core.bukkit.menu.account.joinmessage.JoinMessageMenu;
import com.minecraft.core.bukkit.menu.account.max.MaxResourcesMenu;
import com.minecraft.core.controller.list.CollectibleController;
import com.minecraft.core.util.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

public class CollectibleMenu extends Menu {

    private final Account account;
    private final CollectibleController controller = Core.getCollectibleController();

    public CollectibleMenu(Player player) {
        super(player, "Colecionáveis", 3);

        this.account = Core.getAccountController().of(player.getUniqueId());
    }

    @Override
    public void handle() {
        clear();

        addCategoryItem(9, Material.GOLD_HELMET, "Chapéus", CollectibleCategory.HAT,
                "§7Coloque chapeus na sua",
                "§7cabeça!");

        addCategoryItem(11, Material.ENCHANTMENT_TABLE, "Capas", CollectibleCategory.CAPES,
                "§7Utilize capas que são",
                "§7efeitos com partículas!");

        addCategoryItem(13, Material.NETHER_STAR, "Partículas", CollectibleCategory.PARTICLE,
                "§7Use partículas e esbanje",
                "§7charme nos lobbys!");

        /* Títulos têm menu próprio, separado por categorias */
        java.util.List<com.minecraft.core.api.collectible.type.title.TitleCollectible> titles = new java.util.ArrayList<>();

        for (com.minecraft.core.api.collectible.type.title.TitleCategory group : com.minecraft.core.api.collectible.type.title.TitleCategory.values())
            titles.addAll(com.minecraft.core.api.collectible.type.title.TitleList.list(group));

        long unlockedTitles = titles.stream().filter(title -> title.canUse(account)).count();

        addItem(15, Item.of(Material.NAME_TAG, "§aTítulos",
                        "§7Títulos são mensagens",
                        "§7acima da sua cabeça.",
                        "",
                        "§7Liberados: " + (unlockedTitles >= titles.size() && !titles.isEmpty() ? "§a" : "§c") + unlockedTitles + "/" + titles.size(),
                        "",
                        "§eClique para ver mais!")
                .flags(ItemFlag.values())
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new com.minecraft.core.bukkit.menu.server.collectible.title.TitleCategoryMenu(getPlayer(), this).handle();
                }));

        boolean max = AccountMenu.isDunkPlus(account);
        JoinMessage current = account.getSelectedJoinMessageEnum();

        addItem(17, Item.of(Material.BOOK, "§aMensagens de entrada",
                        "§7Anuncie sua entrada no lobby",
                        "§7com estilos exclusivos.",
                        "",
                        "§7Disponíveis: §f" + account.getAvailableJoinMessages().size(),
                        "§7Atual: §f" + (current != null ? current.getName() : "Nenhuma"),
                        "",
                        max ? "§eClique para ver mais!" : "§cExclusivo Style+.")
                .click(event -> {
                    if (!max) {
                        sound(MenuSound.ERROR);
                        account.send(MaxResourcesMenu.EXCLUSIVE_MESSAGE);
                        return;
                    }

                    sound(MenuSound.PAGINATED);
                    new JoinMessageMenu(getPlayer(), this).handle();
                }));

        display();
    }

    private void addCategoryItem(int slot, Material material, String name, CollectibleCategory category, String... description) {
        int unlocked = account.getCollectibles(category).size();
        int total = controller.list(category).size();

        String[] lore = new String[description.length + 4];

        System.arraycopy(description, 0, lore, 0, description.length);

        lore[description.length] = "";
        lore[description.length + 1] = "§7Desbloqueados: " + (unlocked >= total && total > 0 ? "§a" : "§c") + unlocked + "/" + total
                + " §8(" + Util.percentage(unlocked, Math.max(1, total)) + "%)";
        lore[description.length + 2] = "";
        lore[description.length + 3] = "§eClique para ver mais!";

        addItem(slot, Item.of(material, "§a" + name, lore)
                .flags(ItemFlag.values())
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new CollectibleSelectorMenu(getPlayer(), category, this).handle();
                }));
    }
}
