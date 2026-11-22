package com.minecraft.lobby.architect.list.bedwars.leaderboard;

import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.bukkit.api.hologram.leaderboard.period.LeaderboardPeriod;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.stats.BedStats;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class BedWinsLeaderboard extends BedStatLeaderboard {

    public BedWinsLeaderboard(Player player, Location location) {
        super("wins", "VITÓRIAS", player, location);
    }

    @Override
    protected int getValue(BedMember member, ArcadeCategory category, LeaderboardPeriod period) {
        if (period == LeaderboardPeriod.WEEK)
            return sum(member, category, BedStats::getWeekWins);

        if (period == LeaderboardPeriod.MONTH)
            return sum(member, category, BedStats::getMonthWins);

        return sum(member, category, BedStats::getWins);
    }
}
