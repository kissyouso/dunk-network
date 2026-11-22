package com.minecraft.arcade.bedwars.menu.setup;

import com.minecraft.arcade.bedwars.setup.ArenaSetup;
import com.minecraft.arcade.bedwars.setup.ArenaSetupActions;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.room.team.preset.TeamPresetType;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.util.list.bukkit.ColorUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.List;

public class ArenaSetupMenu extends Menu {

    private static final int[][] TEAM_SLOTS = {
            {},
            {13},
            {12, 14},
            {11, 13, 15},
            {10, 12, 14, 16},
            {11, 12, 13, 14, 15},
            {10, 11, 12, 14, 15, 16},
            {10, 11, 12, 13, 14, 15, 16},
            {9, 10, 11, 12, 14, 15, 16, 17}
    };

    private final ArenaSetup setup;

    public ArenaSetupMenu(Player player, ArenaSetup setup) {
        super(player, "Arena " + setup.getMapName(), 6);

        this.setup = setup;
    }

    @Override
    public void handle() {
        clear();

        Player player = getPlayer();
        List<String> missing = setup.getMissing();

        addItem(4, Item.of(Material.PAPER, "§2" + setup.getMapName(),
                "§7Modo: §f" + setup.getMode(),
                "§7Mundo: §f" + setup.getWorld().getName(),
                "§7Pontos: §f" + setup.getLocations().size(),
                "",
                missing.isEmpty() ? "§a✔ Pronto para salvar!" : "§cFaltam " + missing.size() + " ponto(s).",
                setup.isChanged() ? "§eAlterações não salvas." : "§7Nada para salvar."));

        /* Times */
        List<TeamPresetType> teams = setup.getTeams();
        int[] slots = TEAM_SLOTS[Math.min(teams.size(), 8)];

        for (int index = 0; index < slots.length; index++) {
            TeamPresetType team = teams.get(index);
            int count = setup.countTeam(team, true), required = ArenaSetup.requiredPoints();

            List<String> lore = new ArrayList<>();

            for (ArenaSetup.TeamPoint point : ArenaSetup.TeamPoint.values())
                lore.add((setup.has(point.key(team)) ? "§a✔ " : point.isRequired() ? "§c✘ " : "§8✘ ") + "§7" + point.getName() + (point.isRequired() ? "" : " §8(opcional)"));

            lore.add("");
            lore.add("§eClique esquerdo: §fpontos do time");
            lore.add("§eClique direito: §fusar na hotbar");

            Item item = Item.of(Material.WOOL, ColorUtil.getIdByColor(team.getColor()),
                    ArenaSetup.display(team) + " §7(" + count + "/" + required + ")", lore).flags(ItemFlag.values());

            if (count == required)
                item.enchantment(Enchantment.DURABILITY, 1);

            item.click(event -> {
                if (event.isRightClick()) {
                    ArenaSetupActions.selectTeam(player, setup, team);
                    close();
                    return;
                }

                new ArenaSetupTeamMenu(player, setup, team, this).handle();
            });

            addItem(slots[index], item);
        }

        /* Pontos gerais */
        addItem(28, point(Material.BEACON, "spawn", "§eSpawn da espera", "§7Onde os jogadores esperam a partida."));
        addItem(29, point(Material.STAINED_GLASS, "map_limit_pos1", "§eBorda 1", "§7Um canto do mapa (vale o bloco onde você está)."));
        addItem(30, point(Material.STAINED_GLASS, "map_limit_pos2", "§eBorda 2", "§7O canto oposto do mapa."));

        int limit = setup.getBuildLimit();

        addItem(31, Item.of(Material.LADDER, "§eAltura de construção", "§7Atual: " + (limit > 0 ? "§f" + limit : "§cnão definida"),
                "", "§eClique esquerdo: §fusar sua altura (§f" + getPlayer().getLocation().getBlockY() + "§f)",
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

        addItem(32, ore(Material.DIAMOND_BLOCK, "diamond", "§bGeradores de diamante"));
        addItem(33, ore(Material.EMERALD_BLOCK, "emerald", "§aGeradores de esmeralda"));

        addItem(34, Item.of(Material.ITEM_FRAME, "§eÁrea das ilhas", "§7Proteção da base: " + setup.describeArea("area_protection"),
                "§7Área da ilha: " + setup.describeArea("island_area"), "", "§7Mude com §f/config protecao §7e §f/config ilha§7."));

        /* Rankings da sala de espera */
        int slot = 38;

        for (String type : ArenaSetup.LEADERBOARDS)
            addItem(slot++, point(Material.SIGN, "leaderboard_" + type, "§6Ranking " + type, "§7Holograma na sala de espera. §8(opcional)"));

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
                "§7os mapas do BedWars.", "", missing.isEmpty() ? "§a✔ Tudo pronto!" : "§eFaltam " + missing.size() + " ponto(s), mas dá para salvar.").click(event -> {
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
        boolean defined = setup.has(key);

        Item item = Item.of(material, (defined ? "§a✔ " : "§c✘ ") + name, description, "",
                "§eClique esquerdo: §fdefinir aqui",
                defined ? "§eClique direito: §fteleportar" : "",
                defined ? "§eShift + direito: §fremover" : "").flags(ItemFlag.values());

        item.click(event -> {
            if (event.isRightClick()) {
                if (!defined) {
                    sound(MenuSound.ERROR);
                    return;
                }

                if (event.isShiftClick()) {
                    ArenaSetupActions.remove(getPlayer(), setup, key);
                    handle();
                } else {
                    close();
                    ArenaSetupActions.teleport(getPlayer(), setup, key);
                }
                return;
            }

            define(key);
            sound(MenuSound.DONE);
            handle();
        });

        return item;
    }

    private void define(String key) {
        Player player = getPlayer();

        if (key.equals("spawn")) {
            ArenaSetupActions.setWaitingSpawn(player, setup);
        } else if (key.startsWith("map_limit_pos")) {
            ArenaSetupActions.setBorder(player, setup, Integer.parseInt(key.substring("map_limit_pos".length())));
        } else if (key.startsWith("leaderboard_")) {
            ArenaSetupActions.setLeaderboard(player, setup, key.substring("leaderboard_".length()));
        }
    }

    private Item ore(Material material, String ore, String name) {
        String prefix = "ore_generator_" + ore + "_";
        List<String> keys = setup.getKeys(prefix);
        List<String> lore = new ArrayList<>();

        lore.add("§7Quantidade: §f" + keys.size());

        for (String key : keys)
            lore.add(" §8• §7#" + key.substring(prefix.length()) + " " + com.minecraft.arcade.bedwars.setup.ArenaSetup.format(setup.getLocation(key)));

        lore.add("");
        lore.add("§eClique esquerdo: §fadicionar aqui");

        if (!keys.isEmpty()) {
            lore.add("§eClique direito: §fteleportar ao último");
            lore.add("§eShift + direito: §fremover o último");
        }

        return Item.of(material, name, lore).click(event -> clickOre(event, ore, keys));
    }

    private void clickOre(InventoryClickEvent event, String ore, List<String> keys) {
        Player player = getPlayer();

        if (event.isRightClick()) {
            if (keys.isEmpty()) {
                sound(MenuSound.ERROR);
                return;
            }

            String last = keys.get(keys.size() - 1);

            if (event.isShiftClick()) {
                ArenaSetupActions.remove(player, setup, last);
                handle();
            } else {
                close();
                ArenaSetupActions.teleport(player, setup, last);
            }
            return;
        }

        ArenaSetupActions.addOre(player, setup, ore);
        handle();
    }
}
