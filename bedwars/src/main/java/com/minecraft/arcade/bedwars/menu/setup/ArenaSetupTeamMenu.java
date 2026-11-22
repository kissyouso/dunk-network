package com.minecraft.arcade.bedwars.menu.setup;

import com.minecraft.arcade.bedwars.setup.ArenaSetup;
import com.minecraft.arcade.bedwars.setup.ArenaSetupActions;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.room.team.preset.TeamPresetType;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.util.list.bukkit.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

public class ArenaSetupTeamMenu extends Menu {

    private final ArenaSetup setup;
    private final TeamPresetType team;

    public ArenaSetupTeamMenu(Player player, ArenaSetup setup, TeamPresetType team, Menu last) {
        super(player, "Time " + team.getName(), last, 4);

        this.setup = setup;
        this.team = team;
    }

    @Override
    public void handle() {
        clear();

        Player player = getPlayer();

        addItem(4, Item.of(Material.WOOL, ColorUtil.getIdByColor(team.getColor()), ArenaSetup.display(team),
                "§7Pontos obrigatórios: §f" + setup.countTeam(team, true) + "/" + ArenaSetup.requiredPoints(),
                "", "§eClique para usar este time na hotbar!").click(event -> {
            ArenaSetupActions.selectTeam(player, setup, team);
            close();
        }));

        int slot = 10;

        for (ArenaSetup.TeamPoint point : ArenaSetup.TeamPoint.values()) {
            String key = point.key(team);
            boolean defined = setup.has(key);

            Item item = Item.of(icon(point), (defined ? "§a✔ " : point.isRequired() ? "§c✘ " : "§8✘ ") + "§f" + point.getName() + (point.isRequired() ? "" : " §8(opcional)"),
                    defined ? "§7Em " + ArenaSetup.format(setup.getLocation(key)) : "§7Ainda não definido.",
                    "§7" + hint(point),
                    "",
                    "§eClique esquerdo: §fdefinir",
                    defined ? "§eClique direito: §fteleportar" : "",
                    defined ? "§eShift + direito: §fremover" : "",
                    "§8/config " + team.getName().toLowerCase() + " " + point.getAlias()).flags(ItemFlag.values());

            item.click(event -> {
                if (event.isRightClick()) {
                    if (!defined) {
                        sound(MenuSound.ERROR);
                        return;
                    }

                    if (event.isShiftClick()) {
                        ArenaSetupActions.remove(player, setup, key);
                        handle();
                    } else {
                        close();
                        ArenaSetupActions.teleport(player, setup, key);
                    }
                    return;
                }

                ArenaSetupActions.setTeamPoint(player, setup, team, point);
                handle();
            });

            addItem(slot++, item);
        }

        if (isReturnable())
            addBackButton();

        display();
    }

    private static Material icon(ArenaSetup.TeamPoint point) {
        switch (point) {
            case SPAWN:
                return Material.ARMOR_STAND;
            case BED:
                return Material.BED;
            case GENERATOR:
                return Material.IRON_INGOT;
            case SHOP:
                return Material.EMERALD;
            case UPGRADE:
                return Material.ENCHANTMENT_TABLE;
            case CHEST:
                return Material.CHEST;
            default:
                return Material.ENDER_CHEST;
        }
    }

    private static String hint(ArenaSetup.TeamPoint point) {
        switch (point) {
            case BED:
                return "Olhe para a cama do time.";
            case CHEST:
                return "Olhe para o baú do time.";
            case ENDER_CHEST:
                return "Olhe para o baú do fim.";
            case GENERATOR:
                return "Fique em cima do gerador.";
            default:
                return "Fique no lugar, olhando para a direção.";
        }
    }
}
