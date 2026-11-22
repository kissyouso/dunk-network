package com.minecraft.academy.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecraft.academy.Academy;
import com.minecraft.academy.arena.Arena;
import com.minecraft.academy.config.AcademySettings;
import com.minecraft.academy.config.LocationConfig;
import com.minecraft.academy.user.User;
import com.minecraft.core.Core;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.bukkit.command.structure.BukkitCommandContext;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.annotation.Command;
import com.minecraft.core.command.annotation.Completer;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class AcademyCommand implements CommandInheritor {

    private static final List<String> SUBCOMMANDS = Arrays.asList("list", "go", "setspawn", "set", "remove", "tp", "skin", "config", "reload", "build");

    @Command(name = "academy", aliases = {"academia"}, rank = RankType.ADMIN)
    public void academy(BukkitCommandContext context) {
        Player player = context.getPlayer();
        String[] args = context.getArgs();

        if (args.length == 0) {
            sendHelp(player);
            return;
        }

        Academy academy = Academy.getAcademy();

        LocationConfig config = academy.getLocationConfig();
        AcademySettings settings = academy.getSettings();

        switch (args[0].toLowerCase()) {
            case "list": {
                for (Arena arena : academy.getArenaStorage().getArenas()) {
                    player.sendMessage("§e" + arena.getName() + " §7(" + arena.getWorld().getName() + ") " + (arena.isEnabled() ? "§aativada" : "§cdesativada")
                            + " §7- spawn: " + (config.getSpawn(arena.getName()) != null ? "§a✔" : "§c✘") + " §7- jogadores: §f" + arena.getUsers().size());

                    StringBuilder builder = new StringBuilder(" §7Pontos: ");

                    for (String point : arena.getPoints())
                        builder.append(config.getPoint(arena.getName(), point) != null ? "§a" : "§c").append(point).append(" ");

                    player.sendMessage(builder.toString());
                }

                player.sendMessage("§7Verde = definido, vermelho = falta definir.");
                return;
            }
            case "build": {
                User user = User.fetch(player.getUniqueId());

                if (user == null) return;

                user.setBuild(!user.isBuild());
                player.sendMessage(user.isBuild() ? "§aModo construção ativado." : "§cModo construção desativado.");
                return;
            }
            case "reload": {
                settings.reload();
                config.reload();

                for (Arena arena : academy.getArenaStorage().getArenas()) {
                    Location spawn = config.getSpawn(arena.getName());

                    if (spawn != null)
                        arena.setSpawn(spawn);

                    arena.refresh();
                }

                academy.getArenaStorage().applySettings();
                player.sendMessage("§aConfigurações e coordenadas recarregadas e aplicadas!");
                return;
            }
            case "config": {
                if (args.length == 1) {
                    player.sendMessage("§6§lConfigurações da Academy §7(/academy config <chave> <valor>)");

                    for (String key : settings.getKeys())
                        player.sendMessage(" §e" + key + "§7: §f" + settings.get(key));
                    return;
                }

                if (args.length == 2) {
                    Object value = settings.get(args[1]);
                    player.sendMessage(value == null ? "§cChave '" + args[1] + "' não existe." : "§e" + args[1] + "§7: §f" + value);
                    return;
                }

                String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                String error = settings.set(args[1], value);

                if (error != null) {
                    player.sendMessage("§c" + error);
                    return;
                }

                academy.getArenaStorage().applySettings();
                player.sendMessage("§a" + args[1] + " §7agora é §f" + settings.get(args[1]) + "§a. Aplicado na hora!");
                return;
            }
        }

        if (args.length < 2) {
            sendHelp(player);
            return;
        }

        Arena arena = academy.getArenaStorage().getArena(args[1]);

        if (arena == null) {
            player.sendMessage("§cArena '" + args[1] + "' não encontrada. Arenas: " + arenaNames());
            return;
        }

        switch (args[0].toLowerCase()) {
            case "go": {
                User user = User.fetch(player.getUniqueId());

                if (user != null)
                    user.sendTo(arena);
                return;
            }
            case "setspawn": {
                if (!sameWorld(player, arena)) return;

                Location location = player.getLocation().clone();

                config.setSpawn(arena.getName(), location);
                arena.setSpawn(location);

                player.sendMessage("§aSpawn de §e" + arena.getName() + " §adefinido em §f" + format(location) + "§a!");
                return;
            }
            case "set": {
                String point = point(player, arena, args);

                if (point == null || !sameWorld(player, arena)) return;

                config.setPoint(arena.getName(), point, player.getLocation().clone());
                arena.refresh();

                player.sendMessage("§aPonto §e" + point + " §ade " + arena.getName() + " definido em §f" + format(player.getLocation()) + " §ae atualizado para todos!");
                return;
            }
            case "remove": {
                String point = point(player, arena, args);

                if (point == null) return;

                config.removePoint(arena.getName(), point);
                arena.refresh();

                player.sendMessage("§cPonto §e" + point + " §cde " + arena.getName() + " removido.");
                return;
            }
            case "tp": {
                if (args.length < 3) {
                    player.teleport(arena.getSpawn());
                    player.sendMessage("§aTeleportado para o spawn de §e" + arena.getName() + "§a.");
                    return;
                }

                String point = point(player, arena, args);

                if (point == null) return;

                Location location = config.getPoint(arena.getName(), point);

                if (location == null) {
                    player.sendMessage("§cO ponto §e" + point + " §cainda não foi definido.");
                    return;
                }

                player.teleport(location);
                player.sendMessage("§aTeleportado para o ponto §e" + point + "§a.");
                return;
            }
            case "skin": {
                String point = point(player, arena, args);

                if (point == null) return;

                if (args.length < 4) {
                    player.sendMessage("§cUso: /academy skin <arena> <ponto> <nick>");
                    return;
                }

                String name = args[3];
                Player target = Bukkit.getPlayerExact(name);

                if (target != null) {
                    Iterator<Property> iterator = ((CraftPlayer) target).getHandle().getProfile().getProperties().get("textures").iterator();

                    if (iterator.hasNext()) {
                        config.setSkin(arena.getName(), point, iterator.next());
                        arena.refresh();

                        player.sendMessage("§aSkin do ponto §e" + point + " §aatualizada para §b" + target.getName() + "§a!");
                        return;
                    }
                }

                player.sendMessage("§eBuscando a skin de §f" + name + " §ena Mojang...");

                Bukkit.getScheduler().runTaskAsynchronously(academy, () -> {
                    Property property = fetchSkin(name);

                    Bukkit.getScheduler().runTask(academy, () -> {
                        if (property == null) {
                            player.sendMessage("§cSkin de '" + name + "' não encontrada.");
                            return;
                        }

                        config.setSkin(arena.getName(), point, property);
                        arena.refresh();

                        player.sendMessage("§aSkin do ponto §e" + point + " §aatualizada para §b" + name + "§a!");
                    });
                });
                return;
            }
            default:
                sendHelp(player);
        }
    }

    @Completer(name = "academy", subCommands = {"academia"})
    public List<String> academyCompleter(BukkitCommandContext context) {
        String[] args = context.getArgs();
        List<String> options = new ArrayList<>();

        Academy academy = Academy.getAcademy();

        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("config"))
                options.addAll(academy.getSettings().getKeys());
            else
                for (Arena arena : academy.getArenaStorage().getArenas())
                    options.add(arena.getName());
        } else if (args.length == 3 && Arrays.asList("set", "remove", "tp", "skin").contains(args[0].toLowerCase())) {
            Arena arena = academy.getArenaStorage().getArena(args[1]);

            if (arena != null)
                options.addAll(arena.getPoints());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("skin")) {
            for (Player online : Bukkit.getOnlinePlayers())
                options.add(online.getName());
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();

        return options.stream().filter(option -> option.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }

    /* Busca a skin de qualquer nick (mesmo offline) na API da Mojang */
    private Property fetchSkin(String name) {
        try {
            JsonObject profile = read("https://api.mojang.com/users/profiles/minecraft/" + name);

            if (profile == null || !profile.has("id")) return null;

            JsonObject session = read("https://sessionserver.mojang.com/session/minecraft/profile/" + profile.get("id").getAsString() + "?unsigned=false");

            if (session == null || !session.has("properties")) return null;

            JsonArray properties = session.getAsJsonArray("properties");

            for (JsonElement element : properties) {
                JsonObject property = element.getAsJsonObject();

                if (property.get("name").getAsString().equals("textures"))
                    return new Property("textures", property.get("value").getAsString(), property.get("signature").getAsString());
            }
        } catch (Exception e) {
            Core.getLogger().warning("[Academy] Erro ao buscar skin de " + name + ": " + e.getMessage());
        }

        return null;
    }

    private JsonObject read(String url) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(url).openStream(), StandardCharsets.UTF_8))) {
            String response = reader.lines().collect(Collectors.joining());

            return response.isEmpty() ? null : Core.PARSER.parse(response).getAsJsonObject();
        }
    }

    private String point(Player player, Arena arena, String[] args) {
        if (args.length < 3 || !arena.getPoints().contains(args[2].toUpperCase())) {
            player.sendMessage("§cPontos de " + arena.getName() + ": §f" + String.join(", ", arena.getPoints()));
            return null;
        }

        return args[2].toUpperCase();
    }

    private boolean sameWorld(Player player, Arena arena) {
        if (player.getWorld().getUID().equals(arena.getWorld().getUID()))
            return true;

        player.sendMessage("§cVocê precisa estar no mundo §f" + arena.getWorld().getName() + "§c. Use /academy go " + arena.getName() + ".");
        return false;
    }

    private String arenaNames() {
        return Academy.getAcademy().getArenaStorage().getArenas().stream().map(Arena::getName).collect(Collectors.joining(", "));
    }

    private String format(Location location) {
        return String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ());
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l/academy §7- Configuração da Academy");
        player.sendMessage("§e/academy list §7- Arenas, status, spawns e pontos.");
        player.sendMessage("§e/academy go <arena> §7- Vai até uma arena.");
        player.sendMessage("§e/academy tp <arena> [ponto] §7- Teleporta para o spawn ou um ponto.");
        player.sendMessage("§e/academy setspawn <arena> §7- Define o spawn (na sua posição).");
        player.sendMessage("§e/academy set <arena> <ponto> §7- Define um NPC (na sua posição).");
        player.sendMessage("§e/academy remove <arena> <ponto> §7- Remove um NPC.");
        player.sendMessage("§e/academy skin <arena> <ponto> <nick> §7- Skin do NPC (qualquer nick).");
        player.sendMessage("§e/academy config [chave] [valor] §7- Vê/altera configurações.");
        player.sendMessage("§e/academy reload §7- Recarrega config.json e locations.yml.");
        player.sendMessage("§e/academy build §7- Alterna o modo construção.");
    }
}
