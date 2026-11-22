package com.minecraft.core.bukkit.command.single;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.api.event.EventState;
import com.minecraft.core.bukkit.command.structure.BukkitCommandContext;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.annotation.Command;
import com.minecraft.core.command.annotation.Completer;
import com.minecraft.core.server.Server;
import com.minecraft.core.server.type.ServerType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /evento em qualquer servidor. No servidor de eventos, o módulo de eventos registra um {@link Handler}
 * que assume o comando (entrar, sair, assistir e todos os comandos de administração).
 */
public class EventCommand implements CommandInheritor {

    public static final String PREFIX = "§2§lEVENTO §8» §f";
    public static final String SPECTATE_KEY = "event.spectate";

    /* Definido pelo plugin do servidor de eventos */
    public static Handler handler;

    public interface Handler {
        void handle(BukkitCommandContext context);

        List<String> complete(BukkitCommandContext context);
    }

    @Command(name = "evento", aliases = {"eventos", "event"})
    public void event(BukkitCommandContext context) {
        if (handler != null) {
            handler.handle(context);
            return;
        }

        Player player = context.getPlayer();
        Account account = context.getAccount();
        String[] args = context.getArgs();

        String sub = args.length == 0 ? "entrar" : args[0].toLowerCase();

        switch (sub) {
            case "entrar":
            case "assistir":
            case "camarote": {
                EventState state = EventState.current();

                if (state == null) {
                    player.sendMessage(PREFIX + "§cNão há nenhum evento acontecendo agora.");
                    sendHelp(player, account);
                    return;
                }

                boolean spectate = !sub.equals("entrar");

                if (!spectate && !state.isOpen()) {
                    player.sendMessage(PREFIX + "§cO evento §f" + state.getName() + " §cjá começou. Use §f/evento assistir§c.");
                    return;
                }

                if (!spectate && state.isVipOnly() && !account.isVIP()) {
                    player.sendMessage(PREFIX + "§cEste evento é exclusivo para §aVIPs§c.");
                    return;
                }

                if (spectate && !state.isSpectators()) {
                    player.sendMessage(PREFIX + "§cEste evento não permite espectadores.");
                    return;
                }

                Server server = Core.getServerData().of(ServerType.EVENT);

                if (server == null || server.isDead()) {
                    player.sendMessage(String.format(Constant.SERVER_NOT_FOUND_MESSAGE, ServerType.EVENT.getName()));
                    return;
                }

                /* O servidor de eventos lê essa marca para saber se o jogador vai participar ou assistir */
                if (spectate)
                    account.setCooldown(SPECTATE_KEY, 30_000L);

                player.sendMessage(PREFIX + (spectate ? "Indo para o camarote do evento §a" : "Entrando no evento §a") + state.getName() + "§f...");
                account.redirect(ServerType.EVENT);
                return;
            }
            case "top":
            case "topvencedores": {
                sendTop(player);
                return;
            }
            case "status": {
                EventState state = EventState.current();

                if (state == null)
                    player.sendMessage(PREFIX + "§cNão há nenhum evento acontecendo agora.");
                else
                    player.sendMessage(PREFIX + "Evento: §a" + state.getName() + " §7| §fJogadores: §a" + state.getPlayers()
                            + " §7| " + (state.isOpen() ? "§aAberto (/evento entrar)" : "§eEm andamento (/evento assistir)"));
                return;
            }
            default:
                sendHelp(player, account);
        }
    }

    @Completer(name = "evento", subCommands = {"eventos", "event"})
    public List<String> eventCompleter(BukkitCommandContext context) {
        if (handler != null)
            return handler.complete(context);

        List<String> options = new ArrayList<>();
        String[] args = context.getArgs();

        if (args.length <= 1) {
            String last = args.length == 0 ? "" : args[0].toLowerCase();

            for (String option : new String[]{"entrar", "assistir", "status", "top"}) {
                if (option.startsWith(last))
                    options.add(option);
            }
        }

        return options;
    }

    public static void sendTop(Player player) {
        Core.getPlatform().runAsync(() -> {
            List<Account> top = Core.getAccountData().top("context.eventWins", 10);

            player.sendMessage("");
            player.sendMessage("§2§lTOP VENCEDORES DE EVENTOS");

            if (top.isEmpty())
                player.sendMessage("§7Ninguém venceu um evento ainda.");

            int position = 1;

            for (Account target : top) {
                player.sendMessage("§e" + position + ". " + target.getRank().getType().getColor() + target.getName()
                        + " §7- §a" + target.getContext().getEventWins() + " vitórias");
                position++;
            }

            player.sendMessage("");
        });
    }

    private void sendHelp(Player player, Account account) {
        player.sendMessage("");
        player.sendMessage("§2§lEVENTOS");
        player.sendMessage("§e/evento entrar §7- Entra no evento que está aberto.");
        player.sendMessage("§e/evento assistir §7- Assiste ao evento pelo camarote.");
        player.sendMessage("§e/evento status §7- Mostra o evento atual.");
        player.sendMessage("§e/evento top §7- Jogadores que mais venceram eventos.");

        if (account.hasRank(RankType.ADMIN))
            player.sendMessage("§7(Comandos de administração: use §f/evento §7dentro do servidor de eventos)");

        player.sendMessage("");
    }
}
