package com.minecraft.arcade.thebridge.practice;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;

public final class PracticeCosmetics {

    public static final List<Animation> ANIMATIONS = Arrays.asList(
            new Animation("FOGUETE", "Foguete", Material.FIREWORK),
            new Animation("RAIO", "Relâmpago", Material.NETHER_STAR),
            new Animation("NENHUMA", "Nenhuma", Material.BARRIER)
    );

    private PracticeCosmetics() {
    }

    public static Animation animationOf(String key) {
        return ANIMATIONS.stream()
                .filter(animation -> animation.getId().equalsIgnoreCase(key))
                .findFirst()
                .orElse(ANIMATIONS.get(0));
    }

    @Getter
    @AllArgsConstructor
    public static class Animation {

        private final String id, name;
        private final Material icon;
    }
}
