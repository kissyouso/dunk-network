package com.minecraft.arcade.skywars.world;

import com.minecraft.arcade.skywars.SkyWars;
import com.minecraft.core.Core;
import com.minecraft.core.arcade.room.map.Map;
import com.minecraft.core.util.list.FileUtil;
import com.minecraft.core.util.list.WorldUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;
import java.util.logging.Level;

/**
 * Controlador de mundos do SkyWars sem SlimeWorldManager.
 *
 * <p>Cada mapa guarda a pasta do mundo em {@code plugins/DunkSkyWars/maps/<modo>/<mapa>/world}.
 * Ao iniciar uma arena o template é copiado para a raiz do servidor com o nome
 * {@code <arcade>-<mapa>-<id>} e carregado como um mundo Bukkit normal. No reset a
 * pasta é descarregada e apagada.</p>
 */
public class WorldController {

    public static void cloneWorld(Map map, String worldName) {
        cloneWorld(map, worldName, () -> {
        });
    }

    public static void cloneWorld(Map map, String worldName, Runnable afterGenerate) {
        File template = resolveTemplate(map);

        if (template == null) {
            Core.getLogger().warning("[SkyWars] Template do mapa \"" + map.getName() + "\" não encontrado.");
            return;
        }

        /* Copiar/carregar mundo precisa acontecer no thread principal. */
        Bukkit.getScheduler().runTask(SkyWars.getInstance(), () -> {
            try {
                World loaded = Bukkit.getWorld(worldName);

                if (loaded != null)
                    Bukkit.unloadWorld(loaded, false);

                File destination = new File(Bukkit.getWorldContainer(), worldName);

                if (destination.exists())
                    FileUtil.delete(destination);

                FileUtil.copy(template, destination, path -> !isTransient(path));

                World world = new WorldCreator(worldName)
                        .environment(World.Environment.NORMAL)
                        .generateStructures(false)
                        .createWorld();

                if (world == null) {
                    Core.getLogger().warning("[SkyWars] Não foi possível carregar o mundo \"" + worldName + "\".");
                    return;
                }

                WorldUtil.setup(world);

                Core.getLogger().info("[SkyWars] Mundo \"" + worldName + "\" carregado a partir de \"" + template.getName() + "\".");

                afterGenerate.run();
            } catch (Exception exception) {
                Core.getLogger().log(Level.WARNING, "Não foi possível carregar o mundo \"" + worldName + "\".", exception);
            }
        });
    }

    public static void unloadWorld(World world) {
        unloadWorld(world, () -> {
        });
    }

    public static void unloadWorld(World world, Runnable afterUnload) {
        if (world == null) return;

        String worldName = world.getName();
        File folder = world.getWorldFolder();

        Bukkit.unloadWorld(world, false);

        if (Bukkit.getWorld(worldName) != null) {
            Core.getLogger().warning("[SkyWars] Não foi possível descarregar o mundo \"" + worldName + "\".");
            return;
        }

        FileUtil.delete(folder);

        Core.getLogger().info("[SkyWars] Mundo \"" + worldName + "\" deletado.");

        afterUnload.run();
    }

    private static boolean isTransient(java.nio.file.Path path) {
        String name = path.getFileName().toString().toLowerCase();

        return name.equals("session.lock") || name.equals("uid.dat");
    }

    /**
     * Procura a pasta do mundo do mapa. O layout novo fica em
     * {@code maps/<modo>/<mapa>/world}; o layout antigo do PxSkyWars fica em
     * {@code plugins/PxSkyWars/mundos/<mapa>}.
     */
    private static File resolveTemplate(Map map) {
        File source = map.getSource();

        if (source != null) {
            File world = new File(source, "world");

            if (world.isDirectory()) return world;

            if (new File(source, "level.dat").isFile()) return source;
        }

        File legacyRoot = new File(SkyWars.getInstance().getDataFolder().getParentFile(), "PxSkyWars/mundos");

        File legacy = new File(legacyRoot, map.getName());

        if (legacy.isDirectory()) return legacy;

        return null;
    }
}
