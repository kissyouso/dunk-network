package com.minecraft.event.game.types;

import com.minecraft.core.Core;
import com.minecraft.core.api.item.Item;
import com.minecraft.event.game.GameEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Collections;
import java.util.List;

/**
 * Spleef: chão de neve (chao1 x chao2). Todos ganham uma pá; quem cair abaixo do chão é eliminado.
 */
public class Spleef extends GameEvent {

    private int grace;

    @Override
    public void start() {
        fillFloor();

        Location entrance = point("entrada");

        for (Player player : getAlivePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(entrance);
            player.getInventory().setItem(0, Item.of(Material.DIAMOND_SPADE).unbreakable().enchantment(Enchantment.DIG_SPEED, 5));
            player.updateInventory();
        }

        grace = 3;
        getManager().broadcastLocal("§2§lEVENTO §8» §fVocês podem quebrar a neve em §a3 segundos§f!");
    }

    @Override
    public void stop() {
        fillFloor();
    }

    /* Recoloca a neve em todo o chão */
    private void fillFloor() {
        Location a = point("chao1"), b = point("chao2");

        if (a == null || b == null) return;

        World world = a.getWorld();

        int minX = Math.min(a.getBlockX(), b.getBlockX()), maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY()), maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ()), maxZ = Math.max(a.getBlockZ(), b.getBlockZ());

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    world.getBlockAt(x, y, z).setType(Material.SNOW_BLOCK);
    }

    private boolean inFloor(Location location) {
        Location a = point("chao1"), b = point("chao2");

        if (a == null || b == null || !location.getWorld().equals(a.getWorld())) return false;

        return location.getBlockX() >= Math.min(a.getBlockX(), b.getBlockX()) && location.getBlockX() <= Math.max(a.getBlockX(), b.getBlockX())
                && location.getBlockY() >= Math.min(a.getBlockY(), b.getBlockY()) && location.getBlockY() <= Math.max(a.getBlockY(), b.getBlockY())
                && location.getBlockZ() >= Math.min(a.getBlockZ(), b.getBlockZ()) && location.getBlockZ() <= Math.max(a.getBlockZ(), b.getBlockZ());
    }

    @Override
    public void tick(int tick) {
        if (tick % 20 == 0 && grace > 0) {
            grace--;

            if (grace == 0)
                getAlivePlayers().forEach(player -> Core.getAccountController().of(player.getUniqueId()).title("§a§lJÁ!", "§fQuebre a neve!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageBlock(BlockDamageEvent event) {
        if (!isAlive(event.getPlayer())) return;

        if (grace > 0 || event.getBlock().getType() != Material.SNOW_BLOCK || !inFloor(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        event.setInstaBreak(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (!isAlive(event.getPlayer())) return;

        if (grace > 0 || event.getBlock().getType() != Material.SNOW_BLOCK || !inFloor(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        /* Remove a neve na mão (sem drop), independente das proteções do servidor */
        event.setCancelled(true);
        event.getBlock().setType(Material.AIR);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!isAlive(player)) return;

        double floor = Math.min(point("chao1").getY(), point("chao2").getY());

        if (event.getTo().getY() < floor - 1)
            eliminate(player, "caiu");
    }

    @Override
    public List<String> getSidebar() {
        return Collections.singletonList(grace > 0 ? "Liberado em: §e" + grace + "s" : "§aQuebre a neve!");
    }
}
