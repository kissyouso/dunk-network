package com.minecraft.core.bungee.bot.controller;

import com.minecraft.core.api.punishment.Punishment;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.bungee.BungeeCore;
import com.minecraft.core.util.list.TimeUtil;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.webhook.IncomingWebhook;

import java.awt.*;

public class BotController {

    public static void sendPunishmentMessage(Punishment punishment) {
        try {
            if (BungeeCore.getInstance() == null || BungeeCore.getInstance().getBot() == null) {
                return;
            }

            IncomingWebhook webhook = BungeeCore.getInstance().getBot().getWebhook(System.getProperty("discord_webhook_url", ""));

            if (webhook != null) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("\uD83D\uDCDB Nova punição!")
                        .addField("ID", punishment.getId())
                        .addInlineField("Autor", punishment.getAuthorName())
                        .addInlineField("Jogador", punishment.getPlayerName())
                        .addInlineField("Categoria", punishment.getCategory().getName())
                        .addField("Motivo", punishment.getReason().getName())
                        .addInlineField("Servidor", punishment.getServer().getName());

                if (!punishment.getArcade().equals(ArcadeCategory.NONE))
                    embed.addInlineField("Modo", punishment.getArcade().getName());

                embed.addInlineField("Expira em", TimeUtil.formatTime(punishment.getExpiresAt(), TimeUtil.TimeFormat.SHORT));
                embed.addField("Motivo", "```" + punishment.getCause() + "```");
                embed.setFooter("Controle de Punições - Rede Style");

                webhook.sendMessage(embed).exceptionally(throwable -> null);
            }
        } catch (Exception ignored) {
        }
    }

    public static void sendRevocationMessage(Punishment punishment) {
        try {
            if (BungeeCore.getInstance() == null || BungeeCore.getInstance().getBot() == null) {
                return;
            }

            IncomingWebhook webhook = BungeeCore.getInstance().getBot().getWebhook(System.getProperty("discord_webhook_url", ""));

            if (webhook != null) {
                com.minecraft.core.account.Account revokerAccount = com.minecraft.core.Core.getAccountData().of(punishment.getRevocation().getAuthor());
                String revokerName = revokerAccount != null ? revokerAccount.getName() : "CONSOLE";

                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("✅ Punição Revogada")
                        .setColor(Color.PINK)
                        .addField("ID da Punição", punishment.getId())
                        .addInlineField("Jogador", punishment.getPlayerName())
                        .addInlineField("Categoria", punishment.getCategory().getName())
                        .addInlineField("Revogado por", revokerName)
                        .addField("Motivo", "```" + punishment.getRevocation().getReason() + "```");


                webhook.sendMessage(embed).exceptionally(throwable -> null);
            }
        } catch (Exception ignored) {
        }
    }
}
