package com.minecraft.core.arcade.room.team.preset;

import lombok.AllArgsConstructor;
import lombok.Getter;
//import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Color;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum TeamPresetType {

    RED("Vermelho", "V", ChatColor.RED, Color.RED),
    BLUE("Azul", "A", ChatColor.BLUE, Color.BLUE),
    GREEN("Verde", "V", ChatColor.GREEN, Color.LIME),
    YEL("Amarelo", "A", ChatColor.YELLOW, Color.YELLOW),
    CYAN("Ciano", "C", ChatColor.AQUA, Color.AQUA),
    WHIT("Branco", "B", ChatColor.WHITE, Color.WHITE),
    PINK("Rosa", "R", ChatColor.LIGHT_PURPLE, Color.FUCHSIA),
    GRAY("Cinza", "C", ChatColor.DARK_GRAY, Color.GRAY);

    private final String name;
    private final String id;

    private final ChatColor color;
    private final Color rgb;

    /**
     * Código antigo usado nos mapas feitos antes de Verde/Rosa (oran_spawn, purp_bed...).
     */
    public String getLegacyCode() {
        switch (this) {
            case GREEN:
                return "oran";
            case PINK:
                return "purp";
            default:
                return null;
        }
    }

    public static TeamPresetType of(String name) {
        if (name == null) return null;

        TeamPresetType preset = Arrays.stream(values())
                .filter(team -> team.name().equalsIgnoreCase(name) || team.getName().equalsIgnoreCase(name) || name.equalsIgnoreCase(team.getLegacyCode()))
                .findFirst()
                .orElse(null);

        if (preset != null) return preset;

        switch (name.toLowerCase()) {
            case "vermelho":
            case "red":
                return RED;
            case "azul":
            case "blue":
                return BLUE;
            case "verde":
            case "green":
            case "lime":
            case "orange":
            case "laranja":
                return GREEN;
            case "amarelo":
            case "yellow":
                return YEL;
            case "ciano":
            case "cyan":
            case "aqua":
                return CYAN;
            case "branco":
            case "white":
                return WHIT;
            case "rosa":
            case "pink":
            case "purple":
            case "roxo":
                return PINK;
            case "cinza":
            case "gray":
            case "grey":
                return GRAY;
            default:
                return null;
        }
    }

    public static TeamPresetType of(ChatColor color) {
        return Arrays.stream(values())
                .filter(team -> team.getColor().equals(color) || team.getRgb().toString().equalsIgnoreCase(color.toString()))
                .findFirst()
                .orElse(null);
    }
}
