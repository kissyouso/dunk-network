package com.minecraft.arcade.bedwars.structure.bot;

import lombok.Getter;

import java.util.Arrays;

/**
 * Padrão de construção usado pelo bot ao cruzar um vão.
 * <p>
 * Os offsets são lidos na moldura do deslocamento (para frente / para o lado),
 * sempre uma camada abaixo dos pés. O que muda entre os estilos é a ordem e a
 * quantidade dos blocos do ciclo, se o bot salta no meio da ponte e para onde
 * ele olha - comportamento de entidade, não de pacote.
 */
@Getter
public enum BridgeStyle {

    STRAIGHT("Reto", new int[][]{{0, 0}}, false, false, 1, 1.00),
    DIAGONAL("Diagonal", new int[][]{{0, 0}, {1, 1}}, false, false, 2, 1.05),
    NINJA("Ninja", new int[][]{{0, 0}}, true, false, 1, 1.25),
    GOD("God", new int[][]{{0, 0}, {1, 0}}, false, false, 2, 1.30),
    SPEED("Speed", new int[][]{{0, 0}, {1, 0}}, true, false, 2, 1.40),
    BREEZILY("Breezily", new int[][]{{0, 0}, {1, 1}}, true, false, 2, 1.20),
    MOONWALK("Moonwalk", new int[][]{{0, 0}, {-1, 0}}, false, true, 2, 1.00),
    ANDROMEDA("Andromeda", new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}, false, false, 4, 0.95);

    private final String label;

    /** Pares [frente, lado] relativos ao bloco dos pés, na ordem de colocação. */
    private final int[][] offsets;

    private final boolean jump, faceBackward;

    /** Quantos blocos o estilo tenta colocar por ciclo. */
    private final int blocksPerCycle;

    private final double speedFactor;

    BridgeStyle(String label, int[][] offsets, boolean jump, boolean faceBackward,
                int blocksPerCycle, double speedFactor) {
        this.label = label;
        this.offsets = offsets;
        this.jump = jump;
        this.faceBackward = faceBackward;
        this.blocksPerCycle = blocksPerCycle;
        this.speedFactor = speedFactor;
    }

    /** Devolve nulo quando o administrador digitou um estilo que não existe. */
    public static BridgeStyle named(String name) {
        if (name == null) return null;

        return Arrays.stream(values())
                .filter(style -> style.name().equalsIgnoreCase(name) || style.getLabel().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public static BridgeStyle of(String name) {
        BridgeStyle style = named(name);

        return style == null ? DIAGONAL : style;
    }
}
