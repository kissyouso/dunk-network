package com.minecraft.arcade.thebridge;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.option.ServerOptions;
import com.minecraft.core.bukkit.command.structure.BukkitCommandHandler;
import com.minecraft.core.bukkit.listener.handler.ListenerHandler;
import com.minecraft.core.server.type.ServerType;
import lombok.Getter;

public class TheBridge extends BukkitCore {

    @Getter
    private static TheBridge instance;

    @Override
    public void onLoad() {
        super.onLoad();

        instance = this;

        saveDefaultConfig();

        /* Treino: sem dano, sem fome, sem drops e com construcao liberada. */
        ServerOptions.DAMAGE_ENABLED = false;
        ServerOptions.FOOD_ENABLED = false;
        ServerOptions.DROP_ITEM_ENABLED = false;
        ServerOptions.DEFAULT_CHAT = false;

        ServerOptions.BLOCK_PLACE = true;
        ServerOptions.BLOCK_BREAK = true;
        ServerOptions.DEFAULT_BLOCK_BREAK = false;

        ServerOptions.BLOCK_INTERACTION_ENABLED = true;

        ServerOptions.SPAWN_CREATURES = false;

        Core.setServerType(ServerType.THE_BRIDGE);
    }

    @Override
    public void onEnable() {
        super.onEnable();

        getManager().getArcade().handle(this, Constant.SOURCE_DIR + ".arcade.thebridge.arcade.list");

        new BukkitCommandHandler(this).handle(Constant.SOURCE_DIR + ".arcade.thebridge.command");
        new ListenerHandler(this).handle(Constant.SOURCE_DIR + ".arcade.thebridge.listener");

        getLogger().info("Treino de Ponte iniciado com sucesso.");
    }

    @Override
    public void onDisable() {
        super.onDisable();

        getLogger().info("Treino de Ponte encerrado com sucesso.");
    }
}
