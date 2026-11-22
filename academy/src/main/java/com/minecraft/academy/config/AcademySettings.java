package com.minecraft.academy.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.minecraft.academy.Academy;
import com.minecraft.core.Core;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuração do Academy em plugins/Academy/config.json.
 * Chaves com ponto (ex.: "lava.FACIL.coins") apontam para objetos aninhados no JSON.
 * Um config.yml antigo é convertido automaticamente na primeira vez.
 */
public class AcademySettings {

    private final Academy academy;
    private final File file;

    private JsonObject json;

    public AcademySettings(Academy academy) {
        this.academy = academy;
        this.file = new File(academy.getDataFolder(), "config.json");

        reload();
    }

    public void reload() {
        JsonObject defaults = readDefaults();

        try {
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    json = Core.PARSER.parse(reader).getAsJsonObject();
                }
            } else {
                json = copy(defaults);
                migrateYaml();
            }
        } catch (Exception exception) {
            academy.getLogger().severe("config.json inválido (" + exception.getMessage() + "), usando os valores padrão.");
            json = copy(defaults);
        }

        /* Chaves novas que ainda não existem no arquivo */
        merge(defaults, json);
        save();
    }

    public List<String> getKeys() {
        List<String> keys = new ArrayList<>();
        collect("", json, keys);
        return keys;
    }

    public Object get(String key) {
        JsonElement element = find(key);

        if (element == null || !element.isJsonPrimitive()) return null;

        JsonPrimitive primitive = element.getAsJsonPrimitive();

        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) return primitive.getAsNumber();

        return primitive.getAsString();
    }

    public String set(String key, String value) {
        JsonElement current = find(key);

        if (current == null || !current.isJsonPrimitive())
            return "Chave '" + key + "' não existe. Use /academy config para ver as chaves.";

        JsonPrimitive primitive = current.getAsJsonPrimitive();
        JsonPrimitive parsed;

        try {
            if (primitive.isBoolean()) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
                    return "Use true ou false.";
                parsed = new JsonPrimitive(Boolean.parseBoolean(value));
            } else if (primitive.isNumber()) {
                String number = value.replace(',', '.');
                parsed = primitive.getAsString().contains(".") || number.contains(".")
                        ? new JsonPrimitive(Double.parseDouble(number))
                        : new JsonPrimitive(Integer.parseInt(number));
            } else {
                parsed = new JsonPrimitive(value);
            }
        } catch (NumberFormatException exception) {
            return "Valor inválido para '" + key + "' (esperado: número).";
        }

        if (key.equals("damage.start-block") && Material.matchMaterial(value) == null)
            return "Material '" + value + "' não existe.";

        if (key.equals("arenas.default") && academy.getArenaStorage().getArena(value) == null)
            return "Arena '" + value + "' não existe.";

        int split = key.lastIndexOf('.');
        JsonObject parent = split < 0 ? json : find(key.substring(0, split)).getAsJsonObject();

        parent.add(key.substring(split + 1), parsed);
        save();
        return null;
    }

    /* ---------------- Valores ---------------- */

    public boolean isEnabled(String arena) {
        return getBoolean("arenas." + arena + ".enabled", true);
    }

    public String getDefaultArena() {
        return getString("arenas.default", "Timers");
    }

    public int getTimersTimeoutSeconds() {
        return getInt("timers.timeout-seconds", 60);
    }

    public int getTimersRecordCoins() {
        return getInt("timers.record-coins", 5);
    }

    public int getLavaCoins(String level) {
        return getInt("lava." + level + ".coins", 0);
    }

    public int getLavaMinimumSeconds(String level) {
        return getInt("lava." + level + ".minimum-seconds", 0);
    }

    public Material getDamageStartBlock() {
        Material material = Material.matchMaterial(getString("damage.start-block", "STAINED_CLAY"));
        return material == null ? Material.STAINED_CLAY : material;
    }

    public int getDamageStartBlockData() {
        return getInt("damage.start-block-data", 4);
    }

    public int getDamageDropsIntervalTicks() {
        return Math.max(1, getInt("damage.drops-interval-ticks", 100));
    }

    public double getDamageArmorReduction() {
        return Math.min(1D, Math.max(0D, getDouble("damage.armor-reduction", 0.6D)));
    }

    public int getDamageCoinsPerMinute() {
        return getInt("damage.coins-per-minute", 2);
    }

    public boolean isTextureFlight() {
        return getBoolean("texture.allow-flight", true);
    }

    /* ---------------- JSON ---------------- */

    private JsonElement find(String key) {
        JsonElement element = json;

        for (String part : key.split("\\.")) {
            if (element == null || !element.isJsonObject()) return null;
            element = element.getAsJsonObject().get(part);
        }

        return element;
    }

    private int getInt(String key, int def) {
        JsonElement element = find(key);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsInt() : def;
        } catch (Exception exception) {
            return def;
        }
    }

    private double getDouble(String key, double def) {
        JsonElement element = find(key);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsDouble() : def;
        } catch (Exception exception) {
            return def;
        }
    }

    private boolean getBoolean(String key, boolean def) {
        JsonElement element = find(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() ? element.getAsBoolean() : def;
    }

    private String getString(String key, String def) {
        JsonElement element = find(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : def;
    }

    private static void collect(String prefix, JsonObject object, List<String> keys) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();

            if (entry.getValue().isJsonObject())
                collect(key, entry.getValue().getAsJsonObject(), keys);
            else
                keys.add(key);
        }
    }

    /* Cópia por texto: o Gson do Spigot 1.8 não tem deepCopy() público */
    private static <T extends JsonElement> T copy(T element) {
        @SuppressWarnings("unchecked")
        T copy = (T) Core.PARSER.parse(element.toString());
        return copy;
    }

    private static void merge(JsonObject from, JsonObject into) {
        for (Map.Entry<String, JsonElement> entry : from.entrySet()) {
            JsonElement existing = into.get(entry.getKey());

            if (existing == null)
                into.add(entry.getKey(), copy(entry.getValue()));
            else if (existing.isJsonObject() && entry.getValue().isJsonObject())
                merge(entry.getValue().getAsJsonObject(), existing.getAsJsonObject());
        }
    }

    private JsonObject readDefaults() {
        try (InputStream stream = academy.getResource("config.json")) {
            if (stream != null)
                return Core.PARSER.parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception exception) {
            academy.getLogger().warning("Não foi possível ler o config.json padrão: " + exception.getMessage());
        }

        return new JsonObject();
    }

    /**
     * Traz os valores de um config.yml antigo (se existir) para o JSON e renomeia o arquivo.
     */
    private void migrateYaml() {
        File yaml = new File(academy.getDataFolder(), "config.yml");

        if (!yaml.exists()) return;

        YamlConfiguration old = YamlConfiguration.loadConfiguration(yaml);
        int migrated = 0;

        for (String key : getKeys()) {
            if (!old.contains(key) || old.isConfigurationSection(key)) continue;

            Object value = old.get(key);
            int split = key.lastIndexOf('.');
            JsonObject parent = split < 0 ? json : find(key.substring(0, split)).getAsJsonObject();
            String name = key.substring(split + 1);

            if (value instanceof Boolean) parent.addProperty(name, (Boolean) value);
            else if (value instanceof Number) parent.addProperty(name, (Number) value);
            else if (value != null) parent.addProperty(name, value.toString());
            else continue;

            migrated++;
        }

        yaml.renameTo(new File(academy.getDataFolder(), "config.yml.antigo"));
        academy.getLogger().info("config.yml convertido para config.json (" + migrated + " valores).");
    }

    private void save() {
        try {
            academy.getDataFolder().mkdirs();

            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
                writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(json));
            }
        } catch (Exception exception) {
            academy.getLogger().warning("Não foi possível salvar o config.json: " + exception.getMessage());
        }
    }
}
