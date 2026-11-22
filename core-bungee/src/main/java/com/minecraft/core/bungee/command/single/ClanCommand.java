package com.minecraft.core.bungee.command.single;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.clan.request.ClanRequest;
import com.minecraft.core.api.clan.Clan;
import com.minecraft.core.api.clan.color.ClanColor;
import com.minecraft.core.api.clan.member.ClanMember;
import com.minecraft.core.api.clan.prefix.ClanPrefix;
import com.minecraft.core.api.clan.role.ClanRole;
import com.minecraft.core.backend.data.list.api.ClanData;
import com.minecraft.core.backend.database.redis.message.types.account.AccountClanChangeMessage;
import com.minecraft.core.bungee.command.structure.BungeeCommandContext;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.annotation.Command;
import com.minecraft.core.command.annotation.Completer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class ClanCommand implements CommandInheritor {

    private final ClanData data = Core.getClanData();

    protected final Pattern NAME_VALIDATOR = Pattern.compile("^[a-zA-Z0-9]{3,12}$"),
            TAG_VALIDATOR = Pattern.compile("^[a-zA-Z0-9]{4,8}$");

    private final String ALREADY_IN_A_CLAN = "§cVocê já faz parte de um clan!",
            NOT_PART_OF_CLAN = "§cVocê não faz parte de um clan!";

    @Completer(name = "clan")
    public List<String> clanCompleter(BungeeCommandContext context) {
        List<String> list = new ArrayList<>(),
                subCommands = context.getSubCommands();

        String[] args = context.getArgs();

        if (args.length > 0 && !args[0].isEmpty()) {
            String command = args[0].toLowerCase();

            for (String subCommand : subCommands) {
                if (subCommand.toLowerCase().startsWith(command))
                    list.add(subCommand);
            }
        } else
            list.addAll(subCommands);

        return list;
    }

    @Command(name = "clan")
    public void clan(BungeeCommandContext context) {
        Account account = context.getAccount();

        String label = "/" + context.getLabel();

        List<String> subCommands = context.getSubCommands();

        String[] args = context.getArgs();

        if (args.length == 0 || !subCommands.contains(args[0].toLowerCase()))
            account.send("§cComo usar " + label + ":",
                    "§c* " + label + " criar (nome) (tag)",
                    "§c* " + label + " deletar",
                    "§c* " + label + " sair",
                    "§c* " + label + " ver (nome/tag)",
                    "§c* " + label + " convidar [jogador]",
                    "§c* " + label + " aceitar [jogador]",
                    "§c* " + label + " recusar [jogador]",
                    "§c* " + label + " transferir [jogador]",
                    "§c* " + label + " cortag",
                    "§c* " + label + " prefix [tipo]");
    }

    @Command(name = "clan.criar")
    public void clanCreate(BungeeCommandContext context) {
        Account account = context.getAccount();

        String[] args = context.getArgs();

        if (account.hasClan()) {
            account.send(ALREADY_IN_A_CLAN);
            return;
        }

        if (args.length <= 1) {
            account.send("§cUso: /" + context.getCommandLabel() + " (nome) (tag).");
            return;
        }

        String name = args[0], tag = args[1];

        if (!NAME_VALIDATOR.matcher(name).matches()) {
            account.send("§cO nome do seu clan não é válido!",
                    "§cSomente letras e números, com caractéres de 3 até 12!");
            return;
        }

        if (data.getByName(name) != null) {
            account.send("§cOps! Um clan com o nome " + name + " já existe.");
            return;
        }

        if (!TAG_VALIDATOR.matcher(tag).matches()) {
            account.send("§cA tag do seu clan não é válida!",
                    "§cSomente letras e números, com caractéres de 4 até 8!");
            return;
        }

        if (data.getByTag(tag) != null) {
            account.send("§cOps! Um clan com a tag " + tag + " já existe.");
            return;
        }

        Clan clan = new Clan(account, UUID.randomUUID(), name, tag);

        account.send("§aO seu clan foi criado com sucesso.");
        account.setClan(clan.getId());

        data.save(clan);

        new AccountClanChangeMessage(account).send();
    }

    @Command(name = "clan.deletar")
    public void clanDelete(BungeeCommandContext context) {
        Account account = context.getAccount();

        if (!account.hasClan()) {
            account.send(NOT_PART_OF_CLAN);
            return;
        }

        if (!account.hasClanRole(ClanRole.LEADER)) {
            account.send("§cVocê não pode deletar o seu clan atual.");
            return;
        }

        String[] args = context.getArgs();

        if (args.length == 0) {
            account.send("§cUso: /" + context.getCommandLabel() + " (confirmar).");
            return;
        }

        if (args[0].equalsIgnoreCase("confirmar"))
            account.getClan().disband();
    }

    @Command(name = "clan.convidar")
    public void clanInvite(BungeeCommandContext context) {
        Account account = context.getAccount();

        if (!account.hasClan()) {
            account.send(NOT_PART_OF_CLAN);
            return;
        }

        if (!account.hasClanRole(ClanRole.RECRUITER)) {
            account.send("§cVocê não pode convidar jogadores para o clan.");
            return;
        }

        Clan clan = account.getClan();

        String[] args = context.getArgs();

        if (args.length == 0) {
            account.send("§cUso: /" + context.getCommandLabel() + " [jogador].");
            return;
        }

        Account target = context.getAccount(args[0]);

        if (target == null) {
            account.send(TARGET_NOT_FOUND);
            return;
        }

        if (account.equals(target)) {
            account.send(SAME_PLAYER);
            return;
        }

        if (!target.getToggle().isAllowClanInvite()) {
            account.send("§cO jogador " + target.getNickname() + " não está recebendo convites de clan.");
            return;
        }

        if (target.hasClan()) {
            account.send("§cO jogador " + target.getNickname() + " já faz parte de um clan!");
            return;
        }

        if (data.hasRequest(clan, target.getId())) {
            account.send("§cO jogador " + target.getNickname() + " já foi convidado para o clan!");
            return;
        }

        account.send("§aVocê convidou " + target.getNickname() + " para o seu clan!");

        TextComponent message = new TextComponent("§6" + account.getNickname() + "§e convidou você para o clan §b" + clan.getName() + "§e!");

        TextComponent acceptButton = new TextComponent("§a§lACEITAR");

        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan aceitar " + account.getNickname()));
        acceptButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Clique para aceitar o convite.")));

        TextComponent declineButton = new TextComponent("§c§lRECUSAR");

        declineButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan recusar " + account.getNickname()));
        declineButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Clique para recusar o convite.")));

        message.addExtra("\n        ");
        message.addExtra(acceptButton);
        message.addExtra("        ");
        message.addExtra(declineButton);

        // Adicionando requisição no Redis
        data.addRequest(new ClanRequest(clan.getId(), account.getId(), target.getId()));

        target.send(message);
    }

    @Command(name = "clan.aceitar")
    public void clanAccept(BungeeCommandContext context) {
        Account account = context.getAccount();

        String[] args = context.getArgs();

        if (account.hasClan()) {
            account.send(ALREADY_IN_A_CLAN);
            return;
        }

        if (args.length == 0) {
            account.send("§cUso: /" + context.getCommandLabel() + " [jogador].");
            return;
        }

        Account target = context.getAccount(args[0]);

        if (target == null) {
            account.send(TARGET_NOT_FOUND);
            return;
        }

        if (account.equals(target)) {
            account.send(SAME_PLAYER);
            return;
        }

        ClanRequest request = data.getRequest(target.getId(), account.getId());

        if (request == null) {
            account.send("§cNenhum convite foi encontrado de " + target.getNickname() + ".");
            return;
        }

        Clan clan = data.of(request.getId());

        data.removeRequest(request);

        if (clan == null) {
            account.send("§cNão foi possível encontrar o clan.");
            return;
        }

        clan.addMember(account);

        account.send("§aVocê aceitou o convite de " + target.getNickname() + " para entrar no clan §7" + clan.getName() + "§a.");
        target.send("§a" + account.getNickname() + " aceitou o seu convite para entrar no clan.");

        new AccountClanChangeMessage(account).send();
    }

    @Command(name = "clan.sair")
    public void clanLeave(BungeeCommandContext context) {
        Account account = context.getAccount();

        if (account == null) {
            context.getSender().send("§cNão foi possível carregar sua conta.");
            return;
        }

        if (!account.hasClan()) {
            account.send(NOT_PART_OF_CLAN);
            return;
        }

        if (account.isClanLeader()) {
            account.send("§cVocê não pode sair do seu clan!");
            return;
        }

        Clan clan = account.getClan();

        if (clan == null) {
            account.send("§cErro: Clan não encontrado.");
            return;
        }

        if (!clan.isMember(account.getId())) {
            account.send("§cVocê não é membro deste clan.");
            // Limpar referência ao clan caso esteja inconsistente
            account.setClan(Constant.DEFAULT_ID);
            return;
        }

        clan.removeMember(account);

        new AccountClanChangeMessage(account).send();
    }

    @Command(name = "clan.transferir")
    public void clanTransfer(BungeeCommandContext context) {
        Account account = context.getAccount();

        if (!account.hasClan()) {
            account.send(NOT_PART_OF_CLAN);
            return;
        }

        if (!account.isClanLeader()) {
            account.send("§cVocê não pode transferir o clan para outros jogadores.");
            return;
        }

        String[] args = context.getArgs();

        if (args.length == 0) {
            account.send("§cUso: /" + context.getCommandLabel() + " [jogador].");
            return;
        }

        Account target = context.getAccount(args[0]);

        if (target == null) {
            account.send(TARGET_NOT_FOUND);
            return;
        }

        if (account.equals(target)) {
            account.send(SAME_PLAYER);
            return;
        }

        Clan clan = account.getClan();

        if (!clan.isMember(target.getId())) {
            account.send("§cO jogador " + target.getNickname() + " não é membro do seu clan.");
            return;
        }

        clan.setLeader(target.getId());

        clan.send("§7O clan foi transferido para " + target.getNickname() + ".");
    }

    @Command(name = "clan.recusar")
    public void clanDecline(BungeeCommandContext context) {
        Account account = context.getAccount();

        String[] args = context.getArgs();

        if (account.hasClan()) {
            account.send(ALREADY_IN_A_CLAN);
            return;
        }

        if (args.length == 0) {
            account.send("§cUso: /" + context.getCommandLabel() + " [jogador].");
            return;
        }

        Account target = context.getAccount(args[0]);

        if (target == null) {
            account.send(TARGET_NOT_FOUND);
            return;
        }

        if (account.equals(target)) {
            account.send(SAME_PLAYER);
            return;
        }

        ClanRequest request = data.getRequest(target.getId(), account.getId());

        if (request == null) {
            account.send("§cNenhum convite foi encontrado de " + target.getNickname() + ".");
            return;
        }

        data.removeRequest(request);

        account.send("§cVocê recusou o convite de " + target.getNickname() + " para entrar no clan.");
        target.send("§c" + account.getNickname() + " recusou o convite para entrar no clan.");
    }

    @Command(name = "clan.cortag")
    public void clanColor(BungeeCommandContext context) {
        Account account = context.getAccount();

        if (!account.hasClan()) {
            account.send(NOT_PART_OF_CLAN);
            return;
        }

        Clan clan = account.getClan();

        if (clan == null) {
            account.send("§cErro: Clan não encontrado.");
            return;
        }

        ClanMember member = clan.getMember(account.getId());

        if (member == null || member.getRole().ordinal() < ClanRole.RECRUITER.ordinal()) {
            account.send("§cApenas o líder e o recrutador podem alterar a cor da tag do clã.");
            return;
        }

        String[] args = context.getArgs();

        if (args.length == 0) {
            sendColorList(account);
            return;
        }

        String colorCode = ClanColor.parse(args[0]);

        if (colorCode == null) {
            account.send("§cCor inválida! Clique em uma das cores abaixo:");
            sendColorList(account);
            return;
        }

        ClanColor color = ClanColor.getByCode(colorCode);

        if (color == null) {
            account.send("§cErro ao processar a cor.");
            return;
        }

        /* Remove códigos de cor antigos que tenham ficado gravados na tag */
        String tag = clan.getTag();

        if (tag != null && (tag.contains("§") || tag.contains("&")))
            clan.setTag(clan.getTagWithoutColors());

        clan.setTagColor(color.getCode());

        account.send("§aA cor da tag do clã foi alterada para " + color.getCode() + color.getName() + "§a.");
        clan.send("§7A cor da tag do clã foi alterada para " + color.getCode() + color.getName() + "§7 por " + account.getNickname() + ".");

        clan.getOnlineMembers().forEach(onlineMember -> new AccountClanChangeMessage(onlineMember).send());
    }

    @Command(name = "clan.prefix")
    public void clanPrefix(BungeeCommandContext context) {
        Account account = context.getAccount();

        if (!account.hasClan()) {
            account.send(NOT_PART_OF_CLAN);
            return;
        }

        Clan clan = account.getClan();

        ClanMember member = clan.getMember(account.getId());

        if (member == null) {
            account.send("§cErro: Você não é membro deste clan.");
            return;
        }

        ClanRole role = member.getRole();

        if (role != ClanRole.LEADER && role != ClanRole.RECRUITER) {
            account.send("§cApenas o líder e o recrutador podem alterar o prefixo do clan.");
            return;
        }

        String[] args = context.getArgs();

        if (args.length == 0) {
            account.send("§cUse: /" + context.getLabel() + " prefix (tipo)");
            account.send("§cTipos disponíveis:");

            TextComponent typesLine = new TextComponent("");

            ClanPrefix[] prefixes = ClanPrefix.values();

            for (int i = 0; i < prefixes.length; i++) {
                ClanPrefix prefix = prefixes[i];

                TextComponent typeBtn = new TextComponent(prefix.getOpen() + "tag" + prefix.getClose());

                typeBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan prefix " + prefix.getId()));
                typeBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(
                        "§aClique para usar o prefixo " + prefix.getId() + " (" + prefix.getOpen() + "tag" + prefix.getClose() + "§a)"
                                + (prefix.hasPermission(account.getRank().getType()) ? "" : "§c — exige " + prefix.getMinimumRank().getName()))));

                typesLine.addExtra(typeBtn);

                if (i < prefixes.length - 1)
                    typesLine.addExtra("§c, ");
            }

            account.send(typesLine);
            return;
        }

        ClanPrefix prefix = ClanPrefix.of(args[0]);

        if (prefix == null) {
            account.send("§cPrefixo inválido! Tipos disponíveis: " + ClanPrefix.getFormattedList());
            return;
        }

        if (!prefix.hasPermission(account.getRank().getType())) {
            account.send("§cVocê não tem permissão para usar este prefixo (mínimo: " + prefix.getMinimumRank().getName() + ").");
            return;
        }

        clan.setPrefix(prefix.getId());

        account.send("§aPrefixo alterado para " + prefix.getOpen() + "tag" + prefix.getClose() + "§a.");
        clan.send("§7O prefixo do clan foi alterado para " + prefix.getOpen() + "tag" + prefix.getClose() + "§7 por " + account.getNickname() + ".");

        clan.getOnlineMembers().forEach(onlineMember -> new AccountClanChangeMessage(onlineMember).send());
    }

    /* Lista clicável: /clan cortag sem argumentos */
    private void sendColorList(Account account) {
        TextComponent message = new TextComponent("§aSuas cores: ");

        ClanColor[] colors = ClanColor.values();

        for (int i = 0; i < colors.length; i++) {
            ClanColor color = colors[i];

            if (i > 0)
                message.addExtra(new TextComponent("§f, "));

            TextComponent entry = new TextComponent(color.getCode() + color.getName());

            entry.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan cortag " + color.getId()));
            entry.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(
                    "§7Clique para usar §f" + color.getName() + "§7.")));

            message.addExtra(entry);
        }

        account.send(message);
    }
}
