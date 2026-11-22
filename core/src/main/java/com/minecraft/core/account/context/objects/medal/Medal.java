package com.minecraft.core.account.context.objects.medal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;

import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public enum Medal {

    NONE("Nenhuma", "", ChatColor.GRAY),
    HERO("Hero", "Ⓗ", ChatColor.RED),
    LUA("Lua", "☾", ChatColor.YELLOW),
    MONARCA("Monarca", "㊚", ChatColor.DARK_PURPLE),
    KOMI("Komi", "Ⓚ", ChatColor.DARK_PURPLE),
    RADIOATIVO("Radioativo", "☣", ChatColor.DARK_RED),
    SOL("Sol", "☀", ChatColor.YELLOW),
    ESTRELA("Estrela", "✪", ChatColor.GOLD),
    FLOR("Flor", "❀", ChatColor.LIGHT_PURPLE),
    COROA("Coroa", "♕", ChatColor.YELLOW),
    GUERREIRO("Guerreiro", "⚔", ChatColor.RED),
    CORACAO("Coração", "❤", ChatColor.RED),
    LENDARIO("Lendário", "☯", ChatColor.DARK_PURPLE),
    RELAMPAGO("Relâmpago", "⚡", ChatColor.YELLOW),
    DIAMANTE("Diamante", "◆", ChatColor.AQUA),
    REI("Rei", "♛", ChatColor.GOLD),
    MORTE("Morte", "☠", ChatColor.DARK_GRAY),
    TROFEU("Troféu", "♔", ChatColor.GOLD),
    GLORIA("Glória", "★", ChatColor.AQUA),
    HONRA("Honra", "✦", ChatColor.GREEN),
    VALENTE("Valente", "♞", ChatColor.DARK_RED),
    FENIX("Fênix", "☄", ChatColor.RED);

    private final String name, symbol;
    private final ChatColor color;

    public static Medal of(String name) {
        return Stream.of(values())
                .filter(medal -> medal.name().equalsIgnoreCase(name) || medal.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    };

    public String getColoredName() {
        return color + name;
    };

    public String getColoredSymbol() {
        return color + symbol;
    };

    public String getPermission() {
        return "medal." + name.toLowerCase();
    }
}
