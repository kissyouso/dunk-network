package com.minecraft.lobby.architect.list.bedwars.leaderboard;

import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.bukkit.api.hologram.leaderboard.period.LeaderboardPeriod;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.Core;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class BedLevelLeaderboard extends BedStatLeaderboard {

    private static final NumberFormat FORMAT = NumberFormat.getInstance(Locale.US);

    public BedLevelLeaderboard(Player player, Location location) {
        super("level", "NÍVEL", player, location);
    }

    @Override
    protected List<BedMember> loadMembers() {
        return new java.util.ArrayList<>(Core.getBedWarsData().ranking("context.level", TOTAL));
    }

    @Override
    protected int getValue(BedMember member, ArcadeCategory category, LeaderboardPeriod period) {
        if (period == LeaderboardPeriod.WEEK)
            return member.getWeekLevelXp();

        if (period == LeaderboardPeriod.MONTH)
            return member.getMonthLevelXp();

        return member.getTotalXp();
    }

    @Override
    protected Comparator<BedMember> comparator(ArcadeCategory category, LeaderboardPeriod period) {
        return Comparator.<BedMember>comparingInt(BedMember::getLevel)
                .thenComparingInt(member -> getValue(member, category, period))
                .reversed();
    }

    @Override
    protected String formatValue(BedMember member, int value) {
        return member.getLevelName() + " §7(" + FORMAT.format(value) + " XP)";
    }
}
