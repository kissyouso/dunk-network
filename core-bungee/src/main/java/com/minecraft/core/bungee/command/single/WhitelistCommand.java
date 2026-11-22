package com.minecraft.core.bungee.command.single;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.backend.data.list.server.ServerData;
import com.minecraft.core.bungee.command.structure.BungeeCommandContext;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.CommandSender;
import com.minecraft.core.command.annotation.Command;
import com.minecraft.core.command.annotation.Completer;
import com.minecraft.core.server.Server;
import com.minecraft.core.server.payload.whitelist.ServerWhitelist;
import com.minecraft.core.server.type.ServerType;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /whitelist simplificado.
 * <p>
 * O servidor é sempre o último argumento e é opcional: sem ele, vale o servidor onde você está.
 * Também aceita um tipo ("bedwars" = todos os de BedWars), "rede" (entrada na rede) ou "todos".
 */
public class WhitelistCommand implements CommandInheritor {

    private static final String PREFIX = "§2§lWHITELIST §8» §f";

    private static final List<String> ACTIONS = Arrays.asList("ligar", "desligar", "add", "remover", "cargo", "ver", "servidores");

    private final ServerData serverData = Core.getServerData();

    @Command(name = "whitelist", aliases = {"wl"}, rank = RankType.HELPER, onlyPlayer = false)
    public void whitelist(BungeeCommandContext context) {
        CommandSender sender = context.getSender();
        String[] args = context.getArgs();

        if (args.length == 0) {
            List<Server> here = resolve(context, null);

            if (!here.isEmpty())
                here.forEach(server -> sendStatus(sender, server));

            sendHelp(sender, context.getLabel());
            return;
        }

        String action = action(args[0]);

        switch (action) {
            case "servidores": {
                sendServerList(sender);
                return;
            }
            case "ver": {
                List<Server> servers = target(context, args.length > 1 ? args[1] : null);

                servers.forEach(server -> sendStatus(sender, server));
                return;
            }
            case "ligar":
            case "desligar": {
                boolean enable = action.equals("ligar");
                List<Server> servers = target(context, args.length > 1 ? args[1] : null);

                if (servers.isEmpty()) return;

                for (Server server : servers) {
                    server.getWhitelist().setEnabled(enable);
                    save(server);
                }

                sender.send(PREFIX + "Whitelist " + (enable ? "§aLIGADA" : "§cDESLIGADA") + " §fem " + names(servers) + "§f.");

                if (enable)
                    sender.send(PREFIX + "§7Entram: a equipe, os cargos liberados e os jogadores da lista. Use §f/" + context.getLabel() + " add <nick>§7.");
                return;
            }
            case "add":
            case "remover": {
                if (args.length < 2) {
                    sender.send(PREFIX + "§cUso: /" + context.getLabel() + " " + action + " <nick> [servidor]");
                    return;
                }

                String nick = args[1];
                String serverArg = args.length > 2 ? args[2] : null;

                /* Ordem antiga (/whitelist add <servidor> <nick>) continua funcionando */
                if (serverArg != null && !resolve(context, nick).isEmpty() && resolve(context, serverArg).isEmpty()) {
                    String swap = nick;
                    nick = serverArg;
                    serverArg = swap;
                }

                if (!nick.matches("[A-Za-z0-9_]{1,16}")) {
                    sender.send(PREFIX + "§cNick inválido: §f" + nick);
                    return;
                }

                List<Server> servers = target(context, serverArg);

                if (servers.isEmpty()) return;

                boolean add = action.equals("add");
                String lower = nick.toLowerCase();

                for (Server server : servers) {
                    List<String> players = server.getWhitelist().getPlayers();

                    players.removeIf(name -> name.equalsIgnoreCase(lower));

                    if (add)
                        players.add(lower);

                    save(server);
                }

                sender.send(PREFIX + (add ? "§a" + nick + " §fliberado em " : "§c" + nick + " §fremovido de ") + names(servers) + "§f.");

                if (add && servers.stream().noneMatch(server -> server.getWhitelist().isEnabled()))
                    sender.send(PREFIX + "§7A whitelist está desligada aí. Para ligar: §f/" + context.getLabel() + " ligar" + (serverArg != null ? " " + serverArg : ""));
                return;
            }
            case "cargo": {
                RankType rank = args.length > 1 ? RankType.of(args[1]) : null;

                if (rank == null) {
                    sender.send(PREFIX + "§cUso: /" + context.getLabel() + " cargo <cargo> [servidor] §7(liga/desliga o cargo)");
                    sender.send(PREFIX + "§7Cargos: §f" + Arrays.stream(RankType.values()).map(type -> type.name().toLowerCase()).collect(Collectors.joining("§7, §f")));
                    return;
                }

                List<Server> servers = target(context, args.length > 2 ? args[2] : null);

                if (servers.isEmpty()) return;

                /* Alterna: se algum dos servidores ainda não tem o cargo, adiciona em todos */
                boolean add = servers.stream().anyMatch(server -> !server.getWhitelist().hasRank(rank));

                for (Server server : servers) {
                    List<RankType> ranks = server.getWhitelist().getRanks();

                    ranks.remove(rank);

                    if (add)
                        ranks.add(rank);

                    save(server);
                }

                sender.send(PREFIX + "Cargo " + rank.getColoredName() + (add ? " §fliberado em " : " §fretirado de ") + names(servers) + "§f.");
                return;
            }
            default:
                sendHelp(sender, context.getLabel());
        }
    }

    /* ---------------- Servidores ---------------- */

    /**
     * Servidores do argumento (ou o servidor atual). Avisa quem digitou quando não acha nenhum.
     */
    private List<Server> target(BungeeCommandContext context, String argument) {
        List<Server> servers = resolve(context, argument);

        if (servers.isEmpty()) {
            if (argument == null)
                context.getSender().send(PREFIX + "§cInforme o servidor. Ex: §fbedwars§c, §fbedwars-1§c, §frede§c ou §ftodos§c. Veja em §f/whitelist servidores§c.");
            else
                context.getSender().send(PREFIX + "§cNenhum servidor encontrado para §f" + argument + "§c. Veja em §f/whitelist servidores§c.");
        }

        return servers;
    }

    private List<Server> resolve(BungeeCommandContext context, String argument) {
        List<Server> all = serverData.getServerList(server -> !server.isDead());

        if (argument == null) {
            if (!context.getSender().isPlayer()) return new ArrayList<>();

            ProxiedPlayer player = context.getPlayer();

            if (player == null || player.getServer() == null) return new ArrayList<>();

            Server current = serverData.findByProxyServer(player.getServer().getInfo());

            return current != null ? new ArrayList<>(Collections.singletonList(current)) : new ArrayList<>();
        }

        String search = argument.toLowerCase(Locale.ROOT);

        switch (search) {
            case "todos":
            case "all":
                return all;
            case "rede":
            case "proxy":
            case "network":
                return all.stream().filter(server -> server.getType() == ServerType.BUNGEE).collect(Collectors.toList());
        }

        /* Servidor exato: bedwars-1 */
        for (Server server : all) {
            if (server.getNameAndId().equalsIgnoreCase(search) || server.getIdentifier().equalsIgnoreCase(search))
                return new ArrayList<>(Collections.singletonList(server));
        }

        /* Tipo: todos os servidores dele */
        ServerType type = type(search);

        if (type != null)
            return all.stream().filter(server -> server.getType() == type).collect(Collectors.toList());

        return new ArrayList<>();
    }

    private static ServerType type(String search) {
        String clean = search.replace("-", "").replace("_", "").replace(" ", "");

        for (ServerType type : ServerType.values()) {
            if (type.name().replace("_", "").equalsIgnoreCase(clean)
                    || type.getName().replace(" ", "").equalsIgnoreCase(clean)
                    || type.getId().replace("-", "").equalsIgnoreCase(clean)
                    || type.getAliases().stream().anyMatch(alias -> alias.replace("-", "").equalsIgnoreCase(clean)))
                return type;
        }

        return null;
    }

    private void save(Server server) {
        server.refresh();
        serverData.saveWhitelist(server);
    }

    private static String names(List<Server> servers) {
        if (servers.size() > 4)
            return "§e" + servers.size() + " servidores";

        return servers.stream().map(server -> "§e" + server.getNameAndId()).collect(Collectors.joining("§f, "));
    }

    /* ---------------- Mensagens ---------------- */

    private void sendStatus(CommandSender sender, Server server) {
        ServerWhitelist whitelist = server.getWhitelist();

        sender.send("",
                PREFIX + "§e" + server.getNameAndId() + " §7(" + server.getName() + ") " + (whitelist.isEnabled() ? "§a§lLIGADA" : "§c§lDESLIGADA"),
                " §8▪ §7Jogadores (" + whitelist.getPlayers().size() + "): §f" + (whitelist.getPlayers().isEmpty() ? "§7nenhum" : String.join("§7, §f", whitelist.getPlayers())),
                " §8▪ §7Cargos: " + (whitelist.getRanks().isEmpty() ? "§7nenhum" : whitelist.getRanks().stream().map(RankType::getColoredName).collect(Collectors.joining("§7, "))),
                " §8▪ §7A equipe sempre entra.",
                "");
    }

    private void sendServerList(CommandSender sender) {
        List<Server> servers = serverData.getServerList(server -> !server.isDead()).stream()
                .sorted(Comparator.comparing(Server::getNameAndId))
                .collect(Collectors.toList());

        sender.send("", PREFIX + "Servidores §7(" + servers.size() + ")§f:");

        for (Server server : servers) {
            ServerWhitelist whitelist = server.getWhitelist();

            sender.send(" §8▪ " + (whitelist.isEnabled() ? "§a● " : "§7○ ") + "§f" + server.getNameAndId() + " §7- " + server.getName()
                    + " §8(" + server.getPayload().getPlayers() + " online" + (whitelist.getPlayers().isEmpty() ? "" : ", " + whitelist.getPlayers().size() + " liberados") + ")");
        }

        sender.send(" §7Dica: use o tipo (ex: §fbedwars§7) para mexer em todos de uma vez, §frede §7para a entrada na rede.", "");
    }

    private void sendHelp(CommandSender sender, String label) {
        String l = "/" + label;

        sender.send("",
                PREFIX + "Comandos §7(o servidor é opcional; sem ele, vale onde você está)",
                " §e" + l + " ligar §7[servidor] §8- §fliga a whitelist",
                " §e" + l + " desligar §7[servidor] §8- §fdesliga a whitelist",
                " §e" + l + " add <nick> §7[servidor] §8- §flibera um jogador",
                " §e" + l + " remover <nick> §7[servidor] §8- §ftira um jogador",
                " §e" + l + " cargo <cargo> §7[servidor] §8- §flibera/tira um cargo",
                " §e" + l + " ver §7[servidor] §8- §fquem está liberado",
                " §e" + l + " servidores §8- §ftodos os servidores e o estado",
                " §7Servidor pode ser: §fbedwars-1§7, um tipo (§fbedwars§7, §flobby§7...), §frede §7ou §ftodos§7.",
                "");
    }

    private static String action(String input) {
        switch (input.toLowerCase(Locale.ROOT)) {
            case "on":
            case "ativar":
            case "ligar":
                return "ligar";
            case "off":
            case "desativar":
            case "desligar":
                return "desligar";
            case "add":
            case "adicionar":
                return "add";
            case "remove":
            case "remover":
            case "rem":
                return "remover";
            case "cargo":
            case "rank":
                return "cargo";
            case "info":
            case "ver":
            case "status":
                return "ver";
            case "list":
            case "lista":
            case "servidores":
                return "servidores";
            default:
                return input.toLowerCase(Locale.ROOT);
        }
    }

    /* ---------------- TAB ---------------- */

    @Completer(name = "whitelist", subCommands = {"wl"}, rank = RankType.HELPER)
    public List<String> whitelistCompleter(BungeeCommandContext context) {
        String[] args = context.getArgs();
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);

        List<String> options = new ArrayList<>();

        if (args.length <= 1) {
            options.addAll(ACTIONS);
        } else {
            String action = action(args[0]);

            boolean serverPosition = (action.equals("ligar") || action.equals("desligar") || action.equals("ver")) && args.length == 2
                    || (action.equals("add") || action.equals("remover") || action.equals("cargo")) && args.length == 3;

            if (serverPosition) {
                options.addAll(Arrays.asList("rede", "todos"));

                for (Server server : serverData.getServerList(server -> !server.isDead())) {
                    options.add(server.getNameAndId());

                    String type = server.getType().getId();

                    if (!options.contains(type))
                        options.add(type);
                }
            } else if (args.length == 2 && action.equals("add")) {
                Core.getAccountController().list().stream().map(Account::getNickname).forEach(options::add);
            } else if (args.length == 2 && action.equals("remover")) {
                List<Server> here = resolve(context, null);

                if (!here.isEmpty())
                    options.addAll(here.get(0).getWhitelist().getPlayers());
                else
                    Core.getAccountController().list().stream().map(Account::getNickname).forEach(options::add);
            } else if (args.length == 2 && action.equals("cargo")) {
                Arrays.stream(RankType.values()).map(type -> type.name().toLowerCase(Locale.ROOT)).forEach(options::add);
            }
        }

        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(last)).distinct().collect(Collectors.toList());
    }
}
