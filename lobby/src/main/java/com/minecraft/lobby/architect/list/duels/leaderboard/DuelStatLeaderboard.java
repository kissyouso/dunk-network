package com.minecraft.lobby.architect.list.duels.leaderboard;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.bukkit.api.hologram.leaderboard.LeaderboardHologram;
import com.minecraft.core.bukkit.api.hologram.leaderboard.period.LeaderboardPeriod;
import com.minecraft.core.bukkit.api.hologram.touch.Touch;
import com.minecraft.core.bukkit.api.hologram.type.client.HologramClient;
import com.minecraft.core.member.context.stats.ArcadeStats;
import com.minecraft.core.member.list.duels.DuelMember;
import com.minecraft.core.util.Util;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Ranking do lobby de Duels (soma de todos os modos):
 * TOP 100 (1/10) / TÍTULO / 1. Nome - valor ... / Clique para alternar! / Geral Semanal Mensal
 */
public class DuelStatLeaderboard extends LeaderboardHologram {

    private static final int PER_PAGE = 10, TOTAL = 100;

    private final String title;
    private final ToIntFunction<ArcadeStats> total, week, month;

    public DuelStatLeaderboard(String name, String title, Player player, Location location,
                               ToIntFunction<ArcadeStats> total, ToIntFunction<ArcadeStats> week, ToIntFunction<ArcadeStats> month) {
        super(name, player, location, 10);

        this.title = title;

        this.total = total;
        this.week = week;
        this.month = month;
    }

    public static DuelStatLeaderboard bestStreak(Player player, Location location) {
        return new DuelStatLeaderboard("streak", "MELHOR SEQUENCIA", player, location,
                ArcadeStats::getHighWinStreak, ArcadeStats::getWeekWinStreak, ArcadeStats::getMonthWinStreak);
    }

    public static DuelStatLeaderboard kills(Player player, Location location) {
        return new DuelStatLeaderboard("kills", "KILLS", player, location,
                ArcadeStats::getKills, ArcadeStats::getWeekKills, ArcadeStats::getMonthKills);
    }

    public static DuelStatLeaderboard wins(Player player, Location location) {
        return new DuelStatLeaderboard("wins", "VITORIAS", player, location,
                ArcadeStats::getWins, ArcadeStats::getWeekWins, ArcadeStats::getMonthWins);
    }

    private int getValue(DuelMember member, LeaderboardPeriod period) {
        ToIntFunction<ArcadeStats> getter = period == LeaderboardPeriod.WEEK ? week : period == LeaderboardPeriod.MONTH ? month : total;

        return member.getStats().values().stream()
                .filter(stats -> stats != null)
                .mapToInt(getter)
                .sum();
    }

    @Override
    public void handle() {
        HologramClient hologram = getHologram();

        if (hologram.getProperty("page", null) == null)
            hologram.writeProperty("page", 0);

        render();

        hologram.setTouch((player, touch) -> {
            if (touch == Touch.RIGHT) {
                hologram.writeProperty("page", (hologram.getInt("page") + 1) % (TOTAL / PER_PAGE));
            } else {
                LeaderboardPeriod period = (LeaderboardPeriod) hologram.getProperty("period", null);

                /* Geral -> Semanal -> Mensal -> Geral */
                hologram.writeProperty("period", period == null ? LeaderboardPeriod.WEEK : period == LeaderboardPeriod.WEEK ? LeaderboardPeriod.MONTH : null);
                hologram.writeProperty("page", 0);
            }

            player.playSound(player.getLocation(), Sound.CLICK, 1.0f, 2.0f);
            render();
        });
    }

    protected void render() {
        HologramClient hologram = getHologram();

        int page = hologram.getInt("page");
        LeaderboardPeriod period = (LeaderboardPeriod) hologram.getProperty("period", null);

        List<DuelMember> members = new ArrayList<>(Core.getDuelsData().ranking("context.stats", TOTAL)).stream()
                .filter(member -> member != null && member.getStats() != null && Core.getAccountData().of(member.getId()) != null)
                .sorted((a, b) -> Integer.compare(getValue(b, period), getValue(a, period)))
                .collect(Collectors.toList());

        List<String> lines = new ArrayList<>();

        lines.add(String.format("§b§lTOP 100 §7(%d/%d)", page + 1, TOTAL / PER_PAGE));
        lines.add("§e§l" + title);

        for (int i = page * PER_PAGE; i < (page + 1) * PER_PAGE; i++) {
            if (i < members.size()) {
                DuelMember member = members.get(i);
                Account account = Core.getAccountData().of(member.getId());

                String name = account != null && account.getRank() != null
                        ? account.getRank().getType().getColor() + account.getName()
                        : "§7...";

                lines.add("§e" + (i + 1) + ". " + name + " §7- §e" + Util.formatNumber(getValue(member, period)));
            } else
                lines.add("§e" + (i + 1) + ". §7...");
        }

        lines.add("§6§lClique para alternar!");
        lines.add((period == null ? "§6" : "§7") + "Geral "
                + (period == LeaderboardPeriod.WEEK ? "§6" : "§7") + "Semanal "
                + (period == LeaderboardPeriod.MONTH ? "§6" : "§7") + "Mensal");

        hologram.setSpaciousText(lines);
    }
}
