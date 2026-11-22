package com.minecraft.arcade.thebridge.practice;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Catalogo de blocos que podem ser usados para construir a ponte.
 *
 * <p>O 1.8 identifica a variacao pelo par material + data, por isso cada
 * entrada carrega os dois. A lista cobre os blocos "de construcao" do jogo
 * (todas as cores de la, terracota, vidro, tapete, madeiras, pedras, etc.).</p>
 */
public final class BridgeBlocks {

    private static final String[] COLORS = {
            "Branca", "Laranja", "Magenta", "Azul Claro", "Amarela", "Verde-Limão",
            "Rosa", "Cinza", "Cinza Clara", "Ciano", "Roxa", "Azul",
            "Marrom", "Verde", "Vermelha", "Preta"
    };

    private static final List<BridgeBlock> ALL;

    static {
        List<BridgeBlock> list = new ArrayList<>();

        for (int data = 0; data < 16; data++) {
            list.add(new BridgeBlock(Material.WOOL, data, "Lã " + COLORS[data]));
            list.add(new BridgeBlock(Material.STAINED_CLAY, data, "Terracota " + COLORS[data]));
            list.add(new BridgeBlock(Material.STAINED_GLASS, data, "Vidro " + COLORS[data]));
            list.add(new BridgeBlock(Material.CARPET, data, "Tapete " + COLORS[data]));
            list.add(new BridgeBlock(Material.STAINED_GLASS_PANE, data, "Painel de Vidro " + COLORS[data]));
        }

        add(list, Material.STONE, 0, "Pedra");
        add(list, Material.STONE, 1, "Granito");
        add(list, Material.STONE, 2, "Granito Polido");
        add(list, Material.STONE, 3, "Diorito");
        add(list, Material.STONE, 4, "Diorito Polido");
        add(list, Material.STONE, 5, "Andesito");
        add(list, Material.STONE, 6, "Andesito Polido");

        add(list, Material.COBBLESTONE, 0, "Pedra Rústica");
        add(list, Material.MOSSY_COBBLESTONE, 0, "Pedra Rústica Musgosa");
        add(list, Material.SMOOTH_BRICK, 0, "Tijolos de Pedra");
        add(list, Material.SMOOTH_BRICK, 1, "Tijolos de Pedra Musgosos");
        add(list, Material.SMOOTH_BRICK, 2, "Tijolos de Pedra Rachados");
        add(list, Material.SMOOTH_BRICK, 3, "Tijolos de Pedra Cinzelados");

        add(list, Material.GRASS, 0, "Grama");
        add(list, Material.DIRT, 0, "Terra");
        add(list, Material.DIRT, 1, "Terra Grossa");
        add(list, Material.DIRT, 2, "Podzol");
        add(list, Material.MYCEL, 0, "Micélio");
        add(list, Material.SAND, 0, "Areia");
        add(list, Material.GRAVEL, 0, "Cascalho");
        add(list, Material.CLAY, 0, "Argila");
        add(list, Material.HARD_CLAY, 0, "Bloco de Argila");

        add(list, Material.SANDSTONE, 0, "Arenito");
        add(list, Material.SANDSTONE, 1, "Arenito Cinzelado");
        add(list, Material.SANDSTONE, 2, "Arenito Cortado");
        add(list, Material.RED_SANDSTONE, 0, "Arenito Vermelho");
        add(list, Material.RED_SANDSTONE, 1, "Arenito Vermelho Cinzelado");
        add(list, Material.RED_SANDSTONE, 2, "Arenito Vermelho Cortado");

        add(list, Material.BRICK, 0, "Tijolos");
        add(list, Material.NETHER_BRICK, 0, "Tijolos do Nether");
        add(list, Material.NETHERRACK, 0, "Netherrack");
        add(list, Material.SOUL_SAND, 0, "Areia das Almas");
        add(list, Material.ENDER_STONE, 0, "Pedra do End");

        add(list, Material.GLASS, 0, "Vidro");
        add(list, Material.THIN_GLASS, 0, "Painel de Vidro");
        add(list, Material.OBSIDIAN, 0, "Obsidiana");
        add(list, Material.BEDROCK, 0, "Bedrock");

        add(list, Material.SNOW_BLOCK, 0, "Neve");
        add(list, Material.ICE, 0, "Gelo");
        add(list, Material.PACKED_ICE, 0, "Gelo Compacto");
        add(list, Material.GLOWSTONE, 0, "Pedra Luminosa");
        add(list, Material.SEA_LANTERN, 0, "Lanterna do Mar");

        add(list, Material.QUARTZ_BLOCK, 0, "Bloco de Quartzo");
        add(list, Material.QUARTZ_BLOCK, 1, "Quartzo Cinzelado");
        add(list, Material.QUARTZ_BLOCK, 2, "Pilar de Quartzo");

        add(list, Material.PRISMARINE, 0, "Prismarine");
        add(list, Material.PRISMARINE, 1, "Tijolos de Prismarine");
        add(list, Material.PRISMARINE, 2, "Prismarine Sombrio");

        add(list, Material.WOOD, 0, "Madeira de Carvalho");
        add(list, Material.WOOD, 1, "Madeira de Pinheiro");
        add(list, Material.WOOD, 2, "Madeira de Bétula");
        add(list, Material.WOOD, 3, "Madeira de Selva");
        add(list, Material.WOOD, 4, "Madeira de Acácia");
        add(list, Material.WOOD, 5, "Madeira de Carvalho Escuro");

        add(list, Material.LOG, 0, "Tronco de Carvalho");
        add(list, Material.LOG, 1, "Tronco de Pinheiro");
        add(list, Material.LOG, 2, "Tronco de Bétula");
        add(list, Material.LOG, 3, "Tronco de Selva");
        add(list, Material.LOG_2, 0, "Tronco de Acácia");
        add(list, Material.LOG_2, 1, "Tronco de Carvalho Escuro");

        add(list, Material.LEAVES, 0, "Folhas de Carvalho");
        add(list, Material.LEAVES, 1, "Folhas de Pinheiro");
        add(list, Material.LEAVES, 2, "Folhas de Bétula");
        add(list, Material.LEAVES, 3, "Folhas de Selva");
        add(list, Material.LEAVES_2, 0, "Folhas de Acácia");
        add(list, Material.LEAVES_2, 1, "Folhas de Carvalho Escuro");

        add(list, Material.WOOD_STEP, 0, "Laje de Carvalho");
        add(list, Material.WOOD_STEP, 1, "Laje de Pinheiro");
        add(list, Material.WOOD_STEP, 2, "Laje de Bétula");
        add(list, Material.WOOD_STEP, 3, "Laje de Selva");
        add(list, Material.WOOD_STEP, 4, "Laje de Acácia");
        add(list, Material.WOOD_STEP, 5, "Laje de Carvalho Escuro");

        add(list, Material.STEP, 0, "Laje de Pedra");
        add(list, Material.STEP, 1, "Laje de Arenito");
        add(list, Material.STEP, 2, "Laje de Madeira");
        add(list, Material.STEP, 3, "Laje de Pedra Rústica");
        add(list, Material.STEP, 4, "Laje de Tijolos");
        add(list, Material.STEP, 5, "Laje de Tijolos de Pedra");
        add(list, Material.STEP, 6, "Laje do Nether");
        add(list, Material.STEP, 7, "Laje de Quartzo");

        add(list, Material.COBBLE_WALL, 0, "Muro de Pedra");
        add(list, Material.COBBLE_WALL, 1, "Muro de Pedra Musgoso");

        add(list, Material.COBBLESTONE_STAIRS, 0, "Escada de Pedra Rústica");
        add(list, Material.SMOOTH_STAIRS, 0, "Escada de Tijolos de Pedra");
        add(list, Material.BRICK_STAIRS, 0, "Escada de Tijolos");
        add(list, Material.SANDSTONE_STAIRS, 0, "Escada de Arenito");
        add(list, Material.NETHER_BRICK_STAIRS, 0, "Escada do Nether");
        add(list, Material.QUARTZ_STAIRS, 0, "Escada de Quartzo");
        add(list, Material.RED_SANDSTONE_STAIRS, 0, "Escada de Arenito Vermelho");
        add(list, Material.WOOD_STAIRS, 0, "Escada de Carvalho");
        add(list, Material.SPRUCE_WOOD_STAIRS, 0, "Escada de Pinheiro");
        add(list, Material.BIRCH_WOOD_STAIRS, 0, "Escada de Bétula");
        add(list, Material.JUNGLE_WOOD_STAIRS, 0, "Escada de Selva");
        add(list, Material.ACACIA_STAIRS, 0, "Escada de Acácia");
        add(list, Material.DARK_OAK_STAIRS, 0, "Escada de Carvalho Escuro");

        add(list, Material.FENCE, 0, "Cerca de Carvalho");
        add(list, Material.SPRUCE_FENCE, 0, "Cerca de Pinheiro");
        add(list, Material.BIRCH_FENCE, 0, "Cerca de Bétula");
        add(list, Material.JUNGLE_FENCE, 0, "Cerca de Selva");
        add(list, Material.ACACIA_FENCE, 0, "Cerca de Acácia");
        add(list, Material.DARK_OAK_FENCE, 0, "Cerca de Carvalho Escuro");
        add(list, Material.NETHER_FENCE, 0, "Cerca do Nether");

        add(list, Material.TRAP_DOOR, 0, "Alçapão de Madeira");
        add(list, Material.IRON_TRAPDOOR, 0, "Alçapão de Ferro");
        add(list, Material.LADDER, 0, "Escada de Mão");
        add(list, Material.JUKEBOX, 0, "Jukebox");
        add(list, Material.NOTE_BLOCK, 0, "Bloco Musical");

        add(list, Material.COAL_BLOCK, 0, "Bloco de Carvão");
        add(list, Material.IRON_BLOCK, 0, "Bloco de Ferro");
        add(list, Material.GOLD_BLOCK, 0, "Bloco de Ouro");
        add(list, Material.DIAMOND_BLOCK, 0, "Bloco de Diamante");
        add(list, Material.EMERALD_BLOCK, 0, "Bloco de Esmeralda");
        add(list, Material.LAPIS_BLOCK, 0, "Bloco de Lápis");
        add(list, Material.REDSTONE_BLOCK, 0, "Bloco de Redstone");

        add(list, Material.HAY_BLOCK, 0, "Fardo de Feno");
        add(list, Material.MELON_BLOCK, 0, "Melancia");
        add(list, Material.PUMPKIN, 0, "Abóbora");
        add(list, Material.JACK_O_LANTERN, 0, "Abóbora de Halloween");

        add(list, Material.BOOKSHELF, 0, "Estante");
        add(list, Material.WORKBENCH, 0, "Mesa de Trabalho");
        add(list, Material.FURNACE, 0, "Fornalha");
        add(list, Material.CHEST, 0, "Baú");
        add(list, Material.ENDER_CHEST, 0, "Baú do End");
        add(list, Material.SPONGE, 0, "Esponja");
        add(list, Material.SPONGE, 1, "Esponja Molhada");
        add(list, Material.SLIME_BLOCK, 0, "Bloco de Slime");

        /* Minerios e blocos funcionais (tambem podem ser usados na ponte). */
        add(list, Material.COAL_ORE, 0, "Minério de Carvão");
        add(list, Material.IRON_ORE, 0, "Minério de Ferro");
        add(list, Material.GOLD_ORE, 0, "Minério de Ouro");
        add(list, Material.DIAMOND_ORE, 0, "Minério de Diamante");
        add(list, Material.EMERALD_ORE, 0, "Minério de Esmeralda");
        add(list, Material.REDSTONE_ORE, 0, "Minério de Redstone");
        add(list, Material.LAPIS_ORE, 0, "Minério de Lápis");
        add(list, Material.QUARTZ_ORE, 0, "Minério de Quartzo");

        add(list, Material.TNT, 0, "TNT");
        add(list, Material.REDSTONE_LAMP_OFF, 0, "Lâmpada de Redstone");
        add(list, Material.HOPPER, 0, "Funil");
        add(list, Material.DROPPER, 0, "Ejetor");
        add(list, Material.DISPENSER, 0, "Distribuidor");
        add(list, Material.PISTON_BASE, 0, "Pistão");
        add(list, Material.PISTON_STICKY_BASE, 0, "Pistão Pegajoso");

        add(list, Material.ANVIL, 0, "Bigorna");
        add(list, Material.ANVIL, 1, "Bigorna Danificada");
        add(list, Material.ANVIL, 2, "Bigorna Muito Danificada");
        add(list, Material.BEACON, 0, "Sinalizador");
        add(list, Material.IRON_FENCE, 0, "Grades de Ferro");

        add(list, Material.WOOD_DOOR, 0, "Porta de Carvalho");
        add(list, Material.SPRUCE_DOOR, 0, "Porta de Pinheiro");
        add(list, Material.BIRCH_DOOR, 0, "Porta de Bétula");
        add(list, Material.JUNGLE_DOOR, 0, "Porta de Selva");
        add(list, Material.ACACIA_DOOR, 0, "Porta de Acácia");
        add(list, Material.DARK_OAK_DOOR, 0, "Porta de Carvalho Escuro");
        add(list, Material.IRON_DOOR, 0, "Porta de Ferro");

        add(list, Material.FENCE_GATE, 0, "Portão de Carvalho");
        add(list, Material.SPRUCE_FENCE_GATE, 0, "Portão de Pinheiro");
        add(list, Material.BIRCH_FENCE_GATE, 0, "Portão de Bétula");
        add(list, Material.JUNGLE_FENCE_GATE, 0, "Portão de Selva");
        add(list, Material.ACACIA_FENCE_GATE, 0, "Portão de Acácia");
        add(list, Material.DARK_OAK_FENCE_GATE, 0, "Portão de Carvalho Escuro");

        add(list, Material.DOUBLE_STEP, 0, "Laje Dupla de Pedra");
        add(list, Material.DOUBLE_STEP, 1, "Laje Dupla de Arenito");
        add(list, Material.DOUBLE_STEP, 2, "Laje Dupla de Madeira");
        add(list, Material.DOUBLE_STEP, 3, "Laje Dupla de Pedra Rústica");
        add(list, Material.DOUBLE_STEP, 4, "Laje Dupla de Tijolos");
        add(list, Material.DOUBLE_STEP, 5, "Laje Dupla de Tijolos de Pedra");
        add(list, Material.DOUBLE_STEP, 6, "Laje Dupla do Nether");
        add(list, Material.DOUBLE_STEP, 7, "Laje Dupla de Quartzo");

        add(list, Material.WOOD_DOUBLE_STEP, 0, "Laje Dupla de Carvalho");
        add(list, Material.WOOD_DOUBLE_STEP, 1, "Laje Dupla de Pinheiro");
        add(list, Material.WOOD_DOUBLE_STEP, 2, "Laje Dupla de Bétula");
        add(list, Material.WOOD_DOUBLE_STEP, 3, "Laje Dupla de Selva");
        add(list, Material.WOOD_DOUBLE_STEP, 4, "Laje Dupla de Acácia");
        add(list, Material.WOOD_DOUBLE_STEP, 5, "Laje Dupla de Carvalho Escuro");

        ALL = Collections.unmodifiableList(list);
    }

    private BridgeBlocks() {
    }

    private static void add(List<BridgeBlock> list, Material material, int data, String name) {
        if (material == null) return;

        list.add(new BridgeBlock(material, data, name));
    }

    public static List<BridgeBlock> all() {
        return ALL;
    }

    public static BridgeBlock of(String key) {
        if (key == null) return defaultBlock();

        String[] split = key.split(":");

        Material material = Material.getMaterial(split[0]);

        if (material == null || !material.isBlock()) return defaultBlock();

        int data = 0;

        if (split.length > 1) {
            try {
                data = Integer.parseInt(split[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        return new BridgeBlock(material, data, prettyName(material, data));
    }

    public static BridgeBlock defaultBlock() {
        return new BridgeBlock(Material.WOOL, 14, "Lã Vermelha");
    }

    private static String prettyName(Material material, int data) {
        String[] split = material.name().toLowerCase().split("_");

        StringBuilder builder = new StringBuilder();

        for (String part : split) {
            if (builder.length() > 0) builder.append(' ');

            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }

        return builder + (data > 0 ? " (" + data + ")" : "");
    }

    @Getter
    @AllArgsConstructor
    public static class BridgeBlock {

        private final Material material;
        private final int data;
        private final String name;

        public String key() {
            return material.name() + ":" + data;
        }

        /** Formato do id exibido no menu: #0035/14. */
        public String id() {
            return String.format("#%04d/%d", material.getId(), data);
        }

        public ItemStack stack(int amount) {
            return new ItemStack(material, amount, (short) data);
        }

        public String display() {
            return name;
        }
    }
}
