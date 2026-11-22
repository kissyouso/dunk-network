package com.minecraft.core.member.list.bedwars.objects.metadata.objects.cosmetic;

import com.minecraft.core.account.Account;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cosméticos simples do BedWars (categorias além dos colecionáveis de cama, kill final e vendedores).
 */
@Getter
@AllArgsConstructor
public enum BedCosmetic {

    /* Efeito de vitória */
    VICTORY_FIREWORKS(Category.VICTORY, "Fogos de artifício", "FIREWORK", 0, "§f", true),

    /* Tipos de madeira (dados da madeira no 1.8) */
    WOOD_OAK(Category.WOOD, "Carvalho", "WOOD", 0, "§f", true),
    WOOD_SPRUCE(Category.WOOD, "Pinheiro", "WOOD", 1, "§f", true),
    WOOD_BIRCH(Category.WOOD, "Bétula", "WOOD", 2, "§f", true),
    WOOD_JUNGLE(Category.WOOD, "Selva", "WOOD", 3, "§f", true),
    WOOD_ACACIA(Category.WOOD, "Acácia", "WOOD", 4, "§f", true),
    WOOD_DARK_OAK(Category.WOOD, "Carvalho escuro", "WOOD", 5, "§f", true),

    /* Mensagem de kill */
    KILL_CLASSIC(Category.KILL_MESSAGE, "Clássica", "PAPER", 0, "foi eliminado por", true),
    KILL_PIRATE(Category.KILL_MESSAGE, "Pirata", "PAPER", 0, "foi jogado aos tubarões por", true),
    KILL_MEDIEVAL(Category.KILL_MESSAGE, "Medieval", "PAPER", 0, "foi derrotado em duelo por", true),
    KILL_WESTERN(Category.KILL_MESSAGE, "Faroeste", "PAPER", 0, "foi abatido no saloon por", true),
    KILL_SPACE(Category.KILL_MESSAGE, "Espacial", "PAPER", 0, "foi lançado para o espaço por", true),
    KILL_NINJA(Category.KILL_MESSAGE, "Ninja", "PAPER", 0, "foi silenciado por", true),
    KILL_COOKING(Category.KILL_MESSAGE, "Culinária", "PAPER", 0, "foi assado por", true),
    KILL_FOOTBALL(Category.KILL_MESSAGE, "Futebol", "PAPER", 0, "levou um gol de placa de", true),
    KILL_MAGIC(Category.KILL_MESSAGE, "Mágica", "PAPER", 0, "desapareceu num truque de", true),
    KILL_ROBOT(Category.KILL_MESSAGE, "Robótica", "PAPER", 0, "foi desligado por", true),

    /* Cor preferida (cor da lã / data) */
    COLOR_RED(Category.COLOR, "Vermelho", "WOOL", 14, "§c", true),
    COLOR_BLUE(Category.COLOR, "Azul", "WOOL", 11, "§9", true),
    COLOR_GREEN(Category.COLOR, "Verde", "WOOL", 13, "§a", true),
    COLOR_YELLOW(Category.COLOR, "Amarelo", "WOOL", 4, "§e", true),
    COLOR_CYAN(Category.COLOR, "Ciano", "WOOL", 9, "§b", true),
    COLOR_PINK(Category.COLOR, "Rosa", "WOOL", 6, "§d", true),
    COLOR_WHITE(Category.COLOR, "Branco", "WOOL", 0, "§f", true),
    COLOR_GRAY(Category.COLOR, "Cinza", "WOOL", 7, "§7", true),

    /* Rastros de projétil (nome do efeito de partícula do Bukkit 1.8) */
    TRAIL_FLAME(Category.TRAIL, "Chamas", "BLAZE_POWDER", 0, "MOBSPAWNER_FLAMES", false),
    TRAIL_HEART(Category.TRAIL, "Corações", "RED_ROSE", 0, "HEART", false),
    TRAIL_NOTE(Category.TRAIL, "Notas musicais", "NOTE_BLOCK", 0, "NOTE", false),
    TRAIL_SMOKE(Category.TRAIL, "Fumaça", "COAL", 0, "SMOKE", false),
    TRAIL_MAGIC(Category.TRAIL, "Encantamento", "ENCHANTMENT_TABLE", 0, "FLYING_GLYPH", false),
    TRAIL_WATER(Category.TRAIL, "Água", "WATER_BUCKET", 0, "SPLASH", false),
    TRAIL_HAPPY(Category.TRAIL, "Felicidade", "EMERALD", 0, "HAPPY_VILLAGER", false),
    TRAIL_REDSTONE(Category.TRAIL, "Redstone", "REDSTONE", 0, "COLOURED_DUST", false),
    TRAIL_PORTAL(Category.TRAIL, "Portal", "OBSIDIAN", 0, "PORTAL", false);

    private final Category category;
    private final String name, material;
    private final int data;

    /* Valor extra: cor, frase da mensagem de kill ou nome da partícula */
    private final String value;

    private final boolean vip;

    public String getKey() {
        return name().toLowerCase();
    }

    public boolean has(Account account) {
        return !vip || account.isVIP();
    }

    public static List<BedCosmetic> of(Category category) {
        return Arrays.stream(values())
                .filter(cosmetic -> cosmetic.getCategory() == category)
                .collect(Collectors.toList());
    }

    public static BedCosmetic of(String key) {
        if (key == null) return null;

        return Arrays.stream(values())
                .filter(cosmetic -> cosmetic.getKey().equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
    }

    @Getter
    @AllArgsConstructor
    public enum Category {

        VICTORY("Efeito de vitória", "GOLD_INGOT", 0),
        WOOD("Tipos de madeira", "WOOD", 0),
        KILL_MESSAGE("Mensagem de kill", "PAPER", 0),
        COLOR("Cor preferida", "WOOL", 0),
        TRAIL("Rastros de projétil", "ARROW", 0);

        private final String name, material;
        private final int data;
    }
}
