package com.minecraft.academy;

import com.minecraft.academy.arena.Arena;
import com.minecraft.academy.arena.ArenaStorage;
import com.minecraft.academy.config.AcademySettings;
import com.minecraft.academy.config.LocationConfig;
import com.minecraft.academy.user.User;
import com.minecraft.core.Constant;
import com.minecraft.core.account.context.objects.academy.AcademyCount;
import com.minecraft.core.Core;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.option.ServerOptions;
import com.minecraft.core.bukkit.command.structure.BukkitCommandHandler;
import com.minecraft.core.bukkit.listener.handler.ListenerHandler;
import com.minecraft.core.server.type.ServerType;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.HashMap;
import java.util.Map;

/**
 * Servidor de treinos da Academy: Timers, Lava, Damage e Texture.
 */
@Getter
public class Academy extends BukkitCore {

    @Getter
    private static Academy academy;

    private LocationConfig locationConfig;
    private AcademySettings settings;
    private ArenaStorage arenaStorage;

    private int tick;

    @Override
    public void onLoad() {
        super.onLoad();

        academy = this;

        ServerOptions.DAMAGE_ENABLED = true;
        ServerOptions.DROP_ITEM_ENABLED = true;

        Core.setServerType(ServerType.ACADEMY);
    }

    @Override
    public void onEnable() {
        super.onEnable();

        setupWorld(Bukkit.getWorlds().get(0));

        for (String name : new String[]{"timers", "lava", "damage", "texture"})
            loadWorld(name);

        this.locationConfig = new LocationConfig(this);
        this.settings = new AcademySettings(this);
        this.arenaStorage = new ArenaStorage(this);

        new BukkitCommandHandler(this).handle(Constant.SOURCE_DIR + ".academy.command");
        new ListenerHandler(this).handle(Constant.SOURCE_DIR + ".academy.listener");

        /* Recraft de cocoa (tigela + 3 sementes de cacau) */
        ShapelessRecipe cocoa = new ShapelessRecipe(new ItemStack(Material.MUSHROOM_SOUP));
        cocoa.addIngredient(Material.BOWL);
        cocoa.addIngredient(3, Material.INK_SACK, 3);
        Bukkit.addRecipe(cocoa);

        getServer().getScheduler().runTaskTimer(this, () -> {
            tick++;

            for (Arena arena : arenaStorage.getArenas())
                arena.tick(tick);

            if (tick % 20 == 0) {
                for (User user : User.getUsers()) {
                    if (user.getArena() != null)
                        user.getArena().updateSidebar(user);
                }
            }

            /* Publica a contagem de jogadores por arena para o lobby */
            if (tick % 40 == 0)
                publishCounts();
        }, 1L, 1L);

        getLogger().info("Academy iniciada com sucesso.");
    }

    @Override
    public void onDisable() {
        Core.getRedis().delete(AcademyCount.KEY + Core.getServerId());

        super.onDisable();
    }

    protected void publishCounts() {
        Map<String, Integer> counts = new HashMap<>();

        for (Arena arena : arenaStorage.getArenas())
            counts.put(arena.getName(), arena.getUsers().size());

        Core.getPlatform().runAsync(() -> Core.getRedis().save(AcademyCount.KEY + Core.getServerId(), new AcademyCount(counts), 10));
    }

    private void loadWorld(String name) {
        WorldCreator creator = new WorldCreator(name);
        creator.generateStructures(false);
        creator.type(WorldType.FLAT);

        World world = Bukkit.getServer().createWorld(creator);

        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof ItemFrame))
                entity.remove();
        }

        setupWorld(world);
        world.setSpawnLocation(0, 71, 0);

        getLogger().info("Mundo " + name + " carregado.");
    }

    private void setupWorld(World world) {
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("naturalRegeneration", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("sendCommandFeedback", "false");
        world.setGameRuleValue("logAdminCommands", "false");
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setAutoSave(false);
        world.setTime(6000);
        world.setPVP(false);
    }
}
