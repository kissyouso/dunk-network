package com.minecraft.core.api.collectible.type.title;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.academy.AcademyMetadata;
import com.minecraft.core.member.context.stats.ArcadeStats;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.member.list.duels.DuelMember;
import lombok.Getter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.ToLongFunction;

/**
 * Estatísticas que os títulos mostram ("Vitórias no Bed Wars: 1.234").
 * Os valores são lidos fora da thread principal pelo TitleOperator e guardados em cache.
 */
@Getter
public enum TitleStat {

    /* Bed Wars */
    BEDWARS_WINS(TitleCategory.BEDWARS, "Vitórias no Bed Wars", account -> bedwars(account).getTotalWins()),
    BEDWARS_FINAL_KILLS(TitleCategory.BEDWARS, "Kills Finais no Bed Wars", account -> bedwars(account).getTotalFinalKills()),
    BEDWARS_KILLS(TitleCategory.BEDWARS, "Kills no Bed Wars", account -> bedwars(account).getTotalKills()),
    BEDWARS_BEDS(TitleCategory.BEDWARS, "Camas Destruídas", account -> bedwars(account).getTotalBedDestruction()),
    BEDWARS_WINSTREAK(TitleCategory.BEDWARS, "Winstreak no Bed Wars", account -> bedwars(account).getTotalWinstreak()),
    BEDWARS_HIGH_WINSTREAK(TitleCategory.BEDWARS, "Maior Winstreak no Bed Wars", account -> bedwars(account).getTotalHighWinstreak()),
    BEDWARS_LEVEL(TitleCategory.BEDWARS, "Nível no Bed Wars", account -> bedwars(account).getLevel()),

    /* Duels */
    DUELS_WINS(TitleCategory.DUELS, "Vitórias no Duels", account -> duelsSum(account, ArcadeStats::getWins)),
    DUELS_KILLS(TitleCategory.DUELS, "Kills no Duels", account -> duelsSum(account, ArcadeStats::getKills)),
    DUELS_DEATHS(TitleCategory.DUELS, "Mortes no Duels", account -> duelsSum(account, ArcadeStats::getDeaths)),
    DUELS_WINSTREAK(TitleCategory.DUELS, "Winstreak no Duels", account -> duelsMax(account, ArcadeStats::getWinStreak)),
    DUELS_HIGH_WINSTREAK(TitleCategory.DUELS, "Maior Winstreak no Duels", account -> duelsMax(account, ArcadeStats::getHighWinStreak)),
    DUELS_MATCHES(TitleCategory.DUELS, "Partidas no Duels", account -> duelsSum(account, ArcadeStats::getMatches)),
    DUELS_LEVEL(TitleCategory.DUELS, "Nível no Duels", account -> duels(account).getLevel()),

    /* Academy (timers em milissegundos) */
    ACADEMY_REFILL(TitleCategory.ACADEMY, "Recorde no Refill", account -> academy(account).getTimerRefill(), true),
    ACADEMY_MUSHROOM(TitleCategory.ACADEMY, "Recorde no Recraft de Cogumelo", account -> academy(account).getTimerMushroom(), true),
    ACADEMY_COCOA(TitleCategory.ACADEMY, "Recorde no Recraft de Cacau", account -> academy(account).getTimerCocoa(), true),
    ACADEMY_LAVA_EASY(TitleCategory.ACADEMY, "Lavas Fáceis concluídas", account -> academy(account).getLavaEasy()),
    ACADEMY_LAVA_MEDIUM(TitleCategory.ACADEMY, "Lavas Médias concluídas", account -> academy(account).getLavaMedium()),
    ACADEMY_LAVA_HARD(TitleCategory.ACADEMY, "Lavas Difíceis concluídas", account -> academy(account).getLavaHard()),
    ACADEMY_LAVA_EXTREME(TitleCategory.ACADEMY, "Lavas Extremas concluídas", account -> academy(account).getLavaExtreme());

    private final TitleCategory category;
    private final String label;
    private final ToLongFunction<Account> loader;

    /* Valor em milissegundos, exibido em segundos ("12,4s") */
    private final boolean time;

    TitleStat(TitleCategory category, String label, ToLongFunction<Account> loader) {
        this(category, label, loader, false);
    }

    TitleStat(TitleCategory category, String label, ToLongFunction<Account> loader, boolean time) {
        this.category = category;
        this.label = label;
        this.loader = loader;
        this.time = time;
    }

    /**
     * Lê o valor do jogador (consulta o banco; chamar fora da thread principal). Sem dados = 0.
     */
    public long load(Account account) {
        try {
            return loader.applyAsLong(account);
        } catch (Exception e) {
            return 0L;
        }
    }

    public String format(long value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("pt", "BR"));

        if (time)
            return value <= 0 ? "--" : new DecimalFormat("0.0", symbols).format(value / 1000D) + "s";

        return new DecimalFormat("#,##0", symbols).format(value);
    }

    /* ---------------- Dados ---------------- */

    private static BedMember bedwars(Account account) {
        return Core.getBedWarsData().of(account.getId(), false);
    }

    private static DuelMember duels(Account account) {
        return Core.getDuelsData().of(account.getId(), false);
    }

    private static AcademyMetadata academy(Account account) {
        return account.getContext().getAcademy();
    }

    private static long duelsSum(Account account, ToLongFunction<ArcadeStats> getter) {
        return duels(account).getStats().values().stream().mapToLong(getter).sum();
    }

    private static long duelsMax(Account account, ToLongFunction<ArcadeStats> getter) {
        return duels(account).getStats().values().stream().mapToLong(getter).max().orElse(0L);
    }
}
