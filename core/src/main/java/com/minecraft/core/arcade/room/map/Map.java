package com.minecraft.core.arcade.room.map;

import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.arcade.room.map.area.Cuboid;
import com.minecraft.core.arcade.room.map.location.SignedLocation;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@RequiredArgsConstructor
public class Map {

    private final int id;
    private final String name;

    private final ArcadeCategory arcade;

    private final transient File source;
    private final transient JsonObject config;

    private final transient int buildLimit;

    private Cuboid area;

    private boolean normal = true;

    private final transient List<SignedLocation> locations = new ArrayList<>();

    @Override
    public boolean equals(Object mapObj) {
        if (this == mapObj) return true;
        if (mapObj == null || getClass() != mapObj.getClass()) return false;

        Map map = (Map) mapObj;

        return map.getName().equals(name) && map.getId() == id;
    }

    /* Dois formatos de nome convivem nos configs da rede ('blue_spawn' no
     * Lanche, 'blue-spawn' no MarioWorld). Sem essa tolerância o time nasce
     * sem base, a partida entra em PLAYING sem teleportar ninguém e o jogador
     * fica preso no lobby de espera para sempre. O nome exato sempre vence. */
    public SignedLocation getLocation(String name) {
        for (SignedLocation signed : locations)
            if (signed.getName().equalsIgnoreCase(name))
                return signed;

        String normalized = name.toLowerCase().replace('_', '-');

        for (SignedLocation signed : locations)
            if (signed.getName().toLowerCase().replace('_', '-').equals(normalized))
                return signed;

        return null;
    }

    public Location getLocation(World world, String name) {
        SignedLocation signed = getLocation(name);

        return signed != null ? signed.getSynthetic().of(world) : null;
    }

    public boolean hasLocation(String name) {
        return getLocation(name) != null;
    }
}
