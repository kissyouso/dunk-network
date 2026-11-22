package com.minecraft.core.bukkit.menu.account.max;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TagColor {

    GREEN("Verde", "§a", 10, 10),
    AQUA("Aqua", "§b", 12, 11),
    RED("Vermelho", "§c", 1, 12),
    PINK("Rosa", "§d", 9, 13),
    YELLOW("Amarelo", "§e", 11, 14),
    WHITE("Branco", "§f", 15, 15),
    GOLD("Dourado", "§6", 14, 16),
    BLUE("Azul", "§9", 4, 19),
    PURPLE("Roxo", "§5", 5, 20),
    CYAN("Ciano", "§3", 6, 21);

    private final String name, color;
    private final int dye, slot;

    public String getColoredName() {
        return color + name;
    }

    public static TagColor of(String color) {
        for (TagColor tagColor : values()) {
            if (tagColor.getColor().equalsIgnoreCase(color))
                return tagColor;
        }

        return GOLD;
    }
}
