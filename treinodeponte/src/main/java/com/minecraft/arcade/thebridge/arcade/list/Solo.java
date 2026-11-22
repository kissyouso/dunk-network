package com.minecraft.arcade.thebridge.arcade.list;

import com.minecraft.arcade.thebridge.arcade.TheBridgeArcade;
import com.minecraft.core.arcade.category.ArcadeCategory;

/**
 * Treino Solo: compartilha a mesma sala do modo (capacidade vinda do
 * {@code max_players} do mapa), como BedWars e PvP.
 */
public class Solo extends TheBridgeArcade {

    public Solo(String directory, Integer minRooms, Integer maxRooms) {
        super(directory, minRooms, maxRooms, ArcadeCategory.THE_BRIDGE_PRACTICE);
    }
}
