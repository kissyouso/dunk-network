package com.minecraft.lobby.architect.list.bedwars.leaderboard;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.bukkit.api.hologram.leaderboard.LeaderboardHologram;
import com.minecraft.core.bukkit.api.hologram.leaderboard.period.LeaderboardPeriod;
import com.minecraft.core.bukkit.api.hologram.touch.Touch;
import com.minecraft.core.bukkit.api.hologram.type.client.HologramClient;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.stats.BedStats;
import com.minecraft.core.util.Util;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ranking do lobby de BedWars no formato:
 * TOP 100 (1/10) / TÍTULO / 1. Nome - valor ... / Clique para alternar! / Geral Semanal Mensal
 */
public abstract class BedStatLeaderboard extends LeaderboardHologram {

    protected static final int PER_PAGE = 10, TOTAL = 100;

    private final String title;

    public BedStatLeaderboard(String name, String title, Player player, Location location) {
        super(name, player, location, 10);

        this.title = title;
    }

    /* Valor do jogador para o modo (null = geral) e período (null = geral) */
    protected abstract int getValue(BedMember member, ArcadeCategory category, LeaderboardPeriod period);

    protected String formatValue(BedMember member, int value) {
        return "§e" + Util.formatNumber(value);
    }

    /* Ordem da lista: maior valor primeiro. Subclasses podem priorizar outro critério. */
    protected Comparator<BedMember> comparator(ArcadeCategory category, LeaderboardPeriod period) {
        return (a, b) -> Integer.compare(getValue(b, category, period), getValue(a, category, period));
    }

    protected List<BedMember> loadMembers() {
        return new ArrayList<>(Core.getBedWarsData().ranking("metadata.statsList", TOTAL));
    }

    protected static int sum(BedMember member, ArcadeCategory category, java.util.function.ToIntFunction<BedStats> getter) {
        return member.getStatsList().stream()
                .filter(stats -> category == null || stats.getArcade() == category)
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
                int pages = TOTAL / PER_PAGE;
                hologram.writeProperty("page", (hologram.getInt("page") + 1) % pages);
            } else {
                LeaderboardPeriod period = (LeaderboardPeriod) hologram.getProperty("period", null);

                /* Geral -> Semanal -> Mensal -> Geral */
                LeaderboardPeriod next = period == null ? LeaderboardPeriod.WEEK : period == LeaderboardPeriod.WEEK ? LeaderboardPeriod.MONTH : null;

                hologram.writeProperty("period", next);
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
        ArcadeCategory category = mapCategory(String.valueOf(hologram.getProperty("mode", "GERAL")));

        List<BedMember> members = loadMembers().stream()
                .filter(member -> Core.getAccountData().of(member.getId()) != null)
                .sorted(comparator(category, period))
                .collect(Collectors.toList());

        List<String> lines = new ArrayList<>();

        lines.add(String.format("§b§lTOP 100 §7(%d/%d)", page + 1, TOTAL / PER_PAGE));
        lines.add("§e§l" + title);

        for (int i = page * PER_PAGE; i < (page + 1) * PER_PAGE; i++) {
            if (i < members.size()) {
                BedMember member = members.get(i);
                Account account = Core.getAccountData().of(member.getId());

                String name = account != null && account.getRank() != null
                        ? account.getRank().getType().getColor() + account.getName()
                        : "§7...";

                lines.add("§e" + (i + 1) + ". " + name + " §7- " + formatValue(member, getValue(member, category, period)));
            } else
                lines.add("§e" + (i + 1) + ". §7...");
        }

        lines.add("§6§lClique para alternar!");
        lines.add((period == null ? "§6" : "§7") + "Geral "
                + (period == LeaderboardPeriod.WEEK ? "§6" : "§7") + "Semanal "
                + (period == LeaderboardPeriod.MONTH ? "§6" : "§7") + "Mensal");

        hologram.setSpaciousText(lines);
    }

    public static ArcadeCategory mapCategory(String mode) {
        if (mode == null) return null;

        switch (mode.toUpperCase()) {
            case "SOLO":
                return ArcadeCategory.BEDWARS_SOLO;
            case "DUPLAS":
                return ArcadeCategory.BEDWARS_DUO;
            case "TRIOS":
                return ArcadeCategory.BEDWARS_TRIO;
            case "QUARTETOS":
                return ArcadeCategory.BEDWARS_QUARTET;
            default:
                return null;
        }
    }
}
