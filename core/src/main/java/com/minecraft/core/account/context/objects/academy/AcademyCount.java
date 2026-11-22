package com.minecraft.core.account.context.objects.academy;

import com.minecraft.core.Core;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * Quantidade de jogadores por arena de um servidor da Academy (publicada no Redis).
 */
@Getter
@AllArgsConstructor
public class AcademyCount {

    public static final String KEY = "academy:count:";

    private final Map<String, Integer> arenas;

    /* Soma cada arena em todos os servidores da Academy (mapa vazio = nenhum servidor online) */
    public static Map<String, Integer> totals() {
        Map<String, Integer> totals = new java.util.HashMap<>();

        for (AcademyCount count : Core.getRedis().loadAll(KEY, AcademyCount.class)) {
            if (count == null || count.getArenas() == null) continue;

            count.getArenas().forEach((arena, players) -> totals.merge(arena, players, Integer::sum));
        }

        return totals;
    }
}
