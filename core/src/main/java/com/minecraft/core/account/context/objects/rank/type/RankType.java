package com.minecraft.core.account.context.objects.rank.type;

import com.minecraft.core.account.context.objects.rank.type.role.RankRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;

import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * A ordem de declaracao DEFINE a hierarquia: {@code Account.hasRank(Type)} compara com
 * {@code ordinal() >=}. Os ranks novos entraram nos degraus que faltavam sem mover os
 * ja existentes de lugar (Style/Style+ seguem em VIP < DUNK < BETA < DUNK_PLUS_1 < _3 < _6).
 */
@Getter
@AllArgsConstructor
public enum RankType {

    MEMBER("Membro", RankRole.NONE, ChatColor.GRAY, emptyList()),
    VIP("VIP", RankRole.VIP, ChatColor.GREEN, emptyList()),
    @com.google.gson.annotations.SerializedName(value = "DUNK", alternate = {"HEAVEN", "STOP"})
    DUNK("Style", RankRole.VIP, ChatColor.GOLD, emptyList()),
    @com.google.gson.annotations.SerializedName(value = "BETA", alternate = "AURA")
    BETA("Beta", RankRole.VIP, ChatColor.DARK_BLUE, emptyList()),

    /* Vips da lista de producao */
    NITRO("Nitro", RankRole.SPECIAL, ChatColor.LIGHT_PURPLE, emptyList()),
    PRIME("Prime", RankRole.VIP, ChatColor.YELLOW, emptyList()),
    PRIME_PLUS("Prime+", RankRole.VIP, ChatColor.LIGHT_PURPLE, emptyList()),
    BLAZE("Blaze", RankRole.SPECIAL, ChatColor.YELLOW, emptyList()),
    MASTER("Master", RankRole.SPECIAL, ChatColor.YELLOW, emptyList()),

    /* Style+: cada duracao tem a sua cor (&2 1 mes, &6 3 meses, &b 6 meses); o "+" e personalizavel */
    @com.google.gson.annotations.SerializedName(value = "DUNK_PLUS_1", alternate = {"HEAVEN_PLUS_1", "DUNKPLUS_1"})
    DUNK_PLUS_1("Style+", RankRole.VIP, ChatColor.DARK_GREEN, emptyList()), // 1 mes
    @com.google.gson.annotations.SerializedName(value = "DUNK_PLUS_3", alternate = {"HEAVEN_PLUS_3", "DUNKPLUS_3"})
    DUNK_PLUS_3("Style+", RankRole.VIP, ChatColor.GOLD, emptyList()), // 3 meses
    @com.google.gson.annotations.SerializedName(value = "DUNK_PLUS_6", alternate = {"HEAVEN_PLUS_6", "DUNKPLUS_6"})
    DUNK_PLUS_6("Style+", RankRole.VIP, ChatColor.AQUA, emptyList()), // 6 meses

    @com.google.gson.annotations.SerializedName(value = "PARTNER", alternate = "CREATOR")
    PARTNER("Partner", RankRole.SPECIAL, ChatColor.AQUA, emptyList()),
    @com.google.gson.annotations.SerializedName(value = "PARTNER_PLUS", alternate = "CREATOR_PLUS")
    PARTNER_PLUS("Partner+", RankRole.SPECIAL, ChatColor.DARK_AQUA, emptyList()),

    /* Criadores (abaixo da equipe: criador nao e staff) */
    TIKTOKER("Tiktoker", RankRole.SPECIAL, ChatColor.LIGHT_PURPLE, emptyList()),
    STREAMER("Streamer", RankRole.SPECIAL, ChatColor.DARK_PURPLE, emptyList()),
    YOUTUBER("Youtuber", RankRole.SPECIAL, ChatColor.RED, emptyList()),
    MIDIA("Midia", RankRole.SPECIAL, ChatColor.AQUA, emptyList()),
    MIDIA_PLUS("Creator+", RankRole.SPECIAL, ChatColor.DARK_AQUA, emptyList()),

    BUILDER("Builder", RankRole.STAFF, ChatColor.DARK_GREEN, emptyList()),
    STUDIO("Studio", RankRole.STAFF, ChatColor.DARK_GREEN, emptyList()),

    /* Equipe (HELPER abaixo de TRIAL) */
    HELPER("Helper", RankRole.STAFF, ChatColor.BLUE, emptyList()),
    TRIAL("Trial", RankRole.STAFF, ChatColor.DARK_PURPLE, emptyList()),
    MOD("Mod", RankRole.STAFF, ChatColor.DARK_PURPLE, emptyList()),
    MODPLUS("Mod+", RankRole.STAFF, ChatColor.DARK_PURPLE, emptyList()),
    ADMIN("Admin", RankRole.STAFF, ChatColor.DARK_RED, singletonList("*")),
    CHEFE("Chefe", RankRole.STAFF, ChatColor.DARK_RED, singletonList("*")),
    DEVELOPER("Developer", RankRole.STAFF, ChatColor.LIGHT_PURPLE, singletonList("*")),
    UWU("uwu", RankRole.STAFF, ChatColor.LIGHT_PURPLE, singletonList("*"));

    private final String name;

    private final RankRole role;
    private final ChatColor color;

    private final List<String> permissions;

    public static RankType of(String name) {
        if ("stop".equalsIgnoreCase(name)) return DUNK;
        if ("aura".equalsIgnoreCase(name)) return BETA;
        if ("creator".equalsIgnoreCase(name)) return PARTNER;
        if ("creator+".equalsIgnoreCase(name) || "creator_plus".equalsIgnoreCase(name)) return PARTNER_PLUS;
        if ("helper".equalsIgnoreCase(name)) return HELPER;

        return Arrays.stream(values())
                .filter(rank -> rank.name().equalsIgnoreCase(name) || rank.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public String getColoredName() {
        return isDunkPlus() ? color + "Style§6+" : color + name;
    }

    public boolean isDunkPlus() {
        return this == DUNK_PLUS_1 || this == DUNK_PLUS_3 || this == DUNK_PLUS_6;
    }

    public String getBoldColorName() {
        return color + "§l" + name;
    }

    public RankType bellow() {
        return this == MEMBER ? MEMBER : values()[ordinal() - 1];
    }
}
