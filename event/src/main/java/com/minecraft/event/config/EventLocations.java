package com.minecraft.event.config;

import com.minecraft.event.Events;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * locations.yml: locais gerais (spawn, camarote) e de cada evento (entrada, espera, pos1, pos2, chao1, chao2...).
 */
public class EventLocations {

    private final File file;
    private YamlConfiguration config;

    public EventLocations(Events plugin) {
        this.file = new File(plugin.getDataFolder(), "locations.yml");

        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();

            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    /* Grupo "geral" para spawn/camarote ou o nome do evento (ex: SUMO) */
    public Location get(String group, String point) {
        ConfigurationSection section = config.getConfigurationSection(group.toLowerCase() + "." + point.toLowerCase());

        if (section == null || !section.contains("world") || Bukkit.getWorld(section.getString("world")) == null)
            return null;

        return new Location(Bukkit.getWorld(section.getString("world")), section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    public void set(String group, String point, Location location) {
        String path = group.toLowerCase() + "." + point.toLowerCase();

        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", (double) location.getYaw());
        config.set(path + ".pitch", (double) location.getPitch());

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Location getSpawn() {
        Location spawn = get("geral", "spawn");

        return spawn != null ? spawn : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public Location getSpectator() {
        Location camarote = get("geral", "camarote");

        return camarote != null ? camarote : getSpawn();
    }
}
