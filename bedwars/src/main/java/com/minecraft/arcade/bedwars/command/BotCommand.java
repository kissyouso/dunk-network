package com.minecraft.arcade.bedwars.command;

import com.minecraft.arcade.bedwars.arcade.arena.Arena;
import com.minecraft.arcade.bedwars.menu.management.BotMenu;
import com.minecraft.arcade.bedwars.structure.bot.BridgeStyle;
import com.minecraft.arcade.bedwars.structure.bot.BotController;
import com.minecraft.arcade.bedwars.structure.bot.BotMode;
import com.minecraft.arcade.bedwars.structure.bot.BotProfile;
import com.minecraft.arcade.bedwars.user.User;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.arcade.room.phase.RoomPhase;
import com.minecraft.core.bukkit.command.structure.BukkitCommandContext;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.annotation.Command;
import org.bukkit.entity.Player;

import java.util.stream.Stream;

/**
 * Controle da IA de treino da sala. Os ajustes caem no perfil daquela sala
 * (guardado nas propriedades do room), nunca no config.yml do servidor, então o
 * administrador testa um ajuste e a próxima partida já nasce com ele.
 */
public class BotCommand implements CommandInheritor {

    private static final String USAGE = "§7/bwbot §f[menu|status|on|off|mode|bridge|speed|strafe|engage|group|team|spawn|clear]";

    @Command(name = "bwbot", aliases = {"bots"}, rank = RankType.ADMIN)
    public void bots(BukkitCommandContext context) {
        Player player = context.getPlayer();

        String[] args = context.getArgs();

        User user = (User) User.of(player.getUniqueId());

        if (user == null || user.getArena() == null) {
            player.sendMessage("§cEntre em uma sala do BedWars para usar o /bwbot.");
            return;
        }

        Arena arena = user.getArena();
        BotProfile profile = BotController.profile(arena);

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            new BotMenu(player, arena).handle();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> {
                status(player, profile);
                return;
            }
            case "on", "off" -> profile.setEnabled(args[0].equalsIgnoreCase("on"));
            case "mode" -> {
                BotMode mode = BotMode.named(args.length > 1 ? args[1] : null);

                if (mode == null) {
                    player.sendMessage("§cModo inválido. §f" + options(BotMode.values()));
                    return;
                }

                profile.setMode(mode);
                player.sendMessage("§aModo dos bots: §f" + mode.getLabel() + " §7(" + mode.getDescription() + "§7).");
            }
            case "bridge" -> {
                BridgeStyle style = BridgeStyle.named(args.length > 1 ? args[1] : null);

                if (style == null) {
                    player.sendMessage("§cEstilo de ponte inválido. §f" + options(BridgeStyle.values()));
                    return;
                }

                profile.setBridgeStyle(style);
                player.sendMessage("§aEstilo de ponte: §f" + style.getLabel() + "§a.");
            }
            case "speed" -> {
                Double speed = number(player, args, 0.1, 0.6);

                if (speed == null) return;

                profile.setSpeed(speed);
                player.sendMessage("§aVelocidade dos bots: §f" + speed + "§a.");
            }
            case "strafe" -> {
                Double strafe = number(player, args, 0.0, 1.0);

                if (strafe == null) return;

                profile.setStrafe(strafe);
                player.sendMessage("§aStrafe dos bots: §f" + strafe + "§a.");
            }
            case "engage" -> {
                Double engage = number(player, args, 4.0, 40.0);

                if (engage == null) return;

                profile.setEngageDistance(engage);
                player.sendMessage("§aDistância de combate: §f" + engage + "§a blocos.");
            }
            case "group" -> {
                boolean group = args.length < 2 || args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("on");

                profile.setGroupInOneTeam(group);
                profile.setTeam("");

                player.sendMessage("§aBots " + (group ? "juntos, no mesmo time adversário" : "espalhados, um por time livre") + "§a.");
            }
            case "team", "time" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /bwbot team <ROSA|AMARELO|AZUL|VERMELHO|...|livre>");
                    return;
                }

                profile.setGroupInOneTeam(true);
                profile.setTeam(args[1].equalsIgnoreCase("livre") ? "" : args[1].toUpperCase());

                player.sendMessage(profile.getTeam().isEmpty()
                        ? "§aTime dos bots: o primeiro time livre da sala."
                        : "§aTime dos bots: §f" + profile.getTeam() + "§a.");
            }
            case "spawn" -> {
                if (!arena.isPhase(RoomPhase.PLAYING)) {
                    player.sendMessage("§cSó é possível mandar bots durante a partida.");
                    return;
                }

                int count = args.length > 1 ? clampParse(args[1], 1, 1, 8) : 1;
                int created = BotController.add(arena, count);

                player.sendMessage(created > 0
                        ? "§a" + created + " bot(s) entraram na sala."
                        : "§cNenhum time com base disponível para o bot.");

                return;
            }
            case "clear" -> {
                int alive = BotController.alive(arena);

                BotController.despawn(arena);

                player.sendMessage("§a" + alive + " bot(s) saíram da sala.");
                return;
            }
            default -> {
                player.sendMessage(USAGE);
                return;
            }
        }

        /* A entidade continua a mesma; só o comportamento muda. */
        BotController.reload(arena);

        player.sendMessage("§aBots da sala " + arena.getIdentifier() + " atualizados. §7" + USAGE);
    }

    private void status(Player player, BotProfile profile) {
        player.sendMessage("§6§lPERFIL DOS BOTS");
        player.sendMessage("§7Ativos: " + (profile.isEnabled() ? "§asim" : "§cnão")
                + " §7| Máximo: §f" + profile.getMaxTotal()
                + " §7| Por time: §f" + profile.getMaxPerTeam());
        player.sendMessage("§7Modo: §f" + profile.getMode().getLabel()
                + " §7| Ponte: §f" + profile.getBridgeStyle().getLabel()
                + " §7(" + profile.getBlocksPerSecond() + " blocos/s)");
        player.sendMessage("§7Velocidade: §f" + profile.getSpeed() + " §7| Strafe: §f" + profile.getStrafe()
                + " §7| Engaje: §f" + profile.getEngageDistance() + " §7| Retirada: §f" + profile.getRetreatHealth() + "❤");
        player.sendMessage("§7Agrupados no mesmo time: " + (profile.isGroupInOneTeam() ? "§asim" : "§cnão")
                + (profile.getTeam().isEmpty() ? "" : " §7Time: §f" + profile.getTeam()));
    }

    private Double number(Player player, String[] args, double min, double max) {
        if (args.length < 2) {
            player.sendMessage("§cInforme um número entre §f" + min + "§c e §f" + max + "§c.");
            return null;
        }

        try {
            return Math.max(min, Math.min(max, Double.parseDouble(args[1].replace(',', '.'))));
        } catch (NumberFormatException exception) {
            player.sendMessage("§c" + args[1] + " não é um número.");
            return null;
        }
    }

    private int clampParse(String value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /** Lista as opções de um enum para a mensagem de uso. */
    private String options(Enum<?>[] values) {
        return Stream.of(values).map(Enum::name).reduce((left, right) -> left + " §7| §f" + right).orElse("");
    }
}