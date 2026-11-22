package com.minecraft.arcade.duels.setup;

import com.minecraft.arcade.duels.command.MapProcessCommand;
import com.minecraft.arcade.duels.menu.setup.ArenaSetupKitMenu;
import com.minecraft.arcade.duels.menu.setup.ArenaSetupMenu;
import com.minecraft.core.Core;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.arcade.room.team.preset.TeamPresetType;
import com.minecraft.core.arcade.slime.SlimeWorldController;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.hologram.type.client.HologramClient;
import com.minecraft.core.bukkit.manager.list.HologramManager;
import com.minecraft.core.member.list.duels.DuelInventories;
import com.minecraft.core.util.list.bukkit.ColorUtil;
import com.minecraft.core.util.list.serialization.Serialization;
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

import static com.minecraft.arcade.duels.setup.ArenaSetup.PREFIX;

/**
 * Ações do /duconfig, usadas pelo comando, pelo menu e pelos itens da hotbar.
 */
public class ArenaSetupActions {

    private static final String MARKER_TAG = "arena_setup_";

    private static final Map<TeamPresetType, Item> TEAM_ITEMS = new EnumMap<>(TeamPresetType.class);

    private static final Item SPAWN_ITEM = kitItem(Material.ARMOR_STAND, "§eSpawn do time", "§7Marca onde você está, olhando", "§7para a direção que você está olhando.")
            .interact(event -> run(event, (player, setup) -> setTeamPoint(player, setup, setup.getSelectedTeam(), ArenaSetup.TeamPoint.SPAWN)));
    private static final Item BED_ITEM = kitItem(Material.BED, "§eCama do time", "§7Olhe para a cama do time", "§7e clique com o botão direito.")
            .interact(event -> run(event, (player, setup) -> setTeamPoint(player, setup, setup.getSelectedTeam(), ArenaSetup.TeamPoint.BED)));
    private static final Item WAITING_ITEM = kitItem(Material.BEACON, "§eSpawn da espera", "§7Onde os dois jogadores esperam", "§7antes da partida.")
            .interact(event -> run(event, ArenaSetupActions::setWaitingSpawn));
    private static final Item BORDER_ONE_ITEM = kitItem(Material.STAINED_GLASS, "§eBorda 1 do mapa", "§7Marca o bloco onde você está.")
            .interact(event -> run(event, (player, setup) -> setBorder(player, setup, 1)));
    private static final Item BORDER_TWO_ITEM = kitItem(Material.STAINED_GLASS, "§eBorda 2 do mapa", "§7Marca o bloco onde você está.")
            .interact(event -> run(event, (player, setup) -> setBorder(player, setup, 2)));
    private static final Item LIMIT_ITEM = kitItem(Material.LADDER, "§eLimite de construção", "§7Usa a altura (Y) do bloco", "§7onde você está.")
            .interact(event -> run(event, (player, setup) -> setBuildLimit(player, setup, player.getLocation().getBlockY())));
    private static final Item INVENTORY_ITEM = kitItem(Material.DIAMOND_CHESTPLATE, "§eKit do modo", "§7Define o inventário padrão", "§7de cada modo de Duels.")
            .interact(event -> run(event, (player, setup) -> new ArenaSetupKitMenu(player, setup.getMode()).handle()));
    private static final Item MENU_ITEM = kitItem(Material.BOOK, "§bConfigurar arena", "§7Abre o menu com todos os pontos.")
            .interact(event -> run(event, (player, setup) -> new ArenaSetupMenu(player, setup).handle()));

    static {
        for (TeamPresetType team : ArenaSetup.TEAMS) {
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
            player.sendMessage(PREFIX + "§cVocê não está configurando nenhuma arena. Use §e/duconfig criar§c.");
            return;
        }

        action.run(player, setup);
    }

    /* Sessão */

    public static void start(Player player, String mode, String mapName) {
        ArenaSetup setup = ArenaSetup.start(player.getUniqueId(), mode, mapName, player.getWorld());

        player.sendMessage("");
        player.sendMessage(PREFIX + (setup.exists() ? "Editando" : "Criando") + " o mapa §b" + mapName + " §f(" + mode + ") no mundo §b" + player.getWorld().getName() + "§f.");
        player.sendMessage("§7Pontos do time: " + (setup.isBedMode() ? "spawn e cama" : "apenas spawn") + ".");
        player.sendMessage("§7Use os itens da hotbar ou §f/duconfig menu§7. Quando terminar, §f/duconfig salvar§7.");
        player.sendMessage("");

        String template = setup.getTemplate();

        if (!player.getWorld().getName().equalsIgnoreCase(template) && !player.getWorld().getName().startsWith(template + "-"))
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
        player.sendMessage(PREFIX + "Você saiu da configuração do mapa §b" + setup.getMapName() + "§f." + (setup.isChanged() ? " §cAs alterações não salvas foram descartadas." : ""));
    }

    public static void giveKit(Player player, ArenaSetup setup) {
        PlayerInventory inventory = player.getInventory();

        inventory.clear();

        inventory.setItem(0, TEAM_ITEMS.get(setup.getSelectedTeam()));
        inventory.setItem(1, SPAWN_ITEM);

        if (setup.isBedMode())
            inventory.setItem(2, BED_ITEM);

        inventory.setItem(3, WAITING_ITEM);
        inventory.setItem(4, BORDER_ONE_ITEM);
        inventory.setItem(5, BORDER_TWO_ITEM);
        inventory.setItem(6, LIMIT_ITEM);
        inventory.setItem(7, INVENTORY_ITEM);
        inventory.setItem(8, MENU_ITEM);

        player.updateInventory();
    }

    public static void selectTeam(Player player, ArenaSetup setup, TeamPresetType team) {
        setup.setSelectedTeam(team);

        player.getInventory().setItem(0, TEAM_ITEMS.get(team));
        player.sendMessage(PREFIX + "Time selecionado: " + ArenaSetup.display(team) + " §7(" + setup.countTeam(team, true) + "/" + setup.requiredPoints() + " pontos)");
        player.playSound(player.getLocation(), Sound.CLICK, 1F, 1F);
    }

    private static void nextTeam(Player player, ArenaSetup setup) {
        List<TeamPresetType> teams = setup.getTeams();
        int index = teams.indexOf(setup.getSelectedTeam());

        selectTeam(player, setup, teams.get((index + 1) % teams.size()));
    }

    /* Pontos */

    public static void setTeamPoint(Player player, ArenaSetup setup, TeamPresetType team, ArenaSetup.TeamPoint point) {
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
            default:
                location = ArenaSetup.standing(player.getLocation());
                break;
        }

        boolean facing = point.equals(ArenaSetup.TeamPoint.SPAWN);
        String key = point.key(team);
        boolean replaced = setup.has(key);

        setup.set(key, location, facing);

        done(player, setup, ArenaSetup.display(team) + " §8- §f" + point.getName() + " §7" + (replaced ? "atualizado" : "definido") + " em " + ArenaSetup.format(location)
                + " §8(" + setup.countTeam(team, true) + "/" + setup.requiredPoints() + ")");
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
        done(player, setup, "§fAltura máxima de construção: §b" + height);
    }

    public static void setArea(Player player, ArenaSetup setup, int frontAndBack, int up, int side) {
        setup.setArea(frontAndBack, up, side);

        player.sendMessage(PREFIX + "Proteção da base: " + setup.describeArea());
        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1F, 1.4F);
    }

    public static void remove(Player player, ArenaSetup setup, String key) {
        if (!setup.remove(key)) {
            player.sendMessage(PREFIX + "§cO ponto §e" + key + " §cnão existe. Veja com §e/duconfig pontos§c.");
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

    /* Kit global do modo */

    public static void loadKit(Player player, ArcadeCategory category) {
        String base64 = DuelInventories.get(category);

        if (base64 == null) {
            player.sendMessage(PREFIX + "§e" + category.getName() + " §7ainda não tem kit global. Monte o inventário e use §f/duconfig inventario salvar " + category.getName() + "§7.");
            return;
        }

        Serialization.sendInventoryToPlayerFromBase64(player, base64);
        player.updateInventory();

        player.sendMessage(PREFIX + "Kit de §b" + category.getName() + " §fcarregado no seu inventário §7(monte como quiser e salve de novo)§f.");
        player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1F, 1.4F);
    }

    public static void saveKit(Player player, ArcadeCategory category) {
        String base64 = Serialization.serializeInventoryOfPlayer(player);

        if (!DuelInventories.save(category, base64)) {
            player.sendMessage(PREFIX + "§cNão foi possível salvar o kit de §e" + category.getName() + "§c.");
            return;
        }

        player.sendMessage(PREFIX + "§aKit de §b" + category.getName() + " §asalvo: agora é o padrão de todos os jogadores§a.");
        player.sendMessage(PREFIX + "§7Quem customizou o próprio inventário no hub continua com a escolha de cada um.");
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1F, 1F);
    }

    public static void removeKit(Player player, ArcadeCategory category) {
        if (!DuelInventories.remove(category)) {
            player.sendMessage(PREFIX + "§c" + category.getName() + " §cnão tem kit global salvo.");
            return;
        }

        player.sendMessage(PREFIX + "Kit de §b" + category.getName() + " §fremovido §7(o modo volta a usar o kit do jogo)§f.");
    }

    /* Status */

    public static void sendStatus(Player player, ArenaSetup setup) {
        List<String> missing = setup.getMissing();

        player.sendMessage("");
        player.sendMessage("§b§lDUELSCONFIG §f" + setup.getMapName() + " §7(" + setup.getMode() + ")" + (setup.isChanged() ? " §c(não salvo)" : ""));
        player.sendMessage("§7Construção até: §f" + (setup.getBuildLimit() > 0 ? setup.getBuildLimit() : "§cnão definida")
                + " §8| §7Proteção: §f" + setup.describeArea());
        player.sendMessage("§7Template do SlimeWorldManager: §f" + setup.getTemplate());

        StringBuilder teams = new StringBuilder();

        for (TeamPresetType team : setup.getTeams()) {
            int count = setup.countTeam(team, true);

            teams.append(team.getColor()).append(team.getId()).append(count == setup.requiredPoints() ? " §a✔ " : " §c" + count + "/" + setup.requiredPoints() + " ");
        }

        player.sendMessage("§7Times: " + teams.toString().trim());

        if (missing.isEmpty()) {
            player.sendMessage("§a✔ Tudo pronto! Use §f/duconfig salvar§a.");
        } else {
            player.sendMessage("§cFaltando §7(" + missing.size() + ")§c:");

            missing.forEach(line -> player.sendMessage(" §c✘ " + line));
        }

        player.sendMessage("");
    }

    public static void sendPoints(Player player, ArenaSetup setup) {
        player.sendMessage("");
        player.sendMessage("§b§lPONTOS §7(" + setup.getLocations().size() + ") §8- §7/duconfig tp <ponto>");

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
            player.sendMessage(PREFIX + "§eAinda faltam " + missing.size() + " ponto(s). Veja com §f/duconfig status§e.");

        String template = setup.getTemplate();

        if (ConfigManager.getWorldConfig() != null && !ConfigManager.getWorldConfig().getWorlds().containsKey(template))
            player.sendMessage(PREFIX + "§eO mundo §f" + template + " §enão está no SlimeWorldManager. Importe/salve o mapa com esse nome.");

        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1F, 1F);

        if (reload) {
            SlimeWorldController.forgetTemplate(template);
            MapProcessCommand.reloadArcade(player, setup.getMode());
        }
    }

    /* Marcadores */

    public static void refreshMarkers(Player player, ArenaSetup setup) {
        clearMarkers(player);

        HologramManager manager = BukkitCore.getManager().getHologram();

        for (String key : setup.getLocations().keySet()) {
            Location location = setup.getLocation(key);

            if (location == null || location.getWorld() == null || !location.getWorld().equals(player.getWorld())) continue;

            HologramClient hologram = manager.spawnClient(player, MARKER_TAG + key, location.clone().add(0, 2.2, 0));

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

        for (TeamPresetType team : ArenaSetup.TEAMS) {
            for (ArenaSetup.TeamPoint point : ArenaSetup.TeamPoint.values()) {
                if (point.key(team).equals(key))
                    return ArenaSetup.display(team) + " §7" + point.getName();
            }
        }

        return "§f" + key;
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
            default:
                return name.equals(type);
        }
    }
}
