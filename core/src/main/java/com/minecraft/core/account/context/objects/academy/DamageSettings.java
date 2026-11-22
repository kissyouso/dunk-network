package com.minecraft.core.account.context.objects.academy;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Configurações do treino de Damage (salvas por jogador).
 */
@Getter
@Setter
public class DamageSettings {

    private Level level = Level.EASY;
    private Frequency frequency = Frequency.HALF;
    private Recraft recraft = Recraft.MUSHROOM;

    private boolean armor, drops, wither, fullInventory = true;

    /* Recordes só valem sem armadura, drops e wither */
    public boolean isRecordValid() {
        return !armor && !drops && !wither;
    }

    public Level getLevel() {
        return level == null ? Level.EASY : level;
    }

    public Frequency getFrequency() {
        return frequency == null ? Frequency.HALF : frequency;
    }

    public Recraft getRecraft() {
        return recraft == null ? Recraft.MUSHROOM : recraft;
    }

    @Getter
    @AllArgsConstructor
    public enum Level {

        PACIFIC("Pacífico", "§b", 3.0D, 12),
        EASY("Fácil", "§a", 4.0D, 10),
        MEDIUM("Médio", "§e", 5.0D, 11),
        HARD("Difícil", "§c", 7.0D, 14),
        VERY_HARD("Muito Difícil", "§5", 8.0D, 5),
        EXTREME("Extremo", "§4", 9.0D, 1);

        private final String name, color;
        private final double damage;
        private final int dyeColor;

        public String getHearts() {
            return String.valueOf(damage / 2D);
        }

        public Level next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    @Getter
    @AllArgsConstructor
    public enum Frequency {

        QUARTER("0.25s", 5),
        HALF("0.5s", 10),
        THREE_QUARTERS("0.75s", 15),
        SECOND("1.0s", 20),
        SECOND_AND_HALF("1.5s", 30);

        private final String display;
        private final int ticks;

        public Frequency next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    @Getter
    @AllArgsConstructor
    public enum Recraft {

        MUSHROOM("Cogumelo"),
        COCOA("Cocoa"),
        NONE("Nenhum");

        private final String name;

        public Recraft next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
}
