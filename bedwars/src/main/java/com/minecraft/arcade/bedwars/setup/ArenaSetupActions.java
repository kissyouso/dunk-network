package com.minecraft.arcade.bedwars.setup;

import com.minecraft.arcade.bedwars.command.MapProcessCommand;
import com.minecraft.arcade.bedwars.menu.setup.ArenaSetupMenu;
import com.minecraft.core.Core;
import com.minecraft.core.bukkit.command.structure.BukkitCommandSender;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.room.team.preset.TeamPresetType;
import com.minecraft.core.arcade.slime.SlimeWorldController;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.hologram.type.client.HologramClient;
import com.minecraft.core.bukkit.manager.list.HologramManager;
import com.minecraft.core.util.list.bukkit.ColorUtil;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.util.*;

import static com.minecraft.arcade.bedwars.setup.ArenaSetup.PREFIX;

/**
 * Ações do /config, usadas pelo comando, pelo menu e pelos itens da hotbar.
 */
public class ArenaSetupActions {

    private static final String MARKER_TAG = "arena_setup_";

    private static final Map<TeamPresetType, Item> TEAM_ITEMS = new EnumMap<>(TeamPresetType.class);

    private static final Item BED_ITEM = kitItem(Material.BED, "§eCama do time", "§7Olhe para a cama do time", "§7e clique com o botão direito.")
            .interact(event -> run(event, (player, setup) -> setTeamPoint(player, setup, setup.getSelectedTeam(), ArenaSetup.TeamPoint.BED)));
    private static final Item SPAWN_ITEM = kitItem(Material.ARMOR_STAND, "§eSpawn do time", "§7Marca onde você está, olhando", "§7para a direção que você está olhando.")
            .interact(event -> run(event, (player, setup) -> setTeamPoint(player, setup, setup.getSelectedTeam(), ArenaSetup.TeamPoint.SPAWN)));
    private static final Item GENERATOR_ITEM = kitItem(Material.IRON_INGOT, "§eGerador do time", "§7Marca o bloco onde você está.")
            .interact(event -> run(event, (player, setup) -> setTeamPoint(player, setup, setup.getSelectedTeam(), ArenaSetup.TeamPoint.GENERATOR)));
    private static final Item SHOP_ITEM = kitItem(Material.EMERALD, "§eLoja do time", "§7Marca onde o vendedor fica,", "§7olhando para onde você olha.")
            .interact(event -> run(event, (player, setup) -> setTeamPoint(player, setup, setup.getSelectedTeam(), ArenaSetup.TeamPoint.SHOP)));
    private static final Item UPGRADE_ITEM = kitItem(Material.ENCHANTMENT_TABLE, "§eMelhorias do time", "§7Marca onde o NPC de melhorias fica,", "§7olhando para onde você olha.")
            .interact(event -> run(event, (player, setup) -> setTeamPoint(player, setup, setup.getSelectedTeam(), ArenaSetup.TeamPoint.UPGRADE)));
    private static final Item DIAMOND_ITEM = kitItem(Material.DIAMOND_BLOCK, "§bGerador de diamante", "§7Adiciona um gerador no bloco onde você está.")
            .interact(event -> run(event, (player, setup) -> addOre(player, setup, "diamond")));
    private static final Item EMERALD_ITEM = kitItem(Material.EMERALD_BLOCK, "§aGerador de esmeralda", "§7Adiciona um gerador no bloco onde você está.")
            .interact(event -> run(event, (player, setup) -> addOre(player, setup, "emerald")));
    private static final Item MENU_ITEM = kitItem(Material.BOOK, "§2Configurar arena", "§7Abre o menu com todos os pontos.")
            .interact(event -> run(event, (player, setup) -> new ArenaSetupMenu(player, setup).handle()));

    static {
        for (TeamPresetType team : TeamPresetType.values()) {
            TEAM_ITEMS.put(team, Item.of(Material.WOOL, ColorUtil.getIdByColor(team.getColor()), "§fTime: " + ArenaSetup.display(team),
                            "§7Os itens da hotbar marcam os", "§7pontos deste time.", "", "§eClique para trocar de time!")
                    .interact(event -> run(event, ArenaSetupActions::nextTeam)));
        }
    }

    private interface Action {
        void run(Player player, ArenaSetup setup);
    }

    private static Item kitItem(Material material, String name, String... lore) {
        List<String> lines = new ArrayList<>(Arrays.asList(lore));

        lines.add("");
        lines.add("§eClique com o botão direito!");

        return Item.of(material, name, lines);
    }

    private static void run(PlayerInteractEvent event, Action action) {
        event.setCancelled(true);

        Player player = event.getPlayer();
        ArenaSetup setup = ArenaSetup.of(player.getUniqueId());

        if (setup == null) {
            player.sendMessage(PREFIX + "§cVocê não está configurando nenhuma arena. Use §e/config criar§c.");
            return;
        }

        action.run(player, setup);
    }

    /* Sessão */

    public static void start(Player player, String mode, String mapName) {
        ArenaSetup setup = ArenaSetup.start(player.getUniqueId(), mode, mapName, player.getWorld());

        player.sendMessage("");
        player.sendMessage(PREFIX + (setup.exists() ? "Editando" : "Criando") + " o mapa §2" + mapName + " §f(" + mode + ") no mundo §2" + player.getWorld().getName() + "§f.");
        player.sendMessage("§7Times deste modo: " + teamsLine(setup));
        player.sendMessage("§7Use os itens da hotbar ou §f/config menu§7. Quando terminar, §f/config salvar§7.");
        player.sendMessage("");

        String template = mode.toLowerCase() + "-" + mapName.toLowerCase();

        if (!player.getWorld().getName().equalsIgnoreCase(template))
            player.sendMessage(PREFIX + "§eAviso: §7as arenas usam o mundo §f" + template + " §7do SlimeWorldManager. Salve este mundo com esse nome.");

        giveKit(player, setup);
        refreshMarkers(player, setup);
    }

    public static void stop(Player player) {
        ArenaSetup setup = ArenaSetup.stop(player.getUniqueId());

        clearMarkers(player);

        if (setup == null) {
            player.sendMessage(PREFIX + "§cVocê não está configurando nenhuma arena.");
            return;
        }

        player.getInventory().clear();
        player.sendMessage(PREFIX + "Você saiu da configuração do mapa §2" + setup.getMapName() + "§f." + (setup.isChanged() ? " §cAs alterações não salvas foram descartadas." : ""));
    }

    public static void giveKit(Player player, ArenaSetup setup) {
        PlayerInventory inventory = player.getInventory();

        inventory.clear();

        inventory.setItem(0, TEAM_ITEMS.get(setup.getSelectedTeam()));
        inventory.setItem(1, SPAWN_ITEM);
        inventory.setItem(2, BED_ITEM);
        inventory.setItem(3, GENERATOR_ITEM);
        inventory.setItem(4, SHOP_ITEM);
        inventory.setItem(5, UPGRADE_ITEM);
        inventory.setItem(6, DIAMOND_ITEM);
        inventory.setItem(7, EMERALD_ITEM);
        inventory.setItem(8, MENU_ITEM);

        player.updateInventory();
    }

    public static void selectTeam(Player player, ArenaSetup setup, TeamPresetType team) {
        setup.setSelectedTeam(team);

        player.getInventory().setItem(0, TEAM_ITEMS.get(team));
        player.sendMessage(PREFIX + "Time selecionado: " + ArenaSetup.display(team) + " §7(" + setup.countTeam(team, true) + "/" + ArenaSetup.requiredPoints() + " pontos)");
        player.playSound(player.getLocation(), Sound.CLICK, 1F, 1F);
    }

    private static void nextTeam(Player player, ArenaSetup setup) {
        List<TeamPresetType> teams = setup.getTeams();
        int index = teams.indexOf(setup.getSelectedTeam());

        selectTeam(player, setup, teams.get((index + 1) % teams.size()));
    }

    /* Pontos */

    public static void setTeamPoint(Player player, ArenaSetup setup, TeamPresetType team, ArenaSetup.TeamPoint point) {
        if (!setup.getTeams().contains(team)) {
            player.sendMessage(PREFIX + "§cO time " + ArenaSetup.display(team) + " §cnão é usado neste modo.");
            return;
        }

        Location location;

        switch (point) {
            case BED: {
                Block bed = findBlock(player, "BED");

                if (bed == null) {
                    player.sendMessage(PREFIX + "§cOlhe para a cama do time (ou fique do lado dela).");
                    return;
                }

                location = ArenaSetup.blockCenter(bed, 0.5);
                break;
            }
            case CHEST:
            case ENDER_CHEST: {
                Block chest = findBlock(player, point == ArenaSetup.TeamPoint.CHEST ? "CHEST" : "ENDER_CHEST");

                if (chest == null) {
                    player.sendMessage(PREFIX + "§cOlhe para o " + (point == ArenaSetup.TeamPoint.CHEST ? "baú" : "baú do fim") + " do time.");
                    return;
                }

                /* Os hologramas do baú usam o canto do bloco */
                location = chest.getLocation();
                break;
            }
            case GENERATOR:
                location = ArenaSetup.blockCenter(player.getLocation().getBlock(), 0);
                break;
            default:
                location = ArenaSetup.standing(player.getLocation());
                break;
        }

        boolean facing = point == ArenaSetup.TeamPoint.SPAWN || point == ArenaSetup.TeamPoint.SHOP || point == ArenaSetup.TeamPoint.UPGRADE;
        String key = point.key(team);
        boolean replaced = setup.has(key);

        setup.set(key, location, facing);

        done(player, setup, ArenaSetup.display(team) + " §8- §f" + point.getName() + " §7" + (replaced ? "atualizado" : "definido") + " em " + ArenaSetup.format(location)
                + " §8(" + setup.countTeam(team, true) + "/" + ArenaSetup.requiredPoints() + ")");
    }

    public static void setWaitingSpawn(Player player, ArenaSetup setup) {
        Location location = ArenaSetup.standing(player.getLocation());

        setup.set("spawn", location, true);
        done(player, setup, "§fSpawn da sala de espera definido em " + ArenaSetup.format(location));
    }

    public static void setBorder(Player player, ArenaSetup setup, int corner) {
        Location location = ArenaSetup.blockCenter(player.getLocation().getBlock(), 0);

        setup.set("map_limit_pos" + corner, location, false);
        done(player, setup, "§fBorda " + corner + " do mapa definida em " + ArenaSetup.format(location));
    }

    public static void setBuildLimit(Player player, ArenaSetup setup, int height) {
        setup.setBuildLimit(height);
        done(player, setup, "§fAltura máxima de construção: §2" + height);
    }

    public static void addOre(Player player, ArenaSetup setup, String ore) {
        String prefix = "ore_generator_" + ore + "_";
        Location location = ArenaSetup.blockCenter(player.getLocation().getBlock(), 0);

        for (String key : setup.getKeys(prefix)) {
            Location other = setup.getLocation(key);

            if (other != null && other.distanceSquared(location) < 4) {
                player.sendMessage(PREFIX + "§cJá existe um gerador aqui (" + key + "). Use §e/config remover " + key + "§c.");
                return;
            }
        }

        int index = setup.nextIndex(prefix);

        setup.set(prefix + index, location, false);
        done(player, setup, (ore.equals("diamond") ? "§bDiamante" : "§aEsmeralda") + " #" + index + " §fadicionado em " + ArenaSetup.format(location));
    }

    public static void setLeaderboard(Player player, ArenaSetup setup, String type) {
        Location location = ArenaSetup.standing(player.getLocation());

        setup.set("leaderboard_" + type, location, true);
        done(player, setup, "§fRanking §6" + type + " §fdefinido em " + ArenaSetup.format(location));
    }

    public static void remove(Player player, ArenaSetup setup, String key) {
        if (!setup.remove(key)) {
            player.sendMessage(PREFIX + "§cO ponto §e" + key + " §cnão existe. Veja com §e/config pontos§c.");
            return;
        }

        done(player, setup, "§fPonto " + describe(key) + " §fremovido.");
    }

    public static void teleport(Player player, ArenaSetup setup, String key) {
        Location location = setup.getLocation(key);

        if (location == null) {
            player.sendMessage(PREFIX + "§cO ponto §e" + key + " §cainda não foi definido.");
            return;
        }

        player.teleport(location.getBlock().getType().isSolid() ? location.clone().add(0, 1, 0) : location);
        player.sendMessage(PREFIX + "Teleportado para " + describe(key) + "§f.");
    }

    private static void done(Player player, ArenaSetup setup, String message) {
        player.sendMessage(PREFIX + message);
        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1F, 1.4F);

        refreshMarkers(player, setup);
    }

    /* Status */

    public static void sendStatus(Player player, ArenaSetup setup) {
        List<String> missing = setup.getMissing();

        player.sendMessage("");
        player.sendMessage("§2§lCONFIG §f" + setup.getMapName() + " §7(" + setup.getMode() + ")" + (setup.isChanged() ? " §c(não salvo)" : ""));
        player.sendMessage("§7Construção até: §f" + (setup.getBuildLimit() > 0 ? setup.getBuildLimit() : "§cnão definida")
                + " §8| §7Diamantes: §b" + setup.getKeys("ore_generator_diamond_").size()
                + " §8| §7Esmeraldas: §a" + setup.getKeys("ore_generator_emerald_").size());

        StringBuilder teams = new StringBuilder();

        for (TeamPresetType team : setup.getTeams()) {
            int count = setup.countTeam(team, true);

            teams.append(team.getColor()).append(team.getId()).append(count == ArenaSetup.requiredPoints() ? " §a✔ " : " §c" + count + "/" + ArenaSetup.requiredPoints() + " ");
        }

        player.sendMessage("§7Times: " + teams.toString().trim());

        if (missing.isEmpty()) {
            player.sendMessage("§a✔ Tudo pronto! Use §f/config salvar§a.");
        } else {
            player.sendMessage("§cFaltando §7(" + missing.size() + ")§c:");

            missing.stream().limit(12).forEach(line -> player.sendMessage(" §c✘ " + line));

            if (missing.size() > 12)
                player.sendMessage(" §7... e mais " + (missing.size() - 12) + ".");
        }

        player.sendMessage("");
    }

    public static void sendPoints(Player player, ArenaSetup setup) {
        player.sendMessage("");
        player.sendMessage("§2§lPONTOS §7(" + setup.getLocations().size() + ") §8- §7/config tp <ponto>");

        for (String key : setup.getLocations().keySet())
            player.sendMessage(" §8• " + describe(key) + " §8(" + key + ") " + ArenaSetup.format(setup.getLocation(key)));

        player.sendMessage("");
    }

    public static void save(Player player, ArenaSetup setup, boolean reload) {
        List<String> missing = setup.getMissing();

        try {
            File file = setup.save();

            player.sendMessage(PREFIX + "§aMapa salvo em §f" + file.getParentFile().getName() + "/config.json§a.");
        } catch (Exception exception) {
            player.sendMessage(PREFIX + "§cNão foi possível salvar: " + exception.getMessage());
            Core.getLogger().warning("[Arena] Erro ao salvar " + setup.getMapName() + ": " + exception.getMessage());
            return;
        }

        if (!missing.isEmpty())
            player.sendMessage(PREFIX + "§eAinda faltam " + missing.size() + " ponto(s). Veja com §f/config status§e.");

        String template = setup.getMode() + "-" + setup.getMapName().toLowerCase();

        if (ConfigManager.getWorldConfig() != null && !ConfigManager.getWorldConfig().getWorlds().containsKey(template))
            player.sendMessage(PREFIX + "§eO mundo §f" + template + " §enão está no SlimeWorldManager. Importe/salve o mapa com esse nome.");

        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1F, 1F);

        if (reload) {
            SlimeWorldController.forgetTemplate(template);
            MapProcessCommand.reloadArcade(new BukkitCommandSender(player));
        }
    }

    /* Marcadores */

    public static void refreshMarkers(Player player, ArenaSetup setup) {
        clearMarkers(player);

        HologramManager manager = BukkitCore.getManager().getHologram();

        for (String key : setup.getLocations().keySet()) {
            Location location = setup.getLocation(key);

            if (location == null || location.getWorld() == null || !location.getWorld().equals(player.getWorld())) continue;

            HologramClient hologram = manager.spawnClient(player, MARKER_TAG + key, location.clone().add(key.startsWith("chest_") || key.startsWith("enderchest_") ? 0.5 : 0, 2.2, key.startsWith("chest_") || key.startsWith("enderchest_") ? 0.5 : 0));

            hologram.setText(Arrays.asList(describe(key), "§7" + key));
            hologram.spawnTo(player);
        }
    }

    public static void clearMarkers(Player player) {
        HologramManager manager = BukkitCore.getManager().getHologram();

        for (HologramClient client : new ArrayList<>(manager.getClients(player))) {
            if (client.getTag().startsWith(MARKER_TAG))
                manager.removeClient(client);
        }
    }

    /* Utilidades */

    public static String describe(String key) {
        if (key.equals("spawn")) return "§eSpawn da espera";
        if (key.startsWith("map_limit_pos")) return "§eBorda " + key.substring("map_limit_pos".length());
        if (key.startsWith("ore_generator_diamond_")) return "§bDiamante #" + key.substring("ore_generator_diamond_".length());
        if (key.startsWith("ore_generator_emerald_")) return "§aEsmeralda #" + key.substring("ore_generator_emerald_".length());
        if (key.startsWith("leaderboard_")) return "§6Ranking " + key.substring("leaderboard_".length());

        for (TeamPresetType team : TeamPresetType.values()) {
            for (ArenaSetup.TeamPoint point : ArenaSetup.TeamPoint.values()) {
                if (point.key(team).equals(key))
                    return ArenaSetup.display(team) + " §7" + point.getName();
            }
        }

        return "§f" + key;
    }

    private static String teamsLine(ArenaSetup setup) {
        StringBuilder builder = new StringBuilder();

        for (TeamPresetType team : setup.getTeams())
            builder.append(ArenaSetup.display(team)).append("§7, ");

        return builder.substring(0, builder.length() - 4);
    }

    /**
     * Bloco para onde o jogador está olhando ou, se não for o tipo pedido, o mais próximo dele (até 3 blocos).
     */
    private static Block findBlock(Player player, String type) {
        Block target = player.getTargetBlock((Set<Material>) null, 6);

        if (target != null && matches(target, type)) return target;

        Block feet = player.getLocation().getBlock();
        Block closest = null;
        double distance = Double.MAX_VALUE;

        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = feet.getRelative(x, y, z);

                    if (!matches(block, type)) continue;

                    double current = block.getLocation().distanceSquared(feet.getLocation());

                    if (current < distance) {
                        distance = current;
                        closest = block;
                    }
                }
            }
        }

        return closest;
    }

    private static boolean matches(Block block, String type) {
        String name = block.getType().name();

        switch (type) {
            case "BED":
                return name.equals("BED_BLOCK");
            case "CHEST":
                return name.equals("CHEST") || name.equals("TRAPPED_CHEST");
            default:
                return name.equals(type);
        }
    }
}
