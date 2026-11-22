package com.minecraft.event;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.api.event.EventState;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.option.ServerOptions;
import com.minecraft.core.bukkit.command.single.EventCommand;
import com.minecraft.core.bukkit.listener.handler.ListenerHandler;
import com.minecraft.core.server.type.ServerType;
import com.minecraft.event.command.EventAdminHandler;
import com.minecraft.event.config.EventLocations;
import com.minecraft.event.config.EventSettings;
import com.minecraft.event.game.EventManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * Servidor de eventos: Sumo, Fight, Spleef, Parkour, Semáforo, Batata Quente e Killer.
 */
@Getter
public class Events extends BukkitCore {

    @Getter
    private static Events events;

    private EventSettings settings;
    private EventLocations locations;
    private EventManager eventManager;

    private int tick;

    @Override
    public void onLoad() {
        super.onLoad();

        events = this;

        ServerOptions.DAMAGE_ENABLED = true;

        Core.setServerType(ServerType.EVENT);
    }

    @Override
    public void onEnable() {
        super.onEnable();

        World world = Bukkit.getWorlds().get(0);

        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("naturalRegeneration", "true");
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setTime(6000);

        this.settings = new EventSettings(this);
        this.locations = new EventLocations(this);
        this.eventManager = new EventManager(this);

        /* O /evento deste servidor é tratado aqui (entrar, sair, assistir e administração) */
        EventCommand.handler = new EventAdminHandler();

        new ListenerHandler(this).handle(Constant.SOURCE_DIR + ".event.listener");

        getServer().getScheduler().runTaskTimer(this, () -> eventManager.tick(++tick), 1L, 1L);

        getLogger().info("Servidor de eventos iniciado com sucesso.");
    }

    @Override
    public void onDisable() {
        if (eventManager != null && eventManager.isRunning())
            eventManager.cancel("servidor reiniciando");

        EventState.publish(null);

        super.onDisable();
    }
}
