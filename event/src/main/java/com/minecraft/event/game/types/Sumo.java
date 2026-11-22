package com.minecraft.event.game.types;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Sumo: só empurrões (sem dano). Cai da plataforma ou toca na água = eliminado.
 */
public class Sumo extends DuelTournament {

    @Override
    protected void prepare(Player player) {
    }

    @Override
    public boolean allowDamage(Player victim, EntityDamageEvent event) {
        if (!isFighting(victim) || isFrozen()) return false;

        /* Só a pancada entre os dois lutadores: empurra mas não tira vida */
        if (event instanceof EntityDamageByEntityEvent) {
            org.bukkit.entity.Entity damager = ((EntityDamageByEntityEvent) event).getDamager();

            if (damager instanceof Player && isFighting((Player) damager)) {
                event.setDamage(0);
                return true;
            }
        }

        return false;
    }

    @EventHandler
    public void onFall(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!isFighting(player) || isFrozen()) return;

        Material block = event.getTo().getBlock().getType();
        double platform = Math.min(point("pos1").getY(), point("pos2").getY());

        if (event.getTo().getY() < platform - 2 || block == Material.WATER || block == Material.STATIONARY_WATER
                || block == Material.LAVA || block == Material.STATIONARY_LAVA)
            lose(player, "caiu da plataforma");
    }
}
