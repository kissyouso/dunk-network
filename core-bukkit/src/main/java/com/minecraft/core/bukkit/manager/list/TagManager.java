package com.minecraft.core.bukkit.manager.list;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.tag.Tag;
import com.minecraft.core.account.context.objects.tag.prefix.TagPrefix;
import com.minecraft.core.bukkit.user.UserModel;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TagManager {

    /* O cliente le o nome/prefixo/sufixo do time com limite de 16 caracteres (contando
     * os códigos de cor). Passar disso não é recusado pelo jar do servidor, mas o
     * DecoderException "string length is longer than maximum allowed (N > 16)" derruba
     * quem recebe o pacote, então 16 é o teto real, não os 32 que o CraftTeam aceita. */
    private static final int TEAM_PREFIX_LIMIT = 16;

    public static List<Tag> getAvailableTags(Account account) {
        return Arrays.stream(Tag.values())
                /* cores de equipe e spectator nao sao escolhas do jogador */
                .filter(tag -> !tag.isTeamColor())
                .filter(account::hasTag)
                .collect(Collectors.toList());
    }

    protected static TextComponent create(Account account, Tag tag, boolean end) {
        TextComponent message = new TextComponent(tag.getColoredName());

        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(
                "§7Prévia:"
                        + "\n§8> " + account.formatTag(tag, account.getTagPrefix()) + account.getNickname()
                        + "\n\n" + (account.isUsingTag(tag) ? "§cJá selecionada." : "§eClique para escolher.")
        )));

        message.addExtra("§r" + (end ? "." : ", "));

        message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tag " + tag.name().toLowerCase()));

        return message;
    }

    public static void sendTags(Account account) {
        List<Tag> tags = getAvailableTags(account);

        TextComponent message = new TextComponent("§aSuas tags: ");

        boolean end = false;

        int index = 1;
        for (Tag tag : tags) {
            if (index >= tags.size())
                end = true;

            message.addExtra(create(account, tag, end));
            index++;
        }

        message.addExtra(new TextComponent("§r"));

        account.send(message);
    }

    public static void updateTag(Account account) {
        updateTag(account, UserModel.has(account.getId()) ? UserModel.of(account.getId()).getTag() : account.getTag());
    }

    public static void updateTag(Player player) {
        Account account = Core.getAccountController().of(player.getUniqueId());

        if (account != null)
            updateTag(account);
    }

    public static void updateTag(Account account, Tag tag) {
        Player player = account.player();

        if (player == null) return; // Jogador não está online

        if (tag == null) tag = Tag.MEMBER;

        /* A TAB sempre mostra a tag de verdade da conta. As tags de cor de time
         * (e a de espectador) servem apenas para colorir o nametag no mundo;
         * usá-las na TAB deixava o jogador sem tag nenhuma (ex.: Duels). */
        Tag tabTag = resolveTabTag(account, tag);

        /* Fundador fica sempre na ordem do FUNDADOR ("-") na TAB, mesmo que
         * esteja exibindo outra tag (cor de time em partida): o cargo dele e o
         * mais alto da rede. */
        String order = "tag:" + (account.isFounder() ? Tag.FUNDADOR.getOrder() : tabTag.getOrder()) + ":" + player.getEntityId();

        /* Em partida o prefixo do time ("[V] ") entra antes da tag e o clan tag
         * sai do sufixo: fica [V] TAG+NICK. */
        String teamPrefix = getTeamPrefix(account);

        /* A TAB aceita nomes maiores que 16 caracteres (o limite de 16 vale
         * apenas para o prefixo do time do scoreboard, usado no nametag). Por
         * isso a TAB usa o prefixo completo, sem o corte que transformava
         * "HELPER NICK" em "HELPNICK". */
        String tabPrefix = getFullPrefix(account, account.getTagPrefix(), tabTag, teamPrefix);

        String clanTag = !account.isUsingFake() && account.hasClan() && account.getToggle().isAllowClanTag()
                ? account.getClanTag()
                : "";

        String tabSuffix = teamPrefix == null ? clanTag : "";

        /* Prefixo/sufixo do scoreboard: usados no nametag e limitados a 16. */
        String prefix = getAdjustedPrefix(account, account.getTagPrefix(), tag, teamPrefix);
        String suffix = teamPrefix == null ? truncate(clanTag) : "";

        // Limpar equipes antigas do jogador
        for (Team old : player.getScoreboard().getTeams()) {
            if (old.getName().startsWith("tag:") && old.hasEntry(player.getName())) {
                old.unregister();
            }
        }

        Team team = createTeamIfNotExists(order, player, player.getName(), prefix, suffix);
        Scoreboard scoreboard = team.getScoreboard();

        String displayName = tabPrefix + player.getName() + tabSuffix;
        player.setDisplayName(displayName);
        player.setPlayerListName(displayName);
        player.setScoreboard(scoreboard);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(player)) continue;

            // Exibe a tag do jogador `player` para `onlinePlayer`
            applyTagForViewer(player, onlinePlayer, tag);

            // Exibe a tag do `onlinePlayer` para o `player`
            UserModel onlineModel = UserModel.of(onlinePlayer.getUniqueId());

            if (onlineModel != null)
                applyTagForViewer(onlinePlayer, player, onlineModel.getTag() != null ? onlineModel.getTag() : Tag.MEMBER);
        }
    }

    private static void applyTagForViewer(Player player, Player viewer, Tag tag) {
        UserModel viewerModel = UserModel.of(viewer.getUniqueId());
        if (viewerModel == null) return;

        Account viewerAccount = viewerModel.getAccount();

        // Definindo o prefixo do viewer com base no TagPrefix do visualizador
        TagPrefix viewerPrefixType = viewerAccount.getTagPrefix();
        Account owner = Core.getAccountController().of(player.getUniqueId());
        String teamPrefix = getTeamPrefix(player);
        String viewerPrefix = getAdjustedPrefix(owner, viewerPrefixType, tag, teamPrefix);

        /* Tag de clã de quem está sendo exibido (some quando há prefixo de time) */
        String playerClanTag = teamPrefix == null && owner != null && !owner.isUsingFake() && owner.hasClan() && owner.getToggle().isAllowClanTag()
                ? truncate(owner.getClanTag())
                : "";

        /* Gerar ordem para o time específico do visualizador.
         * Fundador usa sempre a ordem do FUNDADOR, igual na própria TAB dele. */
        Tag orderTag = owner != null && owner.isFounder()
                ? Tag.FUNDADOR
                : (tag != null ? tag : Tag.MEMBER);

        String viewerOrder = "tag:" + orderTag.getOrder() + ":" + player.getEntityId();

        // Removendo times antigos para o visualizador
        for (Team old : viewer.getScoreboard().getTeams()) {
            if (old.getName().startsWith("tag:") && old.hasEntry(player.getName())) {
                old.unregister();
            }
        }

        // Criando ou obtendo a equipe com a tag ajustada para o visualizador, com a tag de clã do player
        Team viewerTeam = createTeamIfNotExists(viewerOrder, viewer, player.getName(), viewerPrefix, playerClanTag);

        // Adicionando o jogador (player) à equipe com a visualização do viewer
        if (!viewerTeam.hasEntry(player.getName())) {
            viewerTeam.addEntry(player.getName());
        }
        
        // Garantir que o prefixo está atualizado (importante para o tab list)
        viewerTeam.setPrefix(clamp(viewerPrefix, TEAM_PREFIX_LIMIT));
        viewerTeam.setSuffix(truncate(playerClanTag));
    }

    private static Team createTeamIfNotExists(String order, Player player, String entry, String prefix, String suffix) {
        Scoreboard scoreboard = player.getScoreboard();

        if (scoreboard == null || scoreboard.equals(Bukkit.getScoreboardManager().getMainScoreboard()))
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        Team team = scoreboard.getTeam(order);
        if (team == null) {
            team = scoreboard.registerNewTeam(order);
        }

        team.setCanSeeFriendlyInvisibles(false);
        // Sempre atualiza o prefixo e sufixo, mesmo se o Team já existir
        team.setPrefix(clamp(prefix != null ? prefix : "", TEAM_PREFIX_LIMIT));
        team.setSuffix(truncate(suffix));

        if (!team.hasEntry(entry))
            team.addEntry(entry);

        return team;
    }

    private static String getTeamPrefix(Account account) {
        return account == null ? null : getTeamPrefix(account.player());
    }

    private static String getTeamPrefix(Player player) {
        if (player == null) return null;

        UserModel model = UserModel.of(player.getUniqueId());

        return model != null ? model.getTeamPrefix() : null;
    }

    private static String getAdjustedPrefix(Account owner, TagPrefix prefixType, Tag tag, String teamPrefix) {
        String tagPrefix = owner != null ? owner.formatTag(tag, prefixType) : tag.getByPrefix(prefixType);

        if (teamPrefix == null || teamPrefix.isEmpty())
            return truncate(tagPrefix);

        /* A tag ja termina com " " + cor do nickname, entao o tempo so entra na
         * frente: fica [V] TAG NICK, com as tres partes separadas. */
        String composed = teamPrefix + tagPrefix;

        if (composed.length() <= TEAM_PREFIX_LIMIT)
            return composed;

        /* Tag comprida demais para caber com o tempo: mantem o ultimo codigo de
         * cor no fim (sem ele o nickname aparece branco) e corta o resto da tag. */
        String color = lastColor(tagPrefix);

        return clamp(composed.substring(0, TEAM_PREFIX_LIMIT - color.length()), TEAM_PREFIX_LIMIT - color.length()) + color;
    }

    /**
     * Tag exibida na TAB. Cores de equipe (e SPECTATOR) nao tem texto, entao a
     * TAB usa a tag real da conta; o time continua colorindo o nametag.
     */
    private static Tag resolveTabTag(Account account, Tag tag) {
        if (tag == null) return Tag.MEMBER;

        if (!tag.isTeamColor()) return tag;

        Tag accountTag = account.getTag();

        return accountTag != null ? accountTag : Tag.MEMBER;
    }

    /**
     * Prefixo completo (sem o corte de 16) usado no nome da TAB. O limite de 16
     * do protocolo so vale para prefixo/sufixo de scoreboard (nametag).
     */
    private static String getFullPrefix(Account owner, TagPrefix prefixType, Tag tag, String teamPrefix) {
        String tagPrefix = owner != null ? owner.formatTag(tag, prefixType) : tag.getByPrefix(prefixType);

        if (teamPrefix == null || teamPrefix.isEmpty())
            return tagPrefix == null ? "" : tagPrefix;

        return teamPrefix + (tagPrefix == null ? "" : tagPrefix);
    }

    private static String lastColor(String text) {
        for (int index = text.length() - 2; index >= 0; index--) {
            if (text.charAt(index) == '§')
                return text.substring(index, index + 2);
        }

        return "§r";
    }

    private static String truncate(String suffix) {
        return clamp(suffix, 16);
    }

    /**
     * Corta no limite sem partir um codigo de cor no meio.
     */
    private static String clamp(String text, int limit) {
        if (text == null || text.isEmpty()) return text;

        // O protocolo do Minecraft limita suffix a 16 caracteres totais (incluindo códigos de cor)
        if (text.length() <= limit) {
            return text;
        }

        // Truncar para o limite, tentando manter códigos de cor completos
        StringBuilder result = new StringBuilder();
        int totalChars = 0;
        int i = 0;

        while (i < text.length() && totalChars < limit) {
            char c = text.charAt(i);

            if (c == '§' && i + 1 < text.length()) {
                // É um código de cor, verificar se cabe
                if (totalChars + 2 <= limit) {
                    result.append(c);
                    result.append(text.charAt(i + 1));
                    totalChars += 2;
                    i += 2; // Pular o próximo caractere
                } else {
                    // Não cabe mais, parar
                    break;
                }
            } else {
                // É um caractere normal
                if (totalChars < limit) {
                    result.append(c);
                    totalChars++;
                    i++;
                } else {
                    // Limite atingido, parar
                    break;
                }
            }
        }

        return result.toString();
    }

    public static void removeTag(Player player) {
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());

        player.getScoreboard().getTeams().stream()
                .filter(team -> team.getName().startsWith("tag:"))
                .forEach(Team::unregister);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(player) || onlinePlayer.getScoreboard().equals(Bukkit.getScoreboardManager().getMainScoreboard()))
                continue;

            onlinePlayer.getScoreboard().getTeams().forEach(team -> removeEntries(team, player.getName()));
        }
    }

    private static void removeEntries(Team team, String entry) {
        if (team.getName().startsWith("tag:")) {
            team.removeEntry(entry);

            if (team.getEntries().isEmpty())
                team.unregister();
        }
    }
}