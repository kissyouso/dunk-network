package com.minecraft.lobby.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecraft.core.Core;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.arcade.room.map.location.SignedLocation;
import com.minecraft.core.arcade.room.map.location.SyntheticLocation;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.hologram.Hologram;
import com.minecraft.core.bukkit.api.hologram.type.client.HologramClient;
import com.minecraft.core.bukkit.api.npc.Npc;
import com.minecraft.core.bukkit.command.structure.BukkitCommandContext;
import com.minecraft.core.bukkit.manager.list.HologramManager;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.annotation.Command;
import com.minecraft.core.command.annotation.Completer;
import com.minecraft.core.server.type.ServerType;
import com.minecraft.lobby.Lobby;
import com.minecraft.lobby.architect.Architect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pontos do lobby (spawn, NPCs e rankings), salvos no config.json.
 * /setnpc (= /config) set <ponto> [centro] | tp <ponto> | remover <ponto> | lista | mostrar
 * NPCs e hologramas que já estão no mundo são movidos/removidos na hora, sem reiniciar.
 */
public class ConfigCommand implements CommandInheritor {

    private static final String PREFIX = "§6§lCONFIG §8» §7";

    private static final List<String> SUB_COMMANDS = Arrays.asList("set", "tp", "remover", "lista", "mostrar");

    private static final Map<ServerType, Map<String, String>> POINTS = new LinkedHashMap<>();

    static {
        point(ServerType.HUB, "spawn", "Spawn");
        point(ServerType.HUB, "npc_stats", "NPC de estatísticas");
        point(ServerType.HUB, "npc_bw", "NPC do Bed Wars");
        point(ServerType.HUB, "npc_duels", "NPC do Duels");
        point(ServerType.HUB, "npc_academy", "NPC do Academy");
        point(ServerType.HUB, "npc_skywars", "NPC do Sky Wars");
        point(ServerType.HUB, "npc_ew", "NPC do Egg Wars");
        point(ServerType.HUB, "npc_tb", "NPC do The Bridge");

        point(ServerType.HUB_BEDWARS, "spawn", "Spawn");
        point(ServerType.HUB_BEDWARS, "npc_stats", "NPC de estatísticas");
        point(ServerType.HUB_BEDWARS, "npc_solo", "NPC Solo");
        point(ServerType.HUB_BEDWARS, "npc_duo", "NPC Dupla");
        point(ServerType.HUB_BEDWARS, "npc_trio", "NPC Trio");
        point(ServerType.HUB_BEDWARS, "npc_quartet", "NPC Quarteto");
        point(ServerType.HUB_BEDWARS, "npc_versus", "NPC Versus");
        point(ServerType.HUB_BEDWARS, "leaderboard_level", "Ranking de nível");
        point(ServerType.HUB_BEDWARS, "leaderboard_rank", "Ranking de patente");
        point(ServerType.HUB_BEDWARS, "leaderboard_wins", "Ranking de vitórias");
        point(ServerType.HUB_BEDWARS, "leaderboard_kill", "Ranking de abates finais");
        point(ServerType.HUB_BEDWARS, "leaderboard_bed_destruction", "Ranking de camas quebradas");
        point(ServerType.HUB_BEDWARS, "leaderboard_selector", "Seletor de modo dos rankings");
        point(ServerType.HUB_BEDWARS, "leaderboard_period_selector", "Seletor de período dos rankings");

        point(ServerType.HUB_DUELS, "spawn", "Spawn");
        point(ServerType.HUB_DUELS, "npc_stats", "NPC de estatísticas");
        point(ServerType.HUB_DUELS, "npc_simulator", "NPC Simulator");
        point(ServerType.HUB_DUELS, "npc_soup", "NPC Soup");
        point(ServerType.HUB_DUELS, "npc_sumo", "NPC Sumo");
        point(ServerType.HUB_DUELS, "npc_gladiator", "NPC Gladiator");
        point(ServerType.HUB_DUELS, "npc_uhc", "NPC UHC");
        point(ServerType.HUB_DUELS, "npc_fireball", "NPC Fireball");
        point(ServerType.HUB_DUELS, "npc_boxing", "NPC Boxing");
        point(ServerType.HUB_DUELS, "npc_chamber", "NPC Chamber");
        point(ServerType.HUB_DUELS, "npc_thebridge", "NPC The Bridge");
        point(ServerType.HUB_DUELS, "npc_nodebuff", "NPC NoDebuff");
        point(ServerType.HUB_DUELS, "npc_bedfight", "NPC Bed Fight");
        point(ServerType.HUB_DUELS, "npc_pearlfight", "NPC Pearl Fight");
        point(ServerType.HUB_DUELS, "npc_skywars", "NPC Sky Wars");
        point(ServerType.HUB_DUELS, "leaderboard_wins", "Ranking de vitórias");
        point(ServerType.HUB_DUELS, "leaderboard_kills", "Ranking de abates");
        point(ServerType.HUB_DUELS, "leaderboard_streak", "Ranking de winstreak");

        point(ServerType.HUB_PVP, "spawn", "Spawn");
        point(ServerType.HUB_PVP, "npc_stats", "NPC de estatísticas (Academy)");
        point(ServerType.HUB_PVP, "npc_duels", "NPC Duels");
        point(ServerType.HUB_PVP, "npc_timers", "NPC Timers");
        point(ServerType.HUB_PVP, "npc_lava", "NPC Lava");
        point(ServerType.HUB_PVP, "npc_damage", "NPC Damage");
        point(ServerType.HUB_PVP, "npc_texture", "NPC Texture");
    }

    private static void point(ServerType server, String key, String label) {
        POINTS.computeIfAbsent(server, s -> new LinkedHashMap<>()).put(key, label);
    }

    @Command(name = "config", aliases = {"lobbyconfig", "setnpc"}, rank = RankType.ADMIN)
    public void config(BukkitCommandContext context) {
        Player player = context.getPlayer();
        String[] args = context.getArgs();
        String label = context.getLabel();

        if (args.length == 0) {
            help(player, label);
            return;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("lista") || sub.equals("list")) {
            list(player);
            return;
        }

        if (sub.equals("mostrar") || sub.equals("show")) {
            show(player);
            return;
        }

        if (!SUB_COMMANDS.contains(sub) && !sub.equals("remove") && !sub.equals("del")) {
            help(player, label);
            return;
        }

        if (args.length < 2) {
            player.sendMessage(PREFIX + "§cUso: /" + label + " " + sub + " <ponto>" + (sub.equals("set") ? " [centro]" : ""));
            return;
        }

        String key = args[1].toLowerCase();
        String pointLabel = getPoints().get(key);

        if (pointLabel == null) {
            player.sendMessage(PREFIX + "§cPonto §e" + key + " §cnão existe neste lobby. Veja em §e/" + label + " lista§c.");
            return;
        }

        Architect architect = Lobby.getLoader().getArchitect();

        switch (sub) {
            case "set": {
                Location location = player.getLocation();
                boolean center = args.length >= 3 && (args[2].equalsIgnoreCase("centro") || args[2].equalsIgnoreCase("center"));

                double x = center ? location.getBlockX() + 0.5 : round(location.getX());
                double y = center ? location.getBlockY() : round(location.getY());
                double z = center ? location.getBlockZ() + 0.5 : round(location.getZ());
                /* Centralizado também alinha o olhar ao ponto cardeal/diagonal mais próximo */
                float yaw = center ? Math.round(location.getYaw() / 45F) * 45F : (float) round(location.getYaw());
                float pitch = center ? 0F : (float) round(location.getPitch());

                Location old = architect.getLocation(key);

                remove(architect, key);
                architect.getLocations().add(new SignedLocation(key, new SyntheticLocation(x, y, z, yaw, pitch)));

                if (!save(player, architect)) return;

                int moved = old == null || key.equals("spawn") ? 0 : relocate(old, architect.getLocation(key));

                player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1F, 1.5F);
                player.sendMessage(PREFIX + "§a" + pointLabel + " §7definido em §f" + format(x, y, z) + "§7.");
                applyNotice(player, key, moved);
                break;
            }
            case "tp": {
                Location location = architect.getLocation(key);

                if (location == null) {
                    player.sendMessage(PREFIX + "§c" + pointLabel + " ainda não foi definido.");
                    return;
                }

                player.teleport(location);
                player.sendMessage(PREFIX + "Teleportado para §f" + pointLabel + "§7.");
                break;
            }
            default: {
                Location old = architect.getLocation(key);

                if (!remove(architect, key)) {
                    player.sendMessage(PREFIX + "§c" + pointLabel + " ainda não foi definido.");
                    return;
                }

                if (!save(player, architect)) return;

                if (!key.equals("spawn"))
                    relocate(old, null);

                player.playSound(player.getLocation(), Sound.ITEM_PICKUP, 1F, 0.8F);
                player.sendMessage(PREFIX + "§c" + pointLabel + " §7removido.");
                break;
            }
        }
    }

    @Completer(name = "config", subCommands = {"lobbyconfig", "setnpc"})
    public List<String> completer(BukkitCommandContext context) {
        String[] args = context.getArgs();

        if (args.length <= 1)
            return filter(SUB_COMMANDS, args.length == 0 ? "" : args[0]);

        if (args.length == 2 && !args[0].equalsIgnoreCase("lista") && !args[0].equalsIgnoreCase("mostrar"))
            return filter(new ArrayList<>(getPoints().keySet()), args[1]);

        if (args.length == 3 && args[0].equalsIgnoreCase("set"))
            return filter(Collections.singletonList("centro"), args[2]);

        return Collections.emptyList();
    }

    private void help(Player player, String label) {
        player.sendMessage("");
        player.sendMessage("  §6§l" + label.toUpperCase(java.util.Locale.US) + " §8- §7" + Lobby.getLoader().getArchitect().getServer().getName());
        player.sendMessage("");
        player.sendMessage("  §e/" + label + " set <ponto> §8- §7define na sua posição");
        player.sendMessage("  §e/" + label + " set <ponto> centro §8- §7no centro do bloco");
        player.sendMessage("  §e/" + label + " tp <ponto> §8- §7vai até o ponto");
        player.sendMessage("  §e/" + label + " remover <ponto> §8- §7apaga o ponto");
        player.sendMessage("  §e/" + label + " lista §8- §7pontos deste lobby (NPCs e rankings)");
        player.sendMessage("  §e/" + label + " mostrar §8- §7marca os pontos no mundo (30s)");
        player.sendMessage("");
    }

    private void list(Player player) {
        Architect architect = Lobby.getLoader().getArchitect();
        Map<String, String> points = getPoints();

        long defined = points.keySet().stream().filter(key -> architect.getLocation(key) != null).count();

        player.sendMessage("");
        player.sendMessage("  §6§lPONTOS §8- §7" + defined + "/" + points.size() + " definidos");
        player.sendMessage("");

        points.forEach((key, label) -> {
            Location location = architect.getLocation(key);

            player.sendMessage("  " + (location != null ? "§a✔ " : "§c✘ ") + "§f" + label + " §8(" + key + ")"
                    + (location != null ? " §7" + format(location.getX(), location.getY(), location.getZ()) : ""));
        });

        player.sendMessage("");
    }

    private void applyNotice(Player player, String key, int moved) {
        if (key.equals("spawn")) return;

        if (moved > 0)
            player.sendMessage(PREFIX + "§aAplicado na hora.");
        else
            player.sendMessage(PREFIX + "§eReinicie o lobby para criar o que fica nesse ponto.");
    }

    /**
     * Marcadores temporários (só para quem usou o comando) em cada ponto definido.
     */
    private void show(Player player) {
        Architect architect = Lobby.getLoader().getArchitect();
        HologramManager manager = BukkitCore.getManager().getHologram();

        long expires = System.currentTimeMillis() + 30000L;
        int count = 0;

        for (Map.Entry<String, String> entry : getPoints().entrySet()) {
            Location location = architect.getLocation(entry.getKey());

            if (location == null) continue;

            HologramClient marker = manager.spawnClient(player, "config_" + entry.getKey(), location.clone().add(0, 1, 0), expires);
            marker.setText(Arrays.asList("§e§l" + entry.getValue(), "§7" + entry.getKey()));

            count++;
        }

        player.sendMessage(PREFIX + (count == 0 ? "§cNenhum ponto definido ainda." : "§f" + count + " §7ponto(s) marcados por 30 segundos."));
    }

    /**
     * Move (ou remove, se {@code to} for nulo) os NPCs e hologramas que estão no ponto antigo.
     * Hologramas são achados pela mesma coluna X/Z (ficam acima do NPC); a altura relativa é mantida.
     */
    private int relocate(Location from, Location to) {
        if (from == null) return 0;

        int changed = 0;

        for (Npc npc : new ArrayList<>(BukkitCore.getManager().getNpc().getNPCs())) {
            Location location = npc.getLocation();

            if (location == null || !location.getWorld().equals(from.getWorld()) || location.distanceSquared(from) > 0.01) continue;

            List<Player> viewers = new ArrayList<>(npc.getViewers());
            viewers.forEach(npc::despawnTo);

            if (to == null) {
                npc.destroy();
            } else {
                npc.setLocation(to.clone());
                viewers.forEach(npc::spawnTo);
            }

            changed++;
        }

        HologramManager manager = BukkitCore.getManager().getHologram();

        for (Hologram hologram : new ArrayList<>(manager.getHolograms().values())) {
            Location location = hologram.getLocation();

            if (location == null || !location.getWorld().equals(from.getWorld())) continue;
            if (Math.abs(location.getX() - from.getX()) > 0.05 || Math.abs(location.getZ() - from.getZ()) > 0.05) continue;
            if (Math.abs(location.getY() - from.getY()) > 4) continue;
            if (hologram.getTag() != null && hologram.getTag().startsWith("config_")) continue;

            List<Player> viewers = new ArrayList<>(hologram.getViewers());
            viewers.forEach(hologram::despawnTo);

            if (to == null) {
                manager.getHolograms().remove(hologram.getId());
            } else {
                Vector delta = to.toVector().subtract(from.toVector());

                hologram.setLocation(location.clone().add(delta));
                hologram.repositionRows();

                viewers.forEach(hologram::spawnTo);
            }

            changed++;
        }

        return changed;
    }

    private Map<String, String> getPoints() {
        return POINTS.getOrDefault(Lobby.getLoader().getArchitect().getServer(), Collections.emptyMap());
    }

    private boolean remove(Architect architect, String key) {
        return architect.getLocations().removeIf(location -> location.getName().equalsIgnoreCase(key));
    }

    /**
     * Regrava a lista de locations do config.json (mantém o resto do arquivo).
     */
    private boolean save(Player player, Architect architect) {
        File file = new File(Lobby.getInstance().getDataFolder(), "config.json");

        try {
            JsonObject json;

            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    json = Core.PARSER.parse(reader).getAsJsonObject();
                }
            } else {
                json = new JsonObject();
                json.addProperty("server", architect.getServer().name());
            }

            JsonArray array = new JsonArray();

            for (SignedLocation signed : architect.getLocations()) {
                SyntheticLocation synthetic = signed.getSynthetic();
                JsonObject object = new JsonObject();

                object.addProperty("name", signed.getName());
                object.addProperty("x", synthetic.getX());
                object.addProperty("y", synthetic.getY());
                object.addProperty("z", synthetic.getZ());

                if (synthetic.getYaw() != 0 || synthetic.getPitch() != 0) {
                    object.addProperty("yaw", synthetic.getYaw());
                    object.addProperty("pitch", synthetic.getPitch());
                }

                if (synthetic.isAxisX())
                    object.addProperty("axisX", true);

                array.add(object);
            }

            json.add("locations", array);

            file.getParentFile().mkdirs();

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(json));
            }

            return true;
        } catch (Exception e) {
            Core.getLogger().warning("[Config] Não foi possível salvar o config.json: " + e.getMessage());
            player.sendMessage(PREFIX + "§cNão foi possível salvar o config.json.");
            return false;
        }
    }

    private static double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private static String format(double x, double y, double z) {
        return String.format(java.util.Locale.US, "%.1f, %.1f, %.1f", x, y, z);
    }

    private static List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase();

        return options.stream().filter(option -> option.startsWith(lower)).collect(Collectors.toList());
    }
}
