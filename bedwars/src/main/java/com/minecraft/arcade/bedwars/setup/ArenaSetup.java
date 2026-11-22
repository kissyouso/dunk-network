package com.minecraft.arcade.bedwars.setup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecraft.arcade.bedwars.BedWars;
import com.minecraft.core.Core;
import com.minecraft.core.arcade.room.team.preset.TeamPresetType;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Sessão de configuração de um mapa de BedWars (/config).
 * Os pontos ficam em memória até o /config salvar, que grava o config.json do mapa.
 */
@Getter
public class ArenaSetup {

    public static final String PREFIX = "§2§lCONFIG §8» §f";

    private static final Map<UUID, ArenaSetup> SESSIONS = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final List<String> LEADERBOARDS = Arrays.asList("level", "wins", "kill", "winstreak", "rank");

    @Getter
    public enum TeamPoint {
        SPAWN("spawn", "Spawn", "%s_spawn", true),
        BED("cama", "Cama", "%s_bed", true),
        GENERATOR("gerador", "Gerador", "generator_%s", true),
        SHOP("loja", "Loja", "npc_shop_%s", true),
        UPGRADE("melhorias", "Melhorias", "npc_upgrade_%s", true),
        CHEST("bau", "Baú", "chest_%s", false),
        ENDER_CHEST("enderbau", "Baú pessoal", "enderchest_%s", false);

        private final String alias, name, format;
        private final boolean required;

        TeamPoint(String alias, String name, String format, boolean required) {
            this.alias = alias;
            this.name = name;
            this.format = format;
            this.required = required;
        }

        public String key(TeamPresetType team) {
            return String.format(format, team.name().toLowerCase());
        }

        public static TeamPoint of(String name) {
            if (name == null) return null;

            switch (name.toLowerCase()) {
                case "upgrade":
                case "upgrades":
                case "melhoria":
                    return UPGRADE;
                case "shop":
                case "npc":
                    return SHOP;
                case "bed":
                    return BED;
                case "generator":
                case "forja":
                    return GENERATOR;
                case "chest":
                case "baú":
                    return CHEST;
                case "enderchest":
                case "baupessoal":
                    return ENDER_CHEST;
                default:
                    return Arrays.stream(values()).filter(point -> point.getAlias().equalsIgnoreCase(name) || point.name().equalsIgnoreCase(name)).findFirst().orElse(null);
            }
        }
    }

    private final UUID owner;
    private final String mode, mapName;
    private final World world;
    private final File folder;

    private final JsonObject config;
    private final Map<String, JsonObject> locations = new LinkedHashMap<>();

    @Setter
    private TeamPresetType selectedTeam = TeamPresetType.RED;

    private boolean changed;

    private ArenaSetup(UUID owner, String mode, String mapName, World world, File folder, JsonObject config) {
        this.owner = owner;
        this.mode = mode;
        this.mapName = mapName;
        this.world = world;
        this.folder = folder;
        this.config = config;

        if (config.has("locations") && config.get("locations").isJsonArray()) {
            for (JsonElement element : config.getAsJsonArray("locations")) {
                if (!element.isJsonObject() || !element.getAsJsonObject().has("name")) continue;

                JsonObject location = element.getAsJsonObject();
                String name = migrate(location.get("name").getAsString().toLowerCase());

                location.addProperty("name", name);
                locations.put(name, location);
            }
        }

        if (!config.has("name")) config.addProperty("name", mapName);
        if (!config.has("normal")) config.addProperty("normal", true);

        if (!config.has("area_protection")) {
            JsonObject protection = new JsonObject();
            protection.addProperty("frontAndBack", 9);
            protection.addProperty("up", 7);
            protection.addProperty("side", 6);
            config.add("area_protection", protection);
        }

        if (!config.has("island_area")) {
            JsonObject island = new JsonObject();
            island.addProperty("frontAndBack", 17);
            island.addProperty("up", 0);
            island.addProperty("sides", 10);
            config.add("island_area", island);
        }
    }

    /* Sessões */

    public static ArenaSetup of(UUID owner) {
        return SESSIONS.get(owner);
    }

    public static ArenaSetup start(UUID owner, String mode, String mapName, World world) {
        File folder = getMapFolder(mode, mapName), file = new File(folder, "config.json");
        JsonObject config = new JsonObject();

        if (file.exists()) {
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                config = Core.PARSER.parse(reader).getAsJsonObject();
            } catch (Exception exception) {
                Core.getLogger().warning("[Arena] Não foi possível ler " + file.getAbsolutePath() + ": " + exception.getMessage());
            }
        }

        ArenaSetup setup = new ArenaSetup(owner, mode.toLowerCase(), mapName, world, folder, config);

        SESSIONS.put(owner, setup);
        return setup;
    }

    public static ArenaSetup stop(UUID owner) {
        return SESSIONS.remove(owner);
    }

    public boolean exists() {
        return new File(folder, "config.json").exists();
    }

    public static File getMapFolder(String mode, String mapName) {
        return new File(new File(new File(BedWars.getInstance().getDataFolder(), "maps"), mode.toLowerCase()), mapName);
    }

    /**
     * Pastas dos modos configuradas no config.yml (solo, duo, trio...).
     */
    public static List<String> getModes() {
        List<String> modes = new ArrayList<>();
        ConfigurationSection section = BedWars.getInstance().getConfig().getConfigurationSection("modes");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                modes.add(section.getString(key + ".directory", key).toLowerCase());
            }
        }

        if (modes.isEmpty())
            modes.addAll(Arrays.asList("solo", "duo", "trio", "quartet", "soloversus", "duoversus", "trioversus", "quartetversus"));

        return modes;
    }

    /**
     * Mapas já configurados de um modo (nomes das pastas).
     */
    public static List<String> getMaps(String mode) {
        File[] files = new File(new File(BedWars.getInstance().getDataFolder(), "maps"), mode.toLowerCase()).listFiles(File::isDirectory);

        if (files == null) return new ArrayList<>();

        List<String> maps = new ArrayList<>();

        for (File folder : files)
            maps.add(folder.getName());

        return maps;
    }

    /* Times */

    /**
     * Times usados pelo modo (mesma regra do Arcade#handleArenaData).
     */
    public List<TeamPresetType> getTeams() {
        if (mode.contains("versus"))
            return Arrays.asList(TeamPresetType.RED, TeamPresetType.BLUE);

        if (mode.startsWith("trio") || mode.startsWith("quartet"))
            return Arrays.asList(TeamPresetType.RED, TeamPresetType.BLUE, TeamPresetType.GREEN, TeamPresetType.YEL);

        return Arrays.asList(TeamPresetType.values());
    }

    public static String display(TeamPresetType team) {
        return team.getColor() + team.getName();
    }

    /* Pontos */

    public boolean has(String key) {
        return locations.containsKey(key.toLowerCase());
    }

    public JsonObject get(String key) {
        return locations.get(key.toLowerCase());
    }

    public Location getLocation(String key) {
        JsonObject json = get(key);

        if (json == null) return null;

        return new Location(world, json.get("x").getAsDouble(), json.get("y").getAsDouble(), json.get("z").getAsDouble(),
                json.has("yaw") ? json.get("yaw").getAsFloat() : 0, json.has("pitch") ? json.get("pitch").getAsFloat() : 0);
    }

    public void set(String key, Location location, boolean facing) {
        JsonObject json = new JsonObject();

        json.addProperty("name", key.toLowerCase());
        json.addProperty("x", location.getX());
        json.addProperty("y", location.getY());
        json.addProperty("z", location.getZ());

        if (facing) {
            json.addProperty("yaw", location.getYaw());
            json.addProperty("pitch", 0);

            /* Ilha olhando para leste/oeste: área de proteção ao longo do eixo X */
            if (key.endsWith("_spawn") && Math.abs(Math.abs(location.getYaw()) - 90) < 1)
                json.addProperty("axisX", true);
        }

        locations.put(key.toLowerCase(), json);
        changed = true;
    }

    public boolean remove(String key) {
        boolean removed = locations.remove(key.toLowerCase()) != null;

        if (removed) changed = true;

        return removed;
    }

    /**
     * Próximo número livre de gerador (ore_generator_diamond_1, _2...).
     */
    public int nextIndex(String prefix) {
        int index = 1;

        while (has(prefix + index))
            index++;

        return index;
    }

    public List<String> getKeys(String prefix) {
        List<String> keys = new ArrayList<>();

        for (String key : locations.keySet()) {
            if (key.startsWith(prefix)) keys.add(key);
        }

        return keys;
    }

    /* Configurações */

    public int getBuildLimit() {
        return config.has("build_limit") ? config.get("build_limit").getAsInt() : -1;
    }

    public void setBuildLimit(int buildLimit) {
        config.addProperty("build_limit", buildLimit);
        changed = true;
    }

    public void setArea(String section, int frontAndBack, int up, int sides) {
        JsonObject area = new JsonObject();

        area.addProperty("frontAndBack", frontAndBack);
        area.addProperty("up", up);
        area.addProperty(section.equals("area_protection") ? "side" : "sides", sides);

        config.add(section, area);
        changed = true;
    }

    public String describeArea(String section) {
        JsonObject area = config.getAsJsonObject(section);

        return "§7frente/trás §f" + area.get("frontAndBack").getAsInt() + " §7cima §f" + area.get("up").getAsInt()
                + " §7lados §f" + (area.has("side") ? area.get("side") : area.get("sides")).getAsInt();
    }

    /* Validação */

    /**
     * O que ainda falta para o mapa funcionar. Vazio = pronto.
     */
    public List<String> getMissing() {
        List<String> missing = new ArrayList<>();

        if (!has("spawn")) missing.add("§fSpawn da sala de espera §7(/config spawn)");
        if (!has("map_limit_pos1") || !has("map_limit_pos2")) missing.add("§fBordas do mapa §7(/config borda 1 e 2)");
        if (getBuildLimit() <= 0) missing.add("§fAltura máxima de construção §7(/config limite)");

        for (TeamPresetType team : getTeams()) {
            for (TeamPoint point : TeamPoint.values()) {
                if (point.isRequired() && !has(point.key(team)))
                    missing.add(display(team) + " §8- §f" + point.getName() + " §7(/config " + team.getName().toLowerCase() + " " + point.getAlias() + ")");
            }
        }

        if (getKeys("ore_generator_diamond_").isEmpty()) missing.add("§bGerador de diamante §7(/config diamante)");
        if (getKeys("ore_generator_emerald_").isEmpty()) missing.add("§aGerador de esmeralda §7(/config esmeralda)");

        return missing;
    }

    public int countTeam(TeamPresetType team, boolean onlyRequired) {
        int count = 0;

        for (TeamPoint point : TeamPoint.values()) {
            if ((!onlyRequired || point.isRequired()) && has(point.key(team))) count++;
        }

        return count;
    }

    public static int requiredPoints() {
        return (int) Arrays.stream(TeamPoint.values()).filter(TeamPoint::isRequired).count();
    }

    /* Salvar */

    public File save() throws IOException {
        JsonArray array = new JsonArray();

        for (JsonObject location : locations.values())
            array.add(location);

        config.addProperty("name", config.has("name") ? config.get("name").getAsString() : mapName);
        config.add("locations", array);

        if (!folder.exists() && !folder.mkdirs())
            throw new IOException("Não foi possível criar a pasta " + folder.getAbsolutePath());

        File json = new File(folder, "config.json");

        try (FileWriter writer = new FileWriter(json, StandardCharsets.UTF_8)) {
            writer.write(GSON.toJson(config));
        }

        changed = false;
        return json;
    }

    /* Utilidades */

    /**
     * Converte os códigos antigos (oran/purp) para os times novos (green/pink).
     */
    private static String migrate(String key) {
        for (TeamPresetType type : TeamPresetType.values()) {
            String legacy = type.getLegacyCode();

            if (legacy != null)
                key = key.replaceAll("(^|_)" + legacy + "(_|$)", "$1" + type.name().toLowerCase() + "$2");
        }

        return key;
    }

    /**
     * Centro do bloco onde o jogador está, olhando para a direção mais próxima (de 45 em 45 graus).
     */
    public static Location standing(Location location) {
        Location center = new Location(location.getWorld(), location.getBlockX() + 0.5, location.getBlockY(), location.getBlockZ() + 0.5);

        center.setYaw(snapYaw(location.getYaw()));
        return center;
    }

    public static Location blockCenter(Block block, double y) {
        return new Location(block.getWorld(), block.getX() + 0.5, block.getY() + y, block.getZ() + 0.5);
    }

    public static float snapYaw(float yaw) {
        float snapped = Math.round(yaw / 45f) * 45f;

        snapped %= 360;

        if (snapped > 180) snapped -= 360;
        if (snapped <= -180) snapped += 360;

        return snapped;
    }

    public static String format(Location location) {
        return "§f" + location.getBlockX() + "§7, §f" + location.getBlockY() + "§7, §f" + location.getBlockZ();
    }
}
