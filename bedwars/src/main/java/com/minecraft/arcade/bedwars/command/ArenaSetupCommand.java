package com.minecraft.arcade.bedwars.command;

import com.minecraft.arcade.bedwars.menu.setup.ArenaSetupMenu;
import com.minecraft.arcade.bedwars.setup.ArenaSetup;
import com.minecraft.arcade.bedwars.setup.ArenaSetupActions;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.arcade.room.team.preset.TeamPresetType;
import com.minecraft.core.bukkit.command.structure.BukkitCommandContext;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.annotation.Command;
import com.minecraft.core.command.annotation.Completer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.minecraft.arcade.bedwars.setup.ArenaSetup.PREFIX;

/**
 * Configuração dos mapas de BedWars dentro do jogo.
 * Fluxo: /config criar <modo> <mapa> → marcar os pontos (hotbar, menu ou comandos) → /config salvar.
 */
public class ArenaSetupCommand implements CommandInheritor {

    private static final List<String> SUB_COMMANDS = Arrays.asList("criar", "editar", "menu", "itens", "time", "spawn", "borda", "limite",
            "diamante", "esmeralda", "ranking", "protecao", "ilha", "remover", "tp", "pontos", "status", "salvar", "sair");

    @Command(name = "config", aliases = {"bwconfig", "bwsetup"}, rank = RankType.ADMIN)
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

        ArenaSetup setup = ArenaSetup.of(player.getUniqueId());

        if (setup == null) {
            player.sendMessage(PREFIX + "§cVocê não está configurando nenhuma arena. Use §e/config criar <modo> <mapa>§c.");
            return;
        }

        if (!player.getWorld().equals(setup.getWorld()) && !sub.equals("sair") && !sub.equals("salvar") && !sub.equals("status") && !sub.equals("pontos")) {
            player.sendMessage(PREFIX + "§cVá para o mundo §e" + setup.getWorld().getName() + " §cpara marcar os pontos.");
            return;
        }

        /* /config <time> <ponto> */
        TeamPresetType team = TeamPresetType.of(sub);

        if (team != null && !sub.equals("time")) {
            if (args.length < 2) {
                player.sendMessage(PREFIX + "§cUso: /config " + team.getName().toLowerCase() + " <" + pointAliases() + ">");
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
            case "kit":
                ArenaSetupActions.giveKit(player, setup);
                player.sendMessage(PREFIX + "Itens de configuração recebidos.");
                break;
            case "time": {
                TeamPresetType selected = args.length > 1 ? TeamPresetType.of(args[1]) : null;

                if (selected == null || !setup.getTeams().contains(selected)) {
                    player.sendMessage(PREFIX + "§cUso: /config time <" + teamNames(setup) + ">");
                    return;
                }

                ArenaSetupActions.selectTeam(player, setup, selected);
                break;
            }
            case "spawn":
            case "espera":
                ArenaSetupActions.setWaitingSpawn(player, setup);
                break;
            case "borda":
            case "pos": {
                if (args.length < 2 || !(args[1].equals("1") || args[1].equals("2"))) {
                    player.sendMessage(PREFIX + "§cUso: /config borda <1|2> §7(fique em um canto do mapa)");
                    return;
                }

                ArenaSetupActions.setBorder(player, setup, Integer.parseInt(args[1]));
                break;
            }
            case "limite": {
                Integer height = args.length > 1 ? parse(args[1]) : Integer.valueOf(player.getLocation().getBlockY());

                if (height == null || height <= 0 || height > 255) {
                    player.sendMessage(PREFIX + "§cUso: /config limite [altura] §7(sem altura usa a sua)");
                    return;
                }

                ArenaSetupActions.setBuildLimit(player, setup, height);
                break;
            }
            case "diamante":
            case "diamond":
                ArenaSetupActions.addOre(player, setup, "diamond");
                break;
            case "esmeralda":
            case "emerald":
                ArenaSetupActions.addOre(player, setup, "emerald");
                break;
            case "ranking":
            case "top": {
                if (args.length < 2 || !ArenaSetup.LEADERBOARDS.contains(args[1].toLowerCase())) {
                    player.sendMessage(PREFIX + "§cUso: /config ranking <" + String.join("|", ArenaSetup.LEADERBOARDS) + ">");
                    return;
                }

                ArenaSetupActions.setLeaderboard(player, setup, args[1].toLowerCase());
                break;
            }
            case "protecao":
            case "ilha": {
                String section = sub.equals("protecao") ? "area_protection" : "island_area";

                Integer front = args.length > 3 ? parse(args[1]) : null, up = args.length > 3 ? parse(args[2]) : null, sides = args.length > 3 ? parse(args[3]) : null;

                if (front == null || up == null || sides == null) {
                    player.sendMessage(PREFIX + "§cUso: /config " + sub + " <frente/trás> <cima> <lados>");
                    player.sendMessage(PREFIX + "§7Atual: " + setup.describeArea(section));
                    return;
                }

                setup.setArea(section, front, up, sides);
                player.sendMessage(PREFIX + (sub.equals("protecao") ? "Proteção da base" : "Área da ilha") + ": " + setup.describeArea(section));
                break;
            }
            case "remover":
            case "remove": {
                if (args.length < 2) {
                    player.sendMessage(PREFIX + "§cUso: /config remover <ponto> §7(veja em /config pontos)");
                    return;
                }

                ArenaSetupActions.remove(player, setup, resolve(setup, args[1], args.length > 2 ? args[2] : null));
                break;
            }
            case "tp": {
                if (args.length < 2) {
                    player.sendMessage(PREFIX + "§cUso: /config tp <ponto> §7(veja em /config pontos)");
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

    private void start(Player player, String[] args, boolean edit) {
        List<String> modes = ArenaSetup.getModes();

        if (args.length < 2 || !modes.contains(args[1].toLowerCase())) {
            player.sendMessage(PREFIX + "§cUso: /config " + (edit ? "editar" : "criar") + " <modo> " + (edit ? "<mapa>" : "[mapa]"));
            player.sendMessage(PREFIX + "§7Modos: §f" + String.join("§7, §f", modes));
            return;
        }

        String mode = args[1].toLowerCase();
        String mapName = args.length > 2 ? args[2] : player.getWorld().getName();

        /* Mundo "solo-farol" vira o mapa "farol" */
        if (mapName.toLowerCase().startsWith(mode + "-"))
            mapName = mapName.substring(mode.length() + 1);

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
            player.sendMessage(PREFIX + "§cVocê tem alterações não salvas em §e" + current.getMapName() + "§c. Use §e/config salvar §cou §e/config sair§c.");
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
            case "diamante":
                return "ore_generator_diamond_" + (second != null ? second : setup.getKeys("ore_generator_diamond_").size());
            case "esmeralda":
                return "ore_generator_emerald_" + (second != null ? second : setup.getKeys("ore_generator_emerald_").size());
            case "borda":
                return "map_limit_pos" + (second != null ? second : "1");
            case "ranking":
                return "leaderboard_" + (second != null ? second.toLowerCase() : "level");
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
        player.sendMessage("§2§lCONFIG §8- §7Configuração de mapas do BedWars");
        player.sendMessage("§e/config criar <modo> [mapa] §8- §7começa no mundo atual");
        player.sendMessage("§e/config editar <modo> <mapa> §8- §7edita um mapa salvo");
        player.sendMessage("§e/config menu §8- §7menu com todos os pontos");
        player.sendMessage("§e/config itens §8- §7itens de configuração na hotbar");
        player.sendMessage("§e/config <time> <" + pointAliases() + ">");
        player.sendMessage("§e/config spawn §8- §7spawn da sala de espera");
        player.sendMessage("§e/config borda <1|2> §8| §e/config limite [altura]");
        player.sendMessage("§e/config diamante §8| §e/config esmeralda §8- §7adiciona gerador");
        player.sendMessage("§e/config ranking <tipo> §8| §e/config protecao §8| §e/config ilha");
        player.sendMessage("§e/config tp <ponto> §8| §e/config remover <ponto> §8| §e/config pontos");
        player.sendMessage("§e/config status §8| §e/config salvar §8| §e/config sair");
        player.sendMessage("§7Times: §cVermelho§7, §9Azul§7, §aVerde§7, §eAmarelo§7, §bCiano§7, §fBranco§7, §dRosa§7, §8Cinza");
        player.sendMessage("");
    }

    @Completer(name = "config", subCommands = {"bwconfig", "bwsetup"})
    public List<String> arenaCompleter(BukkitCommandContext context) {
        String[] args = context.getArgs();
        ArenaSetup setup = ArenaSetup.of(context.getPlayer().getUniqueId());

        if (args.length <= 1) {
            List<String> options = new ArrayList<>(SUB_COMMANDS);

            if (setup != null)
                setup.getTeams().forEach(team -> options.add(team.getName().toLowerCase()));

            return filter(options, args.length == 0 ? "" : args[0]);
        }

        String sub = args[0].toLowerCase();
        String last = args[args.length - 1];

        if (args.length == 2) {
            switch (sub) {
                case "criar":
                case "editar":
                    return filter(ArenaSetup.getModes(), last);
                case "time":
                    return setup == null ? new ArrayList<>() : filter(setup.getTeams().stream().map(team -> team.getName().toLowerCase()).collect(Collectors.toList()), last);
                case "borda":
                    return filter(Arrays.asList("1", "2"), last);
                case "ranking":
                    return filter(ArenaSetup.LEADERBOARDS, last);
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
