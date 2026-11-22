package com.minecraft.arcade.duels.menu.setup;

import com.minecraft.arcade.duels.setup.ArenaSetup;
import com.minecraft.arcade.duels.setup.ArenaSetupActions;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.room.team.preset.TeamPresetType;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.util.list.bukkit.ColorUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.List;

/**
 * Menu do /duconfig: todos os pontos do mapa de Duels numa tela só.
 */
public class ArenaSetupMenu extends Menu {

    private static final int[] TEAM_SLOTS = {12, 14};
    private static final int[] POINT_SLOTS = {19, 20, 25, 26};

    private final ArenaSetup setup;

    public ArenaSetupMenu(Player player, ArenaSetup setup) {
        super(player, "Arena " + setup.getMapName(), 6);

        this.setup = setup;
    }

    @Override
    public void handle() {
        clear();

        Player player = getPlayer();
        List<TeamPresetType> teams = setup.getTeams();
        List<String> missing = setup.getMissing();

        addItem(4, Item.of(Material.PAPER, "§b" + setup.getMapName(),
                "§7Modo: §f" + setup.getMode(),
                "§7Mundo: §f" + setup.getWorld().getName(),
                "§7Template: §f" + setup.getTemplate(),
                "§7Pontos: §f" + setup.getLocations().size(),
                "",
                missing.isEmpty() ? "§a✔ Pronto para salvar!" : "§cFaltam " + missing.size() + " ponto(s).",
                setup.isChanged() ? "§eAlterações não salvas." : "§7Nada para salvar."));

        /* Times */
        for (int index = 0; index < teams.size() && index < TEAM_SLOTS.length; index++) {
            TeamPresetType team = teams.get(index);
            int count = setup.countTeam(team, true), required = setup.requiredPoints();

            Item item = Item.of(Material.WOOL, ColorUtil.getIdByColor(team.getColor()),
                    ArenaSetup.display(team) + " §7(" + count + "/" + required + ")",
                    "§7Clique esquerdo: §fusar este time",
                    "§7na hotbar dos itens.",
                    "",
                    "§7Os pontos do time ficam na", "§7linha de baixo.").flags(ItemFlag.values());

            if (count == required)
                item.enchantment(Enchantment.DURABILITY, 1);

            item.click(event -> {
                ArenaSetupActions.selectTeam(player, setup, team);
                sound(MenuSound.DONE);
            });

            addItem(TEAM_SLOTS[index], item);
        }

        /* Pontos de cada time */
        int slot = POINT_SLOTS[0];

        for (TeamPresetType team : teams) {
            for (ArenaSetup.TeamPoint point : ArenaSetup.TeamPoint.values()) {
                addItem(slot++, point(point.getIcon(), point.key(team),
                        ArenaSetup.display(team) + " §7" + point.getName(),
                        point.equals(ArenaSetup.TeamPoint.BED) ? "§7Olhe para a cama do time." : "§7Fique no lugar, olhando para a direção.",
                        setup.isRequired(point)));
            }

            slot = POINT_SLOTS[2];
        }

        /* Pontos gerais */
        addItem(28, point(Material.BEACON, "spawn", "§eSpawn da espera", "§7Onde os jogadores esperam a partida."));
        addItem(29, point(Material.STAINED_GLASS, "map_limit_pos1", "§eBorda 1", "§7Um canto do mapa (vale o bloco onde você está)."));
        addItem(30, point(Material.STAINED_GLASS, "map_limit_pos2", "§eBorda 2", "§7O canto oposto do mapa."));

        int limit = setup.getBuildLimit();

        addItem(31, Item.of(Material.LADDER, "§eAltura de construção", "§7Atual: " + (limit > 0 ? "§f" + limit : "§cnão definida"),
                "", "§eClique esquerdo: §fusar sua altura (§f" + player.getLocation().getBlockY() + "§f)",
                "§eClique direito: §f+5 §7| §eShift: §f-5").click(event -> {
            if (event.isShiftClick()) {
                ArenaSetupActions.setBuildLimit(player, setup, Math.max(1, (limit > 0 ? limit : player.getLocation().getBlockY()) - 5));
            } else if (event.isRightClick()) {
                ArenaSetupActions.setBuildLimit(player, setup, (limit > 0 ? limit : player.getLocation().getBlockY()) + 5);
            } else {
                ArenaSetupActions.setBuildLimit(player, setup, player.getLocation().getBlockY());
            }

            sound(MenuSound.DONE);
            handle();
        }));

        addItem(32, areaItem(player));
        addItem(33, Item.of(Material.DIAMOND_CHESTPLATE, "§eKit do modo", "§7Inventário padrão de cada modo,", "", "§eClique para abrir!").click(event -> {
            close();
            new ArenaSetupKitMenu(player, setup.getMode()).handle();
        }));

        /* Ações */
        addItem(45, Item.of(Material.CHEST, "§eItens de configuração", "§7Recebe os itens na hotbar.").click(event -> {
            ArenaSetupActions.giveKit(player, setup);
            close();
        }));

        addItem(47, Item.of(Material.MAP, "§eVer status", "§7Mostra no chat o que falta.").click(event -> {
            close();
            ArenaSetupActions.sendStatus(player, setup);
        }));

        addItem(49, Item.of(Material.EMERALD, "§aSalvar e recarregar", "§7Grava o config.json e recarrega",
                "§7as arenas do modo.", "", missing.isEmpty() ? "§a✔ Tudo pronto!" : "§eFaltam " + missing.size() + " ponto(s), mas dá para salvar.").click(event -> {
            close();
            ArenaSetupActions.save(player, setup, true);
        }));

        addItem(51, Item.of(Material.COMPASS, "§eListar pontos", "§7Mostra todos os pontos no chat.").click(event -> {
            close();
            ArenaSetupActions.sendPoints(player, setup);
        }));

        addItem(53, Item.of(Material.BARRIER, "§cSair sem salvar", "§7Descarta as alterações não salvas.", "", "§cShift + clique para confirmar.").click(event -> {
            if (!event.isShiftClick()) {
                sound(MenuSound.ERROR);
                return;
            }

            close();
            ArenaSetupActions.stop(player);
        }));

        display();
    }

    private Item point(Material material, String key, String name, String description) {
        return point(material, key, name, description, true);
    }

    private Item point(Material material, String key, String name, String description, boolean required) {
        boolean defined = setup.has(key);

        Item item = Item.of(material, (defined ? "§a✔ " : required ? "§c✘ " : "§8✘ ") + name, description, "",
                "§eClique esquerdo: §fdefinir aqui",
                defined ? "§eClique direito: §fteleportar" : "",
                defined ? "§eShift + direito: §fremover" : "").flags(ItemFlag.values());

        item.click(event -> {
            Player player = getPlayer();

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

            define(player, key);
            sound(MenuSound.DONE);
            handle();
        });

        return item;
    }

    private void define(Player player, String key) {
        if (key.equals("spawn")) {
            ArenaSetupActions.setWaitingSpawn(player, setup);
            return;
        }

        if (key.startsWith("map_limit_pos")) {
            ArenaSetupActions.setBorder(player, setup, Integer.parseInt(key.substring("map_limit_pos".length())));
            return;
        }

        for (TeamPresetType team : setup.getTeams()) {
            for (ArenaSetup.TeamPoint point : ArenaSetup.TeamPoint.values()) {
                if (point.key(team).equals(key)) {
                    ArenaSetupActions.setTeamPoint(player, setup, team, point);
                    return;
                }
            }
        }
    }

    private Item areaItem(Player player) {
        Item item = Item.of(Material.ITEM_FRAME, "§eProteção da base", "§7" + setup.describeArea(), "",
                "§eClique esquerdo: §fusar 4 / 3 / 4",
                "§eClique direito: §f+1 em tudo",
                "§eShift: §fdesativar",
                "§8/duconfig protecao <frente> <cima> <lados>").flags(ItemFlag.values());

        if (setup.hasArea())
            item.enchantment(Enchantment.DURABILITY, 1);

        item.click(event -> {
            if (event.isShiftClick()) {
                setup.removeArea();

                player.sendMessage(ArenaSetup.PREFIX + "Proteção da base desativada para este mapa.");
            } else if (event.isRightClick()) {
                int[] area = setup.getArea();

                ArenaSetupActions.setArea(player, setup, area[0] + 1, area[1] + 1, area[2] + 1);
            } else
                ArenaSetupActions.setArea(player, setup, 4, 3, 4);

            sound(MenuSound.DONE);
            handle();
        });

        return item;
    }
}
