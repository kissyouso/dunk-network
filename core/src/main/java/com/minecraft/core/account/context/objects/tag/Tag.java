package com.minecraft.core.account.context.objects.tag;

import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.account.context.objects.tag.prefix.TagPrefix;
import com.minecraft.core.account.context.objects.tag.role.TagRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;

import java.util.*;

import static java.util.Collections.emptyList;

/**
 * O campo {@code order} monta o nome do scoreboard team ({@code tag:<order>:<entityId>}) e
 * define a posicao do jogador na tab list: quanto MENOR o caractere, mais alto o jogador.
 *
 * A ordem de declaracao tambem importa, porque {@code ordinal()} e usado em
 * Account#isAllowColoredChat (>= VIP) e no italico (>= FUNDADOR, so a equipe). Por isso
 * tudo que nao e tag de conta (cores de equipe + Membro) fica ANTES de VIP.
 */
@Getter
@AllArgsConstructor
public enum Tag {

    /* MEMBER fica antes das cores: Tag.of(RankType.MEMBER) usa o primeiro match,
       e as tags de cor tambem tem source = MEMBER (construtor curto). */
    MEMBER("Membro", "Z", "§7", emptyList(), RankType.MEMBER, TagRole.NONE, ChatColor.GRAY),

    /* Cores de equipe (uso interno, nunca aparecem no /tag) */
    SPECTATOR("Spectator", "§7", TagRole.NONE, ChatColor.GRAY),
    AQUA("Ciano", "§b", TagRole.NONE, ChatColor.AQUA),
    ORAN("Laranja", "§6", TagRole.NONE, ChatColor.GOLD),
    YEL("Amarelo", "§e", TagRole.NONE, ChatColor.YELLOW),
    PURP("Roxo", "§5", TagRole.NONE, ChatColor.DARK_PURPLE),
    WHIT("Branco", "§f", TagRole.NONE, ChatColor.WHITE),
    GRAY("Cinza", "§8", TagRole.NONE, ChatColor.DARK_GRAY),

    /* Vips: VIP < Prime < Prime+ < Beta < Nitro < Blaze < Master */
    VIP("VIP", "L", "§a§lVIP §r§a", emptyList(), RankType.VIP, TagRole.VIP, ChatColor.GREEN),
    PRIME("Prime", "H", "§e§lPRIME §r§e", emptyList(), RankType.PRIME, TagRole.VIP, ChatColor.YELLOW),
    PRIME_PLUS("Prime+", "G", "§6§lPRIME§d+ §6", emptyList(), RankType.PRIME_PLUS, TagRole.VIP, ChatColor.GOLD),
    @com.google.gson.annotations.SerializedName(value = "BETA", alternate = "AURA")
    BETA("Beta", "A", "§1§lBETA §r§1", emptyList(), RankType.BETA, TagRole.VIP, ChatColor.DARK_BLUE),
    NITRO("Nitro", "I", "§d§lNITRO §r§d", emptyList(), RankType.NITRO, TagRole.VIP, ChatColor.LIGHT_PURPLE),
    BLAZE("Blaze", "A1", "§e§lBLAZE §r§e", emptyList(), RankType.BLAZE, TagRole.VIP, ChatColor.YELLOW),
    MASTER("Master", "A0", "§6§lMASTER §r§6", emptyList(), RankType.MASTER, TagRole.VIP, ChatColor.GOLD),

    /* Criadores */
    TIKTOKER("Tiktoker", "9", "§b§lTIKTOKER §b", emptyList(), RankType.TIKTOKER, TagRole.VIP, ChatColor.AQUA),
    STREAMER("Streamer", "8", "§b§lSTREAM §b", emptyList(), RankType.STREAMER, TagRole.VIP, ChatColor.AQUA),
    YOUTUBER("YT", "7", "§b§lYT §b", emptyList(), RankType.YOUTUBER, TagRole.VIP, ChatColor.AQUA),
    SOCIAL("Social", "6", "§b§lSOCIAL §b", emptyList(), RankType.MIDIA, TagRole.VIP, ChatColor.AQUA),
    SOCIAL_PLUS("Social+", "5", "§3§lSOCIAL§d+ §3", emptyList(), RankType.MIDIA_PLUS, TagRole.VIP, ChatColor.DARK_AQUA),

    /* Equipe.
       Ordem "-" no nome da team coloca o Chefe acima de todos na TAB; o constante continua
       FUNDADOR porque e assim que a tag ja esta salva no banco. */
    FUNDADOR("Chefe", "-", "§4§lCHEFE §4", emptyList(), RankType.CHEFE, TagRole.STAFF, ChatColor.DARK_RED),
    BUILDER("Builder", "6a", "§2§lBUILDER §2", emptyList(), RankType.BUILDER, TagRole.STAFF, ChatColor.DARK_GREEN),
    STUDIO("Studio", "3a", "§2§lSTUDIO §2", emptyList(), RankType.STUDIO, TagRole.STAFF, ChatColor.DARK_GREEN),
    HELPER("Helper", "4a", "§9§lHELPER §r§9", emptyList(), RankType.HELPER, TagRole.STAFF, ChatColor.BLUE),
    TRIAL("Trial", "4", "§5§lTRIAL §r§5", emptyList(), RankType.TRIAL, TagRole.STAFF, ChatColor.DARK_PURPLE),
    MOD("Mod", "3", "§5§lMOD §r§5", emptyList(), RankType.MOD, TagRole.STAFF, ChatColor.DARK_PURPLE),
    MODPLUS("Mod+", "2", "§5§lMOD+ §r§5", emptyList(), RankType.MODPLUS, TagRole.STAFF, ChatColor.DARK_PURPLE),
    ADMIN("Admin", "1", "§4§lADMIN §r§4", emptyList(), RankType.ADMIN, TagRole.STAFF, ChatColor.DARK_RED),
    /* Sem prefixo: so a cor rosa no nick. */
    UWU("uwu", "0", "§d", emptyList(), RankType.UWU, TagRole.STAFF, ChatColor.LIGHT_PURPLE);

    private final String name, order, prefix;

    private final List<String> surnames;

    private final RankType source;
    private final TagRole role;

    private final ChatColor color;

    private final List<UUID> onlyThese;

    /* Custom Tag Constructor */
    Tag(String name, String order, String prefix, List<String> surnames, RankType source, TagRole role, ChatColor color) {
        this(name, order, prefix, surnames, source, role, color, new ArrayList<>());
    }

    Tag(String name, String prefix, TagRole role, ChatColor color) {
        this(name, prefix, "Y", role, color);
    }

    Tag(String name, String prefix, String order, TagRole role, ChatColor color) {
        this(name, order, prefix, emptyList(), RankType.MEMBER, role, color, new ArrayList<>());
    }

    public static Tag of(String name) {
        if ("aura".equalsIgnoreCase(name)) return BETA;
        if ("chefe".equalsIgnoreCase(name) || "fundador".equalsIgnoreCase(name)) return FUNDADOR;

        return Arrays.stream(values())
                .filter(tag -> tag.name().equalsIgnoreCase(name) || tag.getName().equalsIgnoreCase(name) || tag.getSurnames().stream().anyMatch(surName -> surName.equalsIgnoreCase(name)))
                .findFirst().orElse(null);
    }

    public static Tag of(RankType source) {
        return Arrays.stream(values())
                .filter(tag -> tag.getSource().equals(source))
                .findFirst().orElse(null);
    }

    public String getByPrefix(TagPrefix prefix) {
        if (this == MEMBER) return getPrefix();

        String prefixRegex = getPrefix().split(" ")[0];

        String result = prefixRegex;

        switch (prefix) {
            case DEFAULT: {
                result = getPrefix();
                break;
            }
            case COLOR: {
                result = getColor().toString();
                break;
            }
            case BRACKETS: {
                String name = isRole(TagRole.COLORED)
                        ? ChatColor.stripColor(getPrefix()).split(" ")[0]
                        : getName().toUpperCase();

                result = getColor() + "[" + name + "] ";
                break;
            }
            case BRACKETS_NORMAL_CASE: {
                String name = isRole(TagRole.COLORED)
                        ? ChatColor.stripColor(getPrefix()).split(" ")[0]
                        : getName();

                result = getColor() + "[" + name + "] ";
                break;
            }
            case DEFAULT_WHITE: {
                result = prefixRegex + " §r";
                break;
            }
            case DEFAULT_GRAY: {
                result = prefixRegex + " §r§7";
                break;
            }
        }

        return result;
    }

    public boolean isOnlyThese(UUID id) {
        return !onlyThese.isEmpty() && onlyThese.contains(id);
    }

    public boolean isRole(TagRole role) {
        return this.role.equals(role);
    }

    /**
     * Escada de VIPs: quem esta em uma posicao maior herda as tags de baixo
     * (ex.: quem tem Master tambem pode usar VIP). As outras tags exigem o rank exato.
     */
    public boolean isVipLadder() {
        return getVipLadderIndex() >= 0;
    }

    public int getVipLadderIndex() {
        switch (this) {
            case VIP: return 0;
            case PRIME: return 1;
            case PRIME_PLUS: return 2;
            case BETA: return 3;
            case NITRO: return 4;
            case BLAZE: return 5;
            case MASTER: return 6;
            default: return -1;
        }
    }

    /** Cores de equipe: nunca aparecem no /tag (o jogador nem sabe que existem). */
    public boolean isTeamColor() {
        return this == SPECTATOR || this == AQUA || this == ORAN || this == YEL || this == PURP
                || this == WHIT || this == GRAY;
    }

    /** Tags com "+" na frente: a cor do sinal fica na propria tag. */
    public boolean hasPlus() {
        return this == PRIME_PLUS || this == SOCIAL_PLUS;
    }

    public String getColoredName() {
        return color + name;
    }

    public String getPermission() {
        return "tag." + name.toLowerCase();
    }
}
