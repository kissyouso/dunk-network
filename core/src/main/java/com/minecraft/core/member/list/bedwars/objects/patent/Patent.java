package com.minecraft.core.member.list.bedwars.objects.patent;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Patent {

    INICIADOR("Iniciador", "§7", 0),
    APRENDIZ("Aprendiz", "§f", 5000),
    GUERREIRO("Guerreiro", "§a", 25000),
    VETERANO("Veterano", "§b", 75000),
    ELITE("Elite", "§6", 150000),
    MESTRE("Mestre", "§d", 300000),
    LENDA("Lenda", "§c", 600000);

    private final String name, color;
    private final int requiredXp;

    public String getColoredName() {
        return color + name;
    }

    public static Patent of(int xp) {
        Patent result = INICIADOR;

        for (Patent patent : values()) {
            if (xp >= patent.getRequiredXp())
                result = patent;
        }

        return result;
    }
}
