package com.minecraft.arcade.duels.setup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecraft.arcade.duels.Duels;
import com.minecraft.arcade.duels.arcade.Arcade;
import com.minecraft.arcade.duels.arcade.objects.style.SidebarStyle;
import com.minecraft.core.Core;
import com.minecraft.core.arcade.ArcadeHolder;
import com.minecraft.core.arcade.room.team.preset.TeamPresetType;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * Sessão de configuração de um mapa de Duels (/duconfig).
 * Os pontos ficam em memória até o /duconfig salvar, que grava o config.json do mapa.
 */
@Getter
public class ArenaSetup {

    public static final String PREFIX = "§b§lDUELSCONFIG §8» §f";

    private static final Map<UUID, ArenaSetup> SESSIONS = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Duels joga 1 contra 1: sempre vermelho e azul (mesma regra de Arcade#handleArenaData).
     */
    public static final List<TeamPresetType> TEAMS = Arrays.asList(TeamPresetType.RED, TeamPresetType.BLUE);

    @Getter
    public enum TeamPoint {
        SPAWN("spawn", "Spawn do time", "%s_spawn", true, Material.ARMOR_STAND),
        BED("cama", "Cama", "%s_bed", false, Material.BED);

        private final String alias, name, format;
        private final boolean required;
        private final Material icon;

        TeamPoint(String alias, String name, String format, boolean required, Material icon) {
            this.alias = alias;
            this.name = name;
            this.format = format;
            this.required = required;
            this.icon = icon;
        }

        public String key(TeamPresetType team) {
            return String.format(format, team.name().toLowerCase());
        }

        public static TeamPoint of(String name) {
            if (name == null) return null;

            switch (name.toLowerCase()) {
                case "cama":
                case "bed":
                    return BED;
                default:
                    return Arrays.stream(values()).filter(point -> point.getAlias().equalsIgnoreCase(name)
                            || point.getName().equalsIgnoreCase(name) || point.name().equalsIgnoreCase(name)).findFirst().orElse(null);
            }
        }
    }

    private final UUID owner;
    private final String mode, mapName;
    private final World world;
    private final File folder;

    private final JsonObject config;
    private final Map<String, JsonObject> locations = new LinkedHashMap<>();

    /** Só modo com cama (Bed Fight) exige os pontos de cama. */
    private final boolean bedMode;

    @Setter
    private TeamPresetType selectedTeam = TeamPresetType.RED;

    private boolean changed;

    private ArenaSetup(UUID owner, String mode, String mapName, World world, File folder, JsonObject config, boolean bedMode) {
        this.owner = owner;
        this.mode = mode;
        this.mapName = mapName;
        this.world = world;
        this.folder = folder;
        this.config = config;
        this.bedMode = bedMode;

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

        ArenaSetup setup = new ArenaSetup(owner, mode.toLowerCase(), mapName, world, folder, config, isBedMode(mode));

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
        return new File(new File(new File(Duels.getInstance().getDataFolder(), "maps"), mode.toLowerCase()), mapName);
    }

    /**
     * Pastas de mapas do modo: ArcadeHolder#loadMaps procura em maps/&lt;nome da classe em minúsculo&gt;.
     */
    public static List<String> getModes() {
        List<String> modes = new ArrayList<>();

        for (ArcadeHolder holder : Duels.getManager().getArcade().getArcades())
            modes.add(holder.getName().toLowerCase());

        ConfigurationSection section = Duels.getInstance().getConfig().getConfigurationSection("modes");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                Class<?> type = Duels.getManager().getArcade().read(key);

                String folder = type != null ? type.getSimpleName().toLowerCase()
                        : section.getString(key + ".directory", key).toLowerCase();

                if (!modes.contains(folder)) modes.add(folder);
            }
        }

        Collections.sort(modes);
        return modes;
    }

    public static List<String> getMaps(String mode) {
        File[] files = new File(new File(Duels.getInstance().getDataFolder(), "maps"), mode.toLowerCase()).listFiles(File::isDirectory);

        if (files == null) return new ArrayList<>();

        List<String> maps = new ArrayList<>();

        for (File folder : files)
            maps.add(folder.getName());

        Collections.sort(maps);
        return maps;
    }

    /**
     * O modo usa cama? Compara o sidebar style do arcade carregado; sem arcade carregado
     * (mapa quebrado) cai no nome da classe.
     */
    public static boolean isBedMode(String mode) {
        ArcadeHolder holder = Duels.getManager().getArcade().getGame(mode);

        if (holder instanceof Arcade)
            return ((Arcade) holder).getStyle().equals(SidebarStyle.BED);

        return mode.equalsIgnoreCase("bedfight");
    }

    /**
     * Nome do mundo/template que o SlimeWorldManager precisa ter para as arenas deste mapa nascerem.
     */
    public String getTemplate() {
        String jsonName = config.has("name") ? config.get("name").getAsString() : mapName;
        String[] split = jsonName.split(" ");
        String slug = split.length > 1 ? split[0] + "-" + split[1] : jsonName;

        return mode.toLowerCase() + "-" + slug.toLowerCase();
    }

    /* Pontos */

    public List<TeamPresetType> getTeams() {
        return TEAMS;
    }

    public static String display(TeamPresetType team) {
        return team.getColor() + team.getName();
    }

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

            /* Base olhando para leste/oeste: área de proteção ao longo do eixo X */
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

    /**
     * Zona que o time adversário não pode construir nem destruir em volta da base.
     * Sem essa chave o mapa continua como sempre foi: nada protegido.
     */
    public boolean hasArea() {
        return config.has("area_protection") && config.get("area_protection").isJsonObject();
    }

    public void setArea(int frontAndBack, int up, int side) {
        JsonObject area = new JsonObject();

        area.addProperty("frontAndBack", frontAndBack);
        area.addProperty("up", up);
        area.addProperty("side", side);

        config.add("area_protection", area);
        changed = true;
    }

    public String describeArea() {
        if (!hasArea()) return "§7desativada §8(/duconfig protecao <frente> <cima> <lados>)";

        JsonObject area = config.getAsJsonObject("area_protection");

        return "§7frente/trás §f" + area.get("frontAndBack").getAsInt() + " §7cima §f" + area.get("up").getAsInt()
                + " §7lados §f" + area.get("side").getAsInt();
    }

    /**
     * [frente/trás, cima, lados]; padrão 4 / 3 / 4 quando o mapa não tem proteção.
     */
    public int[] getArea() {
        if (!hasArea()) return new int[]{4, 3, 4};

        JsonObject area = config.getAsJsonObject("area_protection");

        return new int[]{getInt(area, "frontAndBack"), getInt(area, "up"), getInt(area, "side")};
    }

    public void removeArea() {
        config.remove("area_protection");
        changed = true;
    }

    private static int getInt(JsonObject json, String key) {
        JsonElement element = json.get(key);

        return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
    }

    /* Validação */

    /**
     * O que ainda falta para o mapa funcionar. Vazio = pronto.
     */
    public List<String> getMissing() {
        List<String> missing = new ArrayList<>();

        if (!has("spawn")) missing.add("§fSpawn da sala de espera §7(/duconfig spawn)");

        for (TeamPresetType team : getTeams()) {
            for (TeamPoint point : TeamPoint.values()) {
                if (!isRequired(point) || has(point.key(team))) continue;

                missing.add(display(team) + " §8- §f" + point.getName() + " §7(/duconfig " + team.getName().toLowerCase() + " " + point.getAlias() + ")");
            }
        }

        if (getBuildLimit() <= 0) missing.add("§fAltura máxima de construção §7(/duconfig limite)");

        return missing;
    }

    public boolean isRequired(TeamPoint point) {
        return point.isRequired() || (point.equals(TeamPoint.BED) && bedMode);
    }

    public int countTeam(TeamPresetType team, boolean onlyRequired) {
        int count = 0;

        for (TeamPoint point : TeamPoint.values()) {
            if ((!onlyRequired || isRequired(point)) && has(point.key(team))) count++;
        }

        return count;
    }

    public int requiredPoints() {
        int count = 0;

        for (TeamPoint point : TeamPoint.values()) {
            if (isRequired(point)) count++;
        }

        return count;
    }

    /* Salvar */

    public File save() throws IOException {
        JsonArray array = new JsonArray();

        for (JsonObject location : locations.values())
            array.add(location);

        config.addProperty("name", config.has("name") ? config.get("name").getAsString() : mapName);

        /* build_limit é obrigatório no carregamento do config.json */
        if (!config.has("build_limit")) config.addProperty("build_limit", 15);

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
