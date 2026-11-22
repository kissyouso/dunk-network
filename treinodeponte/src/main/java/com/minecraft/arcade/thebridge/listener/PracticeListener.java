package com.minecraft.arcade.thebridge.listener;

import com.minecraft.arcade.thebridge.arcade.TheBridgeArcade;
import com.minecraft.arcade.thebridge.arcade.arena.TheBridgeArena;
import com.minecraft.arcade.thebridge.user.TheBridgeUser;
import com.minecraft.core.Core;
import com.minecraft.core.arcade.ArcadeHolder;
import com.minecraft.core.arcade.room.Room;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.event.type.update.type.UpdateType;
import com.minecraft.core.bukkit.event.type.update.type.list.AsyncUpdateEvent;
import com.minecraft.core.bukkit.user.UserModel;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class PracticeListener implements Listener {

    private TheBridgeUser user(Player player) {
        return (TheBridgeUser) UserModel.of(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        TheBridgeUser user = user(event.getPlayer());

        if (user == null) return;

        TheBridgeArena arena = user.getArena();

        if (arena.getWorld() == null || !arena.getWorld().equals(event.getBlock().getWorld())) {
            event.setCancelled(true);
            return;
        }

        arena.trackBlock(event.getPlayer(), event.getBlockPlaced());
        user.addBlock();

        if (!user.isRunning())
            arena.startRun(user);

        user.getArcade().updateSidebar(user);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        TheBridgeUser user = user(event.getPlayer());

        if (user == null) return;

        if (!user.getArena().isTracked(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        user.getArena().untrackBlock(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPressurePlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;

        org.bukkit.block.Block block = event.getClickedBlock();

        if (block == null) return;

        Material type = block.getType();

        if (type != Material.STONE_PLATE && type != Material.WOOD_PLATE
                && type != Material.GOLD_PLATE && type != Material.IRON_PLATE)
            return;

        TheBridgeUser user = user(event.getPlayer());

        if (user == null) return;

        /* Placa de pressao da ilha: volta para o spawn e limpa a tentativa. */
        event.setCancelled(true);

        user.getArena().resetAttempt(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (user(event.getPlayer()) != null)
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        if (user((Player) event.getWhoClicked()) == null) return;

        /* So o inventario do proprio jogador: os menus de cosmeticos tem
         * holder proprio e continuam funcionando normalmente. */
        if (event.getInventory().getHolder() instanceof Player)
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        TheBridgeUser user = user(event.getPlayer());

        if (user == null) return;

        TheBridgeArena arena = user.getArena();

        Location to = event.getTo(), spawn = arena.getSpawn();

        if (spawn == null) return;

        user.updateDistance(spawn, to);

        arena.checkVoid(event.getPlayer(), user);

        if (!user.isRunning()) return;

        if (event.getFrom().getBlockX() != to.getBlockX()
                || event.getFrom().getBlockY() != to.getBlockY()
                || event.getFrom().getBlockZ() != to.getBlockZ())
            arena.checkFinish(event.getPlayer(), user);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        TheBridgeUser user = user(event.getPlayer());

        if (user == null) return;

        user.updateActionBar();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        if (user((Player) event.getEntity()) != null)
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        if (user((Player) event.getEntity()) != null)
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onUpdate(AsyncUpdateEvent event) {
        if (!event.isType(UpdateType.SECOND)) return;

        Core.getPlatform().runSync(() -> {
            for (ArcadeHolder holder : BukkitCore.getManager().getArcade().getArcades()) {
                if (!(holder instanceof TheBridgeArcade)) continue;

                TheBridgeArcade arcade = (TheBridgeArcade) holder;

                for (Room room : holder.getRooms()) {
                    if (!(room instanceof TheBridgeArena)) continue;

                    TheBridgeArena arena = (TheBridgeArena) room;

                    if (!arena.isReady()) continue;

                    Location spawn = arena.getSpawn();

                    for (Player player : arena.getPlayers()) {
                        TheBridgeUser user = user(player);

                        if (user == null || !player.isOnline()) continue;

                        if (spawn != null)
                            user.updateDistance(spawn, player.getLocation());

                        arena.checkVoid(player, user);

                        if (user.isRunning())
                            arena.checkFinish(player, user);

                        arcade.updateSidebar(user);
                    }
                }
            }
        });
    }
}
