package com.minecraft.arcade.thebridge.arcade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecraft.arcade.thebridge.TheBridge;
import com.minecraft.arcade.thebridge.arcade.arena.TheBridgeArena;
import com.minecraft.arcade.thebridge.user.TheBridgeUser;
import com.minecraft.core.Core;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.ArcadeHolder;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.arcade.room.map.Map;
import com.minecraft.core.arcade.room.map.location.SignedLocation;
import com.minecraft.core.arcade.room.slot.Slot;
import com.minecraft.core.arcade.route.ArcadeRouteContext;
import com.minecraft.arcade.thebridge.world.WorldController;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.event.IgnoreEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static com.minecraft.core.util.list.bukkit.MapUtil.getSignedLocation;

@IgnoreEvent
public abstract class TheBridgeArcade extends ArcadeHolder {

    private final File mapsDirectory;

    /* Salas que ainda estao clonando o mundo. O prelogin procura a sala por
     * roomId aqui antes de a clone terminar. */
    private final java.util.Map<Integer, TheBridgeArena> pendingArenas = new ConcurrentHashMap<>();

    public TheBridgeArcade(String directory, Integer minRooms, Integer maxRooms, ArcadeCategory category) {
        super(directory, minRooms, maxRooms, category);

        this.mapsDirectory = new File(TheBridge.getInstance().getDataFolder(), "maps");
    }

    public String getMode() {
        return getClass().getSimpleName();
    }

    public boolean isPractice() {
        return getCategory().equals(ArcadeCategory.THE_BRIDGE_PRACTICE);
    }

    @Override
    public boolean load() {
        Core.getLogger().info("[TheBridge/" + getMode() + "] Iniciando modo...");

        if (!loadMaps(mapsDirectory)) return false;

        if (getMaps().isEmpty()) {
            Core.getLogger().warning("[TheBridge/" + getMode() + "] Nenhum mapa encontrado em "
                    + mapsDirectory.getAbsolutePath() + ". Crie com /treino createmap <nome>.");
            return false;
        }

        Core.getLogger().info("[TheBridge/" + getMode() + "] Modo iniciado. Mapas: "
                + getMaps().size() + " (arenas sob demanda).");

        return true;
    }

    @Override
    public boolean loadMaps(File root) {
        if (!root.exists()) {
            root.mkdirs();
            Core.getLogger().warning("[TheBridge/" + getMode() + "] Pasta de mapas criada em " + root.getAbsolutePath()
                    + ". Coloque um mapa e reinicie.");
            return false;
        }

        List<File> folders = new ArrayList<>();

        File[] files = root.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && new File(file, "config.json").exists())
                    folders.add(file);
            }
        }

        if (folders.isEmpty() && new File(root, "config.json").exists())
            folders.add(root);

        folders.sort(Comparator.comparing(File::getName));

        int index = 0;

        for (File folder : folders) {
            try {
                File configFile = new File(folder, "config.json");

                JsonObject json = Core.PARSER.parse(new FileReader(configFile)).getAsJsonObject();

                String name = json.has("name") && !json.get("name").getAsString().trim().isEmpty()
                        ? json.get("name").getAsString()
                        : folder.getName();

                int buildLimit = json.has("build_limit") ? json.get("build_limit").getAsInt() : 0;

                Map map = new Map(++index, name, getCategory(), folder, json, buildLimit);

                if (json.has("locations") && json.get("locations").isJsonArray()) {
                    JsonArray locations = json.getAsJsonArray("locations");

                    for (JsonElement element : locations) {
                        SignedLocation signed = getSignedLocation(element);

                        if (signed != null)
                            map.getLocations().add(signed);
                    }
                }

                if (!map.hasLocation("spawn")) {
                    Core.getLogger().warning("[TheBridge/" + getMode() + "] O mapa " + folder.getName()
                            + " nao tem a location \"spawn\" e sera ignorado.");
                    continue;
                }

                if (!map.hasLocation("finish")) {
                    Core.getLogger().warning("[TheBridge/" + getMode() + "] O mapa " + folder.getName()
                            + " nao tem \"finish\"; a conclusao usara a distancia configurada a partir do spawn.");
                }

                getMaps().add(map);

                Core.getLogger().info("[TheBridge/" + getMode() + "] Mapa " + map.getName()
                        + " carregado (template: " + getTemplate(map) + ").");
            } catch (Exception e) {
                Core.getLogger().log(Level.WARNING, "[TheBridge/" + getMode()
                        + "] Falha ao carregar o mapa " + folder.getName(), e);
            }
        }

        return !getMaps().isEmpty();
    }

    public String getTemplate(Map map) {
        JsonObject json = map.getConfig();

        if (json != null && json.has("template") && !json.get("template").getAsString().trim().isEmpty())
            return json.get("template").getAsString();

        return map.getSource().getName();
    }

    /**
     * Quantos jogadores a sala aceita. O mapa pode definir {@code max_players}
     * no config.json (como o PvP usa o maximo do servidor); sem o campo vale o
     * maximo do Slot.
     */
    public int getMaxPlayers(Map map, Slot slot) {
        JsonObject json = map.getConfig();

        if (json != null && json.has("max_players")) {
            int configured = json.get("max_players").getAsInt();

            if (configured > 0)
                return configured;
        }

        return slot.getMaxPlayers();
    }

    @Override
    public TheBridgeArena setupRoom(Map map, Slot slot) {
        int id = (getId().getAndIncrement() + 1);

        TheBridgeArena arena = new TheBridgeArena(id, this, map, slot);

        /* Capacidade por mapa, igual BedWars/PvP: sem "max_players" no
         * config.json vale o maximo do Slot. */
        arena.setMaxPlayers(getMaxPlayers(map, slot));

        synchronized (getRooms()) {
            getRooms().add(arena);
        }

        pendingArenas.put(id, arena);

        Core.getLogger().info("[TheBridge/" + getMode() + "/" + id + "] Criando sala (template "
                + arena.getTemplateName() + ")...");

        WorldController.cloneWorld(arena, () -> {
            arena.applyWorldSettings();
            arena.markReady();
            pendingArenas.remove(id);

            Core.getLogger().info("[TheBridge/" + getMode() + "/" + id + "] Sala pronta.");
        }, () -> removeRoom(arena));

        return arena;
    }

    @Override
    public TheBridgeArena findBestArena(UUID sender, ArcadeRouteContext route) {
        /* Reconnect/explicit room: entrega exatamente aquela sala. */
        if (route.getRoomId() > 0) {
            TheBridgeArena target = getPendingArena(route.getRoomId());

            if (target == null)
                target = (TheBridgeArena) getRoom(route.getRoomId());

            if (target != null) return target;
        }

        Map map = route.hasMap() ? getMap(route.getMapId()) : getRandomMap();

        if (map == null) {
            Core.getLogger().warning("[TheBridge/" + getMode() + "] Nenhum mapa disponivel para a rota.");
            return null;
        }

        /* As duas modalidades compartilham a primeira sala pronta que ainda
         * tenha vaga - a capacidade vem do mapa (max_players). Antes o treino
         * criava um mundo clonado por jogador. */
        Set<com.minecraft.core.arcade.room.Room> snapshot;

        synchronized (getRooms()) {
            snapshot = new HashSet<>(getRooms());
        }

        TheBridgeArena found = snapshot.stream()
                .map(room -> (TheBridgeArena) room)
                .filter(arena -> arena.isReady() && arena.getWorld() != null)
                .filter(arena -> !route.hasMap() || arena.getMap().getId() == route.getMapId())
                .filter(arena -> !arena.isFull())
                .findFirst()
                .orElse(null);

        if (found != null) return found;

        /* Se existe uma sala sendo clonada, devolve ela para o prelogin
         * aguardar em vez de abrir uma segunda. */
        TheBridgeArena pending = pendingArenas.values().stream()
                .filter(arena -> !route.hasMap() || arena.getMap().getId() == route.getMapId())
                .filter(arena -> !arena.isFull())
                .findFirst()
                .orElse(null);

        if (pending != null) return pending;

        return setupRoom(map, isPractice() ? Slot.PRACTICE : Slot.GLOBAL);
    }

    public TheBridgeArena getPendingArena(int id) {
        return pendingArenas.get(id);
    }

    public void removeRoom(TheBridgeArena arena) {
        pendingArenas.remove(arena.getId());

        synchronized (getRooms()) {
            getRooms().remove(arena);
        }

        WorldController.unloadWorld(arena.getWorldName());
    }

    @Override
    public void unload() {
        synchronized (getRooms()) {
            for (com.minecraft.core.arcade.room.Room room : new HashSet<>(getRooms()))
                removeRoom((TheBridgeArena) room);
        }

        pendingArenas.clear();

        Core.getLogger().info("[TheBridge/" + getMode() + "] Modo desligado.");
    }

    public void handleSidebar(TheBridgeUser user) {
        Sidebar sidebar = user.getSidebar();

        if (sidebar == null) return;

        TheBridgeArena arena = user.getArena();

        List<String> top = arena.getSessionTopLines(3);

        sidebar.clear();
        sidebar.setTitle("§6§lTREINO DE PONTE");
        sidebar.blankRow();

        sidebar.addRow("identifier", "§7" + arena.getIdentifier());
        sidebar.addRow("time", "§fTempo atual: §e" + user.getCurrentTimeFormat());
        sidebar.addRow("best", "§fMelhor tempo: §a" + user.getMember().getBestTimeFormat());

        sidebar.blankRow();

        sidebar.addRow("top", "§fTop da sessão:");
        sidebar.addRow("top_0", top.get(0));
        sidebar.addRow("top_1", top.get(1));
        sidebar.addRow("top_2", top.get(2));

        sidebar.blankRow();

        sidebar.addRow("blocks", "§fBlocos: §e" + user.getBlocks());
        sidebar.addRow("distance", "§fDistância: §6" + user.getDistance() + " blocos");

        sidebar.blankRow();
        sidebar.addWebsiteRow();

        sidebar.display();
    }

    public void updateSidebar(TheBridgeUser user) {
        Sidebar sidebar = user.getSidebar();

        if (sidebar == null) return;

        TheBridgeArena arena = user.getArena();

        List<String> top = arena.getSessionTopLines(3);

        sidebar.updateRow("time", "§fTempo atual: §e" + user.getCurrentTimeFormat());
        sidebar.updateRow("best", "§fMelhor tempo: §a" + user.getMember().getBestTimeFormat());
        sidebar.updateRow("blocks", "§fBlocos: §e" + user.getBlocks());
        sidebar.updateRow("distance", "§fDistância: §6" + user.getDistance() + " blocos");
        sidebar.updateRow("top_0", top.get(0));
        sidebar.updateRow("top_1", top.get(1));
        sidebar.updateRow("top_2", top.get(2));
    }

    public void buildHotbar(Player player, TheBridgeUser user) {
        player.getInventory().clear();

        org.bukkit.inventory.ItemStack bridge = user.getBlock().stack(64);

        player.getInventory().setItem(0, bridge.clone());
        player.getInventory().setItem(1, bridge.clone());

        player.getInventory().setItem(2, Item.of(Material.IRON_SWORD, "§aEspada").unbreakable());

        player.getInventory().setItem(3, Item.of(Material.EMERALD, "§aCosméticos")
                .interact(event -> {
                    event.setCancelled(true);

                    new com.minecraft.arcade.thebridge.menu.OptionsMenu(event.getPlayer(), user).handle();
                }));

        player.getInventory().setItem(5, Item.of(Material.NETHER_STAR, "§aOpções")
                .interact(event -> {
                    event.setCancelled(true);

                    new com.minecraft.arcade.thebridge.menu.OptionsMenu(event.getPlayer(), user).handle();
                }));

        player.getInventory().setItem(6, Item.of(Material.RED_MUSHROOM, "§cVoltar ao lobby")
                .interact(event -> {
                    event.setCancelled(true);

                    /* HUB_THE_BRIDGE nao existe no proxy (nao ha lobby proprio
                     * do modo), entao redirectToHub() caia numa rota invalida
                     * e o jogador ficava preso no treino. */
                    if (user.getAccount() != null)
                        user.getAccount().redirect(com.minecraft.core.server.type.ServerType.HUB);
                }));

        player.updateInventory();
    }

    /* Usado pela lobby para mostrar quantos jogadores estao treinando. */
    public int getPlayingNow() {
        return Core.getArcadeData().getOnlinePlayers(getCategory());
    }
}
