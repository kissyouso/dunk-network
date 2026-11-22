package com.minecraft.event.listener;

import com.minecraft.event.Events;
import com.minecraft.event.game.EventManager;
import com.minecraft.event.game.GameEvent;
import com.minecraft.event.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Regras gerais do servidor de eventos: sem dano, sem construir e sem fome (exceto quando o evento permite).
 */
public class ProtectionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        EventManager manager = Events.getEvents().getEventManager();

        /* Fora de um evento: sem dano e quem cair no void volta ao spawn */
        if (manager.getPhase() != EventManager.Phase.RUNNING || manager.getEvent() == null || !manager.getEvent().isAlive(player)) {
            event.setCancelled(true);

            if (event.getCause() == EntityDamageEvent.DamageCause.VOID)
                player.teleport(manager.isSpectator(player) ? Events.getEvents().getLocations().getSpectator() : Events.getEvents().getLocations().getSpawn());
            return;
        }

        GameEvent game = manager.getEvent();

        /* O evento decide; quem morreria é eliminado pelo próprio evento */
        if (!game.allowDamage(player, event))
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

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && !canBuild(event.getPlayer())) {
            switch (event.getClickedBlock().getType()) {
                case CHEST:
                case TRAPPED_CHEST:
                case FURNACE:
                case WORKBENCH:
                case ANVIL:
                case ENCHANTMENT_TABLE:
                case HOPPER:
                case DISPENSER:
                case DROPPER:
                case BREWING_STAND:
                case TRAP_DOOR:
                case WOODEN_DOOR:
                case FENCE_GATE:
                    event.setCancelled(true);
                    break;
                default:
                    break;
            }
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!canBuild(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        if (!canBuild(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState())
            event.setCancelled(true);
    }

    private boolean canBuild(Player player) {
        User user = User.fetch(player.getUniqueId());

        return user != null && user.isBuild();
    }
}
