package com.minecraft.core.bukkit.command.structure;

import com.minecraft.core.Core;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.bukkit.command.structure.annotation.BukkitCommand;
import com.minecraft.core.bukkit.command.structure.annotation.BukkitCompleter;
import com.minecraft.core.command.CommandContext;
import com.minecraft.core.command.CommandHandler;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.annotation.Command;
import com.minecraft.core.command.annotation.Completer;
import com.minecraft.core.util.Util;
import com.minecraft.core.util.list.loader.ClassLoader;
import lombok.Getter;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class BukkitCommandHandler implements CommandHandler {

    private final JavaPlugin plugin;

    private final CommandMap map;

    @Getter
    private static final Map<String, Map.Entry<Method, Object>> commandMap = new HashMap<>();

    private Map<String, org.bukkit.command.Command> knownCommands;

    public BukkitCommandHandler(JavaPlugin plugin) {
        this.plugin = plugin;

        this.map = ((CraftServer) plugin.getServer()).getCommandMap();

        try {
            Field field = map.getClass().getDeclaredField("knownCommands");
            field.setAccessible(true);

            knownCommands = (HashMap<String, org.bukkit.command.Command>) field.get(map);
        } catch (Exception e) {
            Core.getLogger().log(Level.WARNING, "Ocorreu um erro ao carregar os comandos", e);
        }
    }

    /* Permissão dada aos comandos do Minecraft/Bukkit/outros plugins que antes eram liberados para todos */
    public static final String SERVER_COMMAND_PERMISSION = "dunk.command.server";

    /**
     * Esconde do "/" + TAB (e bloqueia) os comandos que não são da rede e estavam liberados para todos
     * (/me, /help, /tell, /minecraft:...). Quem tem "*" (Admin+) e OPs continuam vendo.
     * Também tira as cópias "plugin:comando" do TAB quando o comando sem prefixo existe.
     */
    public static void restrictServerCommands(JavaPlugin plugin) {
        try {
            CommandMap commandMap = ((CraftServer) plugin.getServer()).getCommandMap();

            Field field = commandMap.getClass().getDeclaredField("knownCommands");
            field.setAccessible(true);

            Map<String, org.bukkit.command.Command> known = (Map<String, org.bukkit.command.Command>) field.get(commandMap);

            int restricted = 0;

            for (org.bukkit.command.Command command : new java.util.HashSet<>(known.values())) {
                if (command instanceof BukkitCommand) continue;

                String permission = command.getPermission();

                boolean open = permission == null || permission.isEmpty();

                if (!open) {
                    org.bukkit.permissions.Permission registered = plugin.getServer().getPluginManager().getPermission(permission.split(";")[0]);

                    open = registered != null && registered.getDefault() == org.bukkit.permissions.PermissionDefault.TRUE;
                }

                if (open) {
                    command.setPermission(SERVER_COMMAND_PERMISSION);
                    command.setPermissionMessage(CommandInheritor.WITHOUT_PERMISSION);
                    restricted++;
                }
            }

            known.keySet().removeIf(key -> {
                int separator = key.indexOf(':');

                return separator > 0 && known.containsKey(key.substring(separator + 1));
            });

            Core.getLogger().info("[Comandos] " + restricted + " comando(s) do servidor restritos à equipe no TAB.");
        } catch (Exception e) {
            Core.getLogger().log(Level.WARNING, "Não foi possível restringir os comandos do servidor", e);
        }
    }

    @Override
    public void handle(String path) {
        Instant now = Instant.now();

        int loaded = 0;

        Core.getLogger().info("Registrando comandos...");

        for (Class<?> cmdClass : ClassLoader.getClassesForPackage(plugin, path)) {
            if (CommandInheritor.class.isAssignableFrom(cmdClass)) {
                try {
                    CommandInheritor inheritor = (CommandInheritor) cmdClass.newInstance();

                    registerInheritor(inheritor);

                    loaded++;
                } catch (Exception e) {
                    Core.getLogger().log(Level.WARNING, "Não foi possível registrar o comando " + cmdClass.getSimpleName() + "!", e);
                }
            }
        }

        if (loaded > 0)
            Core.getLogger().info("Registro de comandos concluído. " +
                    "(Total de comandos registrados: " + loaded + " em " + Util.formatInstant(now) + ")");
    }

    @Override
    public void registerInheritor(CommandInheritor inheritor) {
        // Registrando comandos
        for (Method method : inheritor.getClass().getMethods()) {
            if (method.isAnnotationPresent(Command.class)) {
                if (method.getParameterCount() != 1 || !CommandContext.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    Core.getLogger().info("Não foi possível registrar o comando " + method.getName() + ". Argumentos de método não esperados!");
                    continue;
                }

                Command command = method.getAnnotation(Command.class);
                if (command == null) {
                    continue;  // Use continue to check other methods instead of break
                }

                registerCommand(inheritor, command, command.name(), method);

                for (String alias : command.aliases()) {
                    registerCommand(inheritor, command, alias, method);
                }
            }
        }

        // Registrando completers
        for (Method method : inheritor.getClass().getMethods()) {
            if (method.isAnnotationPresent(Completer.class)) {
                if (method.getParameterCount() != 1 || !CommandContext.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    Core.getLogger().info("Não foi possível registrar o completer " + method.getName() + ". Argumentos de método não esperados!");
                    continue;
                }

                if (!List.class.isAssignableFrom(method.getReturnType())) {
                    Core.getLogger().info("Não foi possível registrar o completer " + method.getName() + ". Tipo de retorno inesperado. O retorno deve ser uma Lista de Strings.");
                    continue;
                }

                Completer completer = method.getAnnotation(Completer.class);
                if (completer == null) {
                    continue;  // Use continue to check other methods instead of break
                }

                for (String subCommand : completer.subCommands()) {
                    registerCompleter(inheritor, method, subCommand);
                }

                registerCompleter(inheritor, method, completer.name());
            }
        }
    }

    private void registerCommand(CommandInheritor base, Command command, String label, Method method) {
        Map.Entry<Method, Object> entry = new AbstractMap.SimpleEntry<>(method, base);

        commandMap.put(label.toLowerCase(), entry);

        String commandLabel = label.split("\\.")[0].toLowerCase();
        boolean root = !label.contains(".");

        if (map.getCommand(commandLabel) == null) {
            BukkitCommand cmd = new BukkitCommand(plugin, null, commandLabel);

            cmd.addRank(command.rank(), command.permission(), root);

            map.register(commandLabel, cmd);

            /* Sem a cópia "comando:comando" no TAB */
            if (knownCommands != null)
                knownCommands.remove(commandLabel + ":" + commandLabel);
        } else if (map.getCommand(commandLabel) instanceof BukkitCommand) {
            BukkitCommand bukkitCommand = (BukkitCommand) map.getCommand(commandLabel);

            if (bukkitCommand != null)
                bukkitCommand.addRank(command.rank(), command.permission(), root);
        }
    }

    private void registerCompleter(CommandInheritor base, Method method, String label) {
        String cmdLabel = label.split("\\.")[0].toLowerCase();

        Completer annotation = method.getAnnotation(Completer.class);

        if (map.getCommand(cmdLabel) == null) {
            BukkitCommand command = new BukkitCommand(plugin, null, cmdLabel);

            if (annotation != null)
                command.setCompleterRank(annotation.rank());

            knownCommands.put(cmdLabel, command);
        }

        if (map.getCommand(cmdLabel) instanceof BukkitCommand) {
            BukkitCommand command = (BukkitCommand) map.getCommand(cmdLabel);

            if (command.getCompleter() == null) {
                command.setCompleter(new BukkitCompleter());
            }

            command.getCompleter().addCompleter(label, method, base);
        }

        if (map.getCommand(cmdLabel) instanceof PluginCommand) {
            try {
                org.bukkit.command.Command command = map.getCommand(cmdLabel);

                Field field = command.getClass().getDeclaredField("completer");
                field.setAccessible(true);

                if (field.get(command) == null) {
                    BukkitCompleter completer = new BukkitCompleter();

                    completer.addCompleter(label, method, base);
                    field.set(command, completer);
                }

                if (field.get(command) instanceof BukkitCompleter) {
                    BukkitCompleter completer = (BukkitCompleter) field.get(command);

                    completer.addCompleter(label, method, base);
                } else {
                    Core.getLogger().info("Não foi possível registrar o completer " + method.getName()
                            + ". Um completer já está registrado para esse comando!");
                }

            } catch (Exception e) {
                Core.getLogger().log(Level.WARNING, "Não foi possível registrar o completador...", e);
            }
        }
    }
}
