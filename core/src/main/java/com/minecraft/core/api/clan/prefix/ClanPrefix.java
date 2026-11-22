package com.minecraft.core.api.clan.prefix;

import com.minecraft.core.account.context.objects.rank.type.RankType;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum ClanPrefix {

    DEFAULT("default", "[", "]", RankType.MEMBER),
    BRACKET("bracket", "【", "】", RankType.VIP),
    SHIELD("shield", "╔", "╗", RankType.HELPER),
    CLOSE("close", "《", "》", RankType.MEMBER);

    private final String id, open, close;
    private final RankType minimumRank;

    ClanPrefix(String id, String open, String close, RankType minimumRank) {
        this.id = id;
        this.open = open;
        this.close = close;
        this.minimumRank = minimumRank;
    }

    public static ClanPrefix of(String id) {
        return Arrays.stream(values())
                .filter(p -> p.getId().equalsIgnoreCase(id) || p.name().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    public static String getFormattedList() {
        StringBuilder sb = new StringBuilder("(");
        ClanPrefix[] prefixes = values();
        for (int i = 0; i < prefixes.length; i++) {
            sb.append(prefixes[i].getId());
            if (i < prefixes.length - 1) sb.append("§c, ");
        }
        sb.append("§c)");
        return sb.toString();
    }

    public boolean hasPermission(RankType rank) {
        return rank.ordinal() >= minimumRank.ordinal();
    }
}
