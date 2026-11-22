package com.minecraft.core.bungee.command.single;

import com.minecraft.core.account.context.objects.medal.Medal;
import com.minecraft.core.account.context.objects.permission.Permission;
import com.minecraft.core.api.punishment.objects.enums.PunishmentCategory;
import com.minecraft.core.backend.database.redis.message.types.account.AccountPunishmentsMessage;
import com.minecraft.core.bungee.command.structure.BungeeCommandContext;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.assignment.Assignment;
import com.minecraft.core.account.context.objects.rank.Rank;
import com.minecraft.core.account.context.objects.rank.info.RankInfo;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.api.geography.GeographyService;
import com.minecraft.core.api.geography.GeographyBuilder;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.CommandSender;
import com.minecraft.core.command.annotation.Command;
import com.minecraft.core.command.annotation.Completer;
import com.minecraft.core.util.Util;
import com.minecraft.core.util.list.DateUtil;
import com.minecraft.core.util.list.TimeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AccountCommand implements CommandInheritor {

    private final List<String> subCommands = Arrays.asList("rank", "golds", "permission", "punishments", "medal");

    /* Prefixo das mensagens dos comandos administrativos */
    private static final String PREFIX = "§2§lACC §8» §r";

    private static String usage(String label, String sub, String... lines) {
        StringBuilder builder = new StringBuilder(PREFIX + "§7Como usar §f" + label + " " + sub + "§7:");

        for (String line : lines)
            builder.append("\n §8▪ §f").append(label).append(" [jogador] ").append(line);

        return builder.toString();
    }

    private static String duration(long expiresAt) {
        return expiresAt <= 0 ? "§7permanente" : "§7por §f" + TimeUtil.formatTime(expiresAt, TimeUtil.TimeFormat.SHORT);
    }

    @Completer(name = "account", subCommands = {"acc"}, rank = RankType.HELPER)
    public List<String> accountCompleter(BungeeCommandContext context) {
        return getPlayerNames(context);
    }

    @Command(name = "account", aliases = {"acc"}, onlyPlayer = false)
    public void account(BungeeCommandContext context) {
        CommandSender sender = context.getSender();

        String[] args = context.getArgs();

        Account staffer = context.getAccount();

        if (args.length == 0) {
            if (!sender.isPlayer()) {
                sender.send("§cUso: /acc <jogador>");
                return;
            }
            
            if (staffer == null) {
                sender.send("§cNão foi possível carregar sua conta.");
                return;
            }
            
            sendInfoMessage(sender, staffer);
            return;
        }

        if (sender.isPlayer() && staffer != null && !staffer.hasRank(RankType.HELPER)) {
            sender.send("§cVocê não pode ver a conta de outros jogadores.");
            return;
        }

        Account target = context.getAccount(args[0]);

        if (target == null) {
            sender.send(TARGET_NOT_FOUND);
            return;
        }

        if (args.length == 1) {
            sendInfoMessage(sender, target);
            return;
        }

        String label = "/" + context.getLabel();

        String search = args[1].toLowerCase();

        if (subCommands.stream().noneMatch(subCommand -> subCommand.equalsIgnoreCase(search))) {
            sender.send(usage(label, "", "rank §7- ranks do jogador", "permission §7- permissões extras", "golds §7- golds da conta",
                    "medal §7- medalhas", "punishments §7- histórico de punições"));
            return;
        }

        switch (search) {
            case "rank": {
                if (args.length == 2) {
                    sender.send(usage(label, "rank", "rank set §7<rank> [tempo]", "rank remove §7<rank>", "rank info"));
                    return;
                }

                switch (args[2].toLowerCase()) {
                    case "set": {
                        if (args.length == 3) {
                            sender.send(PREFIX + "§cUso: §f" + label + " [jogador] rank set <rank> [tempo]§c.");
                            return;
                        }

                        RankType rankType = RankType.of(args[3]);

                        if (rankType == null) {
                            sender.send(PREFIX + "§cO rank solicitado não foi encontrado.");
                            return;
                        }

                        if (sender.isPlayer()) {
                            assert staffer != null;
                            if (staffer.getRankType().equals(rankType) || !staffer.hasRank(rankType)) {
                                sender.send(PREFIX + "§cVocê não pode atribuir ranks similares ou iguais ao seu.");
                                return;
                            }
                        }

                        if (sender.isPlayer()) {
                            assert staffer != null;
                            if (!staffer.hasRank(target.getRank().getType())) {
                                sender.send(PREFIX + "§cVocê não pode atribuir ranks para jogadores superiores.");
                                return;
                            }
                        }

                        if (target.hasAvailableRank(rankType) || target.hasOnlyRank(rankType)) {
                            sender.send("§cO jogador " + target.getName() + " já possui o rank " + rankType.getName() + ".");
                            return;
                        }

                        long expiresAt = -1L;

                        if (args.length >= 5)
                            expiresAt = TimeUtil.getTime(args[4]);

                        Rank rank = Rank.builder()
                                .type(rankType)
                                .assignment(sender.isPlayer() ? Assignment.STAFF : Assignment.CONSOLE)
                                .author(sender.getId())
                                .expiresAt(expiresAt)
                                .build();

                        target.setRank(rank);

                        sender.send(PREFIX + "§aRank " + rankType.getColor() + rankType.getName() + " §aadicionado para §f" + target.getNickname() + " §8(" + duration(expiresAt) + "§8)§a.");

                        log(sender, sender.getName() + " adicionou o rank " + rankType.getName() + " na conta de " + target.getNickname());
                        break;
                    }

                    case "remove": {
                        if (args.length == 3) {
                            sender.send(PREFIX + "§cUso: §f" + label + " [jogador] rank remove <rank>§c.");
                            return;
                        }

                        RankType rankType = RankType.of(args[3]);

                        if (rankType == null) {
                            sender.send(PREFIX + "§cO rank solicitado não foi encontrado.");
                            return;
                        }

                        if (rankType.equals(RankType.MEMBER)) {
                            sender.send("§cVocê não pode remover o rank " + RankType.MEMBER.getName() + " da conta de " + target.getNickname() + ".");
                            return;
                        }

                        if (!(target.hasOnlyRank(rankType) || target.hasAvailableRank(rankType))) {
                            sender.send("§cO jogador " + target.getNickname() + " não possui o rank " + rankType.getName() + ".");
                            return;
                        }

                        target.removeRank(rankType);

                        sender.send(PREFIX + "§eRank " + rankType.getColor() + rankType.getName() + " §eremovido de §f" + target.getNickname() + "§e.");

                        log(sender, sender.getName() + " removeu o rank " + rankType.getName() + " da conta de " + target.getNickname());

                        break;
                    }

                    case "info": {
                        RankInfo info = target.getContext().getRankInfo();

                        Rank rank = info.getRank();
                        List<Rank> rankList = info.getAvailableRanks();

                        /* Criar mensagem de lista dos ranks */
                        StringBuilder builder = new StringBuilder();

                        builder.append(" §8▪ §7Outros ranks (§f").append(rankList.size()).append("§7): ");

                        int index = 1;
                        boolean end = false;
                        for (Rank extraRank : rankList) {
                            if (index >= rankList.size()) end = true;

                            builder.append(extraRank.getColoredName()).append("§f").append(end ? "." : ", ");

                            index++;
                        }

                        if (rankList.isEmpty())
                            builder.append("§fnenhum.");

                        sender.send(PREFIX + "§7Ranks de §f" + target.getNickname() + "§7:",
                                " §8▪ §7Ativo: " + rank.getColoredName() + " §8(" + duration(rank.getExpiresAt()) + "§8)",
                                " §8▪ §7Por: §f" + rank.getAuthorName() + " §8• §7em §f" + DateUtil.getDate(rank.getAssignedAt()) + " §8• §6§o" + rank.getAssignment().getName(),
                                builder.toString());
                        break;
                    }
                }
                break;
            }

            case "permission": {
                if (args.length == 2) {
                    sender.send(usage(label, "permission", "permission set §7<chave> [tempo]", "permission remove §7<chave>", "permission list"));
                    return;
                }

                switch (args[2].toLowerCase()) {
                    case "set": {
                        if (args.length <= 3) {
                            sender.send(PREFIX + "§cUso: §f" + label + " [jogador] permission set <chave> [tempo]§c.");
                            return;
                        }

                        String key = args[3];

                        if (target.hasPermission(key)) {
                            sender.send(PREFIX + "§c" + target.getNickname() + " já tem a permissão §f" + key + "§c.");
                            return;
                        }

                        /* Sem tempo = permanente */
                        long expiresAt = args.length >= 5 ? TimeUtil.getTime(args[4]) : -1L;

                        Permission permission = new Permission(key, sender.isPlayer() ? Assignment.STAFF : Assignment.CONSOLE, sender.getId(), expiresAt);

                        target.setPermission(permission);

                        sender.send(PREFIX + "§aPermissão §f" + key + " §aadicionada para §f" + target.getNickname() + " §8(" + duration(expiresAt) + "§8)§a.");

                        log(sender, sender.getName() + " adicionou a permissão " + key + " na conta de " + target.getNickname());
                        break;
                    }

                    case "remove": {
                        if (args.length == 3) {
                            sender.send(PREFIX + "§cUso: §f" + label + " [jogador] permission remove <chave>§c.");
                            return;
                        }

                        String key = args[3];

                        if (!target.hasPermission(key)) {
                            sender.send(PREFIX + "§c" + target.getNickname() + " não tem a permissão §f" + key + "§c.");
                            return;
                        }

                        target.removePermission(key);

                        sender.send(PREFIX + "§ePermissão §f" + key + " §eremovida de §f" + target.getNickname() + "§e.");

                        log(sender, sender.getName() + " removeu a permissão " + key + " da conta de " + target.getNickname());
                        break;
                    }

                    case "list": {
                        List<Permission> list = target.getPermissions();

                        if (list.isEmpty()) {
                            sender.send(PREFIX + "§c" + target.getNickname() + " não possui permissões extras.");
                            return;
                        }

                        StringBuilder builder = new StringBuilder(PREFIX + "§7Permissões de §f" + target.getNickname() + " §7(§f" + list.size() + "§7): ");


                        int index = 1;
                        boolean end = false;
                        for (Permission permission : list) {
                            if (index >= list.size()) end = true;

                            builder.append("§f").append(permission.getKey()).append(end ? "." : ", ");

                            index++;
                        }

                        sender.send(builder.toString());
                        break;
                    }
                }

                break;
            }

            case "punishments": {
                if (!sender.isPlayer()) {
                    sender.send("§cSomente jogadores podem visualizar o histórico de punições.");
                    return;
                }

                if (args.length == 2) {
                    sender.send(PREFIX + "§cUso: §f" + label + " [jogador] punishments <ban|mute>§c.");
                    return;
                }

                PunishmentCategory category = PunishmentCategory.of(args[2]);

                if (category == null) {
                    sender.send("§cA categoria '" + args[2] + "' não foi encontrada.");
                    return;
                }

                if (target.getPunishments(category).isEmpty()) {
                    sender.send("§cO jogador " + target.getNickname() + " não possui um histórico de " + category.getName() + "s.");
                    return;
                }

                new AccountPunishmentsMessage(sender.getId(), target.getId(), category).send();
                break;
            }

            case "golds": {
                if (args.length == 2) {
                    sender.send(usage(label, "golds", "golds add §7<valor>", "golds remove §7<valor>"));
                    return;
                }

                switch (args[2].toLowerCase()) {
                    case "add": {
                        if (args.length == 3) {
                            sender.send(PREFIX + "§cUso: §f" + label + " [jogador] golds add <valor>§c.");
                            return;
                        }

                        if (!Util.isNumber(args[3])) {
                            sender.send(PREFIX + "§cSomente números são válidos.");
                            return;
                        }

                        int value = Integer.parseInt(args[3]);

                        if (value <= 0) {
                            sender.send("§cO valor de ouros não pode ser menor ou igual a zero.");
                            return;
                        }

                        target.addGolds(value);

                        sender.send(PREFIX + "§a+§6" + Util.formatNumber(value) + " golds §apara §f" + target.getNickname() + " §8(§7total: §6" + Util.formatNumber(target.getGolds()) + "§8)§a.");

                        log(sender, sender.getName() + " adicionou " + value + " golds na conta de " + target.getNickname());
                        break;
                    }
                    case "remove": {
                        if (args.length == 3) {
                            sender.send(PREFIX + "§cUso: §f" + label + " [jogador] golds remove <valor>§c.");
                            return;
                        }

                        if (!Util.isNumber(args[3])) {
                            sender.send(PREFIX + "§cSomente números são válidos.");
                            return;
                        }

                        int value = Integer.parseInt(args[3]);

                        if (value <= 0) {
                            sender.send("§cO valor de golds não pode ser menor ou igual a zero.");
                            return;
                        }

                        target.removeGolds(value);

                        sender.send(PREFIX + "§e-§6" + Util.formatNumber(value) + " golds §ede §f" + target.getNickname() + " §8(§7total: §6" + Util.formatNumber(target.getGolds()) + "§8)§e.");

                        log(sender, sender.getName() + " removeu " + value + " golds da conta de " + target.getNickname());
                        break;
                    }
                }

                break;
            }

            case "medal": {
                if (args.length == 2) {
                    sender.send(usage(label, "medal", "medal add §7<nome>", "medal remove §7<nome>", "medal set §7<nome>", "medal list"));
                    return;
                }

                switch (args[2].toLowerCase()) {
                    case "add": {
                        if (args.length == 3) {
                            sender.send("§cUso: " + label + " [jogador] medal add (nome).");
                            return;
                        }

                        Medal medal = Medal.of(args[3]);

                        if (medal == null) {
                            sender.send("§cA medalha '" + args[3] + "' não foi encontrada.");
                            return;
                        }

                        if (medal.equals(Medal.NONE)) {
                            sender.send("§cVocê não pode adicionar a medalha " + medal.getColoredName() + ".");
                            return;
                        }

                        if (target.hasMedal(medal)) {
                            sender.send("§cO jogador " + target.getNickname() + " já possui a medalha " + medal.getColoredName() + ".");
                            return;
                        }

                        target.addMedal(medal);

                        sender.send(PREFIX + "§aMedalha " + medal.getColoredName() + " §aadicionada para §f" + target.getNickname() + "§a.");
                        log(sender, sender.getName() + " adicionou a medalha " + medal.getName() + " na conta de " + target.getNickname());
                        break;
                    }

                    case "remove": {
                        if (args.length == 3) {
                            sender.send("§cUso: " + label + " [jogador] medal remove (nome).");
                            return;
                        }

                        Medal medal = Medal.of(args[3]);

                        if (medal == null) {
                            sender.send("§cA medalha '" + args[3] + "' não foi encontrada.");
                            return;
                        }

                        if (medal.equals(Medal.NONE)) {
                            sender.send("§cVocê não pode remover a medalha " + medal.getColoredName() + ".");
                            return;
                        }

                        if (!target.hasMedal(medal)) {
                            sender.send("§cO jogador " + target.getNickname() + " não possui a medalha " + medal.getColoredName() + ".");
                            return;
                        }

                        target.removeMedal(medal);

                        // Se a medalha removida estava sendo usada, definir como NONE
                        if (target.isUsingMedal(medal)) {
                            target.setMedal(Medal.NONE);
                        }

                        sender.send(PREFIX + "§eMedalha " + medal.getColoredName() + " §eremovida de §f" + target.getNickname() + "§e.");
                        break;
                    }

                    case "set": {
                        if (args.length == 3) {
                            sender.send("§cUso: " + label + " [jogador] medal set (nome).");
                            return;
                        }

                        Medal medal = Medal.of(args[3]);

                        if (medal == null) {
                            sender.send("§cA medalha '" + args[3] + "' não foi encontrada.");
                            return;
                        }

                        if (!target.hasMedal(medal)) {
                            sender.send("§cO jogador " + target.getNickname() + " não possui a medalha " + medal.getColoredName() + ".");
                            return;
                        }

                        target.setMedal(medal);

                        sender.send(PREFIX + "§aMedalha ativa de §f" + target.getNickname() + "§a: " + medal.getColoredName() + "§a.");
                        break;
                    }

                    case "list": {
                        List<Medal> medals = target.getMedals();
                        Medal activeMedal = target.getMedal();

                        if (medals.isEmpty()) {
                            sender.send("§cO jogador " + target.getNickname() + " não possui medalhas.");
                            return;
                        }

                        StringBuilder builder = new StringBuilder(PREFIX + "§7Medalhas de §f" + target.getNickname() + " §7(§f" + medals.size() + "§7): ");

                        int index = 1;
                        boolean end = false;
                        for (Medal medal : medals) {
                            if (index >= medals.size()) end = true;

                            String prefix = medal.equals(activeMedal) ? "§e[ATIVA] " : "";
                            builder.append(prefix).append(medal.getColoredName()).append("§f").append(end ? "." : ", ");

                            index++;
                        }

                        sender.send(builder.toString());
                        if (activeMedal != null && !activeMedal.equals(Medal.NONE)) {
                            sender.send("§aMedalha ativa: " + activeMedal.getColoredName());
                        }
                        break;
                    }
                }

                break;
            }
        }
    }

    protected void sendInfoMessage(CommandSender sender, Account account) {
        List<String> message = new ArrayList<>(Arrays.asList(
                "§aUsuário: §f" + account.getName(),
                "§aTipo: §f" + (account.isPremium() ? "Original" : "Pirata"),
                "§aPrimeira entrada: §f" + DateUtil.getDate(account.getContext().getCreatedAt()),
                "§aÚltima entrada: §f" + DateUtil.getDate(account.getContext().getLastLogin())
        ));

        for (Rank rank : account.getAvailableRanks()) {
            message.addAll(Arrays.asList(
                    "§aRank: " + rank.getColoredName(),
                    " §7Autor: " + rank.getAuthorName(),
                    " §7Atribuído em: " + DateUtil.getDate(rank.getAssignedAt())));
        }

        message.addAll(Arrays.asList(
                "§aRank: " + account.getRank().getColoredName(),
                " §7Autor: " + account.getRank().getAuthorName(),
                " §7Atribuído em: " + DateUtil.getDate(account.getRank().getAssignedAt())
        ));

        CompletableFuture.runAsync(() -> {
            GeographyBuilder geography = GeographyService.getGeography(account.getIpAddress());

            if (geography.isValid())
                message.addAll(Arrays.asList(
                        "§aLocalização:",
                        " §7País: " + geography.getCountry(),
                        " §7Estado: " + geography.getRegion(),
                        " §7Cidade: " + geography.getCity(),
                        " §7ASN: " + geography.getAsn()
                ));
        });

        sender.send(message);
    }
}
