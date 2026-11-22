package com.minecraft.core.api.skin.objects;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SkinCategory {

    NONE("Nenhum"),
    PADRAO("Padrão"),
    YOUTUBERS("Youtubers"),
    ANIMES("Animes"),
    FILMES_SERIES("Filmes & Séries"),
    SUPER_HEROES("Super Heróis"),
    JOGOS("Jogos"),
    DIVERSAS("Diversas"),
    HALLOWEEN("Halloween"),
    NATALINA("Natalinas");

    private final String name;

    public SkinCategory next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
