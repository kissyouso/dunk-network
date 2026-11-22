package com.minecraft.core.bungee.command;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.api.punishment.Punishment;
import com.minecraft.core.api.punishment.objects.enums.PunishmentCategory;
import com.minecraft.core.api.punishment.objects.enums.PunishmentPreset;
import com.minecraft.core.api.punishment.objects.enums.PunishmentReason;
import com.minecraft.core.backend.database.redis.message.types.account.AccountPunishMenuMessage;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.bungee.BungeeCore;
import com.minecraft.core.bungee.bot.controller.BotController;
import com.minecraft.core.bungee.command.structure.BungeeCommandContext;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.CommandSender;
import com.minecraft.core.command.annotation.Command;
import com.minecraft.core.util.list.TimeUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.*;

public class PunishmentCommands implements CommandInheritor {

    @Command(name = "punir", aliases = {"punish", "p"}, rank = RankType.HELPER, onlyPlayer = false)
    public void punish(BungeeCommandContext context) {
        CommandSender sender = context.getSender();
        String[] args = context.getArgs();

        if (args.length == 1) {
            Account target = context.getAccount(args[0]);
            
            if (target == null) {
                sender.send(TARGET_NOT_FOUND);
                return;
            }

            if (sender.getId().equals(target.getId())) {
                sender.send("§cVocê não pode punir a si mesmo.");
                return;
            }

            if (sender.isPlayer()) {
                Account account = context.getAccount();
                if (!account.hasRank(target.getRankType()) || account.hasOnlyRank(target.getRankType())) {
                    sender.send("§cVocê não pode punir alguém superior ou igual a você.");
                    return;
                }
            }

            /* Jogador: abre o menu no servidor onde ele está. Console: escolha pelo chat */
            if (sender.isPlayer()) {
                new AccountPunishMenuMessage(sender.getId(), target.getId()).send();
                return;
            }

            showCategories(sender, target.getName());
            return;
        }

        if (args.length == 2) {
            PunishmentCategory category = PunishmentCategory.of(args[0]);
            
            if (category == null) {
                sender.send("§cCategoria inválida.");
                return;
            }

            Account target = context.getAccount(args[1]);
            
            if (target == null) {
                sender.send(TARGET_NOT_FOUND);
                return;
            }

            if (sender.getId().equals(target.getId())) {
                sender.send("§cVocê não pode punir a si mesmo.");
                return;
            }

            if (sender.isPlayer()) {
                Account account = context.getAccount();
                if (!account.hasRank(target.getRankType()) || account.hasOnlyRank(target.getRankType())) {
                    sender.send("§cVocê não pode punir alguém superior ou igual a você.");
                    return;
                }
            }

            showReasons(sender, category, target.getName());
            return;
        }

        if (args.length < 4) {
            sender.send("§cUso: /" + context.getLabel() + " [jogador] - Para modo interativo");
            sender.send("§cOu: /" + context.getLabel() + " [categoria] [causa] [tempo] [jogador] [motivo] - Modo manual");
            return;
        }

        PunishmentCategory category = PunishmentCategory.of(args[0]);

        if (category == null) {
            sender.send("§cA categoria solicitada não foi encontrada.");
            return;
        }

        PunishmentReason reason = PunishmentReason.of(args[1]);

        if (reason == null) {
            sender.send("§cA causa solicitada não foi encontrada.");
            return;
        }

        long expiresAt = -1L;

        boolean isValidTime = TimeUtil.isValidTime(args[2]);

        if (isValidTime)
            expiresAt = TimeUtil.getTime(args[2]);

        Account target = context.getAccount(args[isValidTime ? 3 : 2]);

        if (target == null) {
            sender.send(TARGET_NOT_FOUND);
            return;
        }

        if (sender.getId().equals(target.getId())) {
            sender.send("§cVocê não pode punir a si mesmo.");
            return;
        }

        if (sender.isPlayer()) {
            Account account = context.getAccount();

            if (!account.hasRank(target.getRankType()) || account.hasOnlyRank(target.getRankType())) {
                sender.send("§cVocê não pode punir alguém superior ou igual a você.");
                return;
            }
        }

        if (target.getActivePunishment(category) != null) {
            sender.send("§cO jogador " + target.getName() + " já possui uma punição em aberto.");
            return;
        }

        String motive = context.getMessage(isValidTime ? 4 : 3, args);

        /* Motivo padrão: respeita o cargo mínimo dele */
        PunishmentPreset preset = PunishmentPreset.ofName(motive);

        if (preset != null && sender.isPlayer() && !context.getAccount().hasRank(preset.getMinimumRank())) {
            sender.send("§cO motivo " + preset.getName() + " só pode ser aplicado por " + preset.getMinimumRank().getColoredName() + "§c ou superior.");
            return;
        }

        Punishment punishment = Punishment.builder()
                .author(sender.getId())
                .player(target.getId())
                .server(target.getServerType())
                .arcade(target.getRoute().isValidArcade() ? target.getArcadeRoute().getArcade() : ArcadeCategory.NONE)
                .ip(target.getIpAddress())
                .reason(reason)
                .category(category)
                .cause(motive)
                .expiresAt(expiresAt)
                .build();

        Core.getPunishmentData().save(punishment);

        if (category.equals(PunishmentCategory.BAN) && target.proxiedPlayer() != null)
            target.proxiedPlayer().disconnect(TextComponent.fromLegacyText(String.format(Constant.BAN_TEMPLATE_MESSAGE,
                    punishment.isTemporary() ? "temporariamente" : "permanentemente",
                    reason.getName(),
                    punishment.getId())));
        else if (category.equals(PunishmentCategory.MUTE))
            target.send("§cVocê foi " + (punishment.isTemporary() ? "temporariamente" : "permanentemente") + " mutado no servidor.");

        sender.send("§cVocê puniu o jogador " + target.getName() + " por " + reason.getName() + ".");

        log(sender, sender.getName() + " puniu " + target.getName() + " por " + motive + " (" + category.getName() + "/" + reason.getName() + ")");

        if (BungeeCore.getInstance().getBot() != null) {
            BotController.sendPunishmentMessage(punishment);
        }
    }

    private void showCategories(CommandSender sender, String targetName) {
        sender.send("§eSelecione uma categoria para punir: §a" + targetName);

        TextComponent message = new TextComponent();

        TextComponent banButton = new TextComponent("§cBanimento");
        banButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            TextComponent.fromLegacyText("§7Clique para ver os motivos")));
        banButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
            "/punir " + PunishmentCategory.BAN.name() + " " + targetName));
        message.addExtra(banButton);
        message.addExtra(" §7- ");

        TextComponent muteButton = new TextComponent("§eMute");
        muteButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            TextComponent.fromLegacyText("§7Clique para ver os motivos")));
        muteButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
            "/punir " + PunishmentCategory.MUTE.name() + " " + targetName));
        message.addExtra(muteButton);

        sender.send(message);
    }

    private void showReasons(CommandSender sender, PunishmentCategory category, String targetName) {
        List<PunishmentPreset> presets = PunishmentPreset.of(category);

        if (presets.isEmpty()) {
            sender.send("§cNenhum motivo disponível para esta categoria.");
            return;
        }

        sender.send("");
        sender.send("§eMotivos de " + category.getName() + " para: §a" + targetName);
        sender.send("");

        TextComponent message = new TextComponent();
        int index = 1;

        for (PunishmentPreset preset : presets) {
            String duration = preset.isPermanent() ? "§cPermanente" : "§7" + preset.getDurationName();

            TextComponent reasonButton = new TextComponent("§7[" + index++ + "] §f" + preset.getName() + " §8(" + duration + "§8)");
            reasonButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    TextComponent.fromLegacyText("§7Categoria: §f" + category.getName()
                            + "\n§7Duração: §f" + preset.getDurationName()
                            + "\n§7Cargo mínimo: " + preset.getMinimumRank().getColoredName()
                            + "\n\n§7" + String.join(" ", preset.getDescription())
                            + "\n\n§eClique para aplicar esta punição")));

            reasonButton.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + preset.toCommand(targetName)));

            message.addExtra(reasonButton);
            message.addExtra("\n");
        }

        sender.send(message);
    }

    @Command(name = "despunir", aliases = {"unpunish", "unban", "unmute"}, rank = RankType.HELPER, onlyPlayer = false)
    public void unpunish(BungeeCommandContext context) {
        CommandSender sender = context.getSender();

        String[] args = context.getArgs();

        if (args.length < 2) {
            sender.send("§cUso: /" + context.getLabel() + " [ID da punição] [motivo].");
            sender.send("§7Você pode encontrar o ID da punição na mensagem de banimento ou usando /conta [jogador].");
            return;
        }

        String punishmentId = args[0];
        Punishment punishment = Core.getPunishmentData().of(punishmentId);

        if (punishment == null) {
            sender.send("§cPunição com ID \"" + punishmentId + "\" não foi encontrada.");
            return;
        }

        if (!punishment.isValid()) {
            sender.send("§cEsta punição já foi revogada anteriormente.");
            return;
        }

        if (punishment.getRevocation().isValid()) {
            sender.send("§cEsta punição já foi revogada.");
            return;
        }

        Account target = Core.getAccountData().of(punishment.getPlayer());

        /* Hierarquia: ninguém revoga punição de cargo igual ou superior ao seu. */
        if (sender.isPlayer() && target != null) {
            Account account = context.getAccount();

            if (!account.hasRank(target.getRankType()) || account.hasOnlyRank(target.getRankType())) {
                sender.send("§cVocê não pode revogar a punição de alguém superior ou igual a você.");
                return;
            }
        }

        String reason = context.getMessage(1, args);

        punishment.revoke(sender.getId(), reason);

        if (target != null) {
            if (punishment.getCategory().equals(PunishmentCategory.BAN)) {
                sender.send("§aVocê desbaniu o jogador " + target.getName() + ".");
            } else if (punishment.getCategory().equals(PunishmentCategory.MUTE)) {
                sender.send("§aVocê desmutou o jogador " + target.getName() + ".");
            } else {
                sender.send("§aVocê revogou a punição do jogador " + target.getName() + ".");
            }
        } else {
            sender.send("§aVocê revogou a punição ID \"" + punishmentId + "\".");
        }

        log(sender, sender.getName() + " revogou a punição " + punishmentId + " de " + punishment.getPlayerName() + " por " + reason);

        if (BungeeCore.getInstance().getBot() != null) {
            BotController.sendRevocationMessage(punishment);
        }
    }
}
