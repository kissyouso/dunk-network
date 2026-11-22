package com.minecraft.core.bungee.command;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.account.context.objects.toggle.Toggle;
import com.minecraft.core.bungee.command.structure.BungeeCommandContext;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.CommandSender;
import com.minecraft.core.command.annotation.Command;

import java.util.List;

public class ModeratorCommands implements CommandInheritor {

    @Command(name = "staffchat", aliases = {"sc"}, rank = RankType.HELPER)
    public void staffChat(BungeeCommandContext context) {
        Account account = context.getAccount();

        Toggle toggle = account.getToggle();

        toggle.setStaffChat(!toggle.isStaffChat());
        account.setToggle(toggle);

        account.send(toggle.isStaffChat() ? "§aVocê entrou no staffchat." : "§cVocê saiu do staffchat.");
    }

    @Command(name = "ac", aliases = {"alerta", "alertas"}, rank = RankType.HELPER)
    public void anticheatAlerts(BungeeCommandContext context) {
        Account account = context.getAccount();
        CommandSender sender = context.getSender();

        String[] args = context.getArgs();

        if (args.length > 0 && (args[0].equalsIgnoreCase("logs") || args[0].equalsIgnoreCase("log") || args[0].equalsIgnoreCase("historico"))) {
            List<String> logs = com.minecraft.core.bungee.listener.AntiCheatListener.recentAlerts();

            if (logs.isEmpty()) {
                sender.send("§cNenhuma flag recente do anticheat.");
                return;
            }

            sender.send("§c§lANTICHEAT §7- últimas " + Math.min(logs.size(), 15) + " flag(s):");

            logs.stream().limit(15).forEach(sender::send);
            return;
        }

        Toggle toggle = account.getToggle();

        if (args.length > 0 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off")))
            toggle.setAnticheatAlerts(args[0].equalsIgnoreCase("on"));
        else
            toggle.setAnticheatAlerts(!toggle.isAnticheatAlerts());

        account.setToggle(toggle);

        sender.send(toggle.isAnticheatAlerts()
                ? "§aVocê ativou os alertas do anticheat."
                : "§cVocê desativou os alertas do anticheat. §7Use /ac logs para ver as flags.");
    }

    @Command(name = "contas", aliases = {"alts"}, rank = RankType.HELPER, onlyPlayer = false, runAsync = true)
    public void sharedAccounts(BungeeCommandContext context) {
        CommandSender sender = context.getSender();

        String[] args = context.getArgs();

        if (args.length == 0) {
            sender.send("§cUtilize /" + context.getLabel() + " [jogador].");
            return;
        }

        Account account = context.getAccount(args[0]);

        if (account == null) {
            sender.send(TARGET_NOT_FOUND);
            return;
        }

        List<String> alts = com.minecraft.core.bungee.listener.AntiCheatListener.sharedAccounts(account.getName());

        if (alts.isEmpty()) {
            sender.send("§cNenhuma conta compartilhada com §f" + account.getName() + "§c.");
            return;
        }

        sender.send("§7Contas compartilhadas com §f" + account.getName() + " §8(§f" + alts.size() + "§8): §f" + String.join("§7, §f", alts));
    }

    @Command(name = "broadcast", aliases = {"bc"}, rank = RankType.ADMIN, onlyPlayer = false)
    public void broadcast(BungeeCommandContext context) {
        CommandSender sender = context.getSender();

        String[] args = context.getArgs();

        if (args.length == 0) {
            sender.send("§cUtilize /" + context.getLabel() + " (mensagem).");
            return;
        }

        String message = context.getMessage(0, args);

        Core.getPlatform().sendBroadcast(message);
    }

    @Command(name = "playerfind", rank = RankType.HELPER, runAsync = true, onlyPlayer = false)
    public void playerfinder(BungeeCommandContext context) {
        CommandSender sender = context.getSender();

        String[] args = context.getArgs();

        if (args.length == 0) {
            sender.send("§cUtilize /" + context.getLabel() + " [jogador].");
            return;
        }

        Account account = context.getAccount(args[0]);

        if (account == null) {
            sender.send(TARGET_NOT_FOUND);
            return;
        }

        List<Account> list = Core.getAccountData().getAlts(account.getIpAddress())
                .stream()
                .filter(alt -> !alt.equals(account))
                .toList();

        if (list.isEmpty())
            sender.send("§cEste jogador não possui contas alternativas.");
        else {
            StringBuilder builder = new StringBuilder();

            builder.append("§aContas Alternativas (§f").append(list.size()).append("§a): ");

            int index = 1;
            boolean end = false;
            for (Account alt : list) {
                if (index >= list.size()) end = true;

                builder.append("§f").append(alt.getName()).append(end ? "." : ", ");

                index++;
            }

            sender.send(builder.toString());
        }
    }
}
