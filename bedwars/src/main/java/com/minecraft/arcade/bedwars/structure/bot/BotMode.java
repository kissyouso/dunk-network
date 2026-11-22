package com.minecraft.arcade.bedwars.structure.bot;

import lombok.Getter;

import java.util.Arrays;

/**
 * Perfil de comportamento do bot. Cada modo muda apenas os pesos de decisão e a
 * ousadia do movimento - nada aqui toca no pacote do cliente.
 */
@Getter
public enum BotMode {

    RUSH("Rush", "cruza o mapa atrás da cama inimiga", 0.20, 1.00, 0.10, false, 1.15, 0.35),
    DEFENSIVE("Defensivo", "guarda a própria cama e castiga invasores", 0.30, 0.15, 1.00, true, 0.95, 0.40),
    AGGRESSIVE("Agressivo", "caça o inimigo mais próximo", 1.00, 0.45, 0.20, false, 1.10, 0.55),
    TACTICAL("Tático", "equilibra caça, cama e defesa", 0.70, 0.75, 0.60, true, 1.00, 0.45),
    BRIDGER("Pontador", "atravessa tudo construindo", 0.25, 1.00, 0.15, true, 1.05, 0.20),
    PVP_MASTER("Mestre do PvP", "luta de perto com strafe pesado", 1.00, 0.20, 0.35, false, 1.10, 0.85),
    SURVIVAL("Sobrevivência", "recua, se reequipa e segura a ilha", 0.35, 0.25, 0.90, true, 0.90, 0.30);

    private final String label, description;

    /* Pesos usados pela decisão do bot */
    private final double hunt, bed, guard;

    /** Recuar quando a vida cai abaixo do limite configurado. */
    private final boolean retreat;

    /** Multiplicador de velocidade e de strafe do modo. */
    private final double speed, strafe;

    BotMode(String label, String description, double hunt, double bed, double guard,
            boolean retreat, double speed, double strafe) {
        this.label = label;
        this.description = description;
        this.hunt = hunt;
        this.bed = bed;
        this.guard = guard;
        this.retreat = retreat;
        this.speed = speed;
        this.strafe = strafe;
    }

    /** Devolve nulo quando o administrador digitou um modo que não existe. */
    public static BotMode named(String name) {
        if (name == null) return null;

        return Arrays.stream(values())
                .filter(mode -> mode.name().equalsIgnoreCase(name) || mode.getLabel().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public static BotMode of(String name) {
        BotMode mode = named(name);

        return mode == null ? TACTICAL : mode;
    }
}
