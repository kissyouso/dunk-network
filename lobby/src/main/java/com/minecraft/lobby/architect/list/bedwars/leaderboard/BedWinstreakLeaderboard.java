package com.minecraft.lobby.architect.list.bedwars.leaderboard;

import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.bukkit.api.hologram.leaderboard.period.LeaderboardPeriod;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.stats.BedStats;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class BedWinstreakLeaderboard extends BedStatLeaderboard {

    public BedWinstreakLeaderboard(Player player, Location location) {
        super("winstreak", "WINSTREAK", player, location);
    }

    @Override
    protected int getValue(BedMember member, ArcadeCategory category, LeaderboardPeriod period) {
        if (period == LeaderboardPeriod.WEEK)
            return sum(member, category, BedStats::getWeekWinStreak);

        if (period == LeaderboardPeriod.MONTH)
            return sum(member, category, BedStats::getMonthWinStreak);

        return sum(member, category, BedStats::getWinStreak);
    }
}
