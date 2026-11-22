package com.minecraft.event.game;

import com.minecraft.event.game.types.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Getter
@AllArgsConstructor
public enum EventType {

    SUMO("Sumo", "Derrube o adversário da plataforma.", Sumo::new, Arrays.asList("entrada", "pos1", "pos2")),
    FIGHT("Fight", "Duelos 1v1 com kit até sobrar um.", Fight::new, Arrays.asList("entrada", "pos1", "pos2")),
    SPLEEF("Spleef", "Quebre a neve e não caia!", Spleef::new, Arrays.asList("entrada", "chao1", "chao2")),
    PARKOUR("Parkour", "Chegue primeiro na placa de ouro.", Parkour::new, Arrays.asList("entrada")),
    SEMAFORO("Semáforo", "Corra no verde, pare no vermelho!", RedLight::new, Arrays.asList("entrada")),
    BATATA_QUENTE("Batata Quente", "Passe a batata antes que ela exploda!", HotPotato::new, Arrays.asList("entrada")),
    KILLER("Killer", "Todos contra todos, o último vivo vence.", Killer::new, Arrays.asList("entrada"));

    private final String name, description;
    private final Supplier<GameEvent> factory;

    /* Locais obrigatórios em locations.yml (grupo = nome do enum) */
    private final List<String> points;

    public static EventType of(String name) {
        for (EventType type : values()) {
            if (type.name().equalsIgnoreCase(name) || type.getName().equalsIgnoreCase(name)
                    || type.name().replace("_", "").equalsIgnoreCase(name))
                return type;
        }

        return null;
    }
}
