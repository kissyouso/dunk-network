package com.minecraft.academy.listener;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.minecraft.academy.Academy;
import com.minecraft.academy.arena.Arena;
import com.minecraft.academy.arena.types.Timers;
import com.minecraft.academy.user.User;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

public class ServerListener implements Listener {

    private final ImmutableSet<Material> blockedInteractions = Sets.immutableEnumSet(Material.CHEST, Material.TRAPPED_CHEST,
            Material.ENCHANTMENT_TABLE, Material.ANVIL, Material.FURNACE, Material.WORKBENCH, Material.JUKEBOX, Material.ENDER_CHEST,
            Material.HOPPER, Material.DROPPER, Material.DISPENSER, Material.BREWING_STAND, Material.TRAP_DOOR, Material.ITEM_FRAME);

    /* Ao trocar de mundo (ex.: /academy go ou teleporte), o jogador entra na arena daquele mundo */
    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        User user = User.fetch(event.getPlayer().getUniqueId());

        if (user == null) return;

        Arena arena = Academy.getAcademy().getArenaStorage().getArena(event.getPlayer().getWorld());

        if (arena != null && user.getArena() != arena)
            user.sendTo(arena);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        User user = User.fetch(event.getEntity().getUniqueId());

        if (user == null || !(user.getArena() instanceof Timers)) return;

        event.setCancelled(true);

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID)
            event.getEntity().teleport(user.getArena().getSpawn());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player)
            event.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();

        if (block != null && blockedInteractions.contains(block.getType()) && !canBuild(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (!canBuild(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (!canBuild(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!canBuild(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!canBuild(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState())
            event.setCancelled(true);
    }

    @EventHandler
    public void onExplosion(ExplosionPrimeEvent event) {
        event.setCancelled(true);
    }

    private boolean canBuild(Player player) {
        User user = User.fetch(player.getUniqueId());

        return user != null && user.isBuild();
    }
}
