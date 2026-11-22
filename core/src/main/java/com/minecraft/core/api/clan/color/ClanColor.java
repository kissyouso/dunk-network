package com.minecraft.core.api.clan.color;

import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.account.context.objects.rank.type.role.RankRole;
import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public enum ClanColor {

    AZUL("Azul", "§1", "1", null, "azul", "blue"),
    AZUL_ESCURO("Azul Escuro", "§3", "3", null, "azul escuro", "dark aqua", "dark blue"),
    CIANO("Ciano", "§b", "b", null, "ciano", "aqua", "cyan"),
    VERDE("Verde", "§a", "a", null, "verde", "green", "lima"),
    VERDE_ESCURO("Verde Escuro", "§2", "2", null, "verde escuro", "dark green"),
    AMARELO("Amarelo", "§e", "e", null, "amarelo", "yellow"),
    LARANJA("Laranja", "§6", "6", null, "laranja", "gold"),
    VERMELHO("Vermelho", "§c", "c", null, "vermelho", "red"),
    SANGUE("Sangue", "§4", "4", null, "sangue", "vermelho escuro", "dark red"),
    ROSA("Rosa", "§d", "d", null, "rosa", "pink", "magenta"),
    ROXO("Roxo", "§5", "5", null, "roxo", "lilas", "purple"),
    BRANCO("Branco", "§f", "f", null, "branco", "white"),
    CINZA("Padrão", "§7", "7", null, "padrao", "padrão", "cinza", "gray", "grey");

    private final String name;
    private final String code;
    private final String id;
    private final String mixedColorTag;
    private final String[] aliases;

    /* Familias desta source: Style/Partner contam como VIP e Style+ conta como Prime. */
    private static final Set<RankType> VIP_FAMILY = EnumSet.of(
            RankType.VIP, RankType.DUNK, RankType.PARTNER, RankType.PARTNER_PLUS);

    private static final Set<RankType> PRIME_FAMILY = EnumSet.of(
            RankType.PRIME, RankType.PRIME_PLUS,
            RankType.DUNK_PLUS_1, RankType.DUNK_PLUS_3, RankType.DUNK_PLUS_6);

    ClanColor(String name, String code, String id, String mixedColorTag, String... aliases) {
        this.name = name;
        this.code = code;
        this.id = id;
        this.mixedColorTag = mixedColorTag;
        this.aliases = aliases;
    };

    public boolean hasMixedColorTag() {
        return mixedColorTag != null && !mixedColorTag.isEmpty();
    };

    /**
     * Cores exclusivas: Azul Escuro fica com a familia VIP, Vermelho com a
     * familia Prime e Rosa/Roxo com o Beta. As demais sao livres, e a equipe
     * (RankRole.STAFF) mantem acesso a todas.
     */
    public boolean hasPermission(RankType rank) {
        if (rank == null) return false;

        if (rank.getRole() == RankRole.STAFF) return true;

        switch (this) {
            case AZUL_ESCURO:
                return VIP_FAMILY.contains(rank);
            case VERMELHO:
                return PRIME_FAMILY.contains(rank);
            case ROSA:
            case ROXO:
                return rank == RankType.BETA;
            default:
                return true;
        }
    };

    public static String getDefaultColor() {
        return "§7";
    };

    public static String parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        };

        String normalized = input.trim().toLowerCase();

        if (normalized.startsWith("§") && normalized.length() == 2) {
            char code = normalized.charAt(1);
            for (ClanColor color : values()) {
                if (color.getCode().equals("§" + code)) {
                    return color.getCode();
                }
            }
        };

        if (normalized.startsWith("&") && normalized.length() == 2) {
            char code = normalized.charAt(1);
            for (ClanColor color : values()) {
                if (color.getCode().equals("§" + code)) {
                    return color.getCode();
                }
            }
        };

        for (ClanColor color : values()) {
            if (color.getId().equalsIgnoreCase(normalized)) {
                return color.getCode();
            }
        };

        for (ClanColor color : values()) {
            for (String alias : color.getAliases()) {
                if (alias.equalsIgnoreCase(normalized)) {
                    return color.getCode();
                }
            }
        };

        return null;
    };

    public static String getName(String colorCode) {
        if (colorCode == null) {
            return "Desconhecida";
        };

        for (ClanColor color : values()) {
            if (color.getCode().equals(colorCode)) {
                return color.getName();
            }
        };

        return "Desconhecida";
    };

    public static boolean isValid(String colorCode) {
        if (colorCode == null) {
            return false;
        };

        for (ClanColor color : values()) {
            if (color.getCode().equals(colorCode)) {
                return true;
            }
        };

        return false;
    }

//    public static List<String> getFormattedColors() {
//        return Arrays.stream(values())
//                .map(color -> color.getCode() + color.getName())
//                .collect(Collectors.toList());
//    }


    public static String getFormattedList(RankType rank) {
        return Arrays.stream(values())
                .filter(color -> color.hasPermission(rank))
                .map(color -> color.getCode() + color.getName())
                .collect(Collectors.joining("§f, ")) + "§f.";
    };

    public static ClanColor getByCode(String colorCode) {
        if (colorCode == null) {
            return null;
        };
        
        for (ClanColor color : values()) {
            if (color.getCode().equals(colorCode)) {
                return color;
            }
        };
        
        return null;
    }

//    public static ClanColor getByMixedColorTag(String mixedTag) {
//        if (mixedTag == null || mixedTag.trim().isEmpty()) {
//            return null;
//        }
//
//        String normalized = mixedTag.replace('&', '§');
//
//        for (ClanColor color : values()) {
//            if (color.hasMixedColorTag() && color.getMixedColorTag().equals(normalized)) {
//                return color;
//            }
//        }
//
//        return null;
//    }
//
//    public static List<ClanColor> getColorsWithMixedTags() {
//        return Arrays.stream(values())
//                .filter(ClanColor::hasMixedColorTag)
//                .collect(Collectors.toList());
//    }
}



