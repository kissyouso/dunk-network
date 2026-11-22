package com.minecraft.lobby;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.option.ServerOptions;
import com.minecraft.core.bukkit.command.structure.BukkitCommandHandler;
import com.minecraft.core.bukkit.listener.handler.ListenerHandler;
import com.minecraft.lobby.architect.loader.ArchitectLoader;
import lombok.Getter;
import org.bukkit.Bukkit;

public class Lobby extends BukkitCore {

    @Getter
    private static ArchitectLoader loader;

    @Override
    public void onLoad() {
        super.onLoad();

        loader = new ArchitectLoader(this);

        getLogger().info("Iniciando Lobby...");

        Core.setServerType(loader.getArchitect().getServer());

        /* Nenhum mob nasce em nenhum servidor (lobby incluso); apenas NPCs e entidades dos modos */
        ServerOptions.SPAWN_CREATURES = false;
        ServerOptions.DEFAULT_CHAT = false;
    }

    @Override
    public void onEnable() {
        super.onEnable();

        if (loader.getArchitect() == null) {
            getLogger().severe("Não foi possível carregar as configurações...");

            Bukkit.shutdown();
        } else
            loader.getArchitect().load();

        com.minecraft.lobby.halloween.Halloween.load();

        new BukkitCommandHandler(this).handle(Constant.SOURCE_DIR + ".lobby.command");
        new ListenerHandler(this).handle(Constant.SOURCE_DIR + ".lobby");

        getLogger().info("Lobby iniciado com sucesso.");
    }

    @Override
    public void onDisable() {
        super.onDisable();

        loader.getArchitect().unload();

        getLogger().info("Lobby encerrado com sucesso.");
    }
}
