package com.minecraft.event.config;

import com.minecraft.event.Events;
import com.minecraft.event.game.EventType;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class EventSettings {

    private final Events plugin;

    public EventSettings(Events plugin) {
        this.plugin = plugin;

        plugin.saveDefaultConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public int getCalls() {
        return Math.max(1, config().getInt("chamadas.quantidade", 5));
    }

    public int getCallInterval() {
        return Math.max(5, config().getInt("chamadas.intervalo", 30));
    }

    public int getMinimumPlayers() {
        return Math.max(2, config().getInt("chamadas.minimo-jogadores", 2));
    }

    public boolean isSpectators() {
        return config().getBoolean("camarote", true);
    }

    public int getMaxTime(EventType type) {
        return config().getInt("tempo-maximo." + type.name(), 600);
    }

    public List<String> getRewards(EventType type) {
        return config().getStringList("recompensas." + type.name());
    }

    public boolean isScheduleEnabled() {
        return config().getBoolean("agenda.ativa", false);
    }

    public List<String> getSchedule() {
        return config().getStringList("agenda.horarios");
    }
}
