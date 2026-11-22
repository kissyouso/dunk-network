package com.minecraft.arcade.duels.menu.setup;

import com.minecraft.arcade.duels.Duels;
import com.minecraft.arcade.duels.setup.ArenaSetupActions;
import com.minecraft.core.arcade.ArcadeHolder;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.member.list.duels.DuelInventories;
import com.minecraft.core.server.type.ServerType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Kits padrão de cada modo de Duels (plugins/duels/inventarios).
 * O kit salvo aqui vale para todo mundo que não customizou o próprio inventário no hub.
 */
public class ArenaSetupKitMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24};
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private final ArcadeCategory current;

    public ArenaSetupKitMenu(Player player, String mode) {
        super(player, "Kits dos Modos", 6);

        this.current = read(mode);
    }

    private static ArcadeCategory read(String mode) {
        if (mode == null) return null;

        ArcadeHolder holder = Duels.getManager().getArcade().getGame(mode);

        return holder != null ? holder.getCategory() : null;
    }

    @Override
    public void handle() {
        clear();

        Player player = getPlayer();
        List<ArcadeCategory> categories = new ArrayList<>(ArcadeCategory.of(ServerType.DUELS));

        addItem(4, Item.of(Material.BOOK, "§bKits dos Modos",
                "§7Monte o inventário que você quer",
                "§7(equipe os itens no criativo) e",
                "§7salve no modo desejado.",
                "",
                "§eEsquerdo: §fcarregar para editar",
                "§eDireito: §fsalvar o meu inventário",
                "§eShift + direito: §fremover"));

        for (int index = 0; index < categories.size() && index < SLOTS.length; index++)
            addItem(SLOTS[index], kitItem(player, categories.get(index)));

        addItem(48, Item.of(Material.ARROW, "§cFechar", "§7Volta para o jogo.").click(event -> close()));

        if (current != null)
            addItem(50, Item.of(Material.EMERALD, "§aSalvar no modo atual", "§7Modo: §f" + current.getName(), "", "§7Pega o inventário que está", "§7na sua mão agora.").click(event -> {
                ArenaSetupActions.saveKit(player, current);
                sound(MenuSound.DONE);
                handle();
            }));

        display();
    }

    private Item kitItem(Player player, ArcadeCategory category) {
        String base64 = DuelInventories.get(category);

        List<String> lore = new ArrayList<>();

        if (base64 == null)
            lore.add("§7Kit: §f" + (category.equals(current) ? "§eusa o padrão do jogo" : "§cninguém salvo"));
        else {
            lore.add("§7Kit: §asalvo §8(" + DATE.format(new Date(DuelInventories.getLastUpdate(category))) + ")");
            lore.add("§7É o padrão de todos os jogadores");
            lore.add("§7que não editaram o próprio kit.");
        }

        if (category.equals(current)) {
            lore.add("");
            lore.add("§8★ modo da arena que você está");
        }

        lore.add("");
        lore.add("§eClique esquerdo: §fcarregar no meu inventário");
        lore.add(base64 == null ? "§8(nada para carregar)" : "§eClique direito: §fsalvar o meu inventário");

        if (base64 != null)
            lore.add("§eShift + direito: §fremover o kit");

        Material icon = Material.getMaterial(category.getIconId());

        Item item = Item.of(icon != null ? icon : Material.STAINED_CLAY,
                (category.equals(current) ? "§b★ " : "§e") + category.getName(), lore).flags(ItemFlag.values());

        item.click(event -> {
            if (event.isShiftClick()) {
                if (base64 == null || !event.isRightClick()) {
                    sound(MenuSound.ERROR);
                    return;
                }

                ArenaSetupActions.removeKit(player, category);
            } else if (event.isRightClick())
                ArenaSetupActions.saveKit(player, category);
            else
                ArenaSetupActions.loadKit(player, category);

            handle();
        });

        return item;
    }
}
