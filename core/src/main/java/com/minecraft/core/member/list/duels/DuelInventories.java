package com.minecraft.core.member.list.duels;

import com.google.gson.JsonObject;
import com.minecraft.core.Core;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.server.type.ServerType;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.Writer;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kit padrão de cada modo de Duels, em plugins/duels/inventarios/&lt;CATEGORIA&gt;.json.
 * Vale para todo mundo que não mexeu no próprio inventário no hub.
 */
public class DuelInventories {

    private static final Map<ArcadeCategory, Cached> CACHE = new HashMap<>();

    private static class Cached {
        private final String base64;
        private final long modified;

        private Cached(String base64, long modified) {
            this.base64 = base64;
            this.modified = modified;
        }
    }

    public static File getDirectory() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("duels");

        return new File(plugin != null ? plugin.getDataFolder() : new File("plugins/duels"), "inventarios");
    }

    public static File getFile(ArcadeCategory category) {
        return new File(getDirectory(), category.name() + ".json");
    }

    /**
     * Kit padrão do modo, ou null quando o admin ainda não montou nenhum.
     */
    public static synchronized String get(ArcadeCategory category) {
        File file = getFile(category);

        if (!file.exists()) {
            CACHE.remove(category);
            return null;
        }

        Cached cached = CACHE.get(category);

        if (cached != null && cached.modified == file.lastModified())
            return cached.base64;

        String base64 = read(file);

        if (base64 == null) {
            CACHE.remove(category);
            return null;
        }

        CACHE.put(category, new Cached(base64, file.lastModified()));
        return base64;
    }

    public static synchronized boolean save(ArcadeCategory category, String base64) {
        File directory = getDirectory();

        if (!directory.exists() && !directory.mkdirs()) {
            Core.getLogger().warning("[Duels] Não foi possível criar " + directory.getAbsolutePath());
            return false;
        }

        JsonObject json = new JsonObject();

        json.addProperty("arcade", category.name());
        json.addProperty("base64", base64);
        json.addProperty("lastUpdate", System.currentTimeMillis());

        File file = getFile(category);

        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write(json.toString());

            CACHE.put(category, new Cached(base64, file.lastModified()));
            return true;
        } catch (Exception exception) {
            Core.getLogger().warning("[Duels] Erro ao salvar o kit de " + category.name() + ": " + exception.getMessage());
            return false;
        }
    }

    public static synchronized boolean remove(ArcadeCategory category) {
        CACHE.remove(category);

        File file = getFile(category);

        return file.exists() && file.delete();
    }

    public static List<ArcadeCategory> getModes() {
        return ArcadeCategory.of(ServerType.DUELS);
    }

    public static synchronized long getLastUpdate(ArcadeCategory category) {
        File file = getFile(category);

        return file.exists() ? file.lastModified() : 0L;
    }

    public static synchronized void reload() {
        CACHE.clear();
    }

    private static String read(File file) {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject json = Core.PARSER.parse(reader).getAsJsonObject();

            return json.has("base64") ? json.get("base64").getAsString() : null;
        } catch (Exception exception) {
            Core.getLogger().warning("[Duels] Kit inválido em " + file.getName() + ": " + exception.getMessage());
            return null;
        }
    }
}
