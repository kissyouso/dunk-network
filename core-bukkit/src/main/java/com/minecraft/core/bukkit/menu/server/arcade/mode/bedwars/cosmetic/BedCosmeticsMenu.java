package com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.cosmetic;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Heads;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.collectible.CollectibleMenu;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.BedCollectible;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.type.BedCollectibleType;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.cosmetic.BedCosmetic;
import com.minecraft.core.server.type.ServerType;
import com.minecraft.core.util.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.List;

public class BedCosmeticsMenu extends Menu {

    private final BedMember member;

    public BedCosmeticsMenu(Player player, Menu last) {
        super(player, "Cosméticos do Bed Wars", last, 4);

        Account account = Core.getAccountController().of(player.getUniqueId());
        BedMember loaded = Core.getBedWarsData().of(player.getUniqueId(), true);

        this.member = loaded != null ? loaded : (BedMember) account.loadMember(ServerType.BEDWARS);
    }

    @Override
    public void handle() {
        clear();

        if (member == null) {
            addErrorButton(22, "§cNão foi possível carregar os seus dados...");
            display();
            return;
        }

        Account account = Core.getAccountController().of(getPlayer().getUniqueId());

        addCollectible(10, Item.of(Material.BED), "Destruição de cama", BedCollectibleType.BED_DESTRUCTION, account);
        addCollectible(12, Item.of(Material.DIAMOND_SWORD), "Kill Final", BedCollectibleType.FINAL_KILL, account);
        addCollectible(14, Heads.url(Heads.VILLAGER), "Skin dos vendedores", BedCollectibleType.SELLER_SKIN, account);
        addCosmetic(16, Material.GOLD_INGOT, 0, BedCosmetic.Category.VICTORY, account);

        addCosmetic(19, Material.WOOD, 0, BedCosmetic.Category.WOOD, account);
        addCosmetic(21, Material.PAPER, 0, BedCosmetic.Category.KILL_MESSAGE, account);
        addCosmetic(23, Material.WOOL, 0, BedCosmetic.Category.COLOR, account);
        addCosmetic(25, Material.ARROW, 0, BedCosmetic.Category.TRAIL, account);

        if (isReturnable())
            addBackButton(31);

        display();
    }

    protected void addCollectible(int slot, Item icon, String name, BedCollectibleType type, Account account) {
        List<BedCollectible> items = type.getItems();

        int total = (int) items.stream().filter(collectible -> !collectible.getName().equalsIgnoreCase("Nenhuma")).count();
        int owned = (int) items.stream().filter(collectible -> !collectible.getName().equalsIgnoreCase("Nenhuma") && collectible.hasPermission(account)).count();

        BedCollectible chosen = member.getActiveCollectible(type);

        addItem(slot, icon.name("§a" + name)
                .lore(categoryLore(chosen != null ? chosen.getName() : "Nenhuma", owned, total))
                .flags(ItemFlag.values())
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new CollectibleMenu(getPlayer(), member, type, this).handle();
                }));
    }

    protected void addCosmetic(int slot, Material material, int data, BedCosmetic.Category category, Account account) {
        List<BedCosmetic> items = BedCosmetic.of(category);

        int owned = (int) items.stream().filter(cosmetic -> cosmetic.has(account)).count();
        BedCosmetic chosen = member.getCosmetic(category);

        addItem(slot, Item.of(material, data, "§a" + category.getName())
                .lore(categoryLore(chosen != null ? chosen.getName() : "Nenhuma", owned, items.size()))
                .flags(ItemFlag.values())
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new BedCosmeticSelectMenu(getPlayer(), member, category, this).handle();
                }));
    }

    protected static String[] categoryLore(String selected, int owned, int total) {
        return new String[]{
                "§7Escolha seus cosméticos",
                "§7dessa categoria.",
                "",
                "§7Selecionado: §a" + selected,
                "§7Desbloqueados: §f" + owned + "/" + total + " (" + Util.percentage(owned, Math.max(1, total)) + "%)",
                "",
                "§eClique para ver mais."
        };
    }
}
