package com.minecraft.event.game.types;

import com.minecraft.core.api.item.Item;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Fight: duelos 1v1 com kit (espada de diamante, armadura de ferro e sopas). Morreu = eliminado.
 */
public class Fight extends DuelTournament {

    @Override
    protected void prepare(Player player) {
        player.getInventory().setItem(0, Item.of(Material.DIAMOND_SWORD).unbreakable());

        for (int slot = 1; slot < 9; slot++)
            player.getInventory().setItem(slot, new ItemStack(Material.MUSHROOM_SOUP));

        player.getInventory().setArmorContents(new ItemStack[]{
                Item.of(Material.IRON_BOOTS).unbreakable(), Item.of(Material.IRON_LEGGINGS).unbreakable(),
                Item.of(Material.IRON_CHESTPLATE).unbreakable(), Item.of(Material.IRON_HELMET).unbreakable()});

        player.updateInventory();
    }

    @Override
    public boolean allowDamage(Player victim, EntityDamageEvent event) {
        if (!isFighting(victim) || isFrozen()) return false;

        /* Dano de outro jogador: só o adversário pode bater */
        if (event instanceof EntityDamageByEntityEvent) {
            if (!(((EntityDamageByEntityEvent) event).getDamager() instanceof Player)
                    || !isFighting((Player) ((EntityDamageByEntityEvent) event).getDamager()))
                return false;
        }

        if (victim.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            lose(victim, "morreu");
            return false;
        }

        return true;
    }
}
