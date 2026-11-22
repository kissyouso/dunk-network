package com.minecraft.academy.arena;

import com.minecraft.academy.Academy;
import com.minecraft.academy.arena.types.Damage;
import com.minecraft.academy.arena.types.Lava;
import com.minecraft.academy.arena.types.Texture;
import com.minecraft.academy.arena.types.Timers;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArenaStorage {

    private final List<Arena> arenas = new ArrayList<>();
    private final Arena fallback;

    public ArenaStorage(Academy academy) {
        this.fallback = register(academy, new Timers());

        register(academy, new Lava());
        register(academy, new Damage());
        register(academy, new Texture());
    }

    private <T extends Arena> T register(Academy academy, T arena) {
        arenas.add(arena);
        academy.getServer().getPluginManager().registerEvents(arena, academy);
        return arena;
    }

    public Arena getDefaultArena() {
        Arena arena = getArena(Academy.getAcademy().getSettings().getDefaultArena());

        if (arena == null || !arena.isEnabled()) {
            for (Arena other : arenas) {
                if (other.isEnabled())
                    return other;
            }

            return fallback;
        }

        return arena;
    }

    public void applySettings() {
        for (Arena arena : arenas)
            arena.applySettings();
    }

    public List<Arena> getArenas() {
        return Collections.unmodifiableList(arenas);
    }

    public Arena getArena(String name) {
        return arenas.stream().filter(arena -> arena.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public Arena getArena(World world) {
        return arenas.stream().filter(arena -> arena.getWorld().getUID().equals(world.getUID())).findFirst().orElse(null);
    }
}
