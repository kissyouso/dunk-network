package com.minecraft.arcade.thebridge.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecraft.arcade.thebridge.TheBridge;
import com.minecraft.core.Core;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.bukkit.command.structure.BukkitCommandContext;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.CommandSender;
import com.minecraft.core.command.annotation.Command;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public class TheBridgeCommand implements CommandInheritor {

    @Command(name = "treino", aliases = {"thebridge", "tb"}, rank = RankType.ADMIN, onlyPlayer = false)
    public void treino(BukkitCommandContext context) {
        CommandSender sender = context.getSender();
        String[] args = context.getArgs();

        if (args.length == 0) {
            help(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "createmap": {
                if (args.length < 2) {
                    sender.send("§cUso: /treino createmap <mapa>");
                    return;
                }

                createMap(sender, args[1]);
                break;
            }
            case "setspawn": {
                saveLocationFromPlayer(context, args, "spawn");
                break;
            }
            case "setfinish": {
                saveLocationFromPlayer(context, args, "finish");
                break;
            }
            case "setstats": {
                saveLocationFromPlayer(context, args, "stats");
                break;
            }
            case "setloc": {
                if (args.length < 3) {
                    sender.send("§cUso: /treino setloc <mapa> <nome>");
                    return;
                }

                saveLocationFromPlayer(context, new String[]{args[0], args[1], args[2]}, args[2]);
                break;
            }
            case "template": {
                if (args.length < 3) {
                    sender.send("§cUso: /treino template <mapa> <template>");
                    return;
                }

                setTemplate(sender, args[1], args[2]);
                break;
            }
            case "maps": {
                listMaps(sender);
                break;
            }
            case "buildmap": {
                buildMap(sender, args);
                break;
            }
            default:
                help(sender);
                break;
        }
    }

    private void help(CommandSender sender) {
        sender.send("§e§lTREINO DE PONTE - Editor");
        sender.send("§f/treino createmap <mapa> §7- cria a pasta/config do mapa");
        sender.send("§f/treino setspawn <mapa> §7- spawn onde você está");
        sender.send("§f/treino setfinish <mapa> §7- local de conclusão da ponte");
        sender.send("§f/treino setstats <mapa> §7- local do holograma");
        sender.send("§f/treino setloc <mapa> <nome> §7- salva uma location qualquer");
        sender.send("§f/treino template <mapa> <template> §7- mundo padrão ou Slime usado na cópia");
        sender.send("§f/treino maps §7- lista os mapas");
        sender.send("§f/treino buildmap <mundo> <x> <y> <z> [dist] §7- constrói o mapa do treino");
    }

    /**
     * Constroi o mapa do Treino de Ponte direto no mundo informado, sem
     * depender do /fill (o core desativa os comandos de edicao no console).
     *
     * <p>O mapa tem: ilha de spawn, ilha de chegada e um piso de placas de
     * pressao abaixo da ponte - cair nele devolve o jogador ao spawn da
     * ilha.</p>
     */
    private void buildMap(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.send("§cUso: /treino buildmap <mundo> <x> <y> <z> [distancia]");
            return;
        }

        World world = Bukkit.getWorld(args[1]);

        if (world == null) {
            sender.send("§cMundo §f" + args[1] + " §cnão está carregado.");
            return;
        }

        int x, y, z, distance;

        try {
            x = Integer.parseInt(args[2]);
            y = Integer.parseInt(args[3]);
            z = Integer.parseInt(args[4]);

            distance = args.length >= 6 ? Integer.parseInt(args[5]) : 49;
        } catch (NumberFormatException e) {
            sender.send("§cAs coordenadas precisam ser números inteiros.");
            return;
        }

        /* Limpa a area para o comando poder ser rodado de novo. */
        for (int bx = x - 10; bx <= x + 10; bx++)
            for (int bz = z - 11; bz <= z + distance + 11; bz++)
                for (int by = y - 6; by <= y + 6; by++)
                    world.getBlockAt(bx, by, bz).setType(Material.AIR);

        /* Piso de placas de pressao: qualquer queda na ponte volta ao spawn. */
        for (int bx = x - 10; bx <= x + 10; bx++) {
            for (int bz = z - 11; bz <= z + distance + 11; bz++) {
                world.getBlockAt(bx, y - 6, bz).setType(Material.SMOOTH_BRICK);
                world.getBlockAt(bx, y - 5, bz).setType(Material.STONE_PLATE);
            }
        }

        /* Ilhas (spawn e chegada). */
        buildIsland(world, x, y, z);
        buildIsland(world, x, y, z + distance);

        /* Centro dourado da chegada. */
        for (int bx = x - 1; bx <= x + 1; bx++)
            for (int bz = z + distance - 1; bz <= z + distance + 1; bz++)
                world.getBlockAt(bx, y, bz).setType(Material.GOLD_BLOCK);

        /* Colunas de quartzo com luz nos cantos da chegada. */
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int by = y + 1; by <= y + 3; by++)
                    world.getBlockAt(x + (sx * 3), by, z + distance + (sz * 3)).setType(Material.QUARTZ_BLOCK);

                world.getBlockAt(x + (sx * 3), y + 4, z + distance + (sz * 3)).setType(Material.GLOWSTONE);
            }
        }

        sender.send("§aMapa construído no mundo §f" + world.getName() + "§a.");
        sender.send("§7Spawn: §f" + (x + 0.5) + ", " + (y + 1) + ", " + (z + 0.5) + " §7(yaw 0)");
        sender.send("§7Chegada: §f" + (x + 0.5) + ", " + (y + 1) + ", " + (z + distance + 0.5));
        world.save();
        sender.send("§7Use §f/treino template <mapa> " + world.getName() + "§7, configure o spawn e reinicie. Não precisa importar no SWM.");
    }

    private void buildIsland(World world, int x, int y, int z) {
        for (int bx = x - 4; bx <= x + 4; bx++) {
            for (int bz = z - 4; bz <= z + 4; bz++) {
                for (int by = y - 5; by < y; by++)
                    world.getBlockAt(bx, by, bz).setType(Material.SMOOTH_BRICK);

                world.getBlockAt(bx, y, bz).setType(Material.QUARTZ_BLOCK);
            }
        }

        /* Borda da ilha em tijolo de pedra, para dar acabamento. */
        for (int bx = x - 4; bx <= x + 4; bx++) {
            world.getBlockAt(bx, y, z - 4).setType(Material.SMOOTH_BRICK);
            world.getBlockAt(bx, y, z + 4).setType(Material.SMOOTH_BRICK);
        }

        for (int bz = z - 4; bz <= z + 4; bz++) {
            world.getBlockAt(x - 4, y, bz).setType(Material.SMOOTH_BRICK);
            world.getBlockAt(x + 4, y, bz).setType(Material.SMOOTH_BRICK);
        }
    }

    private File mapsDirectory() {
        return new File(TheBridge.getInstance().getDataFolder(), "maps");
    }

    private File mapConfig(String map) {
        if (map.isEmpty() || map.equals(".") || map.equals("..") || map.contains("/")
                || map.contains("\\") || map.contains(":"))
            throw new IllegalArgumentException("Nome de mapa inválido.");

        return new File(mapsDirectory(), map + "/config.json");
    }

    private void createMap(CommandSender sender, String map) {
        try {
            File config = mapConfig(map);

            if (config.exists()) {
                sender.send("§cEsse mapa já existe!");
                return;
            }

            config.getParentFile().mkdirs();

            JsonObject json = new JsonObject();
            json.addProperty("name", map);
            json.addProperty("template", map);
            json.addProperty("build_limit", 256);
            json.add("locations", new JsonArray());

            try (FileWriter writer = new FileWriter(config)) {
                writer.write(Core.GSON.toJson(json));
            }

            sender.send("§aMapa §f" + map + " §acriado!");
            sender.send("§7Copie level.dat e region do mundo para §fplugins/thebridge/maps/" + map + "/world§7.");
            sender.send("§7Ou selecione um mundo da pasta do servidor/Slime com §f/treino template " + map + " <mundo>§7.");
            sender.send("§7Configure o spawn com §f/treino setspawn " + map + "§7 e reinicie.");
        } catch (Exception e) {
            sender.send("§cErro ao criar mapa: " + e.getMessage());
        }
    }

    private void saveLocationFromPlayer(BukkitCommandContext context, String[] args, String fallbackName) {
        CommandSender sender = context.getSender();
        Player player = context.getPlayer();

        if (player == null) {
            sender.send("§cApenas jogadores.");
            return;
        }

        if (args.length < 2) {
            sender.send("§cUso: /treino " + args[0].toLowerCase() + " <mapa>");
            return;
        }

        String map = args[1];
        String name = args.length >= 3 ? args[2] : fallbackName;

        saveLocation(sender, map, name, player.getLocation());
    }

    private void saveLocation(CommandSender sender, String map, String name, Location location) {
        try {
            File config = mapConfig(map);

            if (!config.exists()) {
                sender.send("§cMapa não encontrado! Crie com §f/treino createmap " + map);
                return;
            }

            JsonObject json = read(config);

            Map<String, JsonObject> locations = new LinkedHashMap<>();

            if (json.has("locations") && json.get("locations").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("locations")) {
                    JsonObject obj = element.getAsJsonObject();

                    locations.put(obj.get("name").getAsString().toLowerCase(), obj);
                }
            }

            JsonObject loc = new JsonObject();
            loc.addProperty("name", name.toLowerCase());
            loc.addProperty("x", location.getX());
            loc.addProperty("y", location.getY());
            loc.addProperty("z", location.getZ());
            loc.addProperty("yaw", location.getYaw());
            loc.addProperty("pitch", location.getPitch());

            locations.put(name.toLowerCase(), loc);

            JsonArray array = new JsonArray();

            for (JsonObject obj : locations.values())
                array.add(obj);

            json.add("locations", array);

            write(config, json);

            sender.send("§aLocation §f" + name + " §asalva no mapa §f" + map + "§a!");
        } catch (Exception e) {
            sender.send("§cErro ao salvar location: " + e.getMessage());
        }
    }

    private void setTemplate(CommandSender sender, String map, String template) {
        try {
            File config = mapConfig(map);

            if (!config.exists()) {
                sender.send("§cMapa não encontrado! Crie com §f/treino createmap " + map);
                return;
            }

            JsonObject json = read(config);

            json.addProperty("template", template);

            write(config, json);

            sender.send("§aTemplate do mapa §f" + map + " §adefinido para §f" + template + "§a.");
        } catch (Exception e) {
            sender.send("§cErro ao salvar template: " + e.getMessage());
        }
    }

    private void listMaps(CommandSender sender) {
        File root = mapsDirectory();

        File[] maps = root.listFiles(file -> file.isDirectory() && new File(file, "config.json").exists());

        sender.send("§e§lMapas do Treino de Ponte:");

        if (maps == null || maps.length == 0) {
            sender.send("§7Nenhum mapa. Crie com §f/treino createmap <mapa>");
            return;
        }

        for (File map : maps)
            sender.send("§7- §f" + map.getName());

        sender.send("§7Os modos Solo e Global usam os mesmos mapas.");
    }

    private JsonObject read(File config) throws Exception {
        try (FileReader reader = new FileReader(config)) {
            return Core.PARSER.parse(reader).getAsJsonObject();
        }
    }

    private void write(File config, JsonObject json) throws Exception {
        try (FileWriter writer = new FileWriter(config)) {
            writer.write(Core.GSON.toJson(json));
        }
    }
}
