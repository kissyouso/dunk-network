package com.minecraft.event.command;

import com.minecraft.core.Constant;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.bukkit.command.single.EventCommand;
import com.minecraft.core.bukkit.command.structure.BukkitCommandContext;
import com.minecraft.event.Events;
import com.minecraft.event.config.EventLocations;
import com.minecraft.event.game.EventManager;
import com.minecraft.event.game.EventType;
import com.minecraft.event.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

import static com.minecraft.core.bukkit.command.single.EventCommand.PREFIX;

/**
 * /evento dentro do servidor de eventos.
 */
public class EventAdminHandler implements EventCommand.Handler {

    private static final List<String> PLAYER = Arrays.asList("entrar", "sair", "assistir", "status", "top");
    private static final List<String> ADMIN = Arrays.asList("iniciar", "cancelar", "forcestart", "kick", "set", "chao", "locais", "tipos", "build", "reload");

    /* Staff a partir de Helper (ou desenvolvedor) pode hospedar eventos */
    private boolean isStaff(Account account) {
        return account.hasRank(RankType.HELPER) || Constant.DEVELOPERS_IDS.contains(account.getId());
    }

    @Override
    public void handle(BukkitCommandContext context) {
        Player player = context.getPlayer();
        Account account = context.getAccount();
        String[] args = context.getArgs();

        Events plugin = Events.getEvents();
        EventManager manager = plugin.getEventManager();
        EventLocations locations = plugin.getLocations();

        String sub = args.length == 0 ? "entrar" : args[0].toLowerCase();

        switch (sub) {
            case "entrar":
                manager.join(player);
                return;
            case "sair":
                if (!manager.isParticipant(player) && !manager.isSpectator(player) && (manager.getEvent() == null || !manager.getEvent().isAlive(player))) {
                    player.sendMessage(PREFIX + "§cVocê não está em nenhum evento.");
                    return;
                }

                manager.leave(player, false);
                return;
            case "assistir":
            case "camarote":
                manager.spectate(player);
                return;
            case "status":
                if (!manager.isRunning())
                    player.sendMessage(PREFIX + "§cNão há nenhum evento acontecendo agora.");
                else
                    player.sendMessage(PREFIX + "Evento: §a" + manager.getEvent().getType().getName() + " §7| §fFase: §a" + manager.getPhase().name()
                            + " §7| §fJogadores: §a" + (manager.getPhase() == EventManager.Phase.RUNNING ? manager.getEvent().getAlive().size() : manager.getParticipants().size()));
                return;
            case "top":
                EventCommand.sendTop(player);
                return;
        }

        if (!isStaff(account)) {
            sendHelp(player, false);
            return;
        }

        switch (sub) {
            case "iniciar": {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /evento iniciar <tipo> [vip]. Tipos: " + types());
                    return;
                }

                EventType type = EventType.of(args[1]);

                if (type == null) {
                    player.sendMessage("§cTipo inválido. Tipos: " + types());
                    return;
                }

                String error = manager.start(type, args.length >= 3 && args[2].equalsIgnoreCase("vip"));

                player.sendMessage(error != null ? "§c" + error : "§aEvento §e" + type.getName() + " §ainiciado! As chamadas já começaram.");
                return;
            }
            case "cancelar": {
                if (!manager.isRunning()) {
                    player.sendMessage("§cNão há evento para cancelar.");
                    return;
                }

                manager.cancel("cancelado por " + player.getName());
                return;
            }
            case "forcestart": {
                if (manager.getPhase() != EventManager.Phase.CALLING) {
                    player.sendMessage("§cO evento não está nas chamadas.");
                    return;
                }

                manager.forceStart();
                player.sendMessage("§aO evento vai começar agora.");
                return;
            }
            case "kick": {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /evento kick <jogador>");
                    return;
                }

                Player target = Bukkit.getPlayerExact(args[1]);

                if (target == null) {
                    player.sendMessage("§cJogador não encontrado neste servidor.");
                    return;
                }

                manager.kick(target);
                player.sendMessage("§a" + target.getName() + " foi expulso do evento.");
                return;
            }
            case "set": {
                if (args.length < 3) {
                    player.sendMessage("§cUso: /evento set <geral|tipo> <ponto>");
                    player.sendMessage("§7geral: spawn, camarote");
                    player.sendMessage("§7tipos: entrada, espera (opcional) e os pontos de cada evento (veja /evento locais <tipo>)");
                    return;
                }

                String group = args[1].toLowerCase();

                if (!group.equals("geral") && EventType.of(group) == null) {
                    player.sendMessage("§cGrupo inválido. Use geral ou um tipo: " + types());
                    return;
                }

                String groupKey = group.equals("geral") ? "geral" : EventType.of(group).name();

                Location location = player.getLocation().clone();
                locations.set(groupKey, args[2], location);

                player.sendMessage("§aLocal §e" + args[2].toLowerCase() + " §ade §e" + groupKey + " §adefinido em §f"
                        + String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ()) + "§a.");
                return;
            }
            case "chao": {
                if (args.length < 2 || (!args[1].equals("1") && !args[1].equals("2"))) {
                    player.sendMessage("§cUso: /evento chao <1|2> §7(olhando para o bloco do canto do chão do Spleef)");
                    return;
                }

                Block block = player.getTargetBlock((Set<Material>) null, 8);

                if (block == null || block.getType() == Material.AIR) {
                    player.sendMessage("§cOlhe para um bloco do chão (até 8 blocos).");
                    return;
                }

                locations.set(EventType.SPLEEF.name(), "chao" + args[1], block.getLocation());
                player.sendMessage("§aCanto §e" + args[1] + " §ado chão do Spleef definido em §f" + block.getX() + ", " + block.getY() + ", " + block.getZ() + "§a.");
                return;
            }
            case "locais": {
                player.sendMessage("§2§lLOCAIS DOS EVENTOS §7(verde = definido, vermelho = falta)");
                player.sendMessage(" §fGeral: " + mark(locations, "geral", "spawn") + " " + mark(locations, "geral", "camarote"));

                for (EventType type : EventType.values()) {
                    StringBuilder builder = new StringBuilder(" §f" + type.getName() + ": ");

                    for (String point : type.getPoints())
                        builder.append(mark(locations, type.name(), point)).append(" ");

                    builder.append(mark(locations, type.name(), "espera")).append("§7(opcional)");
                    player.sendMessage(builder.toString());
                }

                player.sendMessage("§7Parkour/Semáforo: placa de pressão de §6ouro§7 = chegada, de §fferro§7 = checkpoint.");
                return;
            }
            case "tipos": {
                for (EventType type : EventType.values())
                    player.sendMessage(" §a" + type.name().toLowerCase() + " §7- §f" + type.getName() + ": §7" + type.getDescription());
                return;
            }
            case "build": {
                User user = User.fetch(player.getUniqueId());

                if (user == null) return;

                user.setBuild(!user.isBuild());
                player.setGameMode(user.isBuild() ? org.bukkit.GameMode.CREATIVE : org.bukkit.GameMode.ADVENTURE);
                player.sendMessage(user.isBuild() ? "§aModo construção ativado." : "§cModo construção desativado.");
                return;
            }
            case "reload": {
                plugin.getSettings().reload();
                locations.reload();
                player.sendMessage("§aconfig.yml e locations.yml recarregados.");
                return;
            }
            default:
                sendHelp(player, true);
        }
    }

    @Override
    public List<String> complete(BukkitCommandContext context) {
        String[] args = context.getArgs();
        boolean staff = isStaff(context.getAccount());

        List<String> options = new ArrayList<>();

        if (args.length <= 1) {
            options.addAll(PLAYER);

            if (staff)
                options.addAll(ADMIN);
        } else if (staff && args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "iniciar":
                    for (EventType type : EventType.values())
                        options.add(type.name().toLowerCase());
                    break;
                case "set":
                    options.add("geral");
                    for (EventType type : EventType.values())
                        options.add(type.name().toLowerCase());
                    break;
                case "chao":
                    options.addAll(Arrays.asList("1", "2"));
                    break;
                case "kick":
                    Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
                    break;
            }
        } else if (staff && args.length == 3) {
            if (args[0].equalsIgnoreCase("iniciar"))
                options.add("vip");
            else if (args[0].equalsIgnoreCase("set")) {
                if (args[1].equalsIgnoreCase("geral"))
                    options.addAll(Arrays.asList("spawn", "camarote"));
                else {
                    EventType type = EventType.of(args[1]);

                    if (type != null) {
                        options.addAll(type.getPoints());
                        options.add("espera");
                    }
                }
            }
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();

        return options.stream().filter(option -> option.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }

    private String mark(EventLocations locations, String group, String point) {
        return (locations.get(group, point) != null ? "§a" : "§c") + point;
    }

    private String types() {
        return Arrays.stream(EventType.values()).map(type -> type.name().toLowerCase()).collect(Collectors.joining(", "));
    }

    private void sendHelp(Player player, boolean staff) {
        player.sendMessage("");
        player.sendMessage("§2§lEVENTOS");
        player.sendMessage("§e/evento entrar §7- Entra no evento aberto.");
        player.sendMessage("§e/evento sair §7- Sai do evento ou do camarote.");
        player.sendMessage("§e/evento assistir §7- Assiste pelo camarote.");
        player.sendMessage("§e/evento status §7- Mostra o evento atual.");
        player.sendMessage("§e/evento top §7- Quem mais venceu eventos.");

        if (staff) {
            player.sendMessage("§2§lADMINISTRAÇÃO");
            player.sendMessage("§e/evento iniciar <tipo> [vip] §7- Começa as chamadas de um evento.");
            player.sendMessage("§e/evento forcestart §7- Pula as chamadas e começa agora.");
            player.sendMessage("§e/evento cancelar §7- Cancela o evento atual.");
            player.sendMessage("§e/evento kick <jogador> §7- Tira alguém do evento.");
            player.sendMessage("§e/evento set <geral|tipo> <ponto> §7- Define um local (na sua posição).");
            player.sendMessage("§e/evento chao <1|2> §7- Cantos do chão do Spleef (olhando o bloco).");
            player.sendMessage("§e/evento locais §7- Mostra o que falta configurar.");
            player.sendMessage("§e/evento tipos §7- Lista os eventos.");
            player.sendMessage("§e/evento build §7- Modo construção.");
            player.sendMessage("§e/evento reload §7- Recarrega as configurações.");
        }

        player.sendMessage("");
    }
}
