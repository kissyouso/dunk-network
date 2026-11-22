package com.minecraft.arcade.thebridge.world;

import com.minecraft.arcade.thebridge.TheBridge;
import com.minecraft.arcade.thebridge.arcade.arena.TheBridgeArena;
import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.util.list.FileUtil;
import com.minecraft.core.util.list.WorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class WorldController {

    private static final Map<String, Path> temporaryWorlds = new HashMap<>();

    public static void cloneWorld(TheBridgeArena arena, Runnable afterGenerate, Runnable onFailure) {
        Bukkit.getScheduler().runTask(TheBridge.getInstance(), () -> {
            if (arena.getArcade().getPendingArena(arena.getId()) != arena) return;

            String name = arena.getWorldName();

            try {
                Path template = resolveTemplate(arena.getMap().getSource(), Bukkit.getWorldContainer(), arena.getTemplateName());
                Path destination = new File(Bukkit.getWorldContainer(), name).toPath().toAbsolutePath().normalize();

                if (Bukkit.getWorld(name) != null || Files.exists(destination))
                    throw new IOException("O destino já existe: " + name);

                if (template != null && destination.startsWith(template))
                    throw new IOException("O destino não pode ficar dentro do template.");

                Files.createDirectory(destination);
                temporaryWorlds.put(name, destination);

                World world;

                if (template != null) {
                    for (World loaded : Bukkit.getWorlds()) {
                        if (loaded.getWorldFolder().getCanonicalFile().toPath().equals(template))
                            loaded.save();
                    }

                    copyTemplate(template, destination);
                    world = new WorldCreator(name).environment(World.Environment.NORMAL)
                            .generateStructures(false).createWorld();
                } else {
                    if (!Bukkit.getPluginManager().isPluginEnabled("SlimeWorldManager"))
                        throw new IOException("Template padrão não encontrado e SlimeWorldManager não está ativo: "
                                + arena.getTemplateName() + ". Coloque level.dat e region em maps/<mapa>/world.");

                    world = SlimeSupport.cloneWorld(arena.getTemplateName(), name);
                }

                if (world == null)
                    throw new IOException("Não foi possível carregar o mundo " + name);

                WorldUtil.setup(world);
                afterGenerate.run();
            } catch (Exception | LinkageError exception) {
                Core.getLogger().log(Level.WARNING, "[TheBridge] Falha ao carregar " + name, exception);
                unloadWorld(name);
                onFailure.run();
            }
        });
    }

    public static boolean unloadWorld(String name) {
        Path directory = temporaryWorlds.get(name);

        if (directory == null) return true;

        World world = Bukkit.getWorld(name);

        if (world != null && !Bukkit.unloadWorld(world, false)) {
            Core.getLogger().warning("[TheBridge] O mundo " + name + " continua carregado; sua pasta foi preservada.");
            return false;
        }

        if (Files.exists(directory) && !FileUtil.delete(directory.toFile())) {
            Core.getLogger().warning("[TheBridge] Não foi possível apagar a cópia temporária " + name);
            return false;
        }

        temporaryWorlds.remove(name);
        return true;
    }

    static Path resolveTemplate(File source, File worldContainer, String templateName) throws IOException {
        File nested = new File(source, "world");

        if (nested.exists()) return requireWorld(nested);
        if (new File(source, "level.dat").isFile()) return requireWorld(source);

        Path container = worldContainer.getCanonicalFile().toPath();
        Path named = new File(worldContainer, templateName).getCanonicalFile().toPath();

        if (!named.startsWith(container) || named.equals(container))
            throw new IOException("O template deve ficar dentro da pasta de mundos do servidor.");

        return Files.exists(named) ? requireWorld(named.toFile()) : null;
    }

    private static Path requireWorld(File folder) throws IOException {
        if (!folder.isDirectory() || !new File(folder, "level.dat").isFile())
            throw new IOException("Mundo padrão inválido (level.dat ausente): " + folder);

        return folder.getCanonicalFile().toPath();
    }

    static void copyTemplate(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (!directory.equals(source))
                    Files.createDirectory(destination.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                String name = file.getFileName().toString();

                if (name.equalsIgnoreCase("uid.dat") || name.equalsIgnoreCase("session.lock"))
                    return FileVisitResult.CONTINUE;

                if (attributes.isSymbolicLink())
                    throw new IOException("Links simbólicos não são permitidos no template: " + file);

                Files.copy(file, destination.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // Mantém as classes opcionais do SWM fora do carregamento de mundos padrão.
    private static final class SlimeSupport {
        private static World cloneWorld(String template, String name) throws Exception {
            com.grinderwolf.swm.plugin.SWMPlugin plugin = com.grinderwolf.swm.plugin.SWMPlugin.getInstance();
            com.grinderwolf.swm.api.loaders.SlimeLoader loader = plugin.getLoader(Constant.SLIME_FILE_LOADER);

            if (loader == null || !loader.worldExists(template))
                throw new IOException("Template Slime não encontrado: " + template);

            com.grinderwolf.swm.api.world.properties.SlimePropertyMap properties =
                    new com.grinderwolf.swm.api.world.properties.SlimePropertyMap();
            properties.setString(com.grinderwolf.swm.api.world.properties.SlimeProperties.WORLD_TYPE, "flat");
            properties.setString(com.grinderwolf.swm.api.world.properties.SlimeProperties.DIFFICULTY, "normal");
            properties.setBoolean(com.grinderwolf.swm.api.world.properties.SlimeProperties.ALLOW_ANIMALS, false);
            properties.setBoolean(com.grinderwolf.swm.api.world.properties.SlimeProperties.ALLOW_MONSTERS, false);

            com.grinderwolf.swm.api.world.SlimeWorld world = plugin.loadWorld(loader, template, true, properties);
            plugin.generateWorld(world.clone(name));
            return Bukkit.getWorld(name);
        }
    }
}
