package com.minecraft.lobby.architect.list.bedwars.leaderboard;

import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.bukkit.api.hologram.leaderboard.period.LeaderboardPeriod;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.stats.BedStats;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class BedFinalKillLeaderboard extends BedStatLeaderboard {

    public BedFinalKillLeaderboard(Player player, Location location) {
        super("kills", "KILLS FINAIS", player, location);
    }

    @Override
    protected int getValue(BedMember member, ArcadeCategory category, LeaderboardPeriod period) {
        if (period == LeaderboardPeriod.WEEK)
            return sum(member, category, BedStats::getWeekFinalKills);

        if (period == LeaderboardPeriod.MONTH)
            return sum(member, category, BedStats::getMonthFinalKills);

        return sum(member, category, BedStats::getFinalKills);
    }
}
