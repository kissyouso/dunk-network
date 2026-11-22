package com.minecraft.core.bukkit.menu.server.arcade.mode.duels;

import com.minecraft.core.Core;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.server.arcade.ArcadeQueue;
import com.minecraft.core.util.Util;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class DuelsMenu extends Menu {

    public DuelsMenu(Player player) {
        super(player, "Duels", 4);
    }

    @Override
    public void handle() {
        clear();

        for (Mode mode : Mode.values()) {
            ArcadeCategory arcade = mode.getArcade();

            int playing = arcade != null ? arcade.getPlayingNow() : 0;
            int queue = arcade != null ? Core.getArcadeData().getOnlinePlayers(arcade) - playing : 0;

            addItem(mode.getSlot(), Item.of(mode.getIcon(), mode.getData(), "§e" + mode.getName(),
                            "§fJogando: §a" + Util.formatNumber(playing),
                            "§fEm fila: §e" + Util.formatNumber(Math.max(0, queue)),
                            "",
                            "§eMaior Sequência Diária",
                            "§f1. §7---",
                            "§f2. §7---",
                            "§f3. §7---",
                            "",
                            "§eClique para abrir.")
                    .click(event -> {
                        if (arcade == null) {
                            sound(MenuSound.ERROR);
                            getPlayer().sendMessage("§cEste modo está indisponível no momento.");
                            return;
                        }

                        sound(MenuSound.DONE);
                        close();
                        ArcadeQueue.join(getPlayer(), arcade);
                    }));
        }

        display();
    }

    @Getter
    @AllArgsConstructor
    public enum Mode {

        BED_FIGHT("Bed Fight", "Sala de Duels Bed Fight", Material.BED, 0, ArcadeCategory.DUELS_BED_FIGHT, 10, -1),
        SUMO("Sumo", "Sala de Duels Sumo", Material.SLIME_BALL, 0, ArcadeCategory.DUELS_SUMO, 11, -1),
        GULAG("Gulag", "Sala de Duels Gulag", Material.IRON_FENCE, 0, ArcadeCategory.DUELS_CHAMBER, 12, -1),
        UHC("UHC", "Sala de Duels UHC", Material.ANVIL, 0, ArcadeCategory.DUELS_UHC, 13, 11),
        SOUP("Soup", "Sala de Duels Soup", Material.MUSHROOM_SOUP, 0, ArcadeCategory.DUELS_SOUP, 14, 12),
        GLADIATOR("Gladiator", "Sala de Duels Gladiator", Material.IRON_FENCE, 0, ArcadeCategory.DUELS_GLADIATOR, 15, 13),
        PEARL_FIGHT("Pearl Fight", "Sala de Duels Pearl Fight", Material.ENDER_PEARL, 0, ArcadeCategory.DUELS_PEARL_FIGHT, 16, 15),
        SIMULATOR("Simulator", "Sala de Duels Simulator", Material.WEB, 0, ArcadeCategory.DUELS_SIMULATOR, 19, 14),
        FIREBALL("Fireball", "Sala de Duels Fireball", Material.FIREBALL, 0, ArcadeCategory.DUELS_FIREBALL, 20, 16),
        BOXING("Boxing", "Sala de Duels Boxing", Material.DIAMOND_SWORD, 0, ArcadeCategory.DUELS_BOXING, 21, 20),
        THE_BRIDGE("The Bridge", "Sala de Duels The Bridge", Material.STAINED_CLAY, 0, ArcadeCategory.DUELS_THE_BRIDGE, 22, 21),
        SKYWARS("SkyWars", "Sala de Duels SkyWars", Material.BOW, 0, ArcadeCategory.SKYWARS_SOLO, 23, -1);

        private final String name, room;
        private final Material icon;
        private final int data;
        private final ArcadeCategory arcade;

        /* Posição no menu "Duels" e no "Selecionar inventario" (-1 = não editável) */
        private final int slot, inventorySlot;
    }
}
