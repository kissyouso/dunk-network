package com.minecraft.arcade.duels.command;

import com.minecraft.arcade.duels.menu.setup.ArenaSetupKitMenu;
import com.minecraft.arcade.duels.menu.setup.ArenaSetupMenu;
import com.minecraft.arcade.duels.setup.ArenaSetup;
import com.minecraft.arcade.duels.setup.ArenaSetupActions;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.arcade.room.team.preset.TeamPresetType;
import com.minecraft.core.bukkit.command.structure.BukkitCommandContext;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.annotation.Command;
import com.minecraft.core.command.annotation.Completer;
import com.minecraft.core.member.list.duels.DuelInventories;
import com.minecraft.core.server.type.ServerType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.minecraft.arcade.duels.setup.ArenaSetup.PREFIX;

/**
 * Configuração dos mapas de Duels dentro do jogo.
 * Fluxo: /duconfig criar <modo> <mapa> → marcar os pontos (hotbar, menu ou comandos) → /duconfig salvar.
 */
public class ArenaSetupCommand implements CommandInheritor {

    private static final List<String> SUB_COMMANDS = Arrays.asList("criar", "editar", "menu", "itens", "time", "spawn", "borda", "limite",
            "protecao", "inventario", "remover", "tp", "pontos", "status", "salvar", "sair");

    @Command(name = "duconfig", aliases = {"duelsconfig", "dusetup"}, rank = RankType.ADMIN)
    public void arena(BukkitCommandContext context) {
        Player player = context.getPlayer();
        String[] args = context.getArgs();

        if (args.length == 0) {
            ArenaSetup setup = ArenaSetup.of(player.getUniqueId());

            if (setup != null)
                new ArenaSetupMenu(player, setup).handle();
            else
                help(player);
            return;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("criar") || sub.equals("editar") || sub.equals("create") || sub.equals("edit")) {
            start(player, args, sub.startsWith("e"));
            return;
        }

        if (sub.equals("ajuda") || sub.equals("help")) {
            help(player);
            return;
        }

        /* Kits dos modos não dependem de uma sessão de setup aberta. */
        if (sub.equals("inventario") || sub.equals("kit")) {
            inventory(player, args);
            return;
        }

        ArenaSetup setup = ArenaSetup.of(player.getUniqueId());

        if (setup == null) {
            player.sendMessage(PREFIX + "§cVocê não está configurando nenhuma arena. Use §e/duconfig criar <modo> <mapa>§c.");
            return;
        }

        if (!player.getWorld().equals(setup.getWorld()) && !sub.equals("sair") && !sub.equals("salvar") && !sub.equals("status") && !sub.equals("pontos")) {
            player.sendMessage(PREFIX + "§cVá para o mundo §e" + setup.getWorld().getName() + " §cpara marcar os pontos.");
            return;
        }

        /* /duconfig <time> <ponto> */
        TeamPresetType team = TeamPresetType.of(sub);

        if (team != null && !sub.equals("time")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + "§cUso: /duconfig " + team.getName().toLowerCase() + " <" + pointAliases() + ">");
                return;
            }

            ArenaSetup.TeamPoint point = ArenaSetup.TeamPoint.of(args[1]);

            if (point == null) {
                player.sendMessage(PREFIX + "§cPonto inválido. Use: §e" + pointAliases().replace("|", "§c, §e"));
                return;
            }

            ArenaSetupActions.setTeamPoint(player, setup, team, point);
            return;
        }

        switch (sub) {
            case "menu":
                new ArenaSetupMenu(player, setup).handle();
                break;
            case "itens":
            case "kit_setup":
                ArenaSetupActions.giveKit(player, setup);
                player.sendMessage(PREFIX + "Itens de configuração recebidos.");
                break;
            case "time": {
                TeamPresetType selected = args.length > 1 ? TeamPresetType.of(args[1]) : null;

                if (selected == null || !setup.getTeams().contains(selected)) {
                    player.sendMessage(PREFIX + "§cUso: /duconfig time <" + teamNames(setup) + ">");
                    return;
                }

                ArenaSetupActions.selectTeam(player, setup, selected);
                break;
            }
            case "spawn":
            case "espera":
                if (args.length > 1 && (args[1].equalsIgnoreCase("time") || TeamPresetType.of(args[1]) != null)) {
                    ArenaSetupActions.setTeamPoint(player, setup, setup.getSelectedTeam(), ArenaSetup.TeamPoint.SPAWN);
                    break;
                }

                ArenaSetupActions.setWaitingSpawn(player, setup);
                break;
            case "cama":
            case "bed":
                ArenaSetupActions.setTeamPoint(player, setup, setup.getSelectedTeam(), ArenaSetup.TeamPoint.BED);
                break;
            case "borda":
            case "pos": {
                if (args.length < 2 || !(args[1].equals("1") || args[1].equals("2"))) {
                    player.sendMessage(PREFIX + "§cUso: /duconfig borda <1|2> §7(fique em um canto do mapa)");
                    return;
                }

                ArenaSetupActions.setBorder(player, setup, Integer.parseInt(args[1]));
                break;
            }
            case "limite": {
                Integer height = args.length > 1 ? parse(args[1]) : Integer.valueOf(player.getLocation().getBlockY());

                if (height == null || height <= 0 || height > 255) {
                    player.sendMessage(PREFIX + "§cUso: /duconfig limite [altura] §7(sem altura usa a sua)");
                    return;
                }

                ArenaSetupActions.setBuildLimit(player, setup, height);
                break;
            }
            case "protecao":
            case "protection": {
                if (args.length < 4) {
                    player.sendMessage(PREFIX + "§cUso: /duconfig protecao <frente/trás> <cima> <lados>");
                    player.sendMessage(PREFIX + "§7Atual: " + setup.describeArea());
                    return;
                }

                Integer front = parse(args[1]), up = parse(args[2]), side = parse(args[3]);

                if (front == null || up == null || side == null || front < 0 || up < 0 || side < 0) {
                    player.sendMessage(PREFIX + "§cUse números maiores que zero.");
                    return;
                }

                ArenaSetupActions.setArea(player, setup, front, up, side);
                break;
            }
            case "remover":
            case "remove": {
                if (args.length < 2) {
                    player.sendMessage(PREFIX + "§cUso: /duconfig remover <ponto> §7(veja em /duconfig pontos)");
                    return;
                }

                ArenaSetupActions.remove(player, setup, resolve(setup, args[1], args.length > 2 ? args[2] : null));
                break;
            }
            case "tp": {
                if (args.length < 2) {
                    player.sendMessage(PREFIX + "§cUso: /duconfig tp <ponto> §7(veja em /duconfig pontos)");
                    return;
                }

                ArenaSetupActions.teleport(player, setup, resolve(setup, args[1], args.length > 2 ? args[2] : null));
                break;
            }
            case "pontos":
            case "lista":
                ArenaSetupActions.sendPoints(player, setup);
                break;
            case "status":
                ArenaSetupActions.sendStatus(player, setup);
                break;
            case "salvar":
            case "save":
                ArenaSetupActions.save(player, setup, !(args.length > 1 && args[1].equalsIgnoreCase("sem-recarregar")));
                break;
            case "sair":
            case "cancelar":
                ArenaSetupActions.stop(player);
                break;
            default:
                help(player);
                break;
        }
    }

    /* Kits dos modos */

    private void inventory(Player player, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "menu";

        if (action.equals("menu") || action.equals("listar")) {
            if (action.equals("listar")) {
                player.sendMessage("");
                player.sendMessage("§b§lKITS DOS MODOS §8» §7plugins/duels/inventarios");

                for (ArcadeCategory category : ArcadeCategory.of(ServerType.DUELS)) {
                    String base64 = DuelInventories.get(category);

                    player.sendMessage(" §8• " + (base64 == null ? "§c✘ " : "§a✔ ") + "§f" + category.getName()
                            + (base64 == null ? " §7(usando o kit do jogo)" : " §8(" + category.name() + ")"));
                }

                player.sendMessage("");
                return;
            }

            ArenaSetup setup = ArenaSetup.of(player.getUniqueId());

            new ArenaSetupKitMenu(player, setup != null ? setup.getMode() : null).handle();
            return;
        }

        if (action.equals("recarregar")) {
            DuelInventories.reload();
            player.sendMessage(PREFIX + "Kits recarregados de §eplugins/duels/inventarios§f.");
            return;
        }

        if (args.length < 3) {
            player.sendMessage(PREFIX + "§cUso: /duconfig inventario <editar|salvar|remover> <modo>");
            player.sendMessage(PREFIX + "§7Modos: §f" + ArcadeCategory.of(ServerType.DUELS).stream().map(ArcadeCategory::getName).collect(Collectors.joining("§7, §f")));
            return;
        }

        ArcadeCategory category = readCategory(args[2]);

        if (category == null) {
            player.sendMessage(PREFIX + "§cModo §e" + args[2] + " §cnão existe em Duels.");
            return;
        }

        switch (action) {
            case "editar":
            case "carregar":
            case "load":
                ArenaSetupActions.loadKit(player, category);
                break;
            case "salvar":
            case "save":
                ArenaSetupActions.saveKit(player, category);
                break;
            case "remover":
            case "remove":
                ArenaSetupActions.removeKit(player, category);
                break;
            default:
                player.sendMessage(PREFIX + "§cUso: /duconfig inventario <editar|salvar|remover|listar|recarregar> [modo]");
                break;
        }
    }

    /**
     * Aceita "bedfight", "Bed Fight" ou "DUELS_BED_FIGHT".
     */
    private static ArcadeCategory readCategory(String name) {
        String flat = name.toLowerCase().replace(" ", "").replace("_", "").replace("-", "");

        return ArcadeCategory.of(ServerType.DUELS).stream()
                .filter(category -> category.getName().replace(" ", "").equalsIgnoreCase(flat)
                        || category.name().toLowerCase().replace("duels_", "").equals(flat)
                        || category.name().equalsIgnoreCase(name.toLowerCase()))
                .findFirst().orElse(null);
    }

    private void start(Player player, String[] args, boolean edit) {
        List<String> modes = ArenaSetup.getModes();

        if (args.length < 2 || !modes.contains(args[1].toLowerCase())) {
            player.sendMessage(PREFIX + "§cUso: /duconfig " + (edit ? "editar" : "criar") + " <modo> " + (edit ? "<mapa>" : "[mapa]"));
            player.sendMessage(PREFIX + "§7Modos: §f" + String.join("§7, §f", modes));
            return;
        }

        String mode = args[1].toLowerCase();
        String mapName = args.length > 2 ? args[2] : player.getWorld().getName();

        /* Mundo "bedfight-pascoa-3" vira o mapa "pascoa" */
        if (mapName.toLowerCase().startsWith(mode + "-"))
            mapName = mapName.substring(mode.length() + 1);

        while (!mapName.isEmpty() && mapName.charAt(mapName.length() - 1) >= '0' && mapName.charAt(mapName.length() - 1) <= '9')
            mapName = mapName.substring(0, mapName.length() - 1);

        if (mapName.endsWith("-")) mapName = mapName.substring(0, mapName.length() - 1);

        if (!mapName.matches("[A-Za-z0-9_-]{1,32}")) {
            player.sendMessage(PREFIX + "§cNome de mapa inválido. Use letras, números, _ ou -.");
            return;
        }

        if (edit && !ArenaSetup.getMaps(mode).contains(mapName)) {
            player.sendMessage(PREFIX + "§cO mapa §e" + mapName + " §cnão existe no modo §e" + mode + "§c.");

            List<String> maps = ArenaSetup.getMaps(mode);

            if (!maps.isEmpty())
                player.sendMessage(PREFIX + "§7Mapas: §f" + String.join("§7, §f", maps));
            return;
        }

        ArenaSetup current = ArenaSetup.of(player.getUniqueId());

        if (current != null && current.isChanged()) {
            player.sendMessage(PREFIX + "§cVocê tem alterações não salvas em §e" + current.getMapName() + "§c. Use §e/duconfig salvar §cou §e/duconfig sair§c.");
            return;
        }

        ArenaSetupActions.start(player, mode, mapName);
    }

    /**
     * Aceita o nome do ponto ("red_bed") ou o formato do comando ("vermelho cama").
     */
    private static String resolve(ArenaSetup setup, String first, String second) {
        TeamPresetType team = TeamPresetType.of(first);

        if (team != null && second != null) {
            ArenaSetup.TeamPoint point = ArenaSetup.TeamPoint.of(second);

            if (point != null) return point.key(team);
        }

        switch (first.toLowerCase()) {
            case "espera":
                return "spawn";
            case "borda":
                return "map_limit_pos" + (second != null ? second : "1");
            default:
                return first.toLowerCase();
        }
    }

    private static Integer parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String pointAliases() {
        return Arrays.stream(ArenaSetup.TeamPoint.values()).map(ArenaSetup.TeamPoint::getAlias).collect(Collectors.joining("|"));
    }

    private static String teamNames(ArenaSetup setup) {
        return setup.getTeams().stream().map(team -> team.getName().toLowerCase()).collect(Collectors.joining("|"));
    }

    private void help(Player player) {
        player.sendMessage("");
        player.sendMessage("§b§lDUELSCONFIG §8- §7Configuração de mapas do Duels");
        player.sendMessage("§e/duconfig criar <modo> [mapa] §8- §7começa no mundo atual");
        player.sendMessage("§e/duconfig editar <modo> <mapa> §8- §7edita um mapa salvo");
        player.sendMessage("§e/duconfig menu §8- §7menu com todos os pontos");
        player.sendMessage("§e/duconfig itens §8- §7itens de configuração na hotbar");
        player.sendMessage("§e/duconfig <time> <" + pointAliases() + ">");
        player.sendMessage("§e/duconfig spawn §8- §7sala de espera §8| §e/duconfig cama §8- §7cama do time selecionado");
        player.sendMessage("§e/duconfig borda <1|2> §8| §e/duconfig limite [altura] §8| §e/duconfig protecao <f> <c> <l>");
        player.sendMessage("§e/duconfig inventario [menu|listar|editar|salvar|remover] [modo] §8- §7kit padrão do modo");
        player.sendMessage("§e/duconfig tp <ponto> §8| §e/duconfig remover <ponto> §8| §e/duconfig pontos");
        player.sendMessage("§e/duconfig status §8| §e/duconfig salvar §8| §e/duconfig sair");
        player.sendMessage("§7Times: §cVermelho§7, §9Azul");
        player.sendMessage("");
    }

    @Completer(name = "duconfig", subCommands = {"duelsconfig", "dusetup"})
    public List<String> arenaCompleter(BukkitCommandContext context) {
        String[] args = context.getArgs();
        ArenaSetup setup = ArenaSetup.of(context.getPlayer().getUniqueId());

        if (args.length <= 1) {
            List<String> options = new ArrayList<>(SUB_COMMANDS);

            options.add("inventario");

            if (setup != null)
                setup.getTeams().forEach(team -> options.add(team.getName().toLowerCase()));

            return filter(options, args.length == 0 ? "" : args[0]);
        }

        String sub = args[0].toLowerCase();
        String last = args[args.length - 1];

        if (sub.equals("inventario") || sub.equals("kit")) {
            if (args.length == 2)
                return filter(Arrays.asList("menu", "listar", "editar", "salvar", "remover", "recarregar"), last);

            if (args.length == 3)
                return filter(ArcadeCategory.of(ServerType.DUELS).stream().map(category -> category.getName().replace(" ", "_")).collect(Collectors.toList()), last);

            return new ArrayList<>();
        }

        if (args.length == 2) {
            switch (sub) {
                case "criar":
                case "editar":
                    return filter(ArenaSetup.getModes(), last);
                case "time":
                    return setup == null ? new ArrayList<>() : filter(setup.getTeams().stream().map(team -> team.getName().toLowerCase()).collect(Collectors.toList()), last);
                case "borda":
                    return filter(Arrays.asList("1", "2"), last);
                case "tp":
                case "remover":
                    return setup == null ? new ArrayList<>() : filter(new ArrayList<>(setup.getLocations().keySet()), last);
                case "salvar":
                    return filter(Arrays.asList("sem-recarregar"), last);
                default:
                    if (TeamPresetType.of(sub) != null)
                        return filter(Arrays.stream(ArenaSetup.TeamPoint.values()).map(ArenaSetup.TeamPoint::getAlias).collect(Collectors.toList()), last);
            }
        }

        if (args.length == 3 && sub.equals("editar"))
            return filter(ArenaSetup.getMaps(args[1]), last);

        return new ArrayList<>();
    }

    private static List<String> filter(List<String> options, String start) {
        String lower = start.toLowerCase();

        return options.stream().filter(option -> option.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
