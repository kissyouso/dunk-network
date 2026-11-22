package com.minecraft.core.api.collectible.type.title;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Material;

/**
 * Categorias do menu de títulos.
 */
@Getter
@AllArgsConstructor
public enum TitleCategory {

    GERAIS("Gerais", Material.NAME_TAG, "Títulos animados e exclusivos."),
    BEDWARS("Bed Wars", Material.BED, "Mostre suas estatísticas do Bed Wars."),
    DUELS("Duels", Material.DIAMOND_SWORD, "Mostre suas estatísticas do Duels."),
    ACADEMY("Academy", Material.WATCH, "Mostre suas estatísticas da Academy.");

    private final String name;
    private final Material icon;
    private final String description;
}
