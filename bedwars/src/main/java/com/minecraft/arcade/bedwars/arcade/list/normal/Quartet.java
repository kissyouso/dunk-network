package com.minecraft.arcade.bedwars.arcade.list.normal;

import com.minecraft.arcade.bedwars.arcade.Arcade;
import com.minecraft.core.arcade.category.ArcadeCategory;

public class Quartet extends Arcade {

    public Quartet(String mapsDirectory, Integer minRooms, Integer maxRooms) {
        super(mapsDirectory, minRooms, maxRooms, ArcadeCategory.BEDWARS_QUARTET);
    }
}
