package com.minecraft.academy.command;

import com.minecraft.academy.arena.Arena;
import com.minecraft.core.bukkit.command.structure.BukkitCommandContext;
import com.minecraft.core.command.CommandInheritor;
import com.minecraft.core.command.annotation.Command;

public class SpawnCommand implements CommandInheritor {

    @Command(name = "spawn")
    public void spawn(BukkitCommandContext context) {
        Arena.backToLobby(context.getPlayer());
    }
}
