package com.minecraft.core.bukkit.menu.account.punish;

import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.api.punishment.objects.enums.PunishmentCategory;
import com.minecraft.core.api.punishment.objects.enums.PunishmentPreset;
import com.minecraft.core.bukkit.api.menu.Heads;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * Motivos de uma categoria (banimento ou mute), com duração, cargo mínimo e quando aplicar.
 */
public class PunishReasonMenu extends Menu {

    private final Account viewer, target;
    private final PunishmentCategory category;

    public PunishReasonMenu(Player player, Account viewer, Account target, PunishmentCategory category, Menu last) {
        super(player, category.getName() + " • " + target.getName(), last, 5);

        this.viewer = viewer;
        this.target = target;
        this.category = category;
    }

    @Override
    public void handle() {
        clear();

        String color = color(category), texture = texture(category);

        addItem(4, PunishMenu.targetHead(target));

        List<PunishmentPreset> presets = PunishmentPreset.of(category);

        /* Até 7 por linha, cada linha centralizada (linhas 2 e 3) */
        for (int index = 0; index < presets.size() && index < 14; index++) {
            int row = index / 7,
                    inRow = Math.min(7, presets.size() - row * 7),
                    column = (9 - inRow) / 2 + index % 7;

            addItem(18 + row * 9 + column, reason(presets.get(index), color, texture));
        }

        if (isReturnable())
            addBackButton();

        display();
    }

    private Item reason(PunishmentPreset preset, String color, String texture) {
        boolean allowed = viewer.hasRank(preset.getMinimumRank());

        List<String> lore = new ArrayList<>();

        lore.add("§8" + category.getName() + " • " + preset.getReason().getName());
        lore.add("");
        lore.add("§7Duração: " + (preset.isPermanent() ? "§cPermanente" : "§f" + preset.getDurationName()));
        lore.add("§7Cargo mínimo: " + preset.getMinimumRank().getColoredName());
        lore.add("");
        lore.add("§7Quando aplicar:");

        for (String line : preset.getDescription())
            lore.add("§8▪ §7" + line);

        lore.add("");
        lore.add(allowed ? "§eClique para aplicar." : "§cSeu cargo não pode aplicar este motivo.");

        Item item = Heads.url(allowed ? texture : PunishMenu.LOCKED)
                .name((allowed ? color : "§8") + preset.getName())
                .lore(lore)
                .flags(ItemFlag.values());

        return item.click(event -> {
            if (!allowed) {
                sound(MenuSound.ERROR);
                return;
            }

            sound(MenuSound.PAGINATED);
            new PunishConfirmMenu(getPlayer(), viewer, target, preset, this).handle();
        });
    }

    static String color(PunishmentCategory category) {
        return category == PunishmentCategory.BAN ? "§c" : "§6";
    }

    static String texture(PunishmentCategory category) {
        return category == PunishmentCategory.BAN ? PunishMenu.BAN : PunishMenu.MUTE;
    }
}
