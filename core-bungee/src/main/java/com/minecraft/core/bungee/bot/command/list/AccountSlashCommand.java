package com.minecraft.core.bungee.bot.command.list;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.bungee.bot.command.structure.SlashCommand;
import com.minecraft.core.bungee.bot.command.structure.category.SlashCommandCategory;
import com.minecraft.core.bungee.bot.command.structure.embed.SlashEmbed;
import com.minecraft.core.util.list.DateUtil;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.interaction.SlashCommandInteraction;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOptionBuilder;
import org.javacord.api.interaction.SlashCommandOptionType;

public class AccountSlashCommand extends SlashCommand {

    private final String NAME_OPTION_KEY = "nome";

    public AccountSlashCommand() {
        super("conta", "Colete informações dos jogadores.", SlashCommandCategory.SERVER);

        getOptions().add(
                // Parâmetro 1: Nome da conta (Obrigatório)
                new SlashCommandOptionBuilder()
                        .setType(SlashCommandOptionType.STRING)
                        .setName(NAME_OPTION_KEY)
                        .setDescription("Específique a conta.")
                        .setRequired(true)
                        .build()
        );
    }

    @Override
    public void execute(SlashCommandInteraction interaction) {
        SlashCommandInteractionOption optionName = interaction.getArgumentByName(NAME_OPTION_KEY).orElse(null);

        if (optionName != null) {
            String name = optionName.getStringValue().orElse("...");
            Account account = Core.getAccountData().of(name);

            if (account == null) {
                interaction.createImmediateResponder()
                        .setContent("Não foi possível encontrar a conta \"" + name + "\"...")
                        .respond();
                return;
            }

            EmbedBuilder embed = new SlashEmbed()
                    .setTitle("Informações de conta")
                    .setThumbnail(String.format(Constant.MINOTAR_AVATAR_API_URL, account.getName(), 120))
                    .addField("Nome", account.getName(), true)
                    .addField("Rank", account.getRank().getName(), true)
                    .addField("Entrou em", DateUtil.getDate(account.getContext().getCreatedAt()));

            interaction.createImmediateResponder()
                    .addEmbed(embed)
                    .respond();
        }
    }
}
