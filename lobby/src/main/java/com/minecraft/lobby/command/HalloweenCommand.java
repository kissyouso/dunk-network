package com.minecraft.lobby.command;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.tag.Tag;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.command.structure.BukkitCommandContext;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.annotation.Command;
import com.minecraft.core.command.annotation.Completer;
import com.minecraft.lobby.halloween.Halloween;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuração do evento de Halloween (doces escondidos no lobby).
 */
public class HalloweenCommand implements CommandInheritor {

    private static final List<String> SUBCOMMANDS = Arrays.asList("add", "remove", "list", "tp", "head", "required", "toggle", "progress", "reset", "reload");

    @Command(name = "halloween", aliases = {"doces"})
    public void halloween(BukkitCommandContext context) {
        Player player = context.getPlayer();
        Account account = context.getAccount();

        String[] args = context.getArgs();

        /* Jogadores comuns veem o próprio progresso */
        if (!Halloween.canManage(account)) {
            player.sendMessage("§5§lHALLOWEEN §8» §fDoces encontrados: §6" + Math.min(Halloween.found(account), Halloween.getRequired())
                    + "/" + Halloween.getRequired());
            return;
        }

        if (args.length == 0) {
            sendHelp(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "add": {
                Block block = target(player);

                if (block == null) {
                    player.sendMessage("§cOlhe para uma cabeça colocada no lobby (até 6 blocos). Use §f/halloween head §cpara pegar uma.");
                    return;
                }

                if (Halloween.of(block) != null) {
                    player.sendMessage("§cEsta cabeça já é um doce.");
                    return;
                }

                Halloween.Candy candy = Halloween.add(block);

                player.sendMessage("§aDoce §e#" + candy.getId() + " §aadicionado em §f" + candy.getX() + ", " + candy.getY() + ", " + candy.getZ()
                        + "§a. Total: §e" + Halloween.getCandies().size() + " §7(necessários: " + Halloween.getRequired() + ")");
                return;
            }
            case "remove": {
                Halloween.Candy candy = null;

                if (args.length >= 2) {
                    try {
                        candy = Halloween.of(Integer.parseInt(args[1]));
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    Block block = target(player);
                    candy = block != null ? Halloween.of(block) : null;
                }

                if (candy == null) {
                    player.sendMessage("§cDoce não encontrado. Olhe para ele ou use §f/halloween remove <id>§c.");
                    return;
                }

                Halloween.remove(candy);
                player.sendMessage("§cDoce §e#" + candy.getId() + " §cremovido. §7(a cabeça continua no mapa, quebre se quiser)");
                return;
            }
            case "list": {
                List<Halloween.Candy> candies = Halloween.getCandies();

                player.sendMessage("§5§lHALLOWEEN §7- " + candies.size() + " doces §7(necessários: " + Halloween.getRequired() + ", "
                        + (Halloween.isEnabled() ? "§aativo" : "§cdesativado") + "§7)");

                for (Halloween.Candy candy : candies)
                    player.sendMessage(" §e#" + candy.getId() + " §7- §f" + candy.getWorld() + " " + candy.getX() + ", " + candy.getY() + ", " + candy.getZ());
                return;
            }
            case "tp": {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /halloween tp <id>");
                    return;
                }

                Halloween.Candy candy;

                try {
                    candy = Halloween.of(Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    candy = null;
                }

                if (candy == null || candy.toLocation().getWorld() == null) {
                    player.sendMessage("§cDoce não encontrado.");
                    return;
                }

                player.teleport(candy.toLocation().add(0.5, 1, 0.5));
                player.sendMessage("§aTeleportado para o doce §e#" + candy.getId() + "§a.");
                return;
            }
            case "head": {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /halloween head <textura_base64>");
                    player.sendMessage("§7Pegue a textura (Value) de um doce em sites de cabeças, coloque a cabeça e use §f/halloween add§7.");
                    return;
                }

                player.getInventory().addItem(Item.of(Material.SKULL_ITEM, 3, "§6Doce de Halloween").skullByBase64(args[1]));
                player.sendMessage("§aVocê recebeu uma cabeça de doce. Coloque no lobby e use §f/halloween add §aolhando para ela.");
                return;
            }
            case "required": {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /halloween required <quantidade>");
                    return;
                }

                try {
                    Halloween.setRequired(Integer.parseInt(args[1]));
                    player.sendMessage("§aAgora são necessários §e" + Halloween.getRequired() + " §adoces para ganhar a tag.");
                } catch (NumberFormatException e) {
                    player.sendMessage("§cQuantidade inválida.");
                }
                return;
            }
            case "toggle": {
                Halloween.setEnabled(!Halloween.isEnabled());
                player.sendMessage(Halloween.isEnabled() ? "§aEvento de Halloween ativado." : "§cEvento de Halloween desativado.");
                return;
            }
            case "progress":
            case "reset": {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /halloween " + args[0].toLowerCase() + " <jogador>");
                    return;
                }

                Account target = Core.getAccountData().of(args[1]);

                if (target == null) {
                    player.sendMessage("§cJogador não encontrado.");
                    return;
                }

                if (args[0].equalsIgnoreCase("progress")) {
                    player.sendMessage("§e" + target.getName() + " §fencontrou §6" + Halloween.found(target) + "/" + Halloween.getRequired()
                            + " §fdoces" + (target.hasPermission(Halloween.SPOOKY_PERMISSION) ? " §7(evento concluído)" : "") + ".");
                    return;
                }

                target.getHalloweenCandies().clear();
                target.getPermissions().removeIf(permission -> permission.getKey().equalsIgnoreCase(Halloween.SPOOKY_PERMISSION));

                Core.getPlatform().runAsync(() -> target.saveContext(target.getContext()));

                player.sendMessage("§aProgresso de Halloween de §e" + target.getName() + " §areiniciado.");
                return;
            }
            case "reload": {
                Halloween.load();
                player.sendMessage("§ahalloween.yml recarregado. §7(" + Halloween.getCandies().size() + " doces)");
                return;
            }
            default:
                sendHelp(player);
        }
    }

    @Completer(name = "halloween", subCommands = {"doces"})
    public List<String> halloweenCompleter(BukkitCommandContext context) {
        String[] args = context.getArgs();
        List<String> options = new ArrayList<>();

        if (!Halloween.canManage(context.getAccount())) return options;

        if (args.length == 1)
            options.addAll(SUBCOMMANDS);
        else if (args.length == 2 && Arrays.asList("tp", "remove").contains(args[0].toLowerCase()))
            Halloween.getCandies().forEach(candy -> options.add(String.valueOf(candy.getId())));
        else if (args.length == 2 && Arrays.asList("progress", "reset").contains(args[0].toLowerCase()))
            context.getServerPlayerList().forEach(options::add);

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();

        return options.stream().filter(option -> option.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }

    private Block target(Player player) {
        Block block = player.getTargetBlock((Set<Material>) null, 6);

        return block != null && block.getType() == Material.SKULL ? block : null;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§5§l/halloween §7- Doces de Halloween");
        player.sendMessage("§e/halloween head <textura> §7- Recebe uma cabeça de doce.");
        player.sendMessage("§e/halloween add §7- Transforma a cabeça que você está olhando em doce.");
        player.sendMessage("§e/halloween remove [id] §7- Remove um doce (olhando ou pelo id).");
        player.sendMessage("§e/halloween list §7- Lista os doces.");
        player.sendMessage("§e/halloween tp <id> §7- Teleporta até um doce.");
        player.sendMessage("§e/halloween required <n> §7- Doces necessários para a tag (padrão: 10).");
        player.sendMessage("§e/halloween toggle §7- Ativa/desativa o evento.");
        player.sendMessage("§e/halloween progress <jogador> §7- Vê o progresso de alguém.");
        player.sendMessage("§e/halloween reset <jogador> §7- Zera o progresso e remove a tag.");
        player.sendMessage("§e/halloween reload §7- Recarrega o halloween.yml.");
    }
}
