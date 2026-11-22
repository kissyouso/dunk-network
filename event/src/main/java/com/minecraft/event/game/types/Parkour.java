package com.minecraft.event.game.types;

import com.minecraft.event.game.GameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;

/**
 * Parkour (corrida): o primeiro a pisar na placa de pressão de OURO vence.
 * Placas de FERRO são checkpoints. Quem cair volta para o último checkpoint.
 */
public class Parkour extends GameEvent {

    protected final Map<UUID, Location> checkpoints = new HashMap<>();

    @Override
    public void start() {
        Location entrance = point("entrada");

        for (Player player : getAlivePlayers())
            player.teleport(entrance);
    }

    protected Location checkpoint(Player player) {
        return checkpoints.getOrDefault(player.getUniqueId(), point("entrada"));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!isAlive(player)) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ())
            return;

        handleMove(player, event);
    }

    protected void handleMove(Player player, PlayerMoveEvent event) {
        Block block = event.getTo().getBlock();

        if (block.getType() == Material.GOLD_PLATE) {
            win(player);
            return;
        }

        if (block.getType() == Material.IRON_PLATE) {
            Location location = block.getLocation().add(0.5, 0, 0.5);
            location.setYaw(player.getLocation().getYaw());

            Location current = checkpoints.get(player.getUniqueId());

            if (current == null || current.getBlockX() != location.getBlockX() || current.getBlockZ() != location.getBlockZ()) {
                checkpoints.put(player.getUniqueId(), location);
                player.sendMessage("§2§lEVENTO §8» §aCheckpoint salvo!");
                player.playSound(player.getLocation(), Sound.ORB_PICKUP, 1F, 1F);
            }
            return;
        }

        if (event.getTo().getY() < point("entrada").getY() - 15 || block.getType() == Material.WATER || block.getType() == Material.STATIONARY_WATER
                || block.getType() == Material.LAVA || block.getType() == Material.STATIONARY_LAVA) {
            player.setFallDistance(0);
            player.teleport(checkpoint(player));
        }
    }

    @Override
    public List<String> getSidebar() {
        return Arrays.asList("§fChegue na placa", "§fde §6ouro§f primeiro!");
    }
}
