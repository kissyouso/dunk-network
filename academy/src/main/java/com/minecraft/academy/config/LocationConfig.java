package com.minecraft.academy.config;

import com.minecraft.academy.Academy;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
public class LocationConfig {
    private final File file;
    private final FileConfiguration config;
    public LocationConfig(Academy academy) {
        this.file = new File(academy.getDataFolder(), "locations.yml");
        if (!file.exists()) {
            academy.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }
    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public Location getSpawn(String arena) {
        return read("spawns." + arena);
    }
    public void setSpawn(String arena, Location location) {
        write("spawns." + arena, location);
    }
    public Location getPoint(String arena, String key) {
        return read("points." + arena + "." + key);
    }
    public void setPoint(String arena, String key, Location location) {
        write("points." + arena + "." + key, location);
    }
    public void removePoint(String arena, String key) {
        config.set("points." + arena + "." + key, null);
        save();
    }
    public void reload() {
        try {
            config.load(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public Property getSkin(String arena, String key) {
        String path = "points." + arena + "." + key;
        String value = config.getString(path + ".skin_value");
        if (value == null || value.isEmpty())
            return null;
        return new Property("textures", value, config.getString(path + ".skin_signature"));
    }
    public void setSkin(String arena, String key, Property property) {
        String path = "points." + arena + "." + key;
        config.set(path + ".skin_value", property.getValue());
        config.set(path + ".skin_signature", property.getSignature());
        save();
    }
    private Location read(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null || !section.contains("world"))
            return null;
        if (Bukkit.getWorld(section.getString("world")) == null)
            return null;
        return new Location(Bukkit.getWorld(section.getString("world")), section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }
    private void write(String path, Location location) {
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", (double) location.getYaw());
        config.set(path + ".pitch", (double) location.getPitch());
        save();
    }
}
