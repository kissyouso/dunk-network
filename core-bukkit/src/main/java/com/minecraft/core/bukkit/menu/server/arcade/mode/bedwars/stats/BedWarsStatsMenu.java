package com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.stats;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.arcade.room.type.Type;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.stats.BedStats;
import com.minecraft.core.server.type.ServerType;
import com.minecraft.core.util.Util;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BedWarsStatsMenu extends Menu {

    private static final Map<ArcadeCategory, Integer> SLOTS = new LinkedHashMap<>();

    static {
        SLOTS.put(ArcadeCategory.BEDWARS_SOLO, 20);
        SLOTS.put(ArcadeCategory.BEDWARS_DUO, 21);
        SLOTS.put(ArcadeCategory.BEDWARS_TRIO, 23);
        SLOTS.put(ArcadeCategory.BEDWARS_QUARTET, 24);
        SLOTS.put(ArcadeCategory.BEDWARS_VERSUS_SOLO, 29);
        SLOTS.put(ArcadeCategory.BEDWARS_VERSUS_DUO, 30);
        SLOTS.put(ArcadeCategory.BEDWARS_VERSUS_TRIO, 32);
        SLOTS.put(ArcadeCategory.BEDWARS_VERSUS_QUARTET, 33);
    }

    private static final Map<ArcadeCategory, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put(ArcadeCategory.BEDWARS_SOLO, "BedWars (Solo)");
        NAMES.put(ArcadeCategory.BEDWARS_DUO, "BedWars (Duplas)");
        NAMES.put(ArcadeCategory.BEDWARS_TRIO, "BedWars (Trios)");
        NAMES.put(ArcadeCategory.BEDWARS_QUARTET, "BedWars (Quartetos)");
        NAMES.put(ArcadeCategory.BEDWARS_VERSUS_SOLO, "BedWars (1v1)");
        NAMES.put(ArcadeCategory.BEDWARS_VERSUS_DUO, "BedWars (2v2)");
        NAMES.put(ArcadeCategory.BEDWARS_VERSUS_TRIO, "BedWars (3v3)");
        NAMES.put(ArcadeCategory.BEDWARS_VERSUS_QUARTET, "BedWars (4v4)");
    }

    private final Account target;
    private final BedMember member;

    @Setter
    private Type type = Type.CASUAL;

    public BedWarsStatsMenu(Player player, Menu last) {
        this(player, Core.getAccountData().of(player.getUniqueId()), last);
    }

    public BedWarsStatsMenu(Player player, Account target, Menu last) {
        super(player, player.getUniqueId().equals(target.getId()) ? "Suas estatísticas" : "Estatísticas de " + target.getName(), last, 6);

        this.target = target;

        BedMember loaded = Core.getBedWarsData().of(target.getId(), true);

        if (loaded == null)
            loaded = (BedMember) target.loadMember(ServerType.BEDWARS);

        this.member = loaded;
    }

    @Override
    public void handle() {
        clear();

        if (member == null) {
            addErrorButton(22, "§cNão foi possível carregar as estatísticas.");
        } else {
            int matches = 0, bedsLost = 0, deaths = 0, finalDeaths = 0, defeats = 0;

            for (BedStats stats : member.getStatsList()) {
                matches += stats.getMatches();
                bedsLost += stats.getBedsLost();
                deaths += stats.getDeaths();
                finalDeaths += stats.getFinalDeaths();
                defeats += stats.getDefeats();
            }

            addItem(4, Item.of(Material.PAPER, "§eGeral",
                    "§7Experiência global: §f" + Util.formatNumber(member.getTotalXp()),
                    "",
                    "§7Nível BedWars: " + member.getLevelName(),
                    "§7Experiência: §f" + Util.formatNumber(member.getLevelXp()),
                    "",
                    "§7Partidas: §f" + Util.formatNumber(matches),
                    "",
                    "§7Camas quebradas: §f" + Util.formatNumber(member.getTotalBedDestruction()),
                    "§7Camas perdidas: §f" + Util.formatNumber(bedsLost),
                    "",
                    "§7Kills: §f" + Util.formatNumber(member.getTotalKills()),
                    "§7Mortes: §f" + Util.formatNumber(deaths),
                    "§7Kills Finais: §f" + Util.formatNumber(member.getTotalFinalKills()),
                    "§7Mortes Finais: §f" + Util.formatNumber(finalDeaths),
                    "",
                    "§7Vitórias: §f" + Util.formatNumber(member.getTotalWins()),
                    "§7Derrotas: §f" + Util.formatNumber(defeats),
                    "",
                    "§7Win Streak: §f" + Util.formatNumber(member.getTotalWinstreak()),
                    "§7Melhor Win Streak: §f" + Util.formatNumber(member.getTotalHighWinstreak())));

            SLOTS.forEach((arcade, slot) -> {
                BedStats stats = member.getStats(arcade, arcade.name().contains("VERSUS") ? Type.CASUAL : type);

                addItem(slot, Item.of(Material.BED, "§e" + NAMES.get(arcade), getInfoLore(stats)));
            });
        }

        if (isReturnable())
            addBackButton(49);

        display();
    }

    protected List<String> getInfoLore(BedStats stats) {
        return Arrays.asList(
                "§7Camas Quebradas: §f" + Util.formatNumber(stats.getBedDestruction()),
                "§7Camas Perdidas: §f" + Util.formatNumber(stats.getBedsLost()),
                "",
                "§7Kills: §f" + Util.formatNumber(stats.getKills()),
                "§7Mortes: §f" + Util.formatNumber(stats.getDeaths()),
                "",
                "§7Kills Finais: §f" + Util.formatNumber(stats.getFinalKills()),
                "§7Mortes Finais: §f" + Util.formatNumber(stats.getFinalDeaths()),
                "",
                "§7Kill Streak: §f" + Util.formatNumber(stats.getKillStreak()),
                "§7Melhor Kill Streak: §f" + Util.formatNumber(stats.getHighKillStreak()),
                "",
                "§7Vitórias: §f" + Util.formatNumber(stats.getWins()),
                "§7Derrotas: §f" + Util.formatNumber(stats.getDefeats()),
                "",
                "§7Win Streak: §f" + Util.formatNumber(stats.getWinStreak()),
                "§7Melhor Win Streak: §f" + Util.formatNumber(stats.getHighWinStreak())
        );
    }
}
