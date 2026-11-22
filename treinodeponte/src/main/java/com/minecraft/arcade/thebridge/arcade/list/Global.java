package com.minecraft.arcade.thebridge.arcade.list;

import com.minecraft.arcade.thebridge.arcade.TheBridgeArcade;
import com.minecraft.core.arcade.category.ArcadeCategory;

/**
 * Sala compartilhada do Treino de Ponte (ate 30 jogadores). Quando enche,
 * outra sala e criada a partir do mesmo template.
 */
public class Global extends TheBridgeArcade {

    public Global(String directory, Integer minRooms, Integer maxRooms) {
        super(directory, minRooms, maxRooms, ArcadeCategory.THE_BRIDGE_GLOBAL);
    }
}
