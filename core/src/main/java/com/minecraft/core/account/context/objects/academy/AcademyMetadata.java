package com.minecraft.core.account.context.objects.academy;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Recordes e configurações da Academy (Timers, Lava e Damage).
 */
@Getter
@Setter
public class AcademyMetadata {

    /* Timers: melhor tempo em milissegundos (0 = sem recorde) */
    private long timerRefill, timerMushroom, timerCocoa;

    /* Lava: quantidade de conclusões por nível */
    private int lavaEasy, lavaMedium, lavaHard, lavaExtreme;

    /* Damage: maior tempo aguentado (segundos) por nível */
    private Map<String, Integer> damageRecords = new HashMap<>();

    private DamageSettings damageSettings = new DamageSettings();

    /* Arena escolhida no lobby, consumida ao entrar no servidor da Academy */
    private String route;

    public Map<String, Integer> getDamageRecords() {
        if (damageRecords == null)
            damageRecords = new HashMap<>();

        return damageRecords;
    }

    public DamageSettings getDamageSettings() {
        if (damageSettings == null)
            damageSettings = new DamageSettings();

        return damageSettings;
    }

    public int getDamageRecord(DamageSettings.Level level) {
        return getDamageRecords().getOrDefault(level.name(), 0);
    }

    public void setDamageRecord(DamageSettings.Level level, int seconds) {
        getDamageRecords().put(level.name(), seconds);
    }
}
