package com.minecraft.lobby.architect.list.bedwars.leaderboard;

import com.minecraft.core.bukkit.api.hologram.leaderboard.LeaderboardHologram;
import com.minecraft.core.bukkit.api.hologram.touch.Touch;
import com.minecraft.core.bukkit.api.hologram.type.client.HologramClient;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BedModeSelectorHologram extends LeaderboardHologram {
    private final List<String> modes = Arrays.asList("GERAL", "SOLO", "DUPLAS", "TRIOS", "QUARTETOS");
    private final List<String> names = Arrays.asList("Geral", "Solo", "Dupla", "Trio", "Quarteto");
    private final List<LeaderboardHologram> listeners;
    private int index = 0;

    public BedModeSelectorHologram(Player player, Location location, List<LeaderboardHologram> listeners) {
        super("selector", player, location, 8);

        this.listeners = listeners;
    }

    @Override
    public void handle() {
        HologramClient selector = getHologram();

        selector.writeProperty("page", 0);
        selector.writeProperty("index", index);

        buildText(selector);

        applyModeToListeners(modes.get(index));

        selector.setTouch((player, touch) -> {
            index = (index + 1) % modes.size();

            selector.writeProperty("index", index);

            buildText(selector);

            applyModeToListeners(modes.get(index));
        });
    }

    private void buildText(HologramClient selector) {
        List<String> lines = new ArrayList<>();
        lines.add("§e§lSELECIONAR MODO");

        for (int i = 0; i < modes.size(); i++)
            lines.add((i == index ? "§6" : "§7") + names.get(i));

        lines.add("§6Clique para alternar!");

        selector.setSpaciousText(lines);
    }

    private void applyModeToListeners(String mode) {
        for (LeaderboardHologram lb : listeners) {
            HologramClient client = lb.getHologram();
            client.writeProperty("mode", mode);
            lb.handle();
        }
    }

    private String toTitle(String text) {
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }
}